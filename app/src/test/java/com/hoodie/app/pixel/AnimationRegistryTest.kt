package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationRegistry
import com.hoodie.app.pixel.animation.HoodieClips
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationRegistryTest {
    @Test fun modularCatalogPreservesEveryFrameTimingPoseEventAndPolicy() {
        // Captured from the compiled original catalog before extracting families.
        val expected = requireNotNull(javaClass.getResourceAsStream("/animation-clips-v1.sha256"))
            .bufferedReader().useLines { lines -> lines.associate { line ->
                val (id, digest) = line.split('\t')
                AnimationId.valueOf(id) to digest
            } }
        assertTrue(AnimationRegistry.clips.keys.containsAll(expected.keys))
        AnimationId.entries.forEach { id ->
            val clip = AnimationRegistry.clips.getValue(id)
            assertSame(clip, HoodieClips[id])
            assertTrue(clip.frames.isNotEmpty())
            assertTrue(clip.frames.all { it.durationMs > 0 })
            val data = buildString {
                append("${id.name}|${clip.loop}|${clip.interruptPolicy.name}|${clip.directional}\n")
                clip.frames.forEach { frame ->
                    append("${frame.durationMs}\t${frame.pose}\t")
                    append(frame.events.map { it.name }.sorted().joinToString(","))
                    append('\n')
                }
            }
            val actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8)))
            expected[id]?.let { assertEquals("Procedural clip changed: $id", it, actual) }
        }
    }
}
