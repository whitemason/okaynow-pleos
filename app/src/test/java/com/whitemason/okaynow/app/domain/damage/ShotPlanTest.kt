package com.whitemason.okaynow.app.domain.damage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotPlanTest {

    @Test
    fun zones_coverWholeCarWithoutOverlap() {
        var y = 0.05f
        while (y < CAR_LENGTH_M) {
            var x = 0.05f
            while (x < CAR_WIDTH_M) {
                assertEquals("($x,$y)", 1, CarZone.entries.count { it.rect.contains(x, y) })
                x += 0.1f
            }
            y += 0.1f
        }
        assertNull(CarZone.at(-0.1f, 1f))
        assertEquals(CarZone.LEFT_FRONT_DOOR, CarZone.at(0.1f, 2f))
        assertEquals(CarZone.FRONT_BUMPER, CarZone.at(CAR_WIDTH_M / 2, 0.1f))
    }

    @Test
    fun noZones_noSteps() {
        assertTrue(planShots(emptySet()).isEmpty())
    }

    @Test
    fun singleZone_sceneMediumCloseThenPlateAndVideo() {
        val steps = planShots(setOf(CarZone.FRONT_BUMPER))
        assertEquals(
            listOf(ShotKind.SCENE, ShotKind.MEDIUM, ShotKind.CLOSE_UP, ShotKind.OTHER_PLATE, ShotKind.VIDEO),
            steps.map { it.kind },
        )
        assertEquals((1..5).toList(), steps.map { it.number })
    }

    @Test
    fun multipleZones_orderedClockwise_sharedStepsOnce() {
        val steps = planShots(setOf(CarZone.TRUNK_REAR_BUMPER, CarZone.LEFT_FRONT_DOOR, CarZone.FRONT_BUMPER))
        val zoneOrder = steps.filter { it.kind == ShotKind.MEDIUM }.map { it.zone }
        // 앞 → (시계 방향) 뒤 → 운전석 쪽
        assertEquals(listOf(CarZone.FRONT_BUMPER, CarZone.TRUNK_REAR_BUMPER, CarZone.LEFT_FRONT_DOOR), zoneOrder)
        assertEquals(1, steps.count { it.kind == ShotKind.SCENE })
        assertEquals(1, steps.count { it.kind == ShotKind.OTHER_PLATE })
        assertEquals(1, steps.count { it.kind == ShotKind.VIDEO })
    }

    @Test
    fun cameras_standOutsideCar_andFaceTheZone() {
        CarZone.entries.forEach { zone ->
            val steps = planShots(setOf(zone)).filter { it.zone == zone && it.camera != null && it.facingDegrees != null }
            steps.forEach { s ->
                val c = s.camera!!
                assertTrue("$zone ${s.kind} camera inside car: $c", CarZone.at(c.x, c.y) == null || zone == CarZone.ROOF_GLASS)
            }
        }
        // 앞 범퍼 근접 사진은 앞쪽에 서서 뒤(약 180°)를 본다
        val close = planShots(setOf(CarZone.FRONT_BUMPER)).single { it.kind == ShotKind.CLOSE_UP }
        assertTrue("camera y=${close.camera!!.y}", close.camera.y < 0f)
        assertTrue("facing=${close.facingDegrees}", close.facingDegrees!! in 150..210)
    }

    @Test
    fun encodeSteps_isCompactForQr() {
        val all = planShots(CarZone.entries.toSet())
        val encoded = encodeSteps(all)
        assertEquals(all.size, encoded.split(";").size)
        assertTrue("length ${encoded.length}", encoded.length < 900)
        assertTrue(encoded.startsWith("S,,"))
        assertTrue(encoded.contains(";P,,,,;"))
    }

    @Test
    fun summary_countsPhotosAndVideo() {
        val steps = planShots(setOf(CarZone.FRONT_BUMPER, CarZone.HOOD))
        assertEquals(
            "사진 6장, 영상 1개 이상 필요. 순서는 폰으로 QR을 찍어 보세요. 차도 쪽은 피하세요.",
            shotSummary(steps),
        )
    }

    @Test
    fun damageSummary_listsZonesClockwise() {
        assertEquals(null, damageSummary(emptySet()))
        assertEquals("앞 범퍼, 운전석 앞문 파손.", damageSummary(setOf(CarZone.LEFT_FRONT_DOOR, CarZone.FRONT_BUMPER)))
    }
}
