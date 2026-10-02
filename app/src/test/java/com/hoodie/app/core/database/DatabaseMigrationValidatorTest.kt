package com.hoodie.app.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseMigrationValidatorTest {

    private val source = DatabaseFingerprint(
        userVersion = 4,
        tables = setOf("context_events", "timeline_events", "places", "hoodie_activities", "memories"),
        rowCounts = mapOf("context_events" to 5421L, "timeline_events" to 8339L, "places" to 12L, "hoodie_activities" to 916L, "memories" to 42L),
    )
    private val copy = source.copy(cipherIntegrityOk = true)
    private fun reasons(c: DatabaseFingerprint, s: DatabaseFingerprint = source) =
        (DefaultDatabaseMigrationValidator.validate(s, c) as? MigrationValidationResult.Invalid)?.reasons.orEmpty()

    @Test
    fun `copia identica e valida`() {
        assertEquals(MigrationValidationResult.Valid, DefaultDatabaseMigrationValidator.validate(source, copy))
        // cipher_integrity_check indisponível não reprova.
        assertEquals(MigrationValidationResult.Valid, DefaultDatabaseMigrationValidator.validate(source, copy.copy(cipherIntegrityOk = null)))
    }

    @Test
    fun `linhas diferentes reprovam e dizem a tabela`() {
        val r = reasons(copy.copy(rowCounts = source.rowCounts + ("timeline_events" to 8338L)))
        assertEquals(listOf("linhas em timeline_events: 8338 ≠ 8339"), r)
    }

    @Test
    fun `tabela faltando, versao, quick_check e cipher_integrity_check reprovam`() {
        assertTrue(reasons(copy.copy(tables = copy.tables - "memories", rowCounts = copy.rowCounts - "memories")).single().startsWith("tabelas faltando"))
        assertTrue(reasons(copy.copy(userVersion = 3)).single().startsWith("user_version"))
        assertEquals(listOf("quick_check da cópia falhou"), reasons(copy.copy(quickCheckOk = false)))
        assertEquals(listOf("cipher_integrity_check da cópia falhou"), reasons(copy.copy(cipherIntegrityOk = false)))
        assertEquals(listOf("quick_check da origem falhou"), reasons(copy, source.copy(quickCheckOk = false)))
    }

    @Test
    fun `varios problemas sao todos listados`() {
        assertEquals(3, reasons(copy.copy(userVersion = 1, quickCheckOk = false, cipherIntegrityOk = false)).size)
    }
}
