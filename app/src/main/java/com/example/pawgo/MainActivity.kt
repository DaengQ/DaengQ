package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 주행 화면(PRD 화면 5). 지도 위에 기록 패널(종류별 횟수, 최근 기록 고치기, 지난 기록, 주행 종료)과
 * 말하기 버튼을 띄운다. 강아지 소리는 자동으로 감지해 기록하고 음성으로 알린다.
 * 직접 기록은 횟수 칸을 누르거나 말하기 버튼으로 말로 하고, 고치기는 최근 기록 버튼이나 말로 한다.
 */
class MainActivity : Activity(), NaviManager.Callback {

    private lateinit var naviManager: NaviManager
    private lateinit var voiceFix: PetVoiceFix
    private lateinit var petMap: PetMap
    private lateinit var soundDetector: PetSoundDetector
    private val soundFilter = PetSoundFilter()
    private lateinit var recordStore: PetRecordStore
    private lateinit var tripTracker: TripTracker
    private lateinit var routeStore: RouteStore
    /** 진행 중인 주행의 지나온 길. */
    private var route = mutableListOf<RoutePoint>()
    private var isResumedNow = false
    private var isMicPermissionRequested = false
    private var soundError: String? = null

    private lateinit var driveTitle: TextView
    private lateinit var driveAlert: TextView
    private lateinit var soundStatus: TextView
    private lateinit var tripButton: Button
    private lateinit var latestRecord: View
    private lateinit var latestRecordEmpty: View
    private lateinit var latestRecordActions: LinearLayout
    private lateinit var olderRecords: LinearLayout
    private lateinit var voiceTip: View
    private lateinit var voiceTipText: TextView
    private lateinit var countViews: Map<PetBehavior, TextView>
    private lateinit var latestChoices: RecordChoiceRow

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 최신순 기록. */
    private lateinit var records: MutableList<PetRecord>
    private var currentNaviStatus: NavigationStatus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordStore = PetRecordStore(this)
        records = recordStore.load()
        tripTracker = TripTracker(this)
        routeStore = RouteStore(this)
        routeStore.prune(System.currentTimeMillis() - ROUTE_KEEP_MS)
        driveTitle = findViewById(R.id.driveTitle)
        driveAlert = findViewById(R.id.driveAlert)
        soundStatus = findViewById(R.id.soundStatus)
        tripButton = findViewById(R.id.tripButton)
        latestRecord = findViewById(R.id.latestRecord)
        latestRecordEmpty = findViewById(R.id.latestRecordEmpty)
        latestRecordActions = findViewById(R.id.latestRecordActions)
        olderRecords = findViewById(R.id.olderRecords)
        voiceTip = findViewById(R.id.voiceTip)
        voiceTipText = findViewById(R.id.voiceTipText)
        petMap = PetMap(findViewById<FrameLayout>(R.id.mapContainer))
        // 마이크 없이도 기록할 수 있도록 횟수 칸을 누르면 그 종류를 직접 기록한다
        countViews = buildCountColumns(findViewById(R.id.driveCounts)) { behavior -> recordBehavior(behavior) }
        latestChoices = RecordChoiceRow(
            this,
            latestRecordActions,
            onType = { behavior -> latestTripRecord()?.let { changeRecord(it, behavior) } },
            onDelete = { latestTripRecord()?.let { deleteRecord(it) } },
        )

        naviManager = NaviManager(this, this)
        naviManager.connect()

        voiceFix = PetVoiceFix(
            this,
            onCommand = ::applyCommand,
            onHeard = ::showHeard,
            onError = { showAlert(getString(R.string.voice_error, it)) },
            onBusyChanged = ::updateSoundDetection,
        )
        voiceFix.connect()
        findViewById<View>(R.id.voiceFixButton).setOnClickListener {
            hideVoiceTip()
            voiceFix.start()
        }

        soundDetector = PetSoundDetector(this, ::onSoundDetected, ::onSoundError)
        onTripChanged()
        // 말하기 버튼이 무엇인지 처음에 잠깐 알려 준다
        showVoiceTip(getString(R.string.voice_tip))
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        soundDetector.stop()
        naviManager.release()
        voiceFix.release()
        petMap.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        // 결과 화면에서 기록을 고쳤을 수 있으니 다시 읽는다
        records = recordStore.load()
        renderRecords()
        petMap.onResume()
        // 화면에 없을 때 주행이 끝났다면 다시 보일 때 결과를 보여 준다
        showPendingTripResult()
        updateSoundDetection()
    }

    override fun onPause() {
        isResumedNow = false
        updateSoundDetection()
        petMap.onPause()
        super.onPause()
    }

    override fun onConnected() {
        driveAlert.visibility = View.GONE
        // 주행 전에도 지도가 현재 위치를 보여 주도록 한 번 요청한다
        naviManager.requestCurrentLocation()
        updateLocationPolling()
    }

    override fun onConnectionFailed(throwable: Throwable) {
        showAlert(getString(R.string.location_failed, throwable.message.orEmpty()))
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        currentNaviStatus = status
    }

    override fun onCurrentLocation(address: String, latitude: Double, longitude: Double) {
        petMap.moveToLocation(latitude, longitude)
        // 주행 중 처음 받은 위치가 출발 지점이고, 받은 위치를 이어 지나온 길을 그린다
        tripTracker.recordStartLocation(latitude, longitude)
        showStartPoint()
        if (tripTracker.isActive) {
            val point = RoutePoint(System.currentTimeMillis(), latitude, longitude)
            if (routeStore.add(point)) {
                route.add(point)
                petMap.showRoute(route.toList())
            }
        }

        // 기록 직후 요청한 위치로, 아직 위치가 비어 있는 최근 기록을 채운다
        val now = System.currentTimeMillis()
        var changed = false
        records.replaceAll { record ->
            if (record.latitude == null && now - record.timeMillis <= LOCATION_FILL_WINDOW_MS) {
                changed = true
                record.copy(
                    address = address.ifBlank { null },
                    latitude = latitude,
                    longitude = longitude,
                )
            } else {
                record
            }
        }
        if (changed) {
            recordStore.save(records)
            renderRecords()
        }
    }

    private fun startTrip() {
        tripTracker.startTrip(System.currentTimeMillis())
        onTripChanged()
    }

    /** 확인 없이 바로 끝내고 결과 화면으로 간다. */
    private fun endTrip() {
        tripTracker.endTrip(System.currentTimeMillis())
        onTripChanged()
        showPendingTripResult()
    }

    /** 주행 시작·완료에 맞춰 제목, 버튼, 지도, 위치 요청, 소리 감지를 맞춘다. */
    private fun onTripChanged() {
        // 받침 있는 이름은 '이'를 붙여 부른다. 예: 봄이와 드라이브중...
        val name = petName().withCallingI()
        driveTitle.text = getString(
            if (tripTracker.isActive) R.string.drive_title_active else R.string.drive_title_idle,
            name,
        )
        if (tripTracker.isActive) {
            tripButton.setText(R.string.action_end_trip)
            tripButton.setOnClickListener { endTrip() }
        } else {
            tripButton.setText(R.string.action_start_trip)
            tripButton.setOnClickListener { startTrip() }
            voiceFix.cancel()
        }
        renderRecords()
        showStartPoint()
        val tripStart = tripTracker.activeStartMillis
        route = if (tripStart == null) mutableListOf()
        else routeStore.load(tripStart, Long.MAX_VALUE).toMutableList()
        petMap.showRoute(route.toList())
        updateLocationPolling()
        updateSoundDetection()
    }

    /** 진행 중인 주행의 출발 지점을 지도에 네모로 보여 준다. */
    private fun showStartPoint() {
        val start = tripTracker.activeStartLocation
        if (start == null) petMap.hideStart() else petMap.showStart(start.first, start.second)
    }

    /** 주행 중이고 화면에 보이는 동안 소리를 감지한다. 앱이 말하거나 듣는 동안에는 잠시 멈춘다. */
    private fun updateSoundDetection() {
        val tripActive = tripTracker.isActive
        if (tripActive && !hasMicPermission()) {
            soundDetector.stop()
            soundStatus.setText(R.string.sound_no_permission)
            if (isResumedNow && !isMicPermissionRequested) {
                isMicPermissionRequested = true
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            }
            return
        }
        val error = soundError
        if (tripActive && isResumedNow && !voiceFix.isBusy && error == null) {
            soundDetector.start()
        } else {
            soundDetector.stop()
        }
        soundStatus.text = when {
            error != null -> getString(R.string.sound_error, error)
            !tripActive -> getString(R.string.sound_off)
            voiceFix.isBusy -> getString(R.string.sound_paused)
            else -> getString(R.string.sound_listening)
        }
    }

    private fun hasMicPermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) updateSoundDetection()
    }

    private fun onSoundDetected(behavior: PetBehavior) {
        if (!tripTracker.isActive) return
        val decision = soundFilter.onDetected(behavior, SystemClock.uptimeMillis())
        if (decision.record) recordBehavior(behavior, detected = true)
        if (decision.alert) voiceFix.announce(getString(behavior.soundAlertRes, petName().withIGa()))
    }

    private fun onSoundError(message: String) {
        soundError = message
        updateSoundDetection()
    }

    /** 주행 중에는 지도가 차를 따라가도록 현재 위치를 주기적으로 요청한다. */
    private fun updateLocationPolling() {
        mainHandler.removeCallbacksAndMessages(LOCATION_TOKEN)
        if (!tripTracker.isActive || !naviManager.isConnected) return
        naviManager.requestCurrentLocation()
        mainHandler.postAtTime(
            { updateLocationPolling() },
            LOCATION_TOKEN,
            SystemClock.uptimeMillis() + LOCATION_POLL_INTERVAL_MS
        )
    }

    private fun showPendingTripResult() {
        if (!isResumedNow) return
        val result = tripTracker.pendingResult ?: return
        tripTracker.clearPendingResult()
        startActivity(TripSummaryActivity.intent(this, result))
    }

    private fun recordBehavior(behavior: PetBehavior, detected: Boolean = false) {
        val now = System.currentTimeMillis()
        // 주행 시작을 누르지 않고 기록해도 이번 주행으로 남긴다
        if (!tripTracker.isActive) {
            tripTracker.startTrip(now)
            onTripChanged()
        }
        records.add(
            0,
            PetRecord(
                timeMillis = now,
                behavior = behavior,
                naviStatus = currentNaviStatus?.name,
                address = null,
                detected = detected,
            )
        )
        recordStore.save(records)
        renderRecords()

        // 위치가 오면 onCurrentLocation에서 기록에 넣고 지도에 마커를 찍는다
        if (naviManager.isConnected) naviManager.requestCurrentLocation()
    }

    private fun changeRecord(record: PetRecord, to: PetBehavior) {
        records.replaceAll { if (it.timeMillis == record.timeMillis) it.copy(behavior = to) else it }
        recordStore.save(records)
        renderRecords()
    }

    private fun deleteRecord(record: PetRecord) {
        records.removeAll { it.timeMillis == record.timeMillis }
        recordStore.save(records)
        renderRecords()
    }

    /** 말로 고치기 명령을 기록에 반영하고, 읽어 줄 결과 문장을 돌려준다. */
    private fun applyCommand(command: PetCommand): String = when (command) {
        is PetCommand.Change -> {
            val target = latestTripRecord(command.from)
            when {
                target == null -> nothingToFix(command.from)
                target.behavior == command.to -> getString(R.string.voice_fix_already, getString(command.to.labelRes))
                else -> {
                    changeRecord(target, command.to)
                    getString(R.string.voice_fix_changed, getString(target.behavior.labelRes), getString(command.to.labelRes))
                }
            }
        }
        is PetCommand.Delete -> {
            val target = latestTripRecord(command.target)
            if (target == null) {
                nothingToFix(command.target)
            } else {
                deleteRecord(target)
                getString(R.string.voice_fix_deleted, getString(target.behavior.labelRes))
            }
        }
        is PetCommand.Add -> {
            recordBehavior(command.behavior)
            getString(R.string.voice_fix_added, getString(command.behavior.labelRes))
        }
        PetCommand.Cancel, PetCommand.Unknown -> getString(R.string.voice_fix_cancel)
    }

    private fun nothingToFix(behavior: PetBehavior?): String =
        if (behavior == null) getString(R.string.voice_fix_nothing)
        else getString(R.string.voice_fix_nothing_of, getString(behavior.labelRes))

    /** 들은 말을 말하기 버튼 옆 말풍선에 잠깐 보여 준다. */
    private fun showHeard(text: String?) {
        showVoiceTip(text?.let { getString(R.string.voice_heard, it) } ?: getString(R.string.voice_heard_nothing))
    }

    /** 말하기 버튼 옆 말풍선을 [VOICE_TIP_DURATION_MS] 동안 보여 주고 사라지게 한다. */
    private fun showVoiceTip(text: String) {
        voiceTipText.text = text
        voiceTip.animate().cancel()
        voiceTip.alpha = 1f
        voiceTip.visibility = View.VISIBLE
        mainHandler.removeCallbacksAndMessages(VOICE_TIP_TOKEN)
        mainHandler.postAtTime(::hideVoiceTip, VOICE_TIP_TOKEN, SystemClock.uptimeMillis() + VOICE_TIP_DURATION_MS)
    }

    private fun hideVoiceTip() {
        mainHandler.removeCallbacksAndMessages(VOICE_TIP_TOKEN)
        if (voiceTip.visibility != View.VISIBLE) return
        voiceTip.animate().alpha(0f).setDuration(VOICE_TIP_FADE_MS)
            .withEndAction { voiceTip.visibility = View.GONE }
    }

    /** 위치 연결 실패, 음성 오류를 제목 아래에 보여 준다. */
    private fun showAlert(text: String) {
        driveAlert.text = text
        driveAlert.visibility = View.VISIBLE
    }

    private fun tripRecords(): List<PetRecord> {
        val tripStart = tripTracker.activeStartMillis ?: return emptyList()
        return records.filter { it.timeMillis >= tripStart }
    }

    /** 이번 주행에서 [behavior] 종류(null이면 아무 종류)의 가장 최근 기록. */
    private fun latestTripRecord(behavior: PetBehavior? = null): PetRecord? =
        tripRecords().firstOrNull { behavior == null || it.behavior == behavior }

    /** 이번 주행의 기록만 횟수, 최근 기록 카드, 지난 기록, 지도에 보여 준다. */
    private fun renderRecords() {
        val tripRecords = tripRecords()
        val counts = TripStats.typeCounts(tripRecords)
        showCounts(countViews, counts)

        val latest = tripRecords.firstOrNull()
        latestRecord.visibility = if (latest == null) View.GONE else View.VISIBLE
        latestRecordActions.visibility = latestRecord.visibility
        latestRecordEmpty.visibility = if (latest == null) View.VISIBLE else View.GONE
        if (latest != null) {
            bindRecordRow(latestRecord, latest)
            latestChoices.select(latest.behavior)
        }

        olderRecords.removeAllViews()
        tripRecords.drop(1).forEach { record ->
            val row = layoutInflater.inflate(R.layout.row_drive_record, olderRecords, false)
            bindRecordRow(row, record)
            olderRecords.addView(row)
        }
        olderRecords.visibility = if (olderRecords.childCount == 0) View.GONE else View.VISIBLE
        petMap.showRecords(tripRecords)
    }

    companion object {
        /** 경로는 결과 화면에서 다시 볼 수 있게 30일 동안 둔다. */
        private const val ROUTE_KEEP_MS = 30L * 24 * 60 * 60 * 1000
        private const val VOICE_TIP_DURATION_MS = 5_000L
        private const val VOICE_TIP_FADE_MS = 300L
        private const val LOCATION_FILL_WINDOW_MS = 10_000L
        private const val LOCATION_POLL_INTERVAL_MS = 5_000L
        private val LOCATION_TOKEN = Any()
        private val VOICE_TIP_TOKEN = Any()

        private const val REQUEST_MIC = 1
    }
}
