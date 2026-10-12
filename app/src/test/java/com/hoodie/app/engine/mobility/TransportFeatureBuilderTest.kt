package com.hoodie.app.engine.mobility

import com.hoodie.app.core.mobility.DetectedMovement
import com.hoodie.app.core.mobility.MovementMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportFeatureBuilderTest {
    @Test fun `restoring speed is idempotent and preserves vehicle and stop evidence`() {
        val builder = TransportFeatureBuilder()
        builder.beginSession(101, 0)
        builder.recordMovement(DetectedMovement.IN_VEHICLE, 0)
        builder.recordMovement(DetectedMovement.STILL, 10_000)
        builder.recordMovement(DetectedMovement.WALKING, 40_000)
        val before = builder.build(40_000)
        assertTrue(before.vehicle)
        assertEquals(1, before.stopCount)

        builder.restoreSpeedAggregates(101, 1, 45f, 45f, 0f)
        builder.restoreSpeedAggregates(101, 1, 45f, 45f, 0f)
        val restored = builder.build(40_000)
        assertTrue(restored.vehicle)
        assertEquals(1, restored.stopCount)
        assertEquals(45f, restored.meanSpeedKmh!!, .001f)
        assertEquals(45f, restored.maxSpeedKmh!!, .001f)
        assertEquals(MovementMode.CAR, TransportClassifier.classify(restored, emptyList(), 40_000).mode)
    }

    @Test fun `restoration is monotonic and does not overwrite newer samples`() {
        val builder = TransportFeatureBuilder()
        builder.beginSession(101, 0)
        builder.restoreSpeedAggregates(101, 3, 50f, 80f, .2f)
        builder.speedKmh(80f, 50_000)
        builder.restoreSpeedAggregates(101, 3, 50f, 80f, .2f)
        val result = builder.build(50_000)
        assertEquals(57.5f, result.meanSpeedKmh!!, .001f)
    }

    @Test fun `restore for a different session cannot initialize or leak old evidence`() {
        val builder = TransportFeatureBuilder()
        builder.beginSession(101, 0)
        builder.recordMovement(DetectedMovement.IN_VEHICLE, 0)
        builder.restoreSpeedAggregates(202, 4, 45f, 70f, .1f)
        assertTrue(builder.build(10_000).vehicle)
        builder.beginSession(202, 20_000)
        val fresh = builder.build(20_000)
        assertFalse(fresh.vehicle)
        assertEquals(null, fresh.meanSpeedKmh)
        builder.restoreSpeedAggregates(202, 0, null, null, 0f)
        assertEquals(null, builder.build(20_000).meanSpeedKmh)
    }
}
