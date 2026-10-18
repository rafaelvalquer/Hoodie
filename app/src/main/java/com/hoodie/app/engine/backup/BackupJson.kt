package com.hoodie.app.engine.backup

import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Bound parser recursion before handing untrusted data to the platform JSON parser. */
object BackupJson {
    fun parse(bytes: ByteArray): JSONObject {
        requireBackup(bytes.size <= BackupFormat.MAX_RECORD_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEach { c ->
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{', '[' -> { depth++; requireBackup(depth <= 12) }
                '}', ']' -> { depth--; requireBackup(depth >= 0) }
            }
        }
        requireBackup(depth == 0 && !quoted)
        val tokener = JSONTokener(text)
        val value = tokener.nextValue()
        requireBackup(value is JSONObject && tokener.nextClean() == '\u0000')
        return value as JSONObject
    }
}
