package com.hoodie.app.integration.mobility

import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.MobilitySettings
import com.hoodie.app.core.mobility.MobilityRegistration
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.core.time.DayPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cena pelo deslocamento real; preferência só quando não há sessão; registro do sensor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MobilityVisualIntegrationTest {

    private fun scene(mode: MovementMode?, style: CommuteStyle = CommuteStyle.WALK, variant: Int = 0) =
        VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, commute = style, mobilityMode = mode, variant = variant).scene

    @Test
    fun `mapeamento modo para cena`() {
        assertEquals(SceneId.STREET, scene(MovementMode.WALKING, CommuteStyle.BUS))
        assertEquals(SceneId.STREET, scene(MovementMode.RUNNING, CommuteStyle.BUS))
        assertEquals(SceneId.BICYCLE, scene(MovementMode.BICYCLE, CommuteStyle.BUS))
        assertEquals(SceneId.BUS, scene(MovementMode.BUS, CommuteStyle.WALK))
        assertEquals(SceneId.TRAIN, scene(MovementMode.TRAIN, CommuteStyle.WALK))
        assertEquals(SceneId.METRO, scene(MovementMode.METRO, CommuteStyle.WALK))
        listOf(MovementMode.PUBLIC_TRANSPORT, MovementMode.VEHICLE_UNKNOWN)
            .forEach { assertEquals(it.name, SceneId.TRANSIT, scene(it, CommuteStyle.WALK)) }
        assertEquals(SceneId.GENERIC_RIDE, scene(MovementMode.OTHER, CommuteStyle.WALK))
        assertEquals(SceneId.CAR, scene(MovementMode.CAR))
    }

    @Test
    fun `sem sessao real a preferencia commuteStyle continua valendo`() {
        assertEquals(SceneId.STREET, scene(null, CommuteStyle.WALK))
        assertEquals(SceneId.BUS, scene(null, CommuteStyle.BUS))
        assertEquals(SceneId.STREET, scene(null, CommuteStyle.RANDOM, variant = 0))
        assertEquals(SceneId.BUS, scene(null, CommuteStyle.RANDOM, variant = 1))
        // Fora do deslocamento a mobilidade não muda nada.
        assertEquals(SceneId.OFFICE, VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, mobilityMode = MovementMode.CAR).scene)
    }

    @Test
    fun `carro em camadas registrado, sentado e desenhando com movimento`() {
        val v = VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, mobilityMode = MovementMode.CAR)
        assertTrue(v.actions.any { it.anim == AnimationId.CAR_IDLE })
        val car = SceneRegistry[SceneId.CAR]
        assertFalse("o ocupante permanece no assento durante o percurso", car.walkInPlace)
        fun frame(t: Long) = PixelBuffer(car.width, car.height).also { b ->
            val env = SceneEnv(DayPeriod.DAY, 8 * 60)
            car.drawBackground(b, env)
            car.sortedProps.forEach { it.draw(b, env, t) }
        }
        assertFalse("o mundo corre: quadros diferentes", frame(0).pixels.contentEquals(frame(400).pixels))
        // Faróis acesos à noite: a camada emissiva do carro em camadas fica sobre a luz do período.
        val nightEnv = SceneEnv(DayPeriod.NIGHT, 22 * 60)
        val night = com.hoodie.app.pixel.renderer.SceneRenderer().renderEmpty(car, nightEnv, 0)
        val lit = com.hoodie.app.pixel.art.SceneArtStore.get("car")!!.layer("emissive", DayPeriod.NIGHT)!!.pixels.filter { it ushr 24 == 0xFF }.toSet()
        assertTrue("os faróis acendem à noite", night.pixels.any { it in lit })
    }

    @Test
    fun `deslocamento confirmado muda a cena e candidato nao`() {
        val m = MobilityGraph()
        try {
            m.at(MONDAY, 7, 47); m.move(com.hoodie.app.core.mobility.DetectedMovement.WALKING)
            m.geofence(m.home, com.hoodie.app.engine.context.GeofenceTransition.EXIT)
            assertEquals("candidato não muda a cena", null, runBlocking { m.repo.activeMode.first() })
            m.answer(com.hoodie.app.core.model.QuestionKind.CONFIRM_MOVEMENT, yes = true)
            assertEquals(MovementMode.WALKING, runBlocking { m.repo.activeMode.first() })
        } finally { m.close() }
    }

    @Test
    fun `reboot e ajustes - activity recognition registrado so quando faz sentido`() = runBlocking {
        val m = MobilityGraph()
        try {
            val registrar = FakeArRegistrar()
            val reg = MobilityRegistration(m.g.settings, m.arPermissions, registrar)
            assertFalse("antes do onboarding nada", reg.sync())
            m.g.settings.completeOnboarding("Hoodie", m.now())
            assertTrue(reg.sync()); assertTrue(registrar.registered)
            // "Reboot": o sistema esqueceu; o BootReceiver chama sync de novo.
            registrar.registered = false
            assertTrue(reg.sync()); assertEquals(2, registrar.registerCalls)
            m.g.settings.setMobility(MobilitySettings(detectionEnabled = false))
            assertFalse(reg.sync()); assertFalse(registrar.registered)
            m.g.settings.setMobility(MobilitySettings())
            m.arPermissions.granted = false
            assertFalse(reg.sync()); assertFalse(registrar.registered)
            assertEquals(AppSettings().mobility, MobilitySettings())
        } finally { m.close() }
    }
}
