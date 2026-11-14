package com.example.pawgo

/**
 * 지도 위에 그리는 것들의 층. TMAP은 priority 숫자가 작을수록 위에 그리고, 같으면 먼저 넣은 것이 위에 온다.
 * 지도에 무언가를 더 그릴 때는 priority를 여기에만 추가한다. 순서는 MapLayerTest가 지킨다.
 */
object MapLayer {
    const val LOCATION = 1f

    /** 기록 동그라미는 [RECORD_BOTTOM]부터 [RECORD_TOP]까지 나중에 그린 것일수록 위에 온다. */
    const val RECORD_TOP = 2f
    const val RECORD_BOTTOM = 2.99f
    private const val RECORD_STEP = 0.0001f

    const val SQUARE = 3f

    /** TMAP 기본값(9999)보다 커서, 따로 priority를 주지 않은 마커도 경로 선 위에 보인다. */
    const val ROUTE = 100_000f

    /** 다음에 그릴 기록 동그라미의 priority. 방금 그린 것보다 위에 오되 [RECORD_TOP] 아래로는 내려가지 않는다. */
    fun nextRecord(current: Float): Float = maxOf(RECORD_TOP, current - RECORD_STEP)
}
