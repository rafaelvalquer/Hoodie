package com.hoodie.app.backup

import android.os.SystemClock
import com.hoodie.app.engine.backup.BackupKeyDerivation
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupKeyDerivationDeviceTest {
    @Test fun androidMatchesIndependentVectorAndReportsMinimumCostBenchmark() {
        val durations = (1..3).map {
            val started = SystemClock.elapsedRealtime()
            val secret = "hoodie recovery phrase".toCharArray()
            val key = try { BackupKeyDerivation.derive(secret, ByteArray(16) { it.toByte() }, 600000) } finally { secret.fill('\u0000') }
            val elapsed = SystemClock.elapsedRealtime() - started
            val bytes = key.encoded
            try { assertEquals("2e224320b4aa97ffb2d3f52470a6cb8cd470ccc1da79a45f40cc836b169297cc", bytes.joinToString("") { "%02x".format(it) }) }
            finally { bytes.fill(0) }
            elapsed
        }
        println("BACKUP_KDF algorithm=PBKDF2-HMAC-SHA256 iterations=600000 median_ms=${durations.sorted()[1]} api=${android.os.Build.VERSION.SDK_INT}")
    }
}
