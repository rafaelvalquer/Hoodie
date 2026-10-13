package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextQuestion
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.detection.ConfidenceScore
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePrimaryActionPolicyTest {
    private fun question(kind: QuestionKind, id: Long = 7) =
        ContextQuestion(id, kind, UserContextType.WORK, null, null, null, 0, null, null)

    private fun snapshot(status: HomeNowStatus, eventId: Long? = 11, context: UserContextType? = UserContextType.WORK) = HomeNowSnapshot(
        dayState = null, context = context, contextEventId = eventId, contextStartedAt = 0, placeId = null, placeName = null,
        contextConfidence = ConfidenceScore(.9f), contextSource = ContextSource.GEOFENCE, wakeAt = null, wakeConfidence = null,
        status = status, updatedAt = 0,
    )

    @Test fun `sem pendencia nao mostra cartao extra`() {
        assertNull(HomePresentationMapper.primaryAction(HomeUiState(loading = false)))
    }

    @Test fun `pergunta e sugestao simultaneas mostram so a pergunta`() {
        val s = HomeUiState(loading = false, question = question(QuestionKind.CONFIRM_CONTEXT), suggestSavePlace = PlaceType.WORK)
        assertEquals(HomePrimaryAction.PendingQuestion(7), HomePresentationMapper.primaryAction(s))
    }

    @Test fun `monitoramento indisponivel vence a sugestao de lugar`() {
        val s = HomeUiState(loading = false, locationStatus = LocationStatus.NO_BACKGROUND, suggestSavePlace = PlaceType.WORK)
        assertEquals(HomePrimaryAction.MonitoringWarning(MonitoringIssue.NO_BACKGROUND), HomePresentationMapper.primaryAction(s))
    }

    @Test fun `rotina provavel com localizacao ok vira aviso`() {
        val s = HomeUiState(loading = false, probableMode = true)
        assertEquals(HomePrimaryAction.MonitoringWarning(MonitoringIssue.PROBABLE_ROUTINE), HomePresentationMapper.primaryAction(s))
    }

    @Test fun `sugestao de lugar quando nao ha mais nada`() {
        val s = HomeUiState(loading = false, suggestSavePlace = PlaceType.HOME)
        assertEquals(HomePrimaryAction.SuggestPlace(PlaceType.HOME), HomePresentationMapper.primaryAction(s))
    }

    @Test fun `em veiculo perguntas de lugar novo e sugestoes ficam ocultas`() {
        val newPlace = HomeUiState(loading = false, vehicleMoving = true, question = question(QuestionKind.NEW_PLACE), suggestSavePlace = PlaceType.WORK)
        assertNull(HomePresentationMapper.primaryAction(newPlace))
        val savePlace = newPlace.copy(question = question(QuestionKind.SAVE_PLACE))
        assertNull(HomePresentationMapper.primaryAction(savePlace))
    }

    @Test fun `em veiculo pergunta de confirmacao continua disponivel`() {
        val s = HomeUiState(loading = false, vehicleMoving = true, question = question(QuestionKind.CONFIRM_MOVEMENT, id = 3))
        assertEquals(HomePrimaryAction.PendingQuestion(3), HomePresentationMapper.primaryAction(s))
    }

    @Test fun `inferencia pode ser confirmada mesmo com confianca alta`() {
        val q = HomePresentationMapper.quickActions(snapshot(HomeNowStatus.IDENTIFIED))
        assertTrue(q.canConfirm); assertTrue(q.canCorrect); assertFalse(q.confirmed)
        assertEquals(11L, q.confirmEventId)
    }

    @Test fun `contexto confirmado nao repete a pergunta mas permite corrigir`() {
        val q = HomePresentationMapper.quickActions(snapshot(HomeNowStatus.CONFIRMED))
        assertFalse(q.canConfirm); assertTrue(q.canCorrect); assertTrue(q.confirmed)
    }

    @Test fun `desconhecido ou sem evento nao oferece confirmacao`() {
        assertFalse(HomePresentationMapper.quickActions(snapshot(HomeNowStatus.UNKNOWN, context = null)).canConfirm)
        assertFalse(HomePresentationMapper.quickActions(snapshot(HomeNowStatus.IDENTIFIED, eventId = null)).canConfirm)
        assertFalse(HomePresentationMapper.quickActions(null).canCorrect)
    }
}