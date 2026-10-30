package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), NaviManager.Callback {

    private lateinit var naviManager: NaviManager
    private lateinit var recordStore: PetRecordStore

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

        startRouteButton.setOnClickListener { naviManager.startRoute(destination) }
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
            ?.let { onNavigationStatus(it) }
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

    override fun onCurrentLocation(address: String) {
        // 버튼을 누른 직후 요청한 위치로, 아직 주소가 비어 있는 최근 기록을 채운다
        if (address.isBlank()) return
        val now = System.currentTimeMillis()
        var changed = false
        records.replaceAll { record ->
            if (record.address == null && now - record.timeMillis <= LOCATION_FILL_WINDOW_MS) {
                changed = true
                record.copy(address = address)
            } else {
                record
            }
        }
        if (changed) {
            recordStore.save(records)
            renderRecords()
        }
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
            android.os.SystemClock.uptimeMillis() + FLASH_DURATION_MS
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
