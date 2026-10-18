package com.hoodie.app.engine.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal fun encrypted(vararg records: String): ByteArray = ByteArrayOutputStream().also { out ->
    BackupCrypto.Writer(out, "hoodie recovery phrase".toCharArray()).use { writer -> records.forEach { writer.record(it.toByteArray()) }; writer.finish() }
}.toByteArray()
internal fun decoded(bytes: ByteArray, password: String = "hoodie recovery phrase"): List<String> = mutableListOf<String>().also { rows ->
    BackupCrypto.read(ByteArrayInputStream(bytes), password.toCharArray()) { rows.add(String(it)) }
}
internal fun rejects(block: () -> Unit): BackupException {
    try { block() } catch (e: BackupException) { return e }
    throw AssertionError("Expected backup rejection")
}

class BackupCryptoTest {
    @Test fun roundTripAcrossBlocks() { assertEquals(listOf("one", "two", "three"), decoded(encrypted("one", "two", "three"))) }
    @Test fun wrongPasswordRejected() { assertEquals(BackupError.PASSWORD, rejects { decoded(encrypted("one"), "wrong password") }.error) }
    @Test fun saltsAreRandomAndNoncesUseDistinctAuthenticatedCounters() {
        val first = encrypted("one", "two")
        assertFalse(first.copyOfRange(16, 40).contentEquals(encrypted("one", "two").copyOfRange(16, 40)))
        assertEquals(0, ByteBuffer.wrap(first).getInt(40))
        assertEquals(1, ByteBuffer.wrap(first).getInt(67))
    }
    @Test fun truncationAtHeaderRecordAndFinalMarkerRejected() {
        val file = encrypted("one", "two")
        listOf(0, 8, 39, 45, 67, file.size - 1, file.size - 24).forEach { length -> rejects { decoded(file.copyOf(length)) } }
    }
    @Test fun corruptTagHeaderAndSequenceRejected() {
        val file = encrypted("one", "two")
        listOf(16, 39, 55, 66, 67, file.lastIndex).forEach { offset ->
            rejects { decoded(file.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }) }
        }
    }
    @Test fun blocksCannotBeRepeatedRemovedOrReordered() {
        val file = encrypted("one", "two")
        val header = file.copyOfRange(0, 40)
        val first = file.copyOfRange(40, 67)
        val second = file.copyOfRange(67, 94)
        val end = file.copyOfRange(94, file.size)
        listOf(header + first + first + end, header + second + first + end, header + first + end).forEach { rejects { decoded(it) } }
    }
    @Test fun dataAfterAuthenticatedFinalMarkerRejected() { rejects { decoded(encrypted("one") + byteArrayOf(1)) } }
    @Test fun cannotCloseWithoutAuthenticatedEndMarker() {
        val out = ByteArrayOutputStream()
        BackupCrypto.Writer(out, "hoodie recovery phrase".toCharArray()).use { it.record("one".toByteArray()) }
        rejects { decoded(out.toByteArray()) }
    }
    @Test fun publicHeaderContainsNoPersonalInformation() {
        val file = encrypted("private home coordinates -23.5 -46.6")
        assertFalse(String(file.copyOfRange(0, 40)).contains("private"))
        assertFalse(String(file).contains("coordinates"))
    }
}

class BackupKeyDerivationTest {
    @Test fun matchesIndependentPythonHashlibVector() {
        val key = BackupKeyDerivation.derive("hoodie recovery phrase".toCharArray(), ByteArray(16) { it.toByte() }, 600000)
        assertEquals("2e224320b4aa97ffb2d3f52470a6cb8cd470ccc1da79a45f40cc836b169297cc", key.encoded.joinToString("") { "%02x".format(it) })
    }
    @Test fun kdfLimitsAreValidatedBeforeExpensiveWork() {
        listOf(-1, 0, 599999, 1200001, Int.MAX_VALUE).forEach { iterations -> rejects { BackupKeyDerivation.derive(charArrayOf('a'), ByteArray(16), iterations) } }
    }
    @Test fun passwordPolicyAcceptsPhrasesAndRejectsShortRepeatedPasswords() {
        assertTrue(BackupKeyDerivation.isStrong("meu Hoodie guarda memorias".toCharArray()))
        assertFalse(BackupKeyDerivation.isStrong("aaaaaaaaaaaaaaaaa".toCharArray()))
        assertFalse(BackupKeyDerivation.isStrong("short123".toCharArray()))
    }
}

class BackupFormatCompatibilityTest {
    @Test fun firstV1GoldenBackupRemainsReadable() {
        val fixture = javaClass.classLoader!!.getResourceAsStream("backup-v1-minimal.hoodiebackup")!!.use { it.readBytes() }
        val rows = decoded(fixture).map { BackupJson.parse(it.toByteArray()) }
        val manifest = BackupManifest.parse(rows.first())
        assertEquals(1L, manifest.counts["places"])
        assertEquals("Fixture", rows[1].getJSONObject("data").getString("cat_name"))
        BackupSchemaAdapter.validateRecord(BackupSchemaV1.datasets.first { it.name == "places" }, rows[2].getJSONObject("data"))
        assertEquals(-23.0, rows[2].getJSONObject("data").getDouble("latitude"), 0.0)
    }
    @Test fun newerFormatsRejected() {
        val bytes = encrypted("one")
        ByteBuffer.wrap(bytes).putInt(8, 2)
        assertEquals(BackupError.INCOMPATIBLE, rejects { decoded(bytes) }.error)
    }
    @Test fun oversizedAndNegativeBlocksRejectedWithoutAllocation() {
        listOf(-1, BackupFormat.MAX_RECORD_BYTES + 1, Int.MAX_VALUE).forEach { size ->
            val bytes = encrypted("one"); ByteBuffer.wrap(bytes).putInt(44, size)
            rejects { decoded(bytes) }
        }
    }
}
