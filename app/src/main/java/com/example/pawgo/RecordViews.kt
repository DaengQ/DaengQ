package com.example.pawgo

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 주행 화면과 결과 화면이 같이 쓰는 기록 패널 조각들. */

private val recordTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** 종류별 횟수 세 칸을 [container]에 채운다. 칸 사이에 세로 구분선을 둔다. 돌려받은 글자에 횟수를 쓴다. */
fun Activity.buildCountColumns(container: LinearLayout): Map<PetBehavior, TextView> {
    val counts = mutableMapOf<PetBehavior, TextView>()
    PetBehavior.entries.forEachIndexed { index, behavior ->
        if (index > 0) {
            container.addView(
                View(this).apply { setBackgroundResource(R.drawable.divider_vertical) },
                LinearLayout.LayoutParams(dp(2), dp(56)),
            )
        }
        val column = layoutInflater.inflate(R.layout.column_drive_count, container, false)
        column.findViewById<TextView>(R.id.label).setText(behavior.labelRes)
        counts[behavior] = column.findViewById(R.id.count)
        container.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    return counts
}

fun Context.showCounts(views: Map<PetBehavior, TextView>, counts: Map<PetBehavior, Int>) {
    views.forEach { (behavior, view) -> view.text = getString(R.string.drive_count, counts[behavior] ?: 0) }
}

/** 기록 한 줄(row_drive_record): 시각, 종류 색 점, 종류, 남긴 방법. */
fun Context.bindRecordRow(row: View, record: PetRecord) {
    row.findViewById<TextView>(R.id.time).text = recordTimeFormat.format(Date(record.timeMillis))
    row.findViewById<View>(R.id.dot).backgroundTintList = ColorStateList.valueOf(getColor(record.behavior.markerColorRes))
    row.findViewById<TextView>(R.id.label).setText(record.behavior.labelRes)
    row.findViewById<TextView>(R.id.source).setText(
        if (record.detected) R.string.record_source_detected else R.string.record_source_manual
    )
}

/**
 * 기록 고치기 버튼 줄: 종류 3개와 삭제. 드롭다운 없이 한 번 눌러 끝난다.
 * 지금 종류는 그 종류 색의 테두리와 연한 배경으로 표시한다.
 */
class RecordChoiceRow(
    private val activity: Activity,
    val view: LinearLayout,
    onType: (PetBehavior) -> Unit,
    onDelete: () -> Unit,
) {
    private val buttons = mutableMapOf<PetBehavior, Button>()

    init {
        val gap = activity.resources.getDimensionPixelSize(R.dimen.button_gap)
        PetBehavior.entries.forEach { behavior ->
            val button = addButton(gap, activity.getString(behavior.labelRes)) { onType(behavior) }
            buttons[behavior] = button
        }
        addButton(gap, activity.getString(R.string.action_delete_record), onDelete)
    }

    private fun addButton(gap: Int, text: String, onClick: () -> Unit): Button {
        val button = activity.layoutInflater.inflate(R.layout.button_choice, view, false) as Button
        button.text = text
        button.setTextColor(activity.getColor(R.color.fg_strong))
        button.setOnClickListener { onClick() }
        view.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            if (view.childCount > 0) marginStart = gap
        })
        return button
    }

    fun select(selected: PetBehavior) {
        buttons.forEach { (behavior, button) ->
            button.isSelected = behavior == selected
            if (behavior != selected) {
                button.setBackgroundResource(R.drawable.bg_button_choice)
                return@forEach
            }
            val color = activity.getColor(behavior.markerColorRes)
            button.background = GradientDrawable().apply {
                cornerRadius = activity.dp(12).toFloat()
                setColor(color and 0x00FFFFFF or (CHOICE_FILL_ALPHA shl 24))
                setStroke(activity.dp(2), color)
            }
        }
    }

    companion object {
        /** 고른 종류 버튼 배경의 불투명도(0~255). */
        private const val CHOICE_FILL_ALPHA = 0x2E
    }
}

/**
 * [template]의 %n$s·%n$d 자리에 [args]를 넣고, [bold]에 든 자리(1부터)만 굵은 진한 글자로 한다.
 * 예: "%1$d건 중 %2$s이 …"에서 2번 자리만 굵게.
 */
fun Context.formatWithBold(@StringRes template: Int, args: List<Any>, bold: Set<Int>): CharSequence {
    val text = getString(template)
    val result = SpannableStringBuilder()
    var last = 0
    Regex("""%(\d+)\$[sd]""").findAll(text).forEach { match ->
        result.append(text, last, match.range.first)
        val index = match.groupValues[1].toInt()
        val start = result.length
        result.append(args[index - 1].toString())
        if (index in bold) {
            result.setSpan(StyleSpan(Typeface.BOLD), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            result.setSpan(ForegroundColorSpan(getColor(R.color.fg_strong)), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        last = match.range.last + 1
    }
    result.append(text, last, text.length)
    return result
}
