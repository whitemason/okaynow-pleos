package com.whitemason.okaynow.app.presentation.screens

import com.whitemason.okaynow.app.domain.vehicle.ExteriorLight
import com.whitemason.okaynow.app.domain.vehicle.SafetyWarning
import com.whitemason.okaynow.app.domain.vehicle.SignalType
import com.whitemason.okaynow.app.domain.vehicle.TireStatus
import com.whitemason.okaynow.app.domain.vehicle.VehicleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCheckCardsTest {

    @Test
    fun unsupportedSignals_areHidden() {
        // 에뮬레이터 실측과 같은 조합: 타이어·비상등 미지원
        val status = VehicleStatus(
            supported = setOf(SignalType.DOOR_OPEN, SignalType.SAFETY_WARNINGS, SignalType.EXTERIOR_LIGHTS),
            doorOpen = false,
            safetyWarnings = emptySet(),
            exteriorLights = setOf(ExteriorLight.LOW_BEAM),
            tireStatus = TireStatus.LOW_PRESSURE_WARNING, // 지원 목록에 없으면 값이 있어도 숨긴다
        )
        assertEquals(listOf("도어", "외부 등화", "경고"), checkCards(status).map { it.label })
    }

    @Test
    fun abnormalItems_areFlagged() {
        val status = VehicleStatus(
            supported = SignalType.entries.toSet(),
            doorOpen = true,
            hazardLight = false,
            tireStatus = TireStatus.LOW_PRESSURE_WARNING,
            safetyWarnings = setOf(SafetyWarning.MASTER_WARNING),
            exteriorLights = emptySet(),
        )
        val warnings = checkCards(status).filter { it.warning }.map { it.label }
        assertEquals(setOf("타이어", "도어", "비상등", "경고"), warnings.toSet())
        assertTrue(checkCards(status).single { it.label == "외부 등화" }.warning.not())
    }

    @Test
    fun noValueYet_isHidden() {
        val status = VehicleStatus(supported = setOf(SignalType.DOOR_OPEN), doorOpen = null)
        assertTrue(checkCards(status).isEmpty())
    }
}
