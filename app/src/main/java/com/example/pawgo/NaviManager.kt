package com.example.pawgo

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * NaviHelper SDK(v3.0.5) 래퍼. Pawgo에서는 차량의 현재 위치를 받는 데만 쓴다.
 *
 * 문서의 사용 순서(생성 → initialize → addListener → API 호출 → removeListener/release)를 따른다.
 * SDK 콜백은 백그라운드 스레드에서 오므로 [Callback]은 모두 메인 스레드로 전달한다.
 */
class NaviManager(context: Context, private val callback: Callback) {

    interface Callback {
        fun onConnected()
        fun onConnectionFailed(throwable: Throwable)
        fun onNavigationStatus(status: NavigationStatus)
        fun onCurrentLocation(address: String, latitude: Double, longitude: Double)
    }

    private val naviHelper = NaviHelper(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    var isConnected = false
        private set

    private val naviEventListener = object : NaviHelperEventListener {
        override fun onNavigationStatus(status: NavigationStatus) {
            mainHandler.post { callback.onNavigationStatus(status) }
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            mainHandler.post {
                callback.onCurrentLocation(
                    currentLocationInfo.address, currentLocationInfo.latitude, currentLocationInfo.longitude
                )
            }
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

    /** 현재 위치를 요청한다. 결과는 [Callback.onCurrentLocation]으로 온다. */
    fun requestCurrentLocation() {
        naviHelper.getCurrentLocationInfo()
    }

    companion object {
        private const val TAG = "NaviManager"
    }
}
