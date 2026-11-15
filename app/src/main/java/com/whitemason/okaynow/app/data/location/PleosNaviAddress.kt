package com.whitemason.okaynow.app.data.location

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NaviHelper SDK v3.0.5의 현재 위치 정보(주소 포함). 내비가 탑재된 차량에서만 동작하며(SDK_NOTES),
 * 연결·응답이 없으면 null을 돌려준다.
 */
@Singleton
class PleosNaviAddress @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val navi = NaviHelper(context)
    private val connected = CompletableDeferred<Boolean>()
    private val mutex = Mutex()
    @Volatile private var pending: CompletableDeferred<CurrentLocationInfo>? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            pending?.complete(currentLocationInfo)
        }
    }

    init {
        navi.initialize(
            {
                navi.addListener(listener)
                connected.complete(true)
            },
            { e ->
                Log.w(TAG, "navi init failed: ${e.message}")
                connected.complete(false)
            },
        )
    }

    /** 현재 위치 정보. 내비 연결 실패·응답 없음이면 null. */
    suspend fun currentLocation(): CurrentLocationInfo? = mutex.withLock {
        if (withTimeoutOrNull(TIMEOUT_MS) { connected.await() } != true) return@withLock null
        val request = CompletableDeferred<CurrentLocationInfo>().also { pending = it }
        try {
            navi.getCurrentLocationInfo()
            withTimeoutOrNull(TIMEOUT_MS) { request.await() }.also {
                if (it == null) Log.i(TAG, "no current location info from navi")
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "getCurrentLocationInfo failed: ${e.message}")
            null
        } finally {
            pending = null
        }
    }

    private companion object {
        const val TAG = "PleosNavi"
        const val TIMEOUT_MS = 3_000L
    }
}
