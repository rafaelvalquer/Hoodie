package com.hoodie.app.engine.notification

import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.notification.NotificationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDeliveryPolicyTest {
    private fun input(
        mode: NotificationMode = NotificationMode.BALANCED, allowed: Boolean = true, vehicle: Boolean = false,
        day: DayState? = DayState.ACTIVE, provisional: Boolean = false, age: Long = MINUTE_MS, delivered: Int = 0, relevant: Boolean = true,
    ) = NotificationDecisionInput(mode, allowed, vehicle, day, provisional, age, delivered, relevant)

    private fun decide(i: NotificationDecisionInput) = NotificationDeliveryPolicy.decide(i)

    @Test fun `caso normal mostra agora`() = assertEquals(DeliveryDecision.SHOW_NOW, decide(input()))

    @Test fun `modo silencioso nunca notifica fora do app`() =
        assertEquals(DeliveryDecision.IN_APP_ONLY, decide(input(mode = NotificationMode.SILENT)))

    @Test fun `sem permissao do android fica so no app`() =
        assertEquals(DeliveryDecision.IN_APP_ONLY, decide(input(allowed = false)))

    @Test fun `limites diarios por modo`() {
        assertEquals(0, NotificationDeliveryPolicy.dailyLimit(NotificationMode.SILENT))
        assertEquals(2, NotificationDeliveryPolicy.dailyLimit(NotificationMode.BALANCED))
        assertEquals(4, NotificationDeliveryPolicy.dailyLimit(NotificationMode.PARTICIPATIVE))
        assertEquals(DeliveryDecision.SHOW_NOW, decide(input(delivered = 1)))
        assertEquals(DeliveryDecision.IN_APP_ONLY, decide(input(delivered = 2)))
        assertEquals(DeliveryDecision.SHOW_NOW, decide(input(mode = NotificationMode.PARTICIPATIVE, delivered = 3)))
        assertEquals(DeliveryDecision.IN_APP_ONLY, decide(input(mode = NotificationMode.PARTICIPATIVE, delivered = 4)))
    }

    @Test fun `em veiculo adia`() = assertEquals(DeliveryDecision.DEFER, decide(input(vehicle = true)))

    @Test fun `sono confiavel ou provisorio adia em vez de interromper`() {
        assertEquals(DeliveryDecision.DEFER, decide(input(day = DayState.SLEEPING)))
        assertEquals(DeliveryDecision.DEFER, decide(input(day = DayState.SLEEPING, provisional = true)))
        assertEquals(NotificationDeliveryPolicy.SLEEP_RETRY_MS, NotificationDeliveryPolicy.retryDelayMs(input(day = DayState.SLEEPING)))
        assertEquals(NotificationDeliveryPolicy.PROVISIONAL_RETRY_MS, NotificationDeliveryPolicy.retryDelayMs(input(day = DayState.SLEEPING, provisional = true)))
        assertEquals(NotificationDeliveryPolicy.VEHICLE_RETRY_MS, NotificationDeliveryPolicy.retryDelayMs(input(vehicle = true)))
    }

    @Test fun `pergunta antiga ou irrelevante expira sem alerta tardio`() {
        assertEquals(DeliveryDecision.EXPIRE, decide(input(age = 2 * HOUR_MS + 1)))
        assertEquals(DeliveryDecision.EXPIRE, decide(input(relevant = false)))
        // Expirar vence o modo: nem no app vira alerta.
        assertEquals(DeliveryDecision.EXPIRE, decide(input(mode = NotificationMode.SILENT, relevant = false)))
    }

    @Test fun `modo padrao e equilibrado e valor invalido volta ao padrao`() {
        assertEquals(NotificationMode.BALANCED, NotificationMode.parse(null))
        assertEquals(NotificationMode.BALANCED, NotificationMode.parse("lixo"))
        assertEquals(NotificationMode.SILENT, NotificationMode.parse("SILENT"))
    }
}