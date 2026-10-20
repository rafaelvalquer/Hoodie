package com.hoodie.app.engine.diary.city

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import com.hoodie.app.pixel.diary.city.CityAmbient
import com.hoodie.app.pixel.diary.city.CityRenderer
import com.hoodie.app.pixel.diary.city.CityScene
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Jornada em cidade: a cidade é navegável, cada lugar tem um prédio só e o replay anda pelas calçadas. */
class CityJourneyTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(h: Int, m: Int = 0) = LocalDate.of(2026, 10, 20).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun node(i: Int, place: Long, type: PlaceType, name: String, from: Long, to: Long?) = JourneyNode(
        "journey-$i", i, place, name, type, from, to, (to ?: at(21)) - from, null, null,
    )

    /** O dia do mockup: Casa, Academia, Casa, Trabalho, Restaurante, Trabalho, Farmácia, Casa. */
    private val day: JourneyMapData by lazy {
        val nodes = listOf(
            node(0, 15, PlaceType.HOME, "Casa", at(7), at(7, 30)),
            node(1, 28, PlaceType.GYM, "Smart Fit", at(8), at(9)),
            node(2, 15, PlaceType.HOME, "Casa", at(9, 20), at(9, 45)),
            node(3, 40, PlaceType.WORK, "Escritório", at(10, 20), at(12, 30)),
            node(4, 52, PlaceType.RESTAURANT, "Cantina", at(12, 40), at(13, 30)),
            node(5, 40, PlaceType.WORK, "Escritório", at(13, 40), at(18)),
            node(6, 61, PlaceType.PHARMACY, "Drogaria", at(18, 30), at(18, 50)),
            node(7, 15, PlaceType.HOME, "Casa", at(19, 15), null),
        )
        val segments = nodes.zipWithNext().mapIndexed { i, (a, b) ->
            JourneySegment("journey-seg-$i", i, a.id, b.id, a.departureAt!!, b.arrivalAt, b.arrivalAt - a.departureAt!!, if (i == 2) MovementMode.BUS else MovementMode.WALKING)
        }
        JourneyMapData(nodes, segments, at(7), at(21))
    }
    private val plan by lazy { CityPlan.build(day)!! }

    @Test fun everyBuildingCanReachEveryOtherOnTheSidewalks() {
        CityTown.slots.forEach { a -> CityTown.slots.forEach { b ->
            assertTrue("calçada ${a.id} → ${b.id}", CityTown.route(a.doorX, a.frontY, b.doorX, b.frontY) != null)
        } }
        // nenhum prédio sobrepõe outro
        CityTown.slots.forEach { a -> CityTown.slots.filter { it !== a }.forEach { b ->
            assertFalse("${a.id} × ${b.id}", a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h)
        } }
    }

    @Test fun returnsGoToTheSameBuildingAndTypesPickTheirDistrict() {
        assertEquals("home", plan.placement["id:15"])
        assertEquals("gym", plan.placement["id:28"])
        assertEquals("work", plan.placement["id:40"])
        assertEquals("restaurant", plan.placement["id:52"])
        assertEquals("pharmacy", plan.placement["id:61"])
        assertEquals(listOf(1, 1, 2, 1, 1, 2, 1, 3), plan.stays.map { it.visitNumber })
        assertEquals(setOf("home"), plan.stays.filter { it.placeName == "Casa" }.map { it.slotId }.toSet())
    }

    @Test fun manyPlacesOfTheSameTypeNeverShareABuilding() {
        val gyms = (0 until 3).map { node(it, 100L + it, PlaceType.GYM, "Academia $it", at(8 + it), at(8 + it, 30)) }
        val slots = CityPlacement.assign(gyms).values
        assertEquals(3, slots.toSet().size)
    }

    @Test fun pacingKeepsTheWalkLegibleAndReachesTheEnd() {
        listOf(5, 10).forEach { speed ->
            var ts = plan.startAt; var real = 0L; var guard = 0
            var maxPx = 0.0
            val out = FloatArray(2)
            var last = plan.hoodieAt(ts, speed, out)
            while (ts < plan.endAt && guard++ < 200_000) {
                val next = plan.advance(ts, 33, speed)
                assertTrue("o relógio só anda para a frente", next >= ts)
                val h = plan.hoodieAt(next, speed, FloatArray(2))
                if (h.trip != null && last.trip == h.trip) maxPx = maxOf(maxPx, kotlin.math.abs(h.x - last.x) + kotlin.math.abs(h.y - last.y).toDouble())
                last = h; ts = next; real += 33
            }
            assertEquals(plan.endAt, ts)
            assertTrue("passo de no máximo ~3 px por quadro (era $maxPx)", maxPx <= 3.2)
            assertTrue("$speed min/s: o dia cabe em poucos minutos (${real / 1000}s)", real < 12 * 60_000)
        }
    }

    @Test fun theHoodieWalksOnSidewalksAndIsInsideWhileStaying() {
        val out = FloatArray(2)
        var ts = plan.startAt
        while (ts < plan.endAt) {
            val h = plan.hoodieAt(ts, 10, out)
            if (h.trip != null && h.trip.vehicle == CityTrip.Vehicle.WALK) {
                val tx = (h.x / CityTown.TS).toInt(); val ty = (h.y / CityTown.TS).toInt()
                assertTrue("andando fora da calçada em ($tx,$ty)", CityTown.walkable(tx, ty))
            }
            ts += 60_000
        }
        val working = plan.hoodieAt(at(15), 10)
        assertNotNull(working.inside); assertEquals("work", working.inside!!.slotId)
        assertNull(plan.hoodieAt(at(9, 5), 10).inside) // a caminho de casa
        assertEquals(CityTrip.Vehicle.BUS, plan.tripAt(at(10))!!.vehicle)
    }

    @Test fun rendersFollowAndOverviewFramesAllDay() {
        val scene = CityScene.build(plan)
        val ambient = CityAmbient(); val renderer = CityRenderer(); val view = PixelBuffer(240, 176)
        var t = 0f
        listOf(at(7, 10), at(8, 30), at(9, 5), at(12, 35), at(15), at(19, 5), at(20, 30)).forEach { ts ->
            repeat(30) { ambient.update(1 / 30f, t, 0f, 0f); ambient.updateSmoke(1 / 30f, scene.buildings); t += 1 / 30f }
            val h = plan.hoodieAt(ts, 10)
            val minute = java.time.Instant.ofEpochMilli(ts).atZone(zone).let { it.hour * 60 + it.minute }
            renderer.render(view, scene, ambient, h, (h.x - 120).toInt().coerceIn(0, CityTown.WW - 240), (h.y - 88).toInt().coerceIn(0, CityTown.WH - 176), t, minute, null, false)
            assertTrue(view.pixels.all { it ushr 24 == 0xFF })
            renderer.renderOverview(view, scene, h, ts, t)
        }
        assertEquals("CANTINA", scene.buildingBySlot.getValue("restaurant").label)
    }
}
