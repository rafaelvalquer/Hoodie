package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.ZONE
import com.hoodie.app.engine.at
import com.hoodie.app.engine.context.AskedQuestion
import com.hoodie.app.engine.ms
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras da mobilidade: máquina de estados, score, política, aprendizado e chegada. */
class MobilityRulesTest {

    // ── Máquina de estados ──

    @Test
    fun `transicoes seguem o diagrama`() {
        val sm = MobilityStateMachine
        assertTrue(sm.canTransition(MobilityState.STATIONARY, MobilityState.MOVEMENT_CANDIDATE))
        assertTrue(sm.canTransition(MobilityState.MOVEMENT_CANDIDATE, MobilityState.WALKING))
        assertTrue(sm.canTransition(MobilityState.WALKING, MobilityState.IN_VEHICLE))
        assertTrue(sm.canTransition(MobilityState.IN_VEHICLE, MobilityState.WALKING))
        assertTrue(sm.canTransition(MobilityState.IN_VEHICLE, MobilityState.ARRIVING))
        assertTrue(sm.canTransition(MobilityState.ARRIVING, MobilityState.ARRIVED))
        // Não se chega sem ter saído; não se pula de parado para veículo sem candidato.
        assertFalse(sm.canTransition(MobilityState.STATIONARY, MobilityState.ARRIVED))
        assertFalse(sm.canTransition(MobilityState.STATIONARY, MobilityState.IN_VEHICLE))
        assertFalse(sm.canTransition(MobilityState.MOVEMENT_CANDIDATE, MobilityState.ARRIVED))
    }

    @Test
    fun `ruido nao muda estado - limiares por modo`() {
        val sm = MobilityStateMachine
        val t0 = 1_000_000L
        assertFalse("20 s andando não é deslocamento", sm.isSustained(MovementMode.WALKING, t0, null, t0 + 20_000))
        assertTrue(sm.isSustained(MovementMode.WALKING, t0, null, t0 + HoodieConfig.WALK_CONFIRM_MS))
        assertFalse(sm.isSustained(MovementMode.VEHICLE_UNKNOWN, t0, null, t0 + 60_000))
        assertTrue(sm.isSustained(MovementMode.VEHICLE_UNKNOWN, t0, null, t0 + HoodieConfig.VEHICLE_CONFIRM_MS))
        assertFalse("parou no meio: não é contínuo", sm.isSustained(MovementMode.WALKING, t0, t0 + 10_000, t0 + 5 * MINUTE_MS))
        assertFalse(sm.isArrivalStill(t0, t0 + 2 * MINUTE_MS))
        assertTrue(sm.isArrivalStill(t0, t0 + HoodieConfig.STILL_ARRIVAL_MS))
        assertTrue(sm.isNewSegment(MovementMode.WALKING, MovementMode.BUS))
        assertTrue(sm.isNewSegment(MovementMode.CAR, MovementMode.WALKING))
        assertFalse("veículo → veículo é o mesmo trecho", sm.isNewSegment(MovementMode.BUS, MovementMode.VEHICLE_UNKNOWN))
        assertFalse(sm.isNewSegment(MovementMode.WALKING, MovementMode.RUNNING))
        assertTrue(sm.isFlap(t0, t0 + 2 * MINUTE_MS))
        assertFalse(sm.isFlap(t0, t0 + 10 * MINUTE_MS))
    }

    @Test
    fun `atividade do android vira modo e estado`() {
        assertEquals(MovementMode.VEHICLE_UNKNOWN, DetectedMovement.IN_VEHICLE.toMode())
        assertEquals(MovementMode.BICYCLE, DetectedMovement.ON_BICYCLE.toMode())
        assertEquals(MobilityState.IN_VEHICLE, MobilityStateMachine.stateFor(MovementMode.BUS))
        assertEquals(MobilityState.WALKING, MobilityStateMachine.stateFor(MovementMode.BICYCLE))
        assertFalse(DetectedMovement.STILL.isMoving)
        assertTrue(MovementMode.CAR.isVehicle && !MovementMode.WALKING.isVehicle)
    }

    // ── Score ──

    private fun ev(movement: Boolean = true, exit: Boolean = false, sustained: Boolean = false, time: Boolean = false, history: Boolean = false, inside: Boolean = false) =
        MobilityEvidence(movement, exit, sustained, time, history, inside)

    @Test
    fun `score segue a tabela e as faixas`() {
        assertEquals(30, MobilityConfidenceScorer.score(ev()))
        assertEquals(70, MobilityConfidenceScorer.score(ev(exit = true)))
        assertEquals(85, MobilityConfidenceScorer.score(ev(exit = true, sustained = true)))
        assertEquals(100, MobilityConfidenceScorer.score(ev(exit = true, sustained = true, time = true, history = true)))
        assertEquals(MobilityDecision.IGNORE, MobilityConfidenceScorer.decide(ev()))
        assertEquals(MobilityDecision.APPLY, MobilityConfidenceScorer.decide(ev(exit = true)))
        assertEquals(MobilityDecision.APPLY, MobilityConfidenceScorer.decide(ev(exit = true, sustained = true)))
    }

    @Test
    fun `andar dentro de casa nao inicia deslocamento e geofence sozinha e o fluxo antigo`() {
        // Muito tempo andando dentro de um lugar conhecido, sem saída da geofence.
        assertEquals(MobilityDecision.IGNORE, MobilityConfidenceScorer.decide(ev(sustained = true, time = true, history = true, inside = true)))
        // Sem lugar conhecido, movimento contínuo vale uma pergunta (confiança menor que com geofence).
        assertEquals(MobilityDecision.ASK, MobilityConfidenceScorer.decide(ev(sustained = true)))
        // Só a geofence (sem Activity Recognition): nada aqui — o ContextEngine segue como antes.
        assertEquals(MobilityDecision.IGNORE, MobilityConfidenceScorer.decide(ev(movement = false, exit = true)))
    }

    // ── Política de perguntas ──

    private val now = at(MONDAY, 8).ms()
    private fun policy(kind: QuestionKind, asked: Int = 0, moving: Boolean = false, fg: Boolean = false, recent: List<AskedQuestion> = emptyList()) =
        MobilityConfirmationPolicy.evaluate(kind, asked, moving, fg, now, ZONE, null, recent)

    @Test
    fun `uma pergunta de movimento por sessao e o transporte como segunda`() {
        assertEquals(MobilityConfirmationPolicy.Verdict.ASK, policy(QuestionKind.CONFIRM_MOVEMENT))
        assertEquals(MobilityConfirmationPolicy.Verdict.SKIP, policy(QuestionKind.CONFIRM_MOVEMENT, asked = 1))
        assertEquals(MobilityConfirmationPolicy.Verdict.ASK, policy(QuestionKind.SELECT_TRANSPORT_MODE, asked = 1))
        assertEquals(MobilityConfirmationPolicy.Verdict.SKIP, policy(QuestionKind.SELECT_TRANSPORT_MODE, asked = 2))
        // Chegada tem a própria pergunta.
        assertEquals(MobilityConfirmationPolicy.Verdict.ASK, policy(QuestionKind.CONFIRM_ARRIVAL, asked = 2))
    }

    @Test
    fun `seguranca - veiculo andando adia a pergunta, com app aberto pode`() {
        assertEquals(MobilityConfirmationPolicy.Verdict.DEFER, policy(QuestionKind.SELECT_TRANSPORT_MODE, moving = true))
        assertEquals(MobilityConfirmationPolicy.Verdict.ASK, policy(QuestionKind.SELECT_TRANSPORT_MODE, moving = true, fg = true))
    }

    @Test
    fun `limite diario compartilhado - transporte espera, o resto pula`() {
        val retiredContext = (0 until HoodieConfig.MAX_QUESTIONS_PER_DAY).map { AskedQuestion(QuestionKind.CONFIRM_CONTEXT, UserContextType.WORK, now - it * 1_000) }
        assertEquals("old context confirmations do not block transport", MobilityConfirmationPolicy.Verdict.ASK,
            policy(QuestionKind.CONFIRM_MOVEMENT, recent = retiredContext))
        val full = (0 until HoodieConfig.MAX_QUESTIONS_PER_DAY).map { AskedQuestion(QuestionKind.SELECT_TRANSPORT_MODE, UserContextType.WORK, now - it * 1_000) }
        assertEquals(MobilityConfirmationPolicy.Verdict.SKIP, policy(QuestionKind.CONFIRM_MOVEMENT, recent = full))
        assertEquals(MobilityConfirmationPolicy.Verdict.DEFER, policy(QuestionKind.SELECT_TRANSPORT_MODE, recent = full))
    }

    // ── Aprendizado ──

    private fun trip(day: Int, h: Int, origin: Long? = 1, dest: Long? = 2, vehicle: List<MovementMode> = listOf(MovementMode.BUS), arrival: Boolean? = true) =
        TripRecord(at(day, h).ms(), origin, dest, listOf(MovementMode.WALKING) + vehicle, vehicle, arrival)

    @Test
    fun `historico e destino provavel exigem repeticao no mesmo horario e tipo de dia`() {
        val trips = listOf(trip(MONDAY + 1, 8), trip(MONDAY + 2, 8))
        assertTrue(MobilityLearningEngine.hasHistory(trips, 1, at(MONDAY, 8, 20).ms(), ZONE))
        assertEquals(2L, MobilityLearningEngine.predictedDestination(trips, 1, at(MONDAY, 8).ms(), ZONE))
        assertFalse("outro horário", MobilityLearningEngine.hasHistory(trips, 1, at(MONDAY, 18).ms(), ZONE))
        assertFalse("outra origem", MobilityLearningEngine.hasHistory(trips, 9, at(MONDAY, 8).ms(), ZONE))
        assertFalse("fim de semana", MobilityLearningEngine.hasHistory(trips, 1, at(MONDAY + 5, 8).ms(), ZONE))
        assertNull(MobilityLearningEngine.predictedDestination(trips.take(1), 1, at(MONDAY, 8).ms(), ZONE))
    }

    @Test
    fun `modo de veiculo aprendido so com escolhas consistentes`() {
        val bus3 = (1..3).map { trip(MONDAY + it, 8) }
        assertEquals(MovementMode.BUS, MobilityLearningEngine.learnedVehicleMode(bus3, 1, at(MONDAY, 8).ms(), ZONE))
        assertNull("2 vezes ainda não", MobilityLearningEngine.learnedVehicleMode(bus3.take(2), 1, at(MONDAY, 8).ms(), ZONE))
        // O usuário corrigiu para carro duas vezes: nenhuma maioria de 2/3.
        val mixed = bus3 + listOf(trip(MONDAY + 4, 8, vehicle = listOf(MovementMode.CAR)), trip(MONDAY + 7, 8, vehicle = listOf(MovementMode.CAR)))
        assertNull(MobilityLearningEngine.learnedVehicleMode(mixed, 1, at(MONDAY, 8).ms(), ZONE))
    }

    @Test
    fun `chegada vira automatica com 3 confirmacoes seguidas e um nao derruba`() {
        val three = (1..3).map { trip(MONDAY + it, 8) }
        assertFalse(MobilityLearningEngine.arrivalAutoConfirm(three.take(2), 2))
        assertTrue(MobilityLearningEngine.arrivalAutoConfirm(three, 2))
        val corrected = three + trip(MONDAY + 4, 7, arrival = false)
        assertFalse("um 'não estou no trabalho' recente reduz a confiança", MobilityLearningEngine.arrivalAutoConfirm(corrected, 2))
        // Chegadas não decididas (sem pergunta) não contam nem contra nem a favor.
        assertTrue(MobilityLearningEngine.arrivalAutoConfirm(three + trip(MONDAY + 4, 7, arrival = null), 2))
    }

    // ── Chegada ──

    private val workPlace = Place(2, "Trabalho", PlaceType.WORK, -23.6, -46.63, 150f, 0, 0, null)

    @Test
    fun `resolucao da chegada segue a prioridade sem ler posicao a toa`() = runBlocking {
        var reads = 0
        val byGeofence = ArrivalResolver.resolve(workPlace, { reads++; 0.0 to 0.0 }, { _, _ -> null }, { null })
        assertTrue(byGeofence is ArrivalResolution.KnownPlace && (byGeofence as ArrivalResolution.KnownPlace).byGeofence)
        assertEquals("com geofence não lê posição", 0, reads)
        val byPosition = ArrivalResolver.resolve(null, { reads++; -23.6 to -46.63 }, { _, _ -> workPlace }, { null })
        assertTrue(byPosition is ArrivalResolution.KnownPlace && !(byPosition as ArrivalResolution.KnownPlace).byGeofence)
        assertEquals(1, reads)
        assertTrue(ArrivalResolver.resolve(null, { -1.0 to -1.0 }, { _, _ -> null }, { 2L }) is ArrivalResolution.Unknown)
        assertEquals(ArrivalResolution.Probable(2), ArrivalResolver.resolve(null, { null }, { _, _ -> null }, { 2L }))
        assertEquals(ArrivalResolution.Unresolved, ArrivalResolver.resolve(null, { null }, { _, _ -> null }, { null }))
    }
}
