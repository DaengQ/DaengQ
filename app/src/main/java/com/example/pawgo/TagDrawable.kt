package com.example.pawgo

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * 결과 화면 한 줄 요약의 꼬리표 배경(요약.svg). 왼쪽은 마스코트 뒤로 들어가는 곧은 끝,
 * 오른쪽은 아래로 비스듬히 내려가 오른쪽 아래가 둥글게 뾰족한 끝이다.
 * 글자 길이에 맞춰 가로로 늘어나도록 오른쪽 끝 모양만 원본 좌표(높이 69 기준)로 그린다.
 * 테두리는 원본처럼 모양 안쪽으로만 그린다.
 */
class TagDrawable(context: Context) : Drawable() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.tag_bg) }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand_primary)
        style = Paint.Style.STROKE
    }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        val scale = bounds.height() / SOURCE_HEIGHT
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        // 원본의 오른쪽 끝(x=576)에서 떨어진 거리로 옮긴다
        fun x(source: Float) = bounds.right - (SOURCE_RIGHT - source) * scale
        fun y(source: Float) = top + source * scale
        path.reset()
        path.moveTo(left, y(0.0654f))
        path.lineTo(x(524.874f), y(0.0654f))
        path.cubicTo(x(532.126f), y(-0.5145f), x(539.649f), y(2.7894f), x(543.811f), y(9.9815f))
        path.lineTo(x(560.631f), y(39.0488f))
        path.cubicTo(x(568.346f), y(52.3821f), x(558.725f), y(69.0653f), x(543.32f), y(69.0654f))
        path.lineTo(left, y(69.0654f))
        path.close()
        // 안쪽 테두리 3.76(원본)의 절반이 보이도록 두 배로 그리고 모양으로 자른다
        border.strokeWidth = STROKE * scale
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fill)
        canvas.save()
        canvas.clipPath(path)
        canvas.drawPath(path, border)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        border.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        border.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable 기본 API", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val SOURCE_HEIGHT = 69.13f
        const val SOURCE_RIGHT = 576f
        const val STROKE = 3.759f
    }
}
