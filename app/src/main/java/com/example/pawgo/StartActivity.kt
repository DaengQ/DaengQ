package com.example.pawgo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * 시작 화면(PRD 화면 1~3). 주행 전에만 보인다.
 *
 * 1. 강아지 선택: 등록된 강아지를 고르면 주행 화면으로, 추가하기를 누르면 이름 입력으로.
 *    이름 입력: 같은 틀에 입력칸을 보여 준다. 등록하면 2로.
 * 2. 첫 주행 질문: 네(첫 주행)면 3으로, 아니요면 주행 화면으로.
 * 3. 적응 타이머: 5분이 지나거나 건너뛰면 주행 화면으로.
 */
class StartActivity : Activity() {

    private enum class Step { SELECT, NAME, FIRST_DRIVE, TIMER }

    private lateinit var dogStore: DogStore
    private lateinit var title: TextView
    private lateinit var gauge: AdaptTimerView
    private lateinit var mascot: View
    private lateinit var input: EditText
    private lateinit var nameRow: View
    private lateinit var nameSuffix: TextView
    private lateinit var mascotFrame: View
    private lateinit var topSpace: View
    private lateinit var buttons: LinearLayout
    private var step = Step.SELECT
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 주행 중에 앱을 다시 열면 바로 주행 화면으로 간다
        if (TripTracker(this).isActive) {
            openDriving()
            return
        }
        setContentView(R.layout.activity_start)
        dogStore = DogStore(this)
        title = findViewById(R.id.heroTitle)
        gauge = findViewById(R.id.heroGauge)
        mascot = findViewById(R.id.heroMascot)
        buttons = findViewById(R.id.heroButtons)
        input = findViewById(R.id.heroInput)
        nameRow = findViewById(R.id.heroNameRow)
        nameSuffix = findViewById(R.id.heroNameSuffix)
        mascotFrame = findViewById(R.id.heroMascotFrame)
        topSpace = findViewById(R.id.heroTopSpace)
        // 쓰는 이름의 받침에 맞춰 와/과를 바꾼다. 비어 있으면 "누구와"
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateNameSuffix()
        })
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) registerDog(input.text.toString())
            true
        }
        showSelect()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    @Deprecated("Activity의 뒤로 가기. 단계 안에서는 앞 단계로 돌아간다")
    override fun onBackPressed() {
        when (step) {
            Step.SELECT -> super.onBackPressed()
            Step.NAME, Step.FIRST_DRIVE -> showSelect()
            Step.TIMER -> showFirstDrive()
        }
    }

    private fun showSelect() {
        step = Step.SELECT
        stopTimer()
        hideInput()
        val placeholder = getString(R.string.start_select_placeholder)
        title.text = highlight(getString(R.string.start_select_title, placeholder), placeholder, R.color.hero_placeholder)
        buttons.removeAllViews()
        // 버튼이 너무 좁아지지 않도록 최근에 등록한 강아지 3마리까지만 보여 준다
        dogStore.names.takeLast(MAX_DOG_BUTTONS).forEach { name ->
            addButton(textButton(R.style.Button_Daengq_Hero_Dog, name) { selectDog(name) })
        }
        addButton(textButton(R.style.Button_Daengq_Hero_Add, getString(R.string.start_add_dog)) { showName() })
    }

    /**
     * 이름만 받는다. 주행 전 화면이라 키보드를 써도 된다.
     * 제목 "오늘은 [누구]와 달려볼까요?"의 빈칸에 바로 쓰고, 마스코트를 빼서 아래를 키보드에 내준다.
     */
    private fun showName() {
        step = Step.NAME
        title.visibility = View.GONE
        mascotFrame.visibility = View.GONE
        topSpace.visibility = View.VISIBLE
        nameRow.visibility = View.VISIBLE
        buttons.removeAllViews()
        addButton(textButton(R.style.Button_Daengq_Hero_Neutral, getString(R.string.start_add_dog_cancel)) { showSelect() })
        addButton(textButton(R.style.Button_Daengq_Hero_Dog, getString(R.string.start_add_dog_confirm)) {
            registerDog(input.text.toString())
        })

        input.text.clear()
        updateNameSuffix()
        input.requestFocus()
        // 화면이 바뀐 직후에는 키보드 요청이 무시될 수 있어 조금 기다렸다 띄운다
        input.postDelayed({ if (step == Step.NAME) keyboard().showSoftInput(input, 0) }, KEYBOARD_DELAY_MILLIS)
    }

    private fun updateNameSuffix() {
        val name = input.text.toString().trim()
        nameSuffix.setText(if (name.hasBatchim()) R.string.start_name_suffix_gwa else R.string.start_name_suffix_wa)
        // 빈칸 폭은 "누구" 안내 글자에 맞춰져 있다. 쓰기 시작하면 안내를 빼서 이름 길이만큼 줄인다
        input.hint = if (input.text.isEmpty()) getString(R.string.start_select_placeholder) else null
        input.requestLayout()
    }

    private fun hideInput() {
        if (nameRow.visibility != View.VISIBLE) return
        keyboard().hideSoftInputFromWindow(input.windowToken, 0)
        input.clearFocus()
        nameRow.visibility = View.GONE
        topSpace.visibility = View.GONE
        title.visibility = View.VISIBLE
        mascotFrame.visibility = View.VISIBLE
    }

    private fun keyboard() = getSystemService(InputMethodManager::class.java)

    private fun showFirstDrive() {
        step = Step.FIRST_DRIVE
        stopTimer()
        hideInput()
        // 받침 있는 이름은 '이'를 붙여 부른다. 예: 봄이는 첫 주행인가요?
        val name = petName().withCallingI()
        title.text = highlight(getString(R.string.start_first_drive_title, name.withEunNeun()), name, R.color.hero_name)
        buttons.removeAllViews()
        addButton(iconButton(R.drawable.ic_answer_yes, R.string.start_yes) { showTimer() })
        addButton(iconButton(R.drawable.ic_answer_no, R.string.start_no) { openDriving() })
    }

    private fun showTimer() {
        step = Step.TIMER
        // 첫 주행 질문과 같이 부른다. 예: 봄이에게
        val name = petName().withCallingI()
        title.text = highlight(getString(R.string.start_timer_title, name), name, R.color.hero_name)
        buttons.removeAllViews()
        addButton(textButton(R.style.Button_Daengq_Hero_Neutral, getString(R.string.start_timer_skip)) { openDriving() })

        mascot.visibility = View.GONE
        gauge.visibility = View.VISIBLE
        showRemaining(ADAPT_MILLIS)
        timer = object : CountDownTimer(ADAPT_MILLIS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) = showRemaining(millisUntilFinished)
            override fun onFinish() {
                // 다 찬 게이지(0:00)를 잠깐 보여 주고 주행 화면으로 간다
                showRemaining(0L)
                gauge.postDelayed({ if (step == Step.TIMER) openDriving() }, FINISH_HOLD_MILLIS)
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        gauge.visibility = View.GONE
        mascot.visibility = View.VISIBLE
    }

    private fun showRemaining(millis: Long) {
        // 남은 시간을 올림해서 5:00부터 0:01까지, 끝나면 0:00을 보여 준다
        val seconds = ((millis + 999) / 1000).toInt()
        gauge.setTime(1f - millis.toFloat() / ADAPT_MILLIS, String.format("%d:%02d", seconds / 60, seconds % 60))
    }

    private fun selectDog(name: String) {
        dogStore.selected = name
        openDriving()
    }

    private fun registerDog(name: String) {
        if (name.isBlank()) {
            input.requestFocus()
            return
        }
        val added = dogStore.add(name)
        if (added == null) {
            Toast.makeText(this, R.string.start_add_dog_duplicate, Toast.LENGTH_SHORT).show()
            return
        }
        dogStore.selected = added
        showFirstDrive()
    }

    private fun openDriving() {
        timer?.cancel()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun addButton(view: View) {
        val gap = resources.getDimensionPixelSize(R.dimen.hero_button_gap)
        buttons.addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
            if (buttons.childCount > 0) marginStart = gap
        })
    }

    private fun textButton(styleRes: Int, text: String, onClick: () -> Unit): Button =
        Button(this, null, 0, styleRes).apply {
            this.text = text
            setOnClickListener { onClick() }
        }

    private fun iconButton(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit): View =
        layoutInflater.inflate(R.layout.button_hero_icon, buttons, false).apply {
            findViewById<ImageView>(R.id.icon).setImageResource(icon)
            findViewById<TextView>(R.id.label).setText(label)
            setOnClickListener { onClick() }
        }

    /** [text] 안의 [part]만 [color]로 칠한다. */
    private fun highlight(text: String, part: String, @ColorRes color: Int): CharSequence {
        val start = text.indexOf(part)
        if (start < 0) return text
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(getColor(color)), start, start + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    companion object {
        private const val MAX_DOG_BUTTONS = 3
        private const val ADAPT_MILLIS = 5 * 60 * 1000L
        private const val KEYBOARD_DELAY_MILLIS = 200L
        private const val FINISH_HOLD_MILLIS = 1_500L
    }
}
