package com.example.pawgo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 주행 결과(PRD 화면 6). 지도에 지나온 길과 출발·도착 지점, 기록을 보여 주고
 * 패널에 한 줄 요약, 종류별 횟수, 발성 기록, 시간대별 막대를 보여 준다.
 * 기록을 누르면 그 아래에 종류 바꾸기·삭제 버튼이 열린다.
 */
class TripSummaryActivity : Activity() {

    private lateinit var petMap: PetMap
    private lateinit var recordStore: PetRecordStore
    private lateinit var trip: TripResult
    private lateinit var countViews: Map<PetBehavior, TextView>

    /** 저장된 전체 기록(최신순). 고치면 통째로 다시 저장한다. */
    private lateinit var allRecords: MutableList<PetRecord>

    /** 고치기 버튼이 열린 기록의 시각. 열린 기록이 없으면 null. */
    private var editingTime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trip_summary)

        trip = intent.toTripResult()
        recordStore = PetRecordStore(this)
        allRecords = recordStore.load()

        petMap = PetMap(findViewById<FrameLayout>(R.id.tripMapContainer))
        // 받침 있는 이름은 '이'를 붙여 부른다. 예: 봄이와 드라이브 완료!
        findViewById<TextView>(R.id.tripTitle).text = getString(R.string.trip_title, petName().withCallingI())
        listOf(R.id.tripHighlight, R.id.tripPeak).forEach { id ->
            findViewById<View>(id).findViewById<TextView>(R.id.tagText).background = TagDrawable(this)
        }
        countViews = buildCountColumns(findViewById(R.id.tripCounts))
        // 닫으면 다음 주행을 위해 강아지 선택부터 다시 시작한다
        findViewById<Button>(R.id.tripCloseButton).setOnClickListener {
            startActivity(
                Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }

        val startLatitude = trip.startLatitude
        val startLongitude = trip.startLongitude
        if (startLatitude != null && startLongitude != null) petMap.showStart(startLatitude, startLongitude)
        val route = RouteStore(this).load(trip.startMillis, trip.endMillis)
        petMap.showRoute(route)
        route.lastOrNull()?.let { petMap.showEnd(it.latitude, it.longitude) }
        render()
        // 결과 패널이 지도 오른쪽을 덮으니 그만큼 비우고 맞춘다
        petMap.fitToRecords(
            resources.getDimensionPixelSize(R.dimen.drive_panel_width) + 2 * resources.getDimensionPixelSize(R.dimen.drive_panel_margin)
        )
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

    /** 이번 주행의 기록, 최신순. */
    private fun tripRecords(): List<PetRecord> =
        allRecords.filter { it.timeMillis in trip.startMillis..trip.endMillis }.sortedByDescending { it.timeMillis }

    private fun render() {
        val records = tripRecords()
        petMap.showRecords(records)

        val noLocationCount = records.count { !it.hasLocation }
        findViewById<TextView>(R.id.tripNoLocation).apply {
            visibility = if (noLocationCount > 0) View.VISIBLE else View.GONE
            text = getString(R.string.trip_no_location, noLocationCount)
        }

        val counts = TripStats.typeCounts(records)
        renderHighlight(records.size, counts)
        showCounts(countViews, counts)
        renderRecordList(records)
        renderTimeline(records)
    }

    private fun tagText(id: Int): TextView = findViewById<View>(id).findViewById(R.id.tagText)

    private fun renderHighlight(total: Int, counts: Map<PetBehavior, Int>) {
        val top = TripStats.topTypes(counts)
        val (types, count) = top ?: run {
            tagText(R.id.tripHighlight).text = getString(R.string.trip_no_records, petName().withIGa())
            return
        }
        tagText(R.id.tripHighlight).text = when (types.size) {
            // 예: 4건 중 울음이 3건으로 가장 많았어요. 종류와 건수만 굵게
            1 -> formatWithBold(R.string.trip_highlight, listOf(total, getString(types[0].labelRes), count), setOf(2, 3))
            // 세 종류가 모두 같으면 고르게 나왔다고 쓴다. 예: 세 소리가 1건씩 고르게 나왔어요.
            PetBehavior.entries.size -> formatWithBold(R.string.trip_highlight_even, listOf(count), setOf(1))
            // 두 종류가 같으면 둘을 함께 쓴다. 예: 4건 중 울음·하울링이 2건씩 가장 많았어요.
            else -> formatWithBold(
                R.string.trip_highlight_tie,
                listOf(total, types.joinToString("·") { getString(it.labelRes) }, count),
                setOf(2, 3),
            )
        }
    }

    private fun renderTimeline(records: List<PetRecord>) {
        val section = findViewById<View>(R.id.tripTimelineSection)
        if (records.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE
        val buckets = TripStats.buckets(trip.startMillis, trip.endMillis, records.map { it.timeMillis })
        val peaks = TripStats.peakIndices(buckets)
        findViewById<TimelineChartView>(R.id.tripTimeline).setData(buckets, peaks, trip.startMillis)
        val peakTag = findViewById<View>(R.id.tripPeak)
        if (peaks.isEmpty()) {
            peakTag.visibility = View.GONE
            return
        }
        peakTag.visibility = View.VISIBLE
        val count = buckets[peaks[0]].count
        // 끝은 올림해서 1분이 안 되는 짧은 주행도 0-1분으로 쓴다
        val ranges = peaks.map { i ->
            formatElapsedRange(minutesAfterStart(buckets[i].startMillis), minutesAfterStart(buckets[i].endMillis, roundUp = true))
        }
        tagText(R.id.tripPeak).text = when (ranges.size) {
            // 예: 출발 후 5-10분이 3건으로 가장 잦았어요. 구간과 건수만 굵게
            1 -> formatWithBold(R.string.trip_peak, listOf(ranges[0], count), setOf(1, 2))
            // 예: 출발 후 0-5분과 20-25분이 2건씩 가장 잦았어요.
            2 -> formatWithBold(R.string.trip_peak_two, listOf(ranges[0], ranges[1], count), setOf(1, 2, 3))
            // 구간을 다 쓰면 너무 길어 개수만 쓴다. 예: 3개 구간이 2건씩 가장 잦았어요.
            else -> formatWithBold(R.string.trip_peak_many, listOf(ranges.size, count), setOf(1, 2))
        }
    }

    private fun minutesAfterStart(time: Long, roundUp: Boolean = false): Int {
        val elapsed = time - trip.startMillis
        return ((if (roundUp) elapsed + 59_999 else elapsed) / 60_000).toInt()
    }

    /** 발성 기록 목록. 누른 기록 아래에만 고치기 버튼 줄을 연다. 다시 누르면 닫는다. */
    private fun renderRecordList(records: List<PetRecord>) {
        findViewById<View>(R.id.tripRecordsSection).visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        val container = findViewById<LinearLayout>(R.id.tripRecords)
        container.removeAllViews()
        records.forEach { record ->
            val row = layoutInflater.inflate(R.layout.row_drive_record, container, false)
            bindRecordRow(row, record)
            row.setOnClickListener {
                editingTime = if (editingTime == record.timeMillis) null else record.timeMillis
                render()
            }
            container.addView(row)
            if (record.timeMillis == editingTime) container.addView(choiceRow(record, container))
        }
    }

    private fun choiceRow(record: PetRecord, container: LinearLayout): View {
        val view = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val choices = RecordChoiceRow(
            this,
            view,
            onType = { behavior -> changeRecord(record, behavior) },
            onDelete = {
                editingTime = null
                allRecords.removeAll { it.timeMillis == record.timeMillis }
                recordStore.save(allRecords)
                render()
            },
        )
        choices.select(record.behavior)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            resources.getDimensionPixelSize(R.dimen.choice_button_height),
        ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.button_gap) }
        return view
    }

    private fun changeRecord(record: PetRecord, to: PetBehavior) {
        allRecords.replaceAll { if (it.timeMillis == record.timeMillis) it.copy(behavior = to) else it }
        recordStore.save(allRecords)
        render()
    }

    companion object {
        private const val EXTRA_START = "start"
        private const val EXTRA_END = "end"
        private const val EXTRA_START_LAT = "startLat"
        private const val EXTRA_START_LNG = "startLng"

        fun intent(context: Context, trip: TripResult): Intent =
            Intent(context, TripSummaryActivity::class.java)
                .putExtra(EXTRA_START, trip.startMillis)
                .putExtra(EXTRA_END, trip.endMillis)
                .apply {
                    if (trip.startLatitude != null && trip.startLongitude != null) {
                        putExtra(EXTRA_START_LAT, trip.startLatitude)
                        putExtra(EXTRA_START_LNG, trip.startLongitude)
                    }
                }

        private fun Intent.toTripResult() = TripResult(
            startMillis = getLongExtra(EXTRA_START, 0L),
            endMillis = getLongExtra(EXTRA_END, System.currentTimeMillis()),
            startLatitude = if (hasExtra(EXTRA_START_LAT)) getDoubleExtra(EXTRA_START_LAT, 0.0) else null,
            startLongitude = if (hasExtra(EXTRA_START_LNG)) getDoubleExtra(EXTRA_START_LNG, 0.0) else null,
        )
    }
}
