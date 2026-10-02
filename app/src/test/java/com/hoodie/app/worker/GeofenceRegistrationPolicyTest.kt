package com.hoodie.app.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceRegistrationPolicyTest {
    @Test fun successfulDailyRegistrationIsNotRepeatedAtFourOrAfterRestart() {
        assertTrue(GeofenceRegistrationPolicy.shouldRegister(4, 100, 99, true))
        assertFalse(GeofenceRegistrationPolicy.shouldRegister(4, 100, 100, true))
        assertFalse(GeofenceRegistrationPolicy.shouldRegister(4, 100, 100, null))
        assertFalse(GeofenceRegistrationPolicy.shouldRegister(12, 100, 100, null))
    }

    @Test fun failedRegistrationCanRecoverAndFirstRunRegisters() {
        assertTrue(GeofenceRegistrationPolicy.shouldRegister(12, 100, 100, false))
        assertTrue(GeofenceRegistrationPolicy.shouldRegister(12, 100, -1, null))
        assertFalse(GeofenceRegistrationPolicy.shouldRegister(12, 100, 99, true))
    }
}
