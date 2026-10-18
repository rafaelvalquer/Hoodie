package com.hoodie.app.engine.backup

/** Stable wire contract. A record is one authenticated UTF-8 JSON object, never a SQLite file. */
object BackupFormat {
    const val VERSION = 1
    const val MIME = "application/vnd.hoodie.backup"
    const val EXTENSION = ".hoodiebackup"
    const val MAX_RECORD_BYTES = 256 * 1024
    const val MAX_FILE_BYTES = 512L * 1024 * 1024
    const val MAX_RECORDS = 1_000_000L
    const val MIN_ITERATIONS = 600_000
    const val MAX_ITERATIONS = 1_200_000
    val magic: ByteArray get() = "HOODIEB1".toByteArray(Charsets.US_ASCII)
    fun recordLimit(dataset: String): Long = when (dataset) {
        "routines" -> 1
        "places", "memories" -> 10_000
        "learned_routine_slots" -> 1_000
        "day_exceptions" -> 50_000
        else -> MAX_RECORDS - 2
    }
}
