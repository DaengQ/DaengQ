package com.example.pawgo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.skt.tmap.TMapInsets
import com.skt.tmap.TMapPoint
import com.skt.tmap.TMapView
import com.skt.tmap.overlay.TMapMarkerItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TMAP 지도 SDK(V3.8) 래퍼. 행동 기록을 지도 위 마커로 보여 준다.
 *
 * 공식 샘플의 순서(생성 → setSKTMapApiKey → addView → 리스너 등록)를 따르고,
 * 지도가 준비되기 전에 들어온 요청은 onMapReady 때 반영한다.
 */
class PetMap(private val container: FrameLayout) {

    private val context: Context = container.context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val markerIcons = mutableMapOf<PetBehavior, Bitmap>()
    private val locationIcon by lazy { drawLocationIcon() }

    private var mapView: TMapView? = null
    private var isReady = false
    private var isReleased = false

    private val shownMarkerIds = mutableSetOf<String>()
    private var pendingRecords: List<PetRecord> = emptyList()
    private var pendingLocation: Pair<Double, Double>? = null
    private var fitPending = false

    init {
        val appKey = BuildConfig.TMAP_APP_KEY
        if (appKey.isBlank()) {
            showMessage(context.getString(R.string.map_key_missing))
        } else {
            createMap(appKey)
        }
    }

    private fun createMap(appKey: String) {
        val view = TMapView(context)
        view.setSKTMapApiKey(appKey)
        container.addView(view)
        view.setOnApiKeyListenerCallback(object : TMapView.OnApiKeyListenerCallback {
            override fun onSKTMapApikeySucceed() = Unit

            override fun onSKTMapApikeyFailed(message: String?) {
                Log.e(TAG, "TMAP key failed: $message")
                mainHandler.post {
                    if (!isReleased) showMessage(context.getString(R.string.map_key_failed, message.orEmpty()))
                }
            }
        })
        view.setOnMapReadyListener {
            mainHandler.post { onMapReady() }
        }
        mapView = view
    }

    private fun onMapReady() {
        if (isReleased) return
        isReady = true
        mapView?.setZoomLevel(DEFAULT_ZOOM)
        syncMarkers()
        pendingLocation?.let { (latitude, longitude) -> applyLocation(latitude, longitude) }
        if (fitPending) applyFit()
    }

    /** 지도의 마커를 [records]와 같게 맞춘다. 새 기록만 추가하고 없어진 기록은 지운다. */
    fun showRecords(records: List<PetRecord>) {
        pendingRecords = records
        if (isReady) syncMarkers()
    }

    /** 현재 위치 아이콘을 옮기고 지도 중심을 맞춘다. */
    fun moveToLocation(latitude: Double, longitude: Double) {
        pendingLocation = latitude to longitude
        if (isReady) applyLocation(latitude, longitude)
    }

    /** 기록된 마커가 모두 보이도록 지도 영역을 맞춘다. */
    fun fitToRecords() {
        fitPending = true
        if (isReady) applyFit()
    }

    fun onResume() {
        mapView?.onResume()
    }

    fun onPause() {
        mapView?.onPause()
    }

    fun release() {
        isReleased = true
        isReady = false
        mainHandler.removeCallbacksAndMessages(null)
        mapView?.onDestroy()
        mapView = null
    }

    private fun syncMarkers() {
        val view = mapView ?: return
        val located = pendingRecords.filter { it.latitude != null && it.longitude != null }
        val wantedIds = located.map { it.markerId() }.toSet()
        (shownMarkerIds - wantedIds).forEach { view.removeTMapMarkerItem(it) }
        shownMarkerIds.retainAll(wantedIds)
        located.filter { it.markerId() !in shownMarkerIds }.forEach { record ->
            view.addTMapMarkerItem(record.toMarker())
            shownMarkerIds += record.markerId()
        }
    }

    private fun applyLocation(latitude: Double, longitude: Double) {
        val view = mapView ?: return
        // 공식 샘플의 위치 추적과 같은 방식이다. setIconVisibility는 V3.8에서 NullPointerException이 나서 쓰지 않는다
        view.setLocationPoint(latitude, longitude)
        view.setCenterPoint(latitude, longitude)
        val marker = TMapMarkerItem().apply {
            id = LOCATION_MARKER_ID
            icon = locationIcon
            setPosition(0.5f, 0.5f)
            setTMapPoint(latitude, longitude)
        }
        if (view.getMarkerItemFromId(LOCATION_MARKER_ID) == null) {
            view.addTMapMarkerItem(marker)
        } else {
            view.updateTMapMarkerItem(marker)
        }
    }

    private fun applyFit() {
        val view = mapView ?: return
        fitPending = false
        val points = pendingRecords
            .mapNotNull { record ->
                val latitude = record.latitude ?: return@mapNotNull null
                val longitude = record.longitude ?: return@mapNotNull null
                latitude to longitude
            }
            .distinct()
        when (points.size) {
            0 -> Unit
            1 -> view.setCenterPoint(points[0].first, points[0].second)
            else -> {
                val bounds = view.getBoundsFromPoints(ArrayList(points.map { TMapPoint(it.first, it.second) }))
                val inset = context.dp(FIT_INSET_DP).toInt()
                if (bounds != null) view.fitBounds(bounds, TMapInsets.of(inset, inset, inset, inset))
            }
        }
    }

    private fun PetRecord.markerId() = "record_$timeMillis"

    private fun PetRecord.toMarker(): TMapMarkerItem {
        val record = this
        return TMapMarkerItem().apply {
            id = record.markerId()
            icon = markerIcons.getOrPut(record.behavior) { drawMarkerIcon(record.behavior) }
            setPosition(0.5f, 1.0f)
            setTMapPoint(record.latitude!!, record.longitude!!)
            calloutTitle = if (record.detected) {
                context.getString(R.string.record_detected, context.getString(record.behavior.labelRes))
            } else {
                context.getString(record.behavior.labelRes)
            }
            calloutSubTitle = timeFormat.format(Date(record.timeMillis))
            canShowCallout = true
        }
    }

    /** 현재 위치를 나타내는 흰 테두리의 파란 점. */
    private fun drawLocationIcon(): Bitmap {
        val radius = context.dp(LOCATION_ICON_RADIUS_DP)
        val stroke = context.dp(MARKER_STROKE_DP)
        val center = radius + stroke
        val bitmap = Bitmap.createBitmap((center * 2).toInt(), (center * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)
        paint.color = context.getColor(R.color.location_icon)
        canvas.drawCircle(center, center, radius, paint)
        return bitmap
    }

    /** 행동별 색의 원과 아래쪽 꼬리로 된 핀. 꼬리 끝이 기록 위치를 가리킨다. */
    private fun drawMarkerIcon(behavior: PetBehavior): Bitmap {
        val radius = context.dp(MARKER_RADIUS_DP)
        val tail = context.dp(MARKER_TAIL_DP)
        val stroke = context.dp(MARKER_STROKE_DP)
        val center = radius + stroke
        val width = (center * 2).toInt()
        val height = (center * 2 + tail).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pin = Path().apply {
            addCircle(center, center, radius, Path.Direction.CW)
            moveTo(center - radius * 0.55f, center + radius * 0.75f)
            lineTo(center, height - stroke)
            lineTo(center + radius * 0.55f, center + radius * 0.75f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke * 2
        paint.color = Color.WHITE
        canvas.drawPath(pin, paint)
        paint.style = Paint.Style.FILL
        paint.color = context.getColor(behavior.markerColorRes)
        canvas.drawPath(pin, paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        paint.textSize = context.dp(MARKER_TEXT_DP)
        val baseline = center - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(context.getString(behavior.markerLabelRes), center, baseline, paint)
        return bitmap
    }

    private fun showMessage(message: String) {
        container.removeAllViews()
        container.addView(
            TextView(context).apply {
                text = message
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setBackgroundResource(R.color.last_record_idle)
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
    }

    private fun Context.dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        private const val TAG = "PetMap"
        private const val DEFAULT_ZOOM = 16
        private const val LOCATION_MARKER_ID = "position"
        private const val FIT_INSET_DP = 80f
        private const val MARKER_RADIUS_DP = 26f
        private const val MARKER_TAIL_DP = 14f
        private const val MARKER_STROKE_DP = 2f
        private const val MARKER_TEXT_DP = 15f
        private const val LOCATION_ICON_RADIUS_DP = 11f
    }
}
