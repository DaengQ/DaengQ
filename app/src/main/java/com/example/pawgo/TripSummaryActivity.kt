package com.example.pawgo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행이 끝난 뒤 이번 주행의 기록 지도와 반려동물 행동 결과를 보여 준다. */
class TripSummaryActivity : Activity() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private lateinit var petMap: PetMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_summary)

        val trip = intent.toTripResult()
        // 기록은 최신순으로 저장돼 있으니 타임라인을 위해 시간순으로 뒤집는다
        val tripRecords = PetRecordStore(this).load()
            .filter { it.timeMillis in trip.startMillis..trip.endMillis }
            .sortedBy { it.timeMillis }

        petMap = PetMap(findViewById<FrameLayout>(R.id.tripMapContainer))
        petMap.showRecords(tripRecords)
        petMap.fitToRecords()

        val noLocationCount = tripRecords.count { it.latitude == null || it.longitude == null }
        findViewById<TextView>(R.id.tripNoLocation).apply {
            visibility = if (noLocationCount > 0) View.VISIBLE else View.GONE
            text = getString(R.string.trip_no_location, noLocationCount)
        }

        findViewById<TextView>(R.id.tripStats).text = buildStats(trip)
        renderBehaviors(tripRecords)
        renderTimeline(tripRecords, trip.startMillis)

        findViewById<Button>(R.id.tripExportButton).apply {
            isEnabled = tripRecords.any { it.latitude != null && it.longitude != null }
            setOnClickListener { export(tripRecords) }
        }
        findViewById<Button>(R.id.tripCloseButton).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        petMap.onResume()
    }

    override fun onPause() {
        petMap.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        petMap.release()
        super.onDestroy()
    }

    private fun buildStats(trip: TripResult): String {
        val range = "${timeFormat.format(Date(trip.startMillis))} – ${timeFormat.format(Date(trip.endMillis))}"
        return "$range · ${formatDuration(((trip.endMillis - trip.startMillis) / 1000).toInt())}"
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

    private fun renderTimeline(tripRecords: List<PetRecord>, startMillis: Long) {
        findViewById<TextView>(R.id.tripTimeline).text = tripRecords.joinToString("\n") { record ->
            buildString {
                append(timeFormat.format(Date(record.timeMillis)))
                append(" (+").append((record.timeMillis - startMillis) / 60_000).append("분)")
                append("  ").append(getString(record.behavior.labelRes))
                if (record.detected) append(" · ").append(getString(R.string.record_detected_short))
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

        fun intent(context: Context, trip: TripResult): Intent =
            Intent(context, TripSummaryActivity::class.java)
                .putExtra(EXTRA_START, trip.startMillis)
                .putExtra(EXTRA_END, trip.endMillis)

        private fun Intent.toTripResult() = TripResult(
            startMillis = getLongExtra(EXTRA_START, 0L),
            endMillis = getLongExtra(EXTRA_END, System.currentTimeMillis()),
        )
    }
}
