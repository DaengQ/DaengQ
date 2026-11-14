package com.example.pawgo

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/** 반려동물 행동 기록을 앱 내부 저장소의 JSON 파일에 보관한다. */
class PetRecordStore(private val dir: File) {

    constructor(context: Context) : this(context.filesDir)

    private val file = File(dir, FILE_NAME)

    /**
     * 파일이 일부 깨져 있어도 읽을 수 있는 기록은 모두 돌려준다.
     * 깨진 원본은 다음 저장에 덮이기 전에 따로 복사해 둔다.
     */
    fun load(): MutableList<PetRecord> {
        if (!file.exists()) return mutableListOf()
        val text = file.readText()
        val (records, intact) = readRecords(text)
        if (!intact) {
            // 내용으로 이름을 지어, 저장 전에 여러 번 읽어도 사본은 하나만 남는다
            val backup = File(dir, "$FILE_NAME.damaged-${Integer.toHexString(text.hashCode())}")
            if (!backup.exists()) file.copyTo(backup)
            Log.e(TAG, "Pet records were damaged. Kept ${records.size} records, original copied to ${backup.name}")
        }
        return records
    }

    /** 임시 파일에 다 쓴 뒤 바꿔치기해서, 쓰다가 전원이 꺼져도 예전 파일은 그대로 남는다. */
    fun save(records: List<PetRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        val temp = File(dir, "$FILE_NAME.tmp")
        temp.writeText(array.toString())
        check(temp.renameTo(file)) { "Could not replace ${file.name}" }
    }

    companion object {
        private const val TAG = "PetRecordStore"
        private const val FILE_NAME = "pet_records.json"

        /** 읽은 기록과, 파일이 온전했는지. 잘린 파일은 끝까지 다 쓴 항목까지, 망가진 항목은 건너뛰고 읽는다. */
        fun readRecords(text: String): Pair<MutableList<PetRecord>, Boolean> {
            val records = mutableListOf<PetRecord>()
            var intact = true
            val tokener = JSONTokener(text)
            try {
                if (tokener.nextClean() != '[') return records to false
                if (tokener.nextClean() == ']') return records to true
                tokener.back()
                while (true) {
                    val value = tokener.nextValue()
                    val record = try {
                        (value as? JSONObject)?.toRecord()
                    } catch (e: JSONException) {
                        intact = false
                        null
                    }
                    record?.let(records::add)
                    when (tokener.nextClean()) {
                        ',' -> continue
                        ']' -> break
                        else -> return records to false
                    }
                }
            } catch (e: JSONException) {
                return records to false
            }
            return records to intact
        }

        private fun PetRecord.toJson() = JSONObject().apply {
            put("time", timeMillis)
            put("behavior", behavior.name)
            put("naviStatus", naviStatus ?: JSONObject.NULL)
            put("address", address ?: JSONObject.NULL)
            put("latitude", latitude ?: JSONObject.NULL)
            put("longitude", longitude ?: JSONObject.NULL)
            put("detected", detected)
        }

        /** 지금은 기록하지 않는 예전 종류의 기록이면 null. */
        private fun JSONObject.toRecord(): PetRecord? {
            val behavior = PetBehavior.fromStoredName(getString("behavior")) ?: return null
            return PetRecord(
                timeMillis = getLong("time"),
                behavior = behavior,
                naviStatus = optStringOrNull("naviStatus"),
                address = optStringOrNull("address"),
                latitude = optDoubleOrNull("latitude"),
                longitude = optDoubleOrNull("longitude"),
                detected = optBoolean("detected", false),
            )
        }

        // isNull은 키가 없을 때도 true라서 좌표가 없던 예전 기록도 그대로 읽힌다
        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name)) null else getString(name)

        private fun JSONObject.optDoubleOrNull(name: String): Double? =
            if (isNull(name)) null else getDouble(name)
    }
}
