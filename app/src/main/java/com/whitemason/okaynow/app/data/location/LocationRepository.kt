package com.whitemason.okaynow.app.data.location

import com.whitemason.okaynow.app.domain.common.Clock
import com.whitemason.okaynow.app.domain.location.LocationFix
import com.whitemason.okaynow.app.domain.location.LocationSource
import com.whitemason.okaynow.app.domain.vehicle.SignalSourceRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 시뮬레이션 모드면 고정 위치, 아니면 Pleos(내비 주소·FusedLocation) 위치. */
@Singleton
class LocationRepository @Inject constructor(
    private val signals: SignalSourceRepository,
    private val pleos: PleosLocationSource,
    clock: Clock,
) : LocationSource {
    private val fake = FakeLocationSource(clock::nowMillis)

    override suspend fun current(): LocationFix? =
        (if (signals.simulation.value) fake else pleos).current()
}
