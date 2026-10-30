package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), NaviManager.Callback {

    private lateinit var naviManager: NaviManager
    private lateinit var recordStore: PetRecordStore
    private lateinit var tripTracker: TripTracker
    private var isResumedNow = false
    private var ignoreRouteCancelUntil = 0L

    private lateinit var connectionStatus: TextView
    private lateinit var navigationStatus: TextView
    private lateinit var searchKeyword: EditText
    private lateinit var eventLog: TextView
    private lateinit var naviButtons: List<Button>

    private lateinit var lastRecord: TextView
    private lateinit var recordCount: TextView
    private lateinit var recordList: TextView
    private val behaviorButtons = mutableMapOf<PetBehavior, Button>()

    private val events = ArrayDeque<String>()
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
        setUpPetRecording()

        connectionStatus = findViewById(R.id.connectionStatus)
        navigationStatus = findViewById(R.id.navigationStatus)
        searchKeyword = findViewById(R.id.searchKeyword)
        eventLog = findViewById(R.id.eventLog)

        val destination = NaviManager.SAMPLE_DESTINATION
        findViewById<TextView>(R.id.destinationName).text = destination.name
        findViewById<TextView>(R.id.destinationAddress).text = destination.address

        val startRouteButton = findViewById<Button>(R.id.startRouteButton)
        val cancelRouteButton = findViewById<Button>(R.id.cancelRouteButton)
        val destinationInfoButton = findViewById<Button>(R.id.destinationInfoButton)
        val searchButton = findViewById<Button>(R.id.searchButton)
        val showNavigationButton = findViewById<Button>(R.id.showNavigationButton)
        naviButtons = listOf(
            startRouteButton, cancelRouteButton, destinationInfoButton, searchButton, showNavigationButton
        )
        setNaviButtonsEnabled(false)

        // 창 분할·크기 변경으로 화면이 다시 만들어져도 보던 상태를 이어서 보여 준다
        savedInstanceState?.let { restoreState(it) }

        naviManager = NaviManager(this, this)

        startRouteButton.setOnClickListener {
            // 안내 중에 새 경로를 요청하면 기존 경로가 먼저 취소되므로, 그 취소로는 주행을 끝내지 않는다
            if (tripTracker.isActive) {
                ignoreRouteCancelUntil = SystemClock.uptimeMillis() + ROUTE_REPLACE_WINDOW_MS
            }
            naviManager.startRoute(destination)
        }
        cancelRouteButton.setOnClickListener { naviManager.cancelRoute() }
        destinationInfoButton.setOnClickListener { naviManager.requestDestinationInfo() }
        showNavigationButton.setOnClickListener { naviManager.showNavigation() }
        searchButton.setOnClickListener { searchPlace() }
        searchKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH && naviManager.isConnected) {
                searchPlace()
                true
            } else {
                false
            }
        }

        naviManager.connect()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EVENTS, ArrayList(events))
        outState.putString(STATE_NAVI_STATUS, currentNaviStatus?.name)
        outState.putCharSequence(STATE_LAST_RECORD, lastRecord.text)
    }

    private fun restoreState(state: Bundle) {
        state.getStringArrayList(STATE_EVENTS)?.let { saved ->
            events.addAll(saved)
            if (events.isNotEmpty()) eventLog.text = events.joinToString("\n")
        }
        state.getString(STATE_NAVI_STATUS)
            ?.let { name -> NavigationStatus.entries.firstOrNull { it.name == name } }
            ?.let { showNavigationStatus(it) }
        state.getCharSequence(STATE_LAST_RECORD)?.let { lastRecord.text = it }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        naviManager.release()
        super.onDestroy()
    }

    override fun onConnected() {
        connectionStatus.text = getString(R.string.status_connected)
        setNaviButtonsEnabled(true)
    }

    override fun onConnectionFailed(throwable: Throwable) {
        connectionStatus.text = getString(R.string.status_failed, throwable.message.orEmpty())
        setNaviButtonsEnabled(false)
    }

    override fun onNavigationStatus(status: NavigationStatus) {
        // Pawgo를 켜기 전에 시작된 경로 안내도 주행으로 잡는다
        if (status == NavigationStatus.ROUTE_DRIVE) tripTracker.onRouteStarted(System.currentTimeMillis())
        showNavigationStatus(status)
    }

    private fun showNavigationStatus(status: NavigationStatus) {
        currentNaviStatus = status
        navigationStatus.text = getString(
            when (status) {
                NavigationStatus.INACTIVE -> R.string.navi_status_inactive
                NavigationStatus.INITIALIZING -> R.string.navi_status_initializing
                NavigationStatus.FREE_DRIVE -> R.string.navi_status_free_drive
                NavigationStatus.ROUTE_DRIVE -> R.string.navi_status_route_drive
            }
        )
    }

    override fun onCurrentLocation(address: String, latitude: Double, longitude: Double) {
        // 버튼을 누른 직후 요청한 위치로, 아직 위치가 비어 있는 최근 기록을 채운다
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

    override fun onRouteStarted() {
        ignoreRouteCancelUntil = 0L
        tripTracker.onRouteStarted(System.currentTimeMillis())
    }

    override fun onRouteCancelled() {
        if (SystemClock.uptimeMillis() < ignoreRouteCancelUntil) {
            ignoreRouteCancelUntil = 0L
            return
        }
        // 내비에서 안내를 종료하면 주행도 끝난 것으로 보고 바로 결과를 보여 준다
        if (!tripTracker.isActive) return
        tripTracker.endTrip(System.currentTimeMillis())
        showPendingTripResult()
    }

    override fun onDestinationArrived(name: String, durationSeconds: Int, distanceMeters: Int) {
        tripTracker.endTrip(System.currentTimeMillis(), name, durationSeconds, distanceMeters)
        showPendingTripResult()
    }

    override fun onResume() {
        super.onResume()
        isResumedNow = true
        // 화면에 없을 때 주행이 끝났다면 다시 보일 때 결과를 보여 준다
        showPendingTripResult()
    }

    override fun onPause() {
        isResumedNow = false
        super.onPause()
    }

    private fun confirmEndTrip() {
        AlertDialog.Builder(this)
            .setTitle(R.string.end_trip_title)
            .setMessage(R.string.end_trip_message)
            .setPositiveButton(R.string.end_trip_confirm) { _, _ ->
                tripTracker.endTrip(System.currentTimeMillis())
                showPendingTripResult()
            }
            .setNegativeButton(R.string.clear_records_cancel, null)
            .show()
    }

    private fun showPendingTripResult() {
        if (!isResumedNow) return
        val result = tripTracker.pendingResult ?: return
        tripTracker.clearPendingResult()
        startActivity(TripSummaryActivity.intent(this, result))
    }

    override fun onEvent(message: String) {
        events.addFirst("${timeFormat.format(Date())}  $message")
        while (events.size > MAX_EVENTS) events.removeLast()
        eventLog.text = events.joinToString("\n")
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
        findViewById<Button>(R.id.endTripButton).setOnClickListener { confirmEndTrip() }
        renderRecords()
    }

    private fun recordBehavior(behavior: PetBehavior) {
        val record = PetRecord(
            timeMillis = System.currentTimeMillis(),
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

    private fun renderRecords() {
        val counts = records.groupingBy { it.behavior }.eachCount()
        behaviorButtons.forEach { (behavior, button) ->
            button.text = getString(R.string.behavior_button, getString(behavior.labelRes), counts[behavior] ?: 0)
        }
        recordCount.text = getString(R.string.record_count, records.size)
        recordList.text = records.take(MAX_RECORDS_SHOWN).joinToString("\n") { it.toDisplayText() }
    }

    private fun PetRecord.toDisplayText(): String = buildString {
        append(timeFormat.format(Date(timeMillis)))
        append("  ")
        append(getString(behavior.labelRes))
        naviStatusLabel(naviStatus)?.let { append(" · ").append(it) }
        address?.let { append(" · ").append(it) }
    }

    private fun naviStatusLabel(name: String?): String? {
        val status = NavigationStatus.entries.firstOrNull { it.name == name } ?: return name
        return getString(
            when (status) {
                NavigationStatus.INACTIVE -> R.string.navi_short_inactive
                NavigationStatus.INITIALIZING -> R.string.navi_short_initializing
                NavigationStatus.FREE_DRIVE -> R.string.navi_short_free_drive
                NavigationStatus.ROUTE_DRIVE -> R.string.navi_short_route_drive
            }
        )
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

    private fun searchPlace() {
        val keyword = searchKeyword.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, R.string.toast_empty_keyword, Toast.LENGTH_SHORT).show()
            return
        }
        naviManager.searchPlace(keyword)
    }

    private fun setNaviButtonsEnabled(enabled: Boolean) {
        naviButtons.forEach { it.isEnabled = enabled }
    }

    companion object {
        private const val MAX_EVENTS = 20
        private const val ROUTE_REPLACE_WINDOW_MS = 5_000L
        private const val MAX_RECORDS_SHOWN = 50
        private const val BEHAVIOR_COLUMNS = 3
        private const val FLASH_DURATION_MS = 1500L
        private const val LOCATION_FILL_WINDOW_MS = 10_000L
        private val FLASH_TOKEN = Any()

        private const val STATE_EVENTS = "events"
        private const val STATE_NAVI_STATUS = "naviStatus"
        private const val STATE_LAST_RECORD = "lastRecord"
    }
}
