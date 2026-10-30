package com.example.pawgo

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** 반려동물 행동 기록을 앱 내부 저장소의 JSON 파일에 보관한다. */
class PetRecordStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    fun load(): MutableList<PetRecord> {
        if (!file.exists()) return mutableListOf()
        return try {
            val array = JSONArray(file.readText())
            MutableList(array.length()) { i -> array.getJSONObject(i).toRecord() }
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to read pet records", e)
            mutableListOf()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unknown behavior in pet records", e)
            mutableListOf()
        }
    }

    fun save(records: List<PetRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun PetRecord.toJson() = JSONObject().apply {
        put("time", timeMillis)
        put("behavior", behavior.name)
        put("naviStatus", naviStatus ?: JSONObject.NULL)
        put("address", address ?: JSONObject.NULL)
    }

    private fun JSONObject.toRecord() = PetRecord(
        timeMillis = getLong("time"),
        behavior = PetBehavior.valueOf(getString("behavior")),
        naviStatus = optStringOrNull("naviStatus"),
        address = optStringOrNull("address"),
    )

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else getString(name)

    companion object {
        private const val TAG = "PetRecordStore"
        private const val FILE_NAME = "pet_records.json"
    }
}
