package com.example.pawgo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행이 끝난 뒤 이번 주행의 반려동물 행동 기록을 요약해 보여 준다. */
class TripSummaryActivity : Activity() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_summary)

        val trip = intent.toTripResult()
        // 기록은 최신순으로 저장돼 있으니 타임라인을 위해 시간순으로 뒤집는다
        val tripRecords = PetRecordStore(this).load()
            .filter { it.timeMillis > trip.startMillis && it.timeMillis <= trip.endMillis }
            .sortedBy { it.timeMillis }

        // 경로 안내 없이 끝낸 주행은 시작 시각을 모르므로 첫 기록 시각을 쓴다
        val startMillis = if (trip.startMillis > 0) trip.startMillis else tripRecords.firstOrNull()?.timeMillis

        findViewById<TextView>(R.id.tripHeadline).text = trip.destinationName
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.trip_arrived, it) }
            ?: getString(R.string.trip_ended)
        findViewById<TextView>(R.id.tripStats).text = buildStats(trip, startMillis)
        renderBehaviors(tripRecords)
        renderTimeline(tripRecords, startMillis)

        findViewById<Button>(R.id.tripExportButton).apply {
            isEnabled = tripRecords.any { it.latitude != null && it.longitude != null }
            setOnClickListener { export(tripRecords) }
        }
        findViewById<Button>(R.id.tripCloseButton).setOnClickListener { finish() }
    }

    private fun buildStats(trip: TripResult, startMillis: Long?): String {
        val parts = mutableListOf<String>()
        startMillis?.let {
            parts += "${timeFormat.format(Date(it))} – ${timeFormat.format(Date(trip.endMillis))}"
        }
        val durationSeconds = trip.durationSeconds
            ?: startMillis?.let { ((trip.endMillis - it) / 1000).toInt() }
        durationSeconds?.let { parts += formatDuration(it) }
        trip.distanceMeters?.let { parts += formatDistance(it) }
        return parts.joinToString(" · ")
    }

    private fun renderBehaviors(tripRecords: List<PetRecord>) {
        val counts = tripRecords.groupingBy { it.behavior }.eachCount()
            .entries.sortedByDescending { it.value }
        val highlight = findViewById<TextView>(R.id.tripHighlight)
        val countsView = findViewById<TextView>(R.id.tripCounts)

        if (counts.isEmpty()) {
            highlight.setText(R.string.trip_no_records)
            countsView.text = ""
            return
        }
        val top = counts.first()
        highlight.text = getString(
            R.string.trip_highlight, tripRecords.size, getString(top.key.labelRes), top.value
        )
        countsView.text = counts.joinToString("\n") { (behavior, count) ->
            getString(R.string.trip_count_line, getString(behavior.labelRes), count)
        }
    }

    private fun renderTimeline(tripRecords: List<PetRecord>, startMillis: Long?) {
        findViewById<TextView>(R.id.tripTimeline).text = tripRecords.joinToString("\n") { record ->
            buildString {
                append(timeFormat.format(Date(record.timeMillis)))
                startMillis?.let {
                    append(" (+").append((record.timeMillis - it) / 60_000).append("분)")
                }
                append("  ").append(getString(record.behavior.labelRes))
                record.address?.let { append(" · ").append(it) }
            }
        }
    }

    private fun export(tripRecords: List<PetRecord>) {
        val result = try {
            PetRecordExporter(this).export(tripRecords)
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
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_DURATION = "duration"
        private const val EXTRA_DISTANCE = "distance"

        fun intent(context: Context, trip: TripResult): Intent =
            Intent(context, TripSummaryActivity::class.java)
                .putExtra(EXTRA_START, trip.startMillis)
                .putExtra(EXTRA_END, trip.endMillis)
                .putExtra(EXTRA_NAME, trip.destinationName)
                .apply {
                    trip.durationSeconds?.let { putExtra(EXTRA_DURATION, it) }
                    trip.distanceMeters?.let { putExtra(EXTRA_DISTANCE, it) }
                }

        private fun Intent.toTripResult() = TripResult(
            startMillis = getLongExtra(EXTRA_START, 0L),
            endMillis = getLongExtra(EXTRA_END, System.currentTimeMillis()),
            destinationName = getStringExtra(EXTRA_NAME),
            durationSeconds = if (hasExtra(EXTRA_DURATION)) getIntExtra(EXTRA_DURATION, 0) else null,
            distanceMeters = if (hasExtra(EXTRA_DISTANCE)) getIntExtra(EXTRA_DISTANCE, 0) else null,
        )
    }
}
