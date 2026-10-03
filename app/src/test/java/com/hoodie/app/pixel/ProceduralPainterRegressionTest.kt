package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class ProceduralPainterRegressionTest {
    @Test fun modularPainterPreservesAllClipPixelsAnchorsAndSemanticPartsInThreeViews() {
        val expected = requireNotNull(javaClass.getResourceAsStream("/procedural-painter-v1.sha256"))
            .bufferedReader().useLines { lines -> lines.associate { line ->
                val (key, digest) = line.split('\t')
                key to digest
            } }
        var checked = 0
        AnimationId.entries.forEach { id ->
            id.frames.forEachIndexed { index, frame ->
                Facing.entries.forEach { facing ->
                    val (sprite, parts) = HoodiePainter.paintWithParts(frame.pose.copy(facing = facing))
                    val digest = MessageDigest.getInstance("SHA-256")
                    digest.update(sprite.anchors.toString().toByteArray(Charsets.UTF_8))
                    val pixels = sprite.image.pixels
                    val bytes = ByteBuffer.allocate((pixels.size + parts.size) * Int.SIZE_BYTES)
                    pixels.forEach(bytes::putInt)
                    parts.forEach(bytes::putInt)
                    digest.update(bytes.array())
                    val key = "${id.name}:$index:${facing.name}"
                    // Existing approved hashes lock the original 100 clips. New
                    // transport clips are covered by transport rendering tests.
                    expected[key]?.let { hash ->
                        assertEquals("Procedural rendering changed: $key", hash, HexFormat.of().formatHex(digest.digest()))
                        checked++
                    }
                }
            }
        }
        assertEquals(1335, checked)
        assertEquals(expected.size, checked)
    }
}
