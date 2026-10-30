package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), NaviManager.Callback {

    private lateinit var naviManager: NaviManager

    private lateinit var connectionStatus: TextView
    private lateinit var navigationStatus: TextView
    private lateinit var searchKeyword: EditText
    private lateinit var eventLog: TextView
    private lateinit var naviButtons: List<Button>

    private val events = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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

    override fun onDestroy() {
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
        navigationStatus.text = getString(
            when (status) {
                NavigationStatus.INACTIVE -> R.string.navi_status_inactive
                NavigationStatus.INITIALIZING -> R.string.navi_status_initializing
                NavigationStatus.FREE_DRIVE -> R.string.navi_status_free_drive
                NavigationStatus.ROUTE_DRIVE -> R.string.navi_status_route_drive
            }
        )
    }

    override fun onEvent(message: String) {
        events.addFirst("${timeFormat.format(Date())}  $message")
        while (events.size > MAX_EVENTS) events.removeLast()
        eventLog.text = events.joinToString("\n")
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
    }
}
