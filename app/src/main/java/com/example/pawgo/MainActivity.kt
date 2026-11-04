package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행 화면. 지도를 보여 주고, 버튼이나 음성으로 남긴 기록을 바로 지도에 표시한다. */
class MainActivity : Activity(), NaviManager.Callback {

    private lateinit var naviManager: NaviManager
    private lateinit var petCheckIn: PetCheckIn
    private lateinit var petMap: PetMap
    private lateinit var recordStore: PetRecordStore
    private lateinit var tripTracker: TripTracker
    private var isResumedNow = false
    private var isCheckInScheduled = false

    private lateinit var locationStatus: TextView
    private lateinit var tripButton: Button
    private lateinit var lastRecord: TextView
    private lateinit var recordCount: TextView
    private lateinit var recordList: TextView
    private val behaviorButtons = mutableMapOf<PetBehavior, Button>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
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
        locationStatus = findViewById(R.id.locationStatus)
        tripButton = findViewById(R.id.tripButton)
        petMap = PetMap(findViewById<FrameLayout>(R.id.mapContainer))
        setUpPetRecording()

        // 창 분할·크기 변경으로 화면이 다시 만들어져도 보던 상태를 이어서 보여 준다
        savedInstanceState?.getCharSequence(STATE_LAST_RECORD)?.let { lastRecord.text = it }

        naviManager = NaviManager(this, this)
        naviManager.connect()

        petCheckIn = PetCheckIn(this, onRecorded = ::recordBehavior, onEvent = ::onCheckInEvent)
        petCheckIn.connect()
        onTripChanged()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(STATE_LAST_RECORD, lastRecord.text)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        naviManager.release()
        petCheckIn.release()
        petMap.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        petMap.onResume()
        // 화면에 없을 때 주행이 끝났다면 다시 보일 때 결과를 보여 준다
        showPendingTripResult()
    }

    override fun onPause() {
        isResumedNow = false
        petMap.onPause()
        super.onPause()
    }

    override fun onConnected() {
        locationStatus.text = getString(R.string.location_connected)
        // 주행 전에도 지도가 현재 위치를 보여 주도록 한 번 요청한다
        naviManager.requestCurrentLocation()
        updateLocationPolling()
    }

    override fun onConnectionFailed(throwable: Throwable) {
        locationStatus.text = getString(R.string.location_failed, throwable.message.orEmpty())
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        currentNaviStatus = status
    }

    override fun onCurrentLocation(address: String, latitude: Double, longitude: Double) {
        petMap.moveToLocation(latitude, longitude)

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

    private fun confirmEndTrip() {
        AlertDialog.Builder(this)
            .setTitle(R.string.end_trip_title)
            .setMessage(R.string.end_trip_message)
            .setPositiveButton(R.string.end_trip_confirm) { _, _ ->
                tripTracker.endTrip(System.currentTimeMillis())
                lastRecord.setText(R.string.last_record_empty)
                onTripChanged()
                showPendingTripResult()
            }
            .setNegativeButton(R.string.clear_records_cancel, null)
            .show()
    }

    /** 주행 시작·종료에 맞춰 버튼, 지도, 위치 요청, 음성 질문을 맞춘다. */
    private fun onTripChanged() {
        if (tripTracker.isActive) {
            tripButton.setText(R.string.action_end_trip)
            tripButton.setOnClickListener { confirmEndTrip() }
        } else {
            tripButton.setText(R.string.action_start_trip)
            tripButton.setOnClickListener { startTrip() }
        }
        renderRecords()
        updateLocationPolling()
        updateCheckInSchedule()
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

    /** 주행 중이면 15분마다 음성으로 상태를 묻고, 주행이 끝나면 멈춘다. */
    private fun updateCheckInSchedule() {
        if (!tripTracker.isActive) {
            mainHandler.removeCallbacksAndMessages(CHECK_IN_TOKEN)
            isCheckInScheduled = false
            petCheckIn.cancel()
            return
        }
        if (isCheckInScheduled) return
        isCheckInScheduled = true
        mainHandler.postAtTime(
            {
                isCheckInScheduled = false
                petCheckIn.ask()
                updateCheckInSchedule()
            },
            CHECK_IN_TOKEN,
            SystemClock.uptimeMillis() + CHECK_IN_INTERVAL_MS
        )
    }

    private fun showPendingTripResult() {
        if (!isResumedNow) return
        val result = tripTracker.pendingResult ?: return
        tripTracker.clearPendingResult()
        startActivity(TripSummaryActivity.intent(this, result))
    }

    private fun onCheckInEvent(message: String) {
        locationStatus.text = message
    }

    private fun setUpPetRecording() {
        lastRecord = findViewById(R.id.lastRecord)
        recordCount = findViewById(R.id.recordCount)
        recordList = findViewById(R.id.recordList)

        val container = findViewById<LinearLayout>(R.id.behaviorButtons)
        val buttonHeight = resources.getDimensionPixelSize(R.dimen.behavior_button_height)
        val gap = resources.getDimensionPixelSize(R.dimen.behavior_button_gap)
        PetBehavior.entries.chunked(BEHAVIOR_COLUMNS).forEachIndexed { rowIndex, rowBehaviors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowBehaviors.forEachIndexed { columnIndex, behavior ->
                val button = Button(this).apply {
                    textSize = 22f
                    setOnClickListener { recordBehavior(behavior) }
                }
                row.addView(button, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply {
                    if (columnIndex > 0) marginStart = gap
                })
                behaviorButtons[behavior] = button
            }
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = gap
            })
        }

        findViewById<Button>(R.id.clearRecordsButton).setOnClickListener { confirmClearRecords() }
        findViewById<Button>(R.id.exportRecordsButton).setOnClickListener { exportRecords() }
    }

    private fun recordBehavior(behavior: PetBehavior) {
        val now = System.currentTimeMillis()
        // 주행 시작을 누르지 않고 기록해도 이번 주행으로 남긴다
        if (!tripTracker.isActive) {
            tripTracker.startTrip(now)
            onTripChanged()
        }
        val record = PetRecord(
            timeMillis = now,
            behavior = behavior,
            naviStatus = currentNaviStatus?.name,
            address = null,
        )
        records.add(0, record)
        recordStore.save(records)

        lastRecord.text = getString(
            R.string.last_record, timeFormat.format(Date(record.timeMillis)), getString(behavior.labelRes)
        )
        flashLastRecord()
        renderRecords()

        // 위치가 오면 onCurrentLocation에서 기록에 넣고 지도에 마커를 찍는다
        if (naviManager.isConnected) naviManager.requestCurrentLocation()
    }

    private fun flashLastRecord() {
        mainHandler.removeCallbacksAndMessages(FLASH_TOKEN)
        lastRecord.setBackgroundResource(R.color.last_record_flash)
        mainHandler.postAtTime(
            { lastRecord.setBackgroundResource(R.color.last_record_idle) },
            FLASH_TOKEN,
            SystemClock.uptimeMillis() + FLASH_DURATION_MS
        )
    }

    /** 이번 주행의 기록만 버튼 횟수, 목록, 지도에 보여 준다. */
    private fun renderRecords() {
        val tripStart = tripTracker.activeStartMillis
        val tripRecords = if (tripStart == null) emptyList() else records.filter { it.timeMillis >= tripStart }
        val counts = tripRecords.groupingBy { it.behavior }.eachCount()
        behaviorButtons.forEach { (behavior, button) ->
            button.text = getString(R.string.behavior_button, getString(behavior.labelRes), counts[behavior] ?: 0)
        }
        recordCount.text = getString(R.string.record_count, tripRecords.size)
        recordList.text = tripRecords.take(MAX_RECORDS_SHOWN).joinToString("\n") { it.toDisplayText() }
        petMap.showRecords(tripRecords)
    }

    private fun PetRecord.toDisplayText(): String = buildString {
        append(timeFormat.format(Date(timeMillis)))
        append("  ")
        append(getString(behavior.labelRes))
        address?.let { append(" · ").append(it) }
    }

    private fun confirmClearRecords() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_records_title)
            .setPositiveButton(R.string.clear_records_confirm) { _, _ ->
                records.clear()
                recordStore.save(records)
                lastRecord.setText(R.string.last_record_empty)
                renderRecords()
            }
            .setNegativeButton(R.string.clear_records_cancel, null)
            .show()
    }

    private fun exportRecords() {
        if (records.none { it.latitude != null && it.longitude != null }) {
            Toast.makeText(this, R.string.export_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        val result = try {
            PetRecordExporter(this).export(records)
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.export_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.export_done_title)
            .setMessage(
                getString(
                    R.string.export_done_message,
                    result.exportedCount,
                    result.skippedCount,
                    result.file.absolutePath
                )
            )
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    companion object {
        private const val MAX_RECORDS_SHOWN = 50
        private const val BEHAVIOR_COLUMNS = 3
        private const val FLASH_DURATION_MS = 1500L
        private const val LOCATION_FILL_WINDOW_MS = 10_000L
        private const val LOCATION_POLL_INTERVAL_MS = 5_000L
        private const val CHECK_IN_INTERVAL_MS = 15 * 60 * 1000L
        private val FLASH_TOKEN = Any()
        private val LOCATION_TOKEN = Any()
        private val CHECK_IN_TOKEN = Any()

        private const val STATE_LAST_RECORD = "lastRecord"
    }
}
