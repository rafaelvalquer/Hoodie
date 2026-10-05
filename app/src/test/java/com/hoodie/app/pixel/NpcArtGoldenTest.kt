package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pair goldens guard visual comparison renders while hoodie has its own exact legacy gate. */
class NpcArtGoldenTest {
    @Test fun hoodieVersusEveryNpcReferencePoseMatchesApprovedPixelDigests() {
        val expected = javaClass.getResourceAsStream("/npc-art-v2.sha256")?.bufferedReader()?.useLines { lines ->
            lines.associate { line -> line.split('\t').let { it[0] to it[1] } }
        }.orEmpty()
        val styles = NpcCharacterRegistry.all.associateBy { it.id }
        val actual = linkedMapOf<String, String>()
        NpcArtReviewFixture.characterIds.forEach { id ->
            val style = styles.getValue(id)
            val pair = NpcArtReviewFixture.idleComparison(style)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(ByteBuffer.allocate(pair.pixels.size * Int.SIZE_BYTES).also { bytes -> pair.pixels.forEach(bytes::putInt) }.array())
            val key = "hoodie_vs_$id"
            actual[key] = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
            PreviewExport.save(key, pair, scale = 4)
        }

        val report = java.io.File(PreviewExport.dir, "npc-art-review/npc-art-v2-current.sha256")
        report.parentFile?.mkdirs()
        report.writeText(actual.entries.joinToString("\n", postfix = "\n") { (key, hash) -> "$key\t$hash" })

        if (System.getenv("RECORD_SCENE_GOLDENS") == "true") {
            val output = java.io.File("src/test/resources/npc-art-v2.sha256")
            output.parentFile?.mkdirs()
            output.writeText(report.readText())
        } else {
            assertEquals("candidate SHA report should contain all registered reference poses", 7, actual.size)
            if (expected.isNotEmpty()) {
                assertEquals("Visual hashes changed; review npc-art-v2-current.sha256 and the exported comparison PNGs", 7, expected.size)
                assertEquals("Visual hashes changed; review npc-art-v2-current.sha256 and the exported comparison PNGs", expected, actual)
            } else {
                assertTrue("Golden file not yet approved; review the exported art before recording its hashes", actual.values.all(String::isNotBlank))
            }
        }
    }
}
