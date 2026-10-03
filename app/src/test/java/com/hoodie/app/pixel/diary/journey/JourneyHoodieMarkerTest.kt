package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class JourneyHoodieMarkerTest {
    private val layout = JourneyTestFixtures.layout
    private val seg = layout.segments.first()

    @Test
    fun `meio de transporte vira o visual do Hoodie`() {
        assertEquals(JourneyVehicle.ON_FOOT, JourneyHoodieMarker.vehicleFor(MovementMode.WALKING))
        assertEquals(JourneyVehicle.ON_FOOT, JourneyHoodieMarker.vehicleFor(null))
        assertEquals(JourneyVehicle.BICYCLE, JourneyHoodieMarker.vehicleFor(MovementMode.BICYCLE))
        assertEquals(JourneyVehicle.GENERIC_TRANSIT, JourneyHoodieMarker.vehicleFor(MovementMode.VEHICLE_UNKNOWN))
        assertEquals(JourneyVehicle.GENERIC_TRANSIT, JourneyHoodieMarker.vehicleFor(MovementMode.PUBLIC_TRANSPORT))
        assertEquals(JourneyVehicle.METRO, JourneyHoodieMarker.vehicleFor(MovementMode.METRO))
        assertEquals(JourneyVehicle.TRAIN, JourneyHoodieMarker.vehicleFor(MovementMode.TRAIN))
        assertEquals(JourneyVehicle.OTHER, JourneyHoodieMarker.vehicleFor(MovementMode.OTHER))
    }

    @Test
    fun `sai da plataforma A e chega na plataforma B`() {
        val start = JourneyHoodieMarker.onSegment(seg, MovementMode.WALKING, 0f)
        assertEquals(layout.nodes[0].stop, start.position)
        assertFalse(start.moving)
        val end = JourneyHoodieMarker.onSegment(seg, MovementMode.WALKING, 1f)
        assertEquals(layout.nodes[1].stop, end.position)
        assertTrue(JourneyHoodieMarker.onSegment(seg, MovementMode.WALKING, 0.4f).moving)
    }

    @Test
    fun `nunca teleporta - quadros seguidos andam pouco`() {
        var last = JourneyHoodieMarker.onSegment(seg, MovementMode.BUS, 0f).position
        for (i in 1..200) {
            val p = JourneyHoodieMarker.onSegment(seg, MovementMode.BUS, i / 200f).position
            assertTrue(hypot(p.x - last.x, p.y - last.y) <= seg.path.length / 200f + 0.75f)
            last = p
        }
    }

    @Test
    fun `direcao acompanha a rua e veiculo olha para o sentido do trecho`() {
        assertEquals(MarkerDirection.FRONT, JourneyHoodieMarker.onSegment(seg, MovementMode.WALKING, 0.05f).direction)
        assertEquals(MarkerDirection.RIGHT, JourneyHoodieMarker.onSegment(seg, MovementMode.WALKING, 0.5f).direction)
        assertTrue(JourneyHoodieMarker.onSegment(seg, MovementMode.CAR, 0.05f).facingRight)
        // Trecho 1 vai da direita para a esquerda.
        val back = layout.segments[1]
        assertEquals(MarkerDirection.LEFT, JourneyHoodieMarker.onSegment(back, MovementMode.WALKING, 0.5f).direction)
        assertFalse(JourneyHoodieMarker.onSegment(back, MovementMode.CAR, 0.5f).facingRight)
        val parked = JourneyHoodieMarker.atNode(layout.nodes[2])
        assertEquals(MarkerDirection.FRONT, parked.direction); assertFalse(parked.moving)
    }

    @Test
    fun `cada visual desenha o Hoodie em volta da posicao`() {
        JourneyVehicle.entries.forEach { v ->
            val b = PixelBuffer(80, 60)
            val s = JourneyMarkerState(com.hoodie.app.pixel.diary.MapPoint(40f, 45f), MarkerDirection.RIGHT, moving = true, vehicle = v, facingRight = true)
            JourneyHoodieMarker.paint(b, s, 1_000)
            val painted = (0 until 60).sumOf { y -> (0 until 80).count { x -> b[x, y] ushr 24 != 0 } }
            assertTrue("$v desenhou $painted px", painted > 60)
            // Nada abaixo dos pés além do apoio do sprite e da sombra (até 4 px).
            assertTrue((51 until 60).all { y -> (0 until 80).all { x -> b[x, y] ushr 24 == 0 } })
            assertTrue(JourneyHoodieMarker.heightOf(s) > 0)
        }
    }
}
