package com.hoodie.app.engine.detection

import com.hoodie.app.domain.detection.*
import org.junit.Assert.*
import org.junit.Test

class ConfidenceEngineTest {
    @Test fun contextAndMobilityUseTheSameProvisionalDecisionAndExplainTheirScores() {
        val movement = com.hoodie.app.engine.mobility.MobilityConfidenceScorer.detection(
            com.hoodie.app.engine.mobility.MobilityEvidence(true, true, false, false, false), 100,
        )
        assertEquals(.7f, movement.confidence.value, .0001f)
        assertEquals(2, movement.evidence.size)
        val context = com.hoodie.app.engine.context.ContextCandidate(com.hoodie.app.core.model.UserContextType.LUNCH, 70, emptyList())
        assertEquals(context.unifiedDecision, ConfidenceEngine.decide(movement))
        assertEquals(DetectionDecision.PROVISIONAL, context.unifiedDecision)
    }
    private fun result(score: Float, source: DetectionSource = DetectionSource.SENSOR, conflict: Boolean = false) =
        DetectionResult(ConfidenceScore(score), source, emptyList(), conflict)

    @Test fun allDecisionBoundariesAreExplicit() {
        listOf(0f to DetectionDecision.UNKNOWN, .449f to DetectionDecision.UNKNOWN,
            .45f to DetectionDecision.ASK_USER, .599f to DetectionDecision.ASK_USER,
            .60f to DetectionDecision.PROVISIONAL, .849f to DetectionDecision.PROVISIONAL,
            .85f to DetectionDecision.AUTO_ACCEPT, 1f to DetectionDecision.AUTO_ACCEPT).forEach { (score, decision) ->
                assertEquals("score=$score", decision, ConfidenceEngine.decide(result(score)))
            }
        assertEquals(DetectionDecision.UNKNOWN, ConfidenceEngine.decide(result(.51f), relevant = false))
    }

    @Test fun bandsAndInvalidScores() {
        listOf(0f to ConfidenceBand.VERY_LOW, .45f to ConfidenceBand.LOW,
            .60f to ConfidenceBand.MEDIUM, .75f to ConfidenceBand.HIGH, .90f to ConfidenceBand.VERY_HIGH,
            1f to ConfidenceBand.VERY_HIGH).forEach { (score, band) -> assertEquals(band, ConfidenceScore(score).band) }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -.01f, 1.01f).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { ConfidenceScore(bad) }
        }
    }

    @Test fun duplicateOldAndFutureSensorSamplesDoNotInflateConfidence() {
        val now = 2_000_000L
        val evidence = listOf(
            DetectionEvidence(EvidenceType.MOVEMENT, .9f, now - 100),
            DetectionEvidence(EvidenceType.MOVEMENT, .3f, now),
            DetectionEvidence(EvidenceType.GEOFENCE_EXIT, .4f, now),
            DetectionEvidence(EvidenceType.TIME_MATCH, .1f, 0),
            DetectionEvidence(EvidenceType.HISTORY, .2f, now + 1),
        )
        val evaluated = EvidenceEngine.evaluate(evidence, now)
        assertEquals(.7f, evaluated.confidence.value, .0001f)
        assertEquals(2, evaluated.evidence.size)
        assertEquals(DetectionDecision.PROVISIONAL, ConfidenceEngine.decide(evaluated))
    }

    @Test fun correctionAndConfirmationWinConflictingSensors() {
        val conflicting = listOf(DetectionEvidence(EvidenceType.KNOWN_PLACE, .9f, 1), DetectionEvidence(EvidenceType.HISTORY, -.1f, 1))
        assertEquals(DetectionDecision.ASK_USER, ConfidenceEngine.decide(EvidenceEngine.evaluate(conflicting, 1)))
        listOf(EvidenceType.USER_CONFIRMATION, EvidenceType.USER_CORRECTION).forEach { type ->
            val corrected = EvidenceEngine.evaluate(conflicting + DetectionEvidence(type, 1f, 1), 1)
            assertEquals(ConfidenceScore.CERTAIN, corrected.confidence)
            assertEquals(DetectionDecision.AUTO_ACCEPT, ConfidenceEngine.decide(corrected))
        }
        assertFalse(ConfidenceEngine.canReplace(DetectionSource.USER_CORRECTION, DetectionSource.SENSOR))
        assertFalse(ConfidenceEngine.canReplace(DetectionSource.USER_CONFIRMATION, DetectionSource.ROUTINE))
        assertTrue(ConfidenceEngine.canReplace(DetectionSource.SENSOR, DetectionSource.USER_CORRECTION))
    }

    @Test fun cooldownAndTransportSessionSurviveUsingPersistedHistory() {
        val uncertain = result(.52f)
        val asked = DetectionQuestion("bus", 0, 7, "BUS")
        val history = listOf(asked)
        assertFalse(QuestionPolicy.canAsk(uncertain, "bus", 1_799_999, history))
        assertTrue(QuestionPolicy.canAsk(uncertain, "bus", 1_800_000, history))
        assertFalse(QuestionPolicy.canAsk(uncertain, "car", 2_000_000, history, transportSessionId = 7, mode = "CAR"))
        assertTrue(QuestionPolicy.canAsk(uncertain, "car", 2_000_000, history, transportSessionId = 7, mode = "CAR", realModeChange = true))
        assertFalse(QuestionPolicy.canAsk(uncertain, "bus", 2_000_000, history, transportSessionId = 7, mode = "BUS", realModeChange = true))
        assertFalse(QuestionPolicy.canAsk(result(.8f), "bus", 2_000_000, emptyList()))
        assertFalse(QuestionPolicy.canAsk(result(1f, DetectionSource.USER_CORRECTION), "bus", 2_000_000, emptyList()))
    }
}
