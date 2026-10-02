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
        assertEquals("BikeScene futura: rua", SceneId.STREET, scene(MovementMode.BICYCLE, CommuteStyle.BUS))
        listOf(MovementMode.BUS, MovementMode.TRAIN, MovementMode.METRO, MovementMode.PUBLIC_TRANSPORT, MovementMode.VEHICLE_UNKNOWN)
            .forEach { assertEquals(it.name, SceneId.TRANSIT, scene(it, CommuteStyle.WALK)) }
        assertEquals(SceneId.CAR, scene(MovementMode.CAR))
    }

    @Test
    fun `sem sessao real a preferencia commuteStyle continua valendo`() {
        assertEquals(SceneId.STREET, scene(null, CommuteStyle.WALK))
        assertEquals(SceneId.TRANSIT, scene(null, CommuteStyle.BUS))
        assertEquals(SceneId.STREET, scene(null, CommuteStyle.RANDOM, variant = 0))
        assertEquals(SceneId.TRANSIT, scene(null, CommuteStyle.RANDOM, variant = 1))
        // Fora do deslocamento a mobilidade não muda nada.
        assertEquals(SceneId.OFFICE, VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, mobilityMode = MovementMode.CAR).scene)
    }

    @Test
    fun `CarScene registrada, sentado e desenhando com movimento`() {
        val v = VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, mobilityMode = MovementMode.CAR)
        assertTrue(v.actions.any { it.anim == AnimationId.BUS_SIT })
        val car = SceneRegistry[SceneId.CAR]
        assertTrue(car.walkInPlace)
        fun frame(t: Long) = PixelBuffer(car.width, car.height).also { b ->
            val env = SceneEnv(DayPeriod.DAY, 8 * 60)
            car.drawBackground(b, env)
            car.sortedProps.forEach { it.draw(b, env, t) }
        }
        assertFalse("o mundo corre: quadros diferentes", frame(0).pixels.contentEquals(frame(400).pixels))
        // Noite: farol aceso.
        val night = PixelBuffer(car.width, car.height).also { b -> car.sortedProps.forEach { it.draw(b, SceneEnv(DayPeriod.NIGHT, 22 * 60), 0) } }
        assertTrue(night.pixels.any { it == com.hoodie.app.pixel.scene.P.LAMP_LIGHT })
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
