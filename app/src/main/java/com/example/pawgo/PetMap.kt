package com.example.pawgo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
import com.skt.tmap.overlay.TMapPolyLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TMAP 지도 SDK(V3.8) 래퍼. 지도 위에 지나온 길은 진한 선, 출발 지점은 네모, 기록은 종류 색 동그라미,
 * 현재 위치는 마스코트가 든 초록 동그라미, 끝난 주행의 도착 지점은 초록 네모로 보여 준다.
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
    private val startIcon by lazy { drawSquareIcon(R.color.gray_100) }
    private val endIcon by lazy { drawSquareIcon(R.color.location_icon) }

    private var mapView: TMapView? = null
    private var isReady = false
    private var isReleased = false

    private val shownMarkerIds = mutableSetOf<String>()
    private var pendingRecords: List<PetRecord> = emptyList()
    private var pendingLocation: Pair<Double, Double>? = null
    private var pendingStart: Pair<Double, Double>? = null
    private var pendingEnd: Pair<Double, Double>? = null
    private var pendingRoute: List<RoutePoint> = emptyList()
    private var fitPending = false
    /** 지도 오른쪽을 가리는 패널 폭(px). 그만큼 비우고 영역을 맞춘다. */
    private var fitRightCover = 0

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
        applyRoute()
        applyStart()
        applyEnd()
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

    /** 지나온 길을 진한 선으로 잇는다. */
    fun showRoute(points: List<RoutePoint>) {
        if (points == pendingRoute) return
        pendingRoute = points
        if (isReady) applyRoute()
    }

    /** 출발 지점에 네모를 둔다. */
    fun showStart(latitude: Double, longitude: Double) {
        if (pendingStart == latitude to longitude) return
        pendingStart = latitude to longitude
        if (isReady) applyStart()
    }

    /** 도착 지점에 초록 네모를 둔다. 결과 화면에서 쓴다. */
    fun showEnd(latitude: Double, longitude: Double) {
        pendingEnd = latitude to longitude
        if (isReady) applyEnd()
    }

    fun hideStart() {
        if (pendingStart == null) return
        pendingStart = null
        if (isReady) applyStart()
    }

    /** 기록된 마커가 모두 보이도록 지도 영역을 맞춘다. */
    fun fitToRecords(rightCover: Int = 0) {
        fitRightCover = rightCover
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

    private fun applyRoute() {
        val view = mapView ?: return
        if (view.getPolyLineFromId(ROUTE_LINE_ID) != null) view.removeTMapPolyLine(ROUTE_LINE_ID)
        if (pendingRoute.size < 2) return
        val line = TMapPolyLine(ROUTE_LINE_ID, ArrayList(pendingRoute.map { TMapPoint(it.latitude, it.longitude) })).apply {
            lineColor = context.getColor(R.color.map_route)
            lineWidth = context.dp(ROUTE_WIDTH_DP)
            outLineColor = context.getColor(R.color.map_route)
            outLineWidth = 0f
            // 우선순위 숫자가 작을수록 위에 그린다. 마커(기본 9999)가 선 위에 보이도록 선은 더 크게 둔다
            priority = ROUTE_PRIORITY
        }
        view.addTMapPolyLine(line)
    }

    private fun applyStart() = applySquare(START_MARKER_ID, pendingStart, startIcon)

    private fun applyEnd() = applySquare(END_MARKER_ID, pendingEnd, endIcon)

    private fun applySquare(markerId: String, point: Pair<Double, Double>?, squareIcon: Bitmap) {
        val view = mapView ?: return
        if (point == null) {
            if (view.getMarkerItemFromId(markerId) != null) view.removeTMapMarkerItem(markerId)
            return
        }
        val marker = TMapMarkerItem().apply {
            id = markerId
            icon = squareIcon
            setPosition(0.5f, 0.5f)
            setTMapPoint(point.first, point.second)
        }
        if (view.getMarkerItemFromId(markerId) == null) {
            view.addTMapMarkerItem(marker)
        } else {
            view.updateTMapMarkerItem(marker)
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
        val points = (pendingRecords
            .mapNotNull { record ->
                val latitude = record.latitude ?: return@mapNotNull null
                val longitude = record.longitude ?: return@mapNotNull null
                latitude to longitude
            } + listOfNotNull(pendingStart, pendingEnd) + pendingRoute.map { it.latitude to it.longitude })
            .distinct()
        when (points.size) {
            0 -> Unit
            // 점이 하나면 가운데에 두되, 패널이 가리는 만큼은 생각하지 않는다
            1 -> view.setCenterPoint(points[0].first, points[0].second)
            else -> {
                val bounds = view.getBoundsFromPoints(ArrayList(points.map { TMapPoint(it.first, it.second) }))
                val inset = context.dp(FIT_INSET_DP).toInt()
                if (bounds != null) view.fitBounds(bounds, TMapInsets.of(inset, inset, inset + fitRightCover, inset))
            }
        }
    }

    /** 종류를 바꾸면 마커도 새로 그리도록 종류를 id에 넣는다. */
    private fun PetRecord.markerId() = "record_${timeMillis}_${behavior.name}"

    private fun PetRecord.toMarker(): TMapMarkerItem {
        val record = this
        return TMapMarkerItem().apply {
            id = record.markerId()
            icon = markerIcons.getOrPut(record.behavior) { drawMarkerIcon(record.behavior) }
            setPosition(0.5f, 0.5f)
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

    /** 현재 위치: 흰 테두리의 브랜드 초록 원 안에 흰 마스코트. */
    private fun drawLocationIcon(): Bitmap {
        val radius = context.dp(LOCATION_ICON_RADIUS_DP)
        val stroke = context.dp(LOCATION_STROKE_DP)
        val center = radius + stroke
        val bitmap = Bitmap.createBitmap((center * 2).toInt(), (center * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)
        paint.color = context.getColor(R.color.location_icon)
        canvas.drawCircle(center, center, radius, paint)
        context.getDrawable(R.drawable.mascot_white)?.let { mascot ->
            val width = radius * MASCOT_SCALE
            val height = width * mascot.intrinsicHeight / mascot.intrinsicWidth
            mascot.setBounds(
                (center - width / 2).toInt(), (center - height / 2).toInt(),
                (center + width / 2).toInt(), (center + height / 2).toInt(),
            )
            mascot.draw(canvas)
        }
        return bitmap
    }

    /** 출발(밝은 색)·도착(초록) 지점: 진한 테두리의 네모. */
    private fun drawSquareIcon(fillColor: Int): Bitmap {
        val size = context.dp(START_ICON_SIZE_DP)
        val stroke = context.dp(MARKER_STROKE_DP)
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val corner = context.dp(START_ICON_CORNER_DP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = context.getColor(fillColor)
        canvas.drawRoundRect(rect, corner, corner, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = context.getColor(R.color.map_outline)
        canvas.drawRoundRect(rect, corner, corner, paint)
        return bitmap
    }

    /** 기록: 종류 색 동그라미에 진한 테두리. 가운데가 기록 위치다. */
    private fun drawMarkerIcon(behavior: PetBehavior): Bitmap {
        val radius = context.dp(MARKER_RADIUS_DP)
        val stroke = context.dp(MARKER_STROKE_DP)
        val center = radius + stroke
        val bitmap = Bitmap.createBitmap((center * 2).toInt(), (center * 2).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = context.getColor(R.color.map_outline)
        canvas.drawCircle(center, center, center, paint)
        paint.color = context.getColor(behavior.markerColorRes)
        canvas.drawCircle(center, center, radius, paint)
        return bitmap
    }

    private fun showMessage(message: String) {
        container.removeAllViews()
        container.addView(
            TextView(context).apply {
                text = message
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setBackgroundResource(R.color.bg_sub)
                setTextColor(context.getColor(R.color.fg_muted))
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
        private const val START_MARKER_ID = "start"
        private const val END_MARKER_ID = "end"
        private const val ROUTE_LINE_ID = "route"
        private const val ROUTE_WIDTH_DP = 18f
        private const val ROUTE_PRIORITY = 100_000f
        private const val MARKER_RADIUS_DP = 18f
        private const val MARKER_STROKE_DP = 3f
        private const val START_ICON_SIZE_DP = 36f
        private const val START_ICON_CORNER_DP = 4f
        private const val LOCATION_ICON_RADIUS_DP = 26f
        private const val LOCATION_STROKE_DP = 4f
        /** 마스코트 폭 = 원 반지름 × 이 값. */
        private const val MASCOT_SCALE = 1.2f
    }
}
