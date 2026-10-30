
package com.example.pawgo

import android.app.Activity
import android.os.Bundle
import ai.pleos.playground.navi.helper.NaviHelper

class MainActivity : Activity() {

    private lateinit var naviHelper: NaviHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        naviHelper = NaviHelper(applicationContext)

        naviHelper.initialize(
            onConnected = {
                // NaviHelper 서비스 연결 성공
            },
            onFailed = { throwable ->
                // NaviHelper 서비스 연결 실패
                throwable.printStackTrace()
            }
        )
    }
}
