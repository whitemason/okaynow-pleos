package com.whitemason.okaynow.app.data.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 내비(NaviHelper)가 주소를 주면 내비의 좌표·주소를 함께 쓴다. 주소와 좌표가 같은 지점을 가리키게 하려는 것으로,
 * 에뮬레이터에서는 FusedLocation(미국 기본 위치)과 내비(국내 위치)가 서로 달랐다.
 * 주소가 없으면 FusedLocation SDK v1.1.3 좌표를, 그것도 없으면 내비 좌표를 쓰고, 모두 없으면 null(CLAUDE.md 규칙 3).
 */
@Singleton
class PleosLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val navi: PleosNaviAddress,
) : LocationSource {

    private val fused by lazy {
        FusedLocation(context).takeIf { it.isSupported() }?.also { it.initialize() }
    }

    override suspend fun current(): LocationFix? {
        val info = navi.currentLocation()
        val address = info?.address?.takeIf { it.isNotBlank() }
        if (info != null && address != null) {
            return LocationFix(info.latitude, info.longitude, System.currentTimeMillis(), address)
        }
        return fusedFix()
            ?: info?.let { LocationFix(it.latitude, it.longitude, System.currentTimeMillis()) }
    }

    private suspend fun fusedFix(): LocationFix? {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "$PERMISSION not granted")
            return null
        }
        val sdk = fused ?: return null.also { Log.i(TAG, "FusedLocation not supported") }
        // 마지막 위치가 없으면(null) 위치 업데이트를 잠깐 받아 첫 값을 쓴다.
        return withTimeoutOrNull(TIMEOUT_MS) { once { sdk.getLastKnownLocation(it) } }
            ?: withTimeoutOrNull(TIMEOUT_MS) {
                var listener: FusedLocationListener? = null
                try {
                    once { l -> listener = l; sdk.registerFusedLocationCallback(l) }
                } finally {
                    listener?.let(sdk::unregisterFusedLocationCallback)
                }
            }
    }

    private suspend fun once(start: (FusedLocationListener) -> Unit): LocationFix? =
        suspendCancellableCoroutine { cont ->
            start(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    if (cont.isActive) cont.resume(location?.let { LocationFix(it.latitude, it.longitude, it.timestampMillis) })
                }
                override fun onError(exception: Exception) {
                    Log.w(TAG, "location error: ${exception.message}")
                    if (cont.isActive) cont.resume(null)
                }
            })
        }

    private companion object {
        const val TAG = "PleosLocation"
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
        const val TIMEOUT_MS = 3_000L
    }
}
