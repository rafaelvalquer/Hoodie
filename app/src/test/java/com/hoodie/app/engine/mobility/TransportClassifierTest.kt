package com.hoodie.app.engine.mobility

import com.hoodie.app.core.database.TransportPatternEntity
import com.hoodie.app.core.mobility.*
import com.hoodie.app.domain.detection.*
import com.hoodie.app.engine.detection.ConfidenceEngine
import org.junit.Assert.*
import org.junit.Test

class TransportClassifierTest {
    private fun features(speed: Float, max: Float, stops: Int, regular: Boolean = false) =
        TransportFeatures(20 * 60_000, true, speed, max, .2f, stops, stops * 45_000L, 45_000, regular)
    @Test fun deterministicFeaturesSeparateCarBusTrainAndMetroWithoutCoordinates() {
        assertEquals(MovementMode.CAR, TransportClassifier.classify(features(50f, 80f, 1), emptyList(), 0).mode)
        assertEquals(MovementMode.BUS, TransportClassifier.classify(features(25f, 50f, 6), emptyList(), 0).mode)
        assertEquals(MovementMode.TRAIN, TransportClassifier.classify(features(75f, 100f, 5, true), emptyList(), 0).mode)
        val metro = TransportClassifier.classify(features(40f, 65f, 5, true), emptyList(), 0)
        assertEquals(MovementMode.METRO, metro.mode)
        assertEquals(DetectionDecision.ASK_USER, ConfidenceEngine.decide(metro.detection))
        assertEquals(1f, metro.probabilities.values.sumOf { it.value.toDouble() }.toFloat(), .0001f)
    }
    @Test fun strongLearnedPatternAndExplicitConfirmationBeatAmbiguousSpeeds() {
        val pattern = TransportPatternEntity(1, 2, "WEEKDAY", 15, "BUS", 8, 0, .9f, 0)
        val learned = TransportClassifier.classify(features(40f, 60f, 1), listOf(pattern), 0)
        assertEquals(MovementMode.BUS, learned.mode)
        assertEquals(DetectionDecision.AUTO_ACCEPT, ConfidenceEngine.decide(learned.detection))
        val confirmed = TransportClassifier.classify(features(40f, 60f, 1), listOf(pattern), 0, confirmed = MovementMode.CAR)
        assertEquals(MovementMode.CAR, confirmed.mode)
        assertEquals(1f, confirmed.confidence.value, 0f)
    }
    @Test fun switchNeedsFifteenPointsForFortyFiveSecondsAndCannotOverrideCorrection() {
        fun candidate(mode: MovementMode, score: Float) = TransportClassification(mode, ConfidenceScore(score), emptyMap(), emptyMap())
        val current = ConfidenceScore(.72f)
        assertEquals(MovementMode.BUS, TransportDecisionPolicy.resolve(MovementMode.BUS, current, candidate(MovementMode.CAR, .76f), null, 0).mode)
        val first = TransportDecisionPolicy.resolve(MovementMode.BUS, current, candidate(MovementMode.CAR, .90f), null, 0)
        assertEquals(MovementMode.BUS, first.mode)
        val waiting = TransportDecisionPolicy.resolve(MovementMode.BUS, current, candidate(MovementMode.CAR, .90f), first.pending, 44_999)
        assertEquals(MovementMode.BUS, waiting.mode)
        assertEquals(MovementMode.CAR, TransportDecisionPolicy.resolve(MovementMode.BUS, current, candidate(MovementMode.CAR, .90f), waiting.pending, 45_000).mode)
        assertEquals(MovementMode.BUS, TransportDecisionPolicy.resolve(MovementMode.BUS, current, candidate(MovementMode.CAR, .99f), null, 60_000, corrected = true).mode)
    }
    @Test fun walkingVehicleWalkingRemainsMultimodalAndAggregatesRejectBadSpeeds() {
        assertTrue(MobilityStateMachine.isNewSegment(MovementMode.WALKING, MovementMode.BUS))
        assertTrue(MobilityStateMachine.isNewSegment(MovementMode.BUS, MovementMode.WALKING))
        val builder = TransportFeatureBuilder()
        builder.begin(1, 0)
        builder.movement(DetectedMovement.IN_VEHICLE, 0)
        builder.speed(10f, 0)
        builder.speed(Float.NaN, 1)
        repeat(3) { index -> builder.movement(DetectedMovement.STILL, index * 120_000L + 20_000); builder.movement(DetectedMovement.IN_VEHICLE, index * 120_000L + 60_000) }
        val result = builder.build(400_000)
        assertEquals(36f, result.meanSpeedKmh!!, .001f)
        assertEquals(3, result.stopCount)
        assertTrue(result.regularStops)
    }
}
