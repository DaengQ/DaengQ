package com.example.pawgo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 좌표가 있는 기록을 GeoJSON(RFC 7946) FeatureCollection 파일로 내보낸다.
 *
 * Pleos Connect에는 파일 선택기(SAF)와 공유 대상 앱이 없어서 앱 내부 저장소에 저장한다.
 * 앱 전용 외부 저장소는 adb로도 읽을 수 없어서 쓰지 않는다.
 */
class PetRecordExporter(private val context: Context) {

    class Result(val file: File, val exportedCount: Int, val skippedCount: Int)

    fun export(records: List<PetRecord>): Result {
        val located = records.filter { it.latitude != null && it.longitude != null }
            .sortedBy { it.timeMillis }

        val features = JSONArray()
        located.forEach { features.put(it.toFeature()) }
        val collection = JSONObject()
            .put("type", "FeatureCollection")
            .put("features", features)

        val dir = File(context.filesDir, EXPORT_DIR).apply { mkdirs() }
        val fileName = "pawgo-${fileTimeFormat.format(Date())}.geojson"
        val file = File(dir, fileName)
        file.writeText(collection.toString(2))

        return Result(file, located.size, records.size - located.size)
    }

    private fun PetRecord.toFeature(): JSONObject {
        // GeoJSON 좌표 순서는 [경도, 위도]
        val geometry = JSONObject()
            .put("type", "Point")
            .put("coordinates", JSONArray().put(longitude).put(latitude))
        val properties = JSONObject()
            .put("time", isoTimeFormat.format(Date(timeMillis)))
            .put("behavior", behavior.name)
            .put("label", context.getString(behavior.labelRes))
            .put("naviStatus", naviStatus ?: JSONObject.NULL)
            .put("address", address ?: JSONObject.NULL)
            .put("detected", detected)
        return JSONObject()
            .put("type", "Feature")
            .put("geometry", geometry)
            .put("properties", properties)
    }

    companion object {
        private const val EXPORT_DIR = "exports"
        private val fileTimeFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
        private val isoTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    }
}
