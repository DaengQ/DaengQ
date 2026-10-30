package com.example.pawgo

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * NaviHelper SDK(v3.0.5) 래퍼.
 *
 * 문서의 사용 순서(생성 → initialize → addListener → API 호출 → removeListener/release)를 따른다.
 * SDK 콜백은 백그라운드 스레드에서 오므로 [Callback]은 모두 메인 스레드로 전달한다.
 */
class NaviManager(context: Context, private val callback: Callback) {

    interface Callback {
        fun onConnected()
        fun onConnectionFailed(throwable: Throwable)
        fun onNavigationStatus(status: NavigationStatus)
        fun onCurrentLocation(address: String)
        fun onEvent(message: String)
    }

    private val naviHelper = NaviHelper(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnected = false
        private set

    private val naviEventListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            post(
                "경로 탐색 시작: ${routeStartInfo.name} " +
                    "(${formatDistance(routeStartInfo.distance)}, ${formatDuration(routeStartInfo.duration)})"
            )
        }

        override fun onRoutingCompleted() {
            post("경로 탐색 완료, 안내를 시작합니다")
        }

        override fun onRouteCancelled() {
            post("경로 안내가 취소되었습니다")
        }

        override fun onDestinationChanged() {
            post("목적지가 변경되었습니다")
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            post("목적지에 도착했습니다")
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            // 안내 중이 아니면 빈 목적지 정보가 온다
            if (destinationInfo.name.isBlank()) {
                post("설정된 목적지가 없습니다")
                return
            }
            post(
                "현재 목적지: ${destinationInfo.name} " +
                    "(남은 거리 ${formatDistance(destinationInfo.remainDistance)}, " +
                    "남은 시간 ${formatDuration(destinationInfo.remainTime)})"
            )
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            mainHandler.post { callback.onNavigationStatus(status) }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            mainHandler.post { callback.onCurrentLocation(currentLocationInfo.address) }
        }

        override fun onError(errorCode: NaviErrorCode) {
            val message = when (errorCode) {
                NaviErrorCode.CAUSE_LOW_BATTERY -> "배터리 잔량이 부족합니다"
                NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE -> "탐색 가능한 최대 거리를 넘었습니다"
                NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE -> "목적지가 현재 위치와 너무 가깝습니다"
                NaviErrorCode.CAUSE_SEARCH_ROUTE -> "경로 탐색 서버 오류가 발생했습니다"
                NaviErrorCode.CAUSE_SEARCH_TRIP -> "트립 서버 오류가 발생했습니다"
            }
            post("오류: $message")
        }
    }

    fun connect() {
        naviHelper.initialize(
            onConnected = {
                naviHelper.addListener(naviEventListener)
                mainHandler.post {
                    isConnected = true
                    callback.onConnected()
                }
            },
            onFailed = { throwable ->
                Log.e(TAG, "NaviHelper connection failed", throwable)
                mainHandler.post {
                    isConnected = false
                    callback.onConnectionFailed(throwable)
                }
            }
        )
    }

    fun release() {
        naviHelper.removeListener(naviEventListener)
        naviHelper.release()
        mainHandler.removeCallbacksAndMessages(null)
        isConnected = false
    }

    /** 목적지를 설정하고 안내를 요청한다. 결과는 리스너 콜백으로 온다. */
    fun startRoute(destination: Destination) {
        naviHelper.requestRoute(
            RouteInfo(
                latitude = destination.latitude,
                longitude = destination.longitude,
                poiName = destination.name,
                poiId = destination.poiId,
                poiSubId = destination.poiSubId,
                address = destination.address
            )
        )
    }

    fun cancelRoute() {
        naviHelper.cancelRoute()
    }

    fun requestDestinationInfo() {
        naviHelper.getDestinationInfo()
    }

    /** 현재 위치를 요청한다. 결과는 [Callback.onCurrentLocation]으로 온다. */
    fun requestCurrentLocation() {
        naviHelper.getCurrentLocationInfo()
    }

    fun showNavigation() {
        naviHelper.requestNavigationScreen(NavigationScreenRequest.Foreground)
    }

    fun searchPlace(keyword: String) {
        naviHelper.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword))
    }

    private fun post(message: String) {
        mainHandler.post { callback.onEvent(message) }
    }

    private fun formatDistance(meters: Int): String =
        if (meters >= 1000) String.format("%.1fkm", meters / 1000.0) else "${meters}m"

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
    }

    data class Destination(
        val name: String,
        val address: String,
        val latitude: Double,
        val longitude: Double,
        val poiId: String,
        val poiSubId: String,
    )

    companion object {
        private const val TAG = "NaviManager"

        /** NaviHelper 공식 문서(Destinations)의 예제 목적지. */
        val SAMPLE_DESTINATION = Destination(
            name = "42dot",
            address = "서울시 강남구 남부순환로 2621",
            latitude = 37.48544722,
            longitude = 127.03636666,
            poiId = "36khp37hh8gqcm5nqhe5",
            poiSubId = "0",
        )
    }
}
