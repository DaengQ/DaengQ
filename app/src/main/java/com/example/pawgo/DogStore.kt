package com.example.pawgo

import android.content.Context
import org.json.JSONArray

/** 등록한 강아지 이름과 이번에 함께 타는 강아지를 기기 안에 저장한다. 이름만 저장한다(PRD). */
class DogStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 등록한 순서대로. */
    val names: List<String>
        get() {
            val array = JSONArray(prefs.getString(KEY_NAMES, "[]"))
            return List(array.length()) { array.getString(it) }
        }

    /** 이번 주행의 강아지. 아직 고르지 않았으면 null. */
    var selected: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    /** 이름을 등록한다. 앞뒤 공백은 지운다. 비어 있거나 이미 있으면 등록하지 않고 null. */
    fun add(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed in names) return null
        prefs.edit().putString(KEY_NAMES, JSONArray(names + trimmed).toString()).apply()
        return trimmed
    }

    companion object {
        private const val PREFS_NAME = "dogs"
        private const val KEY_NAMES = "names"
        private const val KEY_SELECTED = "selected"
    }
}

/** 이번 주행의 강아지 이름. 고른 적이 없으면 기본 이름. */
fun Context.petName(): String = DogStore(this).selected ?: getString(R.string.pet_name)
