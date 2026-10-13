package com.hoodie.app.integration

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.ContextQuestionEntity
import com.hoodie.app.core.database.DeliveryStatus
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.NotificationGate
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.daystate.DayStateReason
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.notification.NotificationMode
import com.hoodie.app.engine.notification.DeliverySignals
import com.hoodie.app.engine.notification.DeliverySignalSource
import com.hoodie.app.engine.notification.NotificationDeliveryCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** NOT-02 a NOT-10: entrega persistente, adiamento, expiração, dedupe e recuperação após reinício. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDeliveryCoordinatorTest {
    private lateinit var g: TestGraph
    private var vehicle = false
    private var day: DayStateSnapshot? = null
    private var allowed = true
    private var eventId = 0L

    private val signals = DeliverySignalSource { DeliverySignals(vehicle, day) }
    private val gate = NotificationGate { allowed }
    private fun coordinator() = NotificationDeliveryCoordinator(
        g.db.notificationDeliveryDao(), g.db.questionDao(), g.contextDao, g.settings, signals, g.notifier, gate, g.clock,
    )

    @Before fun setUp() = runBlocking {
        g = TestGraph(1_790_000_000_000L)
        eventId = g.contextDao.insert(ContextEventEntity(type = UserContextType.UNKNOWN, startedAt = g.clock.millis - 60_000, endedAt = null, confidence = .5f, placeId = null, source = ContextSource.GEOFENCE))
    }

    @After fun tearDown() = g.close()

    private suspend fun question(contextEvent: Long? = eventId, askedAt: Long = g.clock.millis) = g.db.questionDao().insert(
        ContextQuestionEntity(kind = QuestionKind.NEW_PLACE, candidate = null, placeId = null, encryptedCoordinates = null, contextEventId = contextEvent, askedAt = askedAt),
    )

    private suspend fun status(id: Long) = g.db.notificationDeliveryDao().forQuestion(id).single().status

    @Test fun `pergunta elegivel e entregue uma unica vez`() = runBlocking {
        val id = question()
        val c = coordinator()
        c.submitQuestion(id, "Novo lugar?", yesNo = false)
        c.submitQuestion(id, "Novo lugar?", yesNo = false) // duplicada
        assertEquals(1, g.notifier.questions.size)
        assertEquals(DeliveryStatus.DELIVERED, status(id))
    }

    @Test fun `em veiculo adia e depois entrega ao reavaliar`() = runBlocking {
        val id = question()
        vehicle = true
        val c = coordinator()
        c.submitQuestion(id, "Novo lugar?", false)
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.DEFERRED, status(id))
        vehicle = false
        g.clock.millis += 11 * 60_000L
        assertEquals(1, c.reevaluateDue())
        assertEquals(DeliveryStatus.DELIVERED, status(id))
    }

    @Test fun `pergunta adiada e respondida no app nao gera alerta depois`() = runBlocking {
        val id = question()
        vehicle = true
        val c = coordinator()
        c.submitQuestion(id, "Novo lugar?", false)
        val q = g.db.questionDao().getById(id)!!
        g.db.questionDao().update(q.copy(answeredAt = g.clock.millis, answer = "HOME"))
        c.cancelForQuestion(id)
        vehicle = false
        g.clock.millis += 15 * 60_000L
        assertEquals(0, c.reevaluateDue())
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.CANCELLED, status(id))
    }

    @Test fun `modo silencioso nao notifica mas a pergunta segue pendente`() = runBlocking {
        g.settings.setNotificationMode(NotificationMode.SILENT)
        val id = question()
        coordinator().submitQuestion(id, "Novo lugar?", false)
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.IN_APP_ONLY, status(id))
        assertNull(g.db.questionDao().getById(id)!!.answeredAt) // continua na Home
    }

    @Test fun `modo equilibrado respeita o limite diario`() = runBlocking {
        val c = coordinator()
        val ids = List(3) { question() }
        ids.forEach { c.submitQuestion(it, "Pergunta $it", false) }
        assertEquals(2, g.notifier.questions.size)
        assertEquals(DeliveryStatus.IN_APP_ONLY, status(ids[2]))
    }

    @Test fun `permissao revogada preserva o funcionamento interno`() = runBlocking {
        allowed = false
        val id = question()
        coordinator().submitQuestion(id, "Novo lugar?", false)
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.IN_APP_ONLY, status(id))
        assertNull(g.db.questionDao().getById(id)!!.answeredAt)
    }

    @Test fun `pergunta antiga expira sem alerta tardio`() = runBlocking {
        val id = question(askedAt = g.clock.millis - 3 * 3_600_000L)
        coordinator().submitQuestion(id, "Chegou?", false)
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.EXPIRED, status(id))
        assertTrue(id in g.notifier.cancelled)
    }

    @Test fun `contexto que mudou torna a pergunta irrelevante`() = runBlocking {
        val id = question()
        g.contextDao.closeOpen(g.clock.millis)
        g.contextDao.insert(ContextEventEntity(type = UserContextType.HOME, startedAt = g.clock.millis, endedAt = null, confidence = 1f, placeId = null, source = ContextSource.MANUAL))
        coordinator().submitQuestion(id, "Novo lugar?", false)
        assertEquals(DeliveryStatus.EXPIRED, status(id))
        assertTrue(g.notifier.questions.isEmpty())
    }

    @Test fun `sono adia a notificacao`() = runBlocking {
        day = DayStateSnapshot(DayState.SLEEPING, g.clock.millis, ConfidenceScore(.9f), DayStateReason.SLEEP_CONFIRMED, provisional = false)
        val id = question()
        coordinator().submitQuestion(id, "Novo lugar?", false)
        assertTrue(g.notifier.questions.isEmpty())
        assertEquals(DeliveryStatus.DEFERRED, status(id))
    }

    @Test fun `reinicio recupera pendencias sem duplicar e sem despejar tudo`() = runBlocking {
        vehicle = true
        val ids = List(3) { question() }
        coordinator().let { c -> ids.forEach { c.submitQuestion(it, "P$it", false) } }
        assertTrue(g.notifier.questions.isEmpty())
        // "Reiniciou": nova instância, mesmo banco. Só uma entrega por rodada.
        vehicle = false
        g.clock.millis += 11 * 60_000L
        val fresh = coordinator()
        assertEquals(1, fresh.reevaluateDue())
        assertEquals(1, g.notifier.questions.size)
        // Submeter de novo uma pergunta já registrada não duplica.
        fresh.submitQuestion(ids[0], "P", false)
        assertEquals(1, g.notifier.questions.size)
        // Próxima rodada libera mais uma, até o limite diário do modo equilibrado.
        g.clock.millis += 16 * 60_000L
        fresh.reevaluateDue()
        assertEquals(2, g.notifier.questions.size)
    }

    @Test fun `resposta duplicada altera o estado canonico uma unica vez`() = runBlocking {
        val id = g.db.questionDao().insert(ContextQuestionEntity(kind = QuestionKind.CONFIRM_CONTEXT, candidate = UserContextType.HOME, placeId = null, encryptedCoordinates = null, contextEventId = eventId, askedAt = g.clock.millis))
        g.engine.answerYesNo(id, true)
        g.engine.answerYesNo(id, false)
        assertEquals("YES", g.db.questionDao().getById(id)!!.answer)
    }
}