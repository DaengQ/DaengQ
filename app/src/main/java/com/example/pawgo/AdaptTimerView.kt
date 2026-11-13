package com.example.pawgo

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.ResourcesCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * 적응 타이머 게이지(PRD 화면 3).
 *
 * 반원 트랙을 왼쪽에서 오른쪽으로 시간이 흐른 만큼 초록 부채꼴로 채운다.
 * 가운데에 마스코트, 그 위 말풍선에 남은 시간(4:59)을 보여 준다.
 * 크기는 dp로 고정해서 그리고, 뷰 높이 아래쪽 남는 부분은 제목과의 간격이 된다.
 */
class AdaptTimerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val stroke = dp(16f)
    private val radius = dp(283f)
    private val progressColor = context.getColor(R.color.gauge_progress)
    private val rimColor = context.getColor(R.color.gauge_rim)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.gauge_track)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = progressColor
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = progressColor }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.fg_strong) }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.bg_surface)
        textSize = dp(56f)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.asta_sans)?.let { Typeface.create(it, Typeface.BOLD) }
        fontFeatureSettings = "tnum"
    }
    private val mascot = context.getDrawable(R.drawable.mascot_flat)!!

    private val oval = RectF()
    private val sector = Path()
    private val bubble = Path()

    /** 흐른 비율 0..1. 0이면 비어 있고 1이면 반원이 다 찬다. */
    private var progress = 0f
    private var timeText = ""

    fun setTime(elapsedFraction: Float, text: String) {
        progress = elapsedFraction.coerceIn(0f, 1f)
        timeText = text
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (radius * 2 + stroke + dp(8f)).toInt()
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(dp(GAUGE_HEIGHT_DP).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = stroke + radius
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val start = 180f
        val sweep = 180f * progress
        val end = Math.toRadians((start + sweep).toDouble())
        val endX = cx + radius * cos(end).toFloat()
        val endY = cy + radius * sin(end).toFloat()

        // 남은 트랙
        if (progress < 1f) canvas.drawArc(oval, start + sweep, 180f - sweep, false, trackPaint)

        if (progress > 0f) {
            // 채운 부분의 한가운데 바깥쪽이 가장 진하고 중심으로 갈수록 옅어진다
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            val gx = cx + radius * cos(mid).toFloat()
            val gy = cy + radius * sin(mid).toFloat()
            fillPaint.shader = LinearGradient(gx, gy, cx, cy, progressColor, withAlpha(progressColor, 0.08f), Shader.TileMode.CLAMP)
            rimPaint.shader = LinearGradient(gx, gy, cx, cy, rimColor, withAlpha(rimColor, 0.15f), Shader.TileMode.CLAMP)

            sector.reset()
            sector.moveTo(cx, cy)
            sector.arcTo(oval, start, sweep)
            sector.close()
            canvas.drawPath(sector, fillPaint)
            canvas.drawArc(oval, start, sweep, false, rimPaint)
        }

        // 바늘과 끝점
        canvas.drawLine(cx, cy, endX, endY, handPaint)
        canvas.drawCircle(endX, endY, dp(8f), dotPaint)

        // 마스코트: 위쪽 85dp가 반원 밑변 위로 올라온다
        val mascotW = dp(172f)
        val mascotH = dp(158f)
        val mascotTop = cy - dp(85f)
        mascot.setBounds((cx - mascotW / 2).toInt(), mascotTop.toInt(), (cx + mascotW / 2).toInt(), (mascotTop + mascotH).toInt())
        mascot.draw(canvas)

        // 말풍선: 꼬리 끝이 마스코트 바로 위
        val tipY = mascotTop - dp(12f)
        val tail = dp(10f)
        val bubbleBottom = tipY - tail
        val bubbleTop = bubbleBottom - dp(76f)
        val half = dp(97f)
        val corner = dp(4f)
        bubble.reset()
        bubble.addRoundRect(cx - half, bubbleTop, cx + half, bubbleBottom, corner, corner, Path.Direction.CW)
        bubble.moveTo(cx - tail, bubbleBottom - 1)
        bubble.lineTo(cx + tail, bubbleBottom - 1)
        bubble.lineTo(cx, tipY)
        bubble.close()
        canvas.drawPath(bubble, bubblePaint)
        val textY = (bubbleTop + bubbleBottom) / 2 - (timePaint.descent() + timePaint.ascent()) / 2
        canvas.drawText(timeText, cx, textY, timePaint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    companion object {
        /** 반원(299) + 밑변 아래 마스코트(73) + 제목까지 간격 */
        private const val GAUGE_HEIGHT_DP = 300f + 73f + 96f
    }
}
