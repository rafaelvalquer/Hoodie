package com.hoodie.app.pixel

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.transport.TransportLighting
import com.hoodie.app.pixel.transport.TransportVibration
import com.hoodie.app.pixel.transport.TransportVisualRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportVisualRegistryTest {
    @Test fun `passageiros do trem deixam o assento do Hoodie visivel`() {
        val scene = SceneRegistry[SceneId.TRAIN]
        val seat = scene.spots.getValue(com.hoodie.app.pixel.scene.SpotId.SEAT)
        val passengers = scene.ambientNpcs(SceneEnv(DayPeriod.DAY, 10 * 60))
        assertTrue("o teste deve observar passageiros reais", passengers.isNotEmpty())
        for (time in 0L..5_000L step 250L) passengers.forEach { passenger ->
            val movement = com.hoodie.app.pixel.npc.NpcMotionController.movement(passenger, time)
            assertTrue("${passenger.definition.id} obstrui o assento do Hoodie em $time",
                kotlin.math.abs(movement.x - seat.x) >= com.hoodie.app.pixel.sprite.HoodiePainter.WIDTH ||
                    kotlin.math.abs(movement.floorY - seat.y) >= com.hoodie.app.pixel.sprite.HoodiePainter.HEIGHT)
        }
    }

    @Test fun `vibracao ambiental altera quadros nos transportes em movimento`() {
        val renderer = SceneRenderer()
        val cases = listOf(
            MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO,
            MovementMode.BICYCLE, MovementMode.OTHER, MovementMode.PUBLIC_TRANSPORT,
        )
        cases.forEach { mode ->
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            val vibrating = profile.ambient
            val still = vibrating.copy(vibration = TransportVibration.NONE)
            val scene = SceneRegistry[profile.scene]
            fun render(ambient: com.hoodie.app.pixel.transport.TransportAmbientProfile, time: Long) =
                renderer.renderEmpty(scene, SceneEnv(DayPeriod.DAY, 10 * 60, transportAmbient = ambient), time).pixels.copyOf()
            // A waveform can be at zero at a single instant. Observe a cycle
            // instead of tying the contract to one implementation phase.
            assertTrue("$mode precisa refletir sua vibração no cenário", (0L..1_600L step 160L).any { time ->
                !render(vibrating, time).contentEquals(render(still, time))
            })
        }
    }

    @Test fun `paralaxe habilitada altera pixels renderizados em todos os transportes`() {
        val renderer = SceneRenderer()
        listOf(MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO,
            MovementMode.BICYCLE, MovementMode.OTHER, MovementMode.PUBLIC_TRANSPORT).forEach { mode ->
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            val enabled = profile.ambient.copy(parallax = true, vibration = TransportVibration.NONE)
            val disabled = enabled.copy(parallax = false)
            val scene = SceneRegistry[profile.scene]
            // Metro alternates station and tunnel. Sample the tunnel so its outside lights can pass.
            val sampleTime = if (mode == MovementMode.METRO) 10_000L else 1_000L
            fun render(ambient: com.hoodie.app.pixel.transport.TransportAmbientProfile) =
                renderer.renderEmpty(scene, SceneEnv(DayPeriod.DAY, 10 * 60, transportAmbient = ambient), sampleTime).pixels.copyOf()
            assertNotEquals("$mode precisa aplicar o perfil de parallax aos pixels", render(enabled).toList(), render(disabled).toList())
        }
    }

    @Test fun `perfil de iluminacao altera render da cabine e do tunel`() {
        val renderer = SceneRenderer()
        val scene = SceneRegistry[SceneId.TRANSIT]
        fun render(lighting: TransportLighting): IntArray {
            val ambient = TransportVisualRegistry.profileFor(MovementMode.PUBLIC_TRANSPORT, CommuteStyle.WALK).ambient.copy(lighting = lighting)
            return renderer.renderEmpty(scene, SceneEnv(DayPeriod.NIGHT, 22 * 60, transportAmbient = ambient), 0).pixels.copyOf()
        }
        val daylightCabin = render(TransportLighting.DAYLIGHT_INTERIOR)
        val tunnel = render(TransportLighting.TUNNEL)
        val openAir = render(TransportLighting.OPEN_AIR)
        assertNotEquals(daylightCabin.toList(), tunnel.toList())
        assertNotEquals(daylightCabin.toList(), openAir.toList())
        assertNotEquals(tunnel.toList(), openAir.toList())
    }

    @Test fun `todos os modais conhecidos recebem cena animacoes ambiente e rota coerentes`() {
        val modes = mapOf(
            MovementMode.WALKING to (SceneId.STREET to JourneyVehicle.ON_FOOT),
            MovementMode.RUNNING to (SceneId.STREET to JourneyVehicle.ON_FOOT),
            MovementMode.BICYCLE to (SceneId.BICYCLE to JourneyVehicle.BICYCLE),
            MovementMode.CAR to (SceneId.CAR to JourneyVehicle.CAR),
            MovementMode.BUS to (SceneId.BUS to JourneyVehicle.BUS),
            MovementMode.TRAIN to (SceneId.TRAIN to JourneyVehicle.TRAIN),
            MovementMode.METRO to (SceneId.METRO to JourneyVehicle.METRO),
            MovementMode.OTHER to (SceneId.GENERIC_RIDE to JourneyVehicle.OTHER),
            MovementMode.PUBLIC_TRANSPORT to (SceneId.TRANSIT to JourneyVehicle.GENERIC_TRANSIT),
            MovementMode.VEHICLE_UNKNOWN to (SceneId.TRANSIT to JourneyVehicle.GENERIC_TRANSIT),
        )
        modes.forEach { (mode, expected) ->
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            assertEquals(mode.name, expected.first, profile.scene)
            assertEquals(mode.name, expected.second, profile.journey.vehicle)
            assertTrue(mode.name, profile.animationSet.primary.isNotEmpty())
            assertTrue(mode.name, profile.scene in SceneId.entries)
            assertTrue(mode.name, profile.ambient.outsideSpeed > 0f)
        }
        val unique = listOf(MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO, MovementMode.BICYCLE)
            .map { TransportVisualRegistry.profileFor(it, CommuteStyle.WALK).scene }
        assertEquals(unique.size, unique.toSet().size)
        assertEquals(SceneId.STREET, TransportVisualRegistry.profileFor(null, CommuteStyle.WALK).scene)
        assertEquals(SceneId.BUS, TransportVisualRegistry.profileFor(null, CommuteStyle.BUS).scene)
        assertEquals(SceneId.STREET, TransportVisualRegistry.profileFor(null, CommuteStyle.RANDOM, variant = 0).scene)
        assertEquals(SceneId.BUS, TransportVisualRegistry.profileFor(null, CommuteStyle.RANDOM, variant = 1).scene)
        assertNotEquals(
            TransportVisualRegistry.profileFor(MovementMode.CAR, CommuteStyle.WALK).ambient,
            TransportVisualRegistry.profileFor(MovementMode.METRO, CommuteStyle.WALK).ambient,
        )
    }

    @Test fun `transporte novo tem cenas renderizaveis e animacoes registradas`() {
        val modes = listOf(MovementMode.CAR, MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO, MovementMode.BICYCLE, MovementMode.OTHER)
        modes.forEach { mode ->
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            assertEquals(profile.scene, SceneRegistry[profile.scene].id)
            (profile.animationSet.enter + profile.animationSet.primary + profile.animationSet.exit).forEach { id ->
                assertTrue("$mode/$id", AnimationId.entries.contains(id))
                assertTrue("$mode/$id", id.frames.isNotEmpty())
            }
        }
    }

    @Test fun `fallback de transporte desconhecido nunca usa animacao identificada de onibus`() {
        listOf(MovementMode.PUBLIC_TRANSPORT, MovementMode.VEHICLE_UNKNOWN).forEach { mode ->
            val profile = TransportVisualRegistry.profileFor(mode, CommuteStyle.WALK)
            val ids = profile.animationSet.enter + profile.animationSet.primary + profile.animationSet.exit
            assertEquals(SceneId.TRANSIT, profile.scene)
            assertTrue(ids.containsAll(listOf(AnimationId.TRANSIT_ENTER, AnimationId.TRANSIT_SIT, AnimationId.TRANSIT_EXIT)))
            assertTrue(ids.none { it.name.startsWith("BUS_") })
        }
    }

    @Test fun `catalogo fisico alinhado para selecao manual e novo lugar`() {
        val expected = listOf("Casa", "Trabalho", "Academia", "Escola", "Restaurante", "Mercado", "Loja", "Casa de amigos ou familiares", "Lazer", "Outro")
        assertEquals(expected, PlaceType.entries.map { it.label })
        assertEquals(expected, PlaceType.newPlaceOptions.map { it.label })
        assertEquals("STORE", PlaceType.STORE.name) // nome persistido é aditivo e estável
        assertEquals(
            listOf(UserContextType.HOME, UserContextType.WORK, UserContextType.STUDY, UserContextType.SHOPPING, UserContextType.GYM, UserContextType.LEISURE, UserContextType.VISITING, UserContextType.LUNCH, UserContextType.DINING, UserContextType.UNKNOWN),
            UserContextType.manualOptions,
        )
    }
}
