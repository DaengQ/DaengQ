package com.example.pawgo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat

/**
 * 시간대별 발성 막대. 가장 잦은 막대만 브랜드 색으로 진하게 칠하고, 기록이 있는 막대 위에 건수를 쓴다.
 * 아래에는 출발 후 몇 분인지를 막대가 많아도 겹치지 않게 몇 개만 쓴다.
 */
class TimelineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var buckets: List<TimeBucket> = emptyList()
    private var peakIndex: Int? = null
    private var tripStartMillis = 0L

    private val typeface = ResourcesCompat.getFont(context, R.font.asta_sans)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val baselinePaint = Paint().apply {
        color = context.getColor(R.color.border_default)
        strokeWidth = dp(2f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // 회색 카드 위에서도 4.5:1이 넘도록 fg_muted
        color = context.getColor(R.color.fg_muted)
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
        typeface = this@TimelineChartView.typeface
    }
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.fg_strong)
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(this@TimelineChartView.typeface, Typeface.BOLD)
    }
    private val rect = RectF()

    fun setData(buckets: List<TimeBucket>, peakIndex: Int?, tripStartMillis: Long) {
        this.buckets = buckets
        this.peakIndex = peakIndex
        this.tripStartMillis = tripStartMillis
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (buckets.isEmpty()) return
        val labelHeight = labelPaint.fontSpacing + dp(8f)
        val countHeight = countPaint.fontSpacing + dp(4f)
        val chartTop = paddingTop + countHeight
        val chartBottom = height - paddingBottom - labelHeight
        val chartWidth = (width - paddingLeft - paddingRight).toFloat()
        val slot = chartWidth / buckets.size
        val gap = (slot * 0.2f).coerceAtMost(dp(12f))
        val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)
        val radius = dp(6f)

        buckets.forEachIndexed { i, bucket ->
            // 막대가 적을 때 너무 넓어지지 않게 폭을 제한하고 칸 가운데에 둔다
            val barWidth = (slot - gap).coerceAtMost(dp(MAX_BAR_WIDTH_DP))
            val left = paddingLeft + slot * i + (slot - barWidth) / 2
            val right = left + barWidth
            val centerX = (left + right) / 2
            if (bucket.count > 0) {
                val top = chartBottom - (chartBottom - chartTop) * bucket.count / maxCount
                rect.set(left, top, right, chartBottom)
                barPaint.color = context.getColor(if (i == peakIndex) R.color.chart_bar_peak else R.color.chart_bar)
                canvas.drawRoundRect(rect, radius, radius, barPaint)
                // 아래쪽 모서리는 각지게 덮는다
                canvas.drawRect(left, (top + radius).coerceAtMost(chartBottom), right, chartBottom, barPaint)
                canvas.drawText(bucket.count.toString(), centerX, top - dp(6f), countPaint)
            }
        }
        canvas.drawLine(paddingLeft.toFloat(), chartBottom, width - paddingRight.toFloat(), chartBottom, baselinePaint)

        // 막대 경계에 "출발 후 N분"을 쓴다. 최대 7개만 쓰고 마지막은 도착 시각에 맞춘다
        val step = ((buckets.size + MAX_LABELS - 2) / (MAX_LABELS - 1)).coerceAtLeast(1)
        val labelY = chartBottom + dp(8f) - labelPaint.ascent()
        val endLabel = elapsedOf(buckets.last().endMillis)
        val endWidth = labelPaint.measureText(endLabel)
        val endX = width - paddingRight - endWidth / 2
        canvas.drawText(endLabel, endX, labelY, labelPaint)
        for (i in buckets.indices step step) {
            val label = elapsedOf(buckets[i].startMillis)
            val halfWidth = labelPaint.measureText(label) / 2
            val x = (paddingLeft + slot * i).coerceAtLeast(paddingLeft + halfWidth)
            // 도착 시각 글자와 겹치면 쓰지 않는다
            if (x + halfWidth + dp(16f) > endX - endWidth / 2) break
            canvas.drawText(label, x, labelY, labelPaint)
        }
    }

    private fun elapsedOf(time: Long) = formatElapsed(((time - tripStartMillis) / 60_000).toInt())

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        private const val MAX_LABELS = 7
        private const val MAX_BAR_WIDTH_DP = 72f
    }
}
