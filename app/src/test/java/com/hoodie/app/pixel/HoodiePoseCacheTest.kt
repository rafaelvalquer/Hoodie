package com.hoodie.app.pixel

import com.hoodie.app.pixel.sprite.*
import org.junit.Assert.*
import org.junit.Test

class HoodiePoseCacheTest {
    @Test fun `pose eviction bounds retained sprites and recreates exactly the same pixels`() {
        val base = HoodiePose()
        val first = HoodiePainter.painted(base)
        val pixels = first.image.pixels.copyOf()
        for (eyes in Eyes.entries) for (mouth in Mouth.entries) for (facing in Facing.entries) for (ears in Ears.entries.take(3)) {
            HoodiePainter.painted(base.copy(eyes = eyes, mouth = mouth, facing = facing, ears = ears))
            assertTrue(HoodiePainter.cachedPoseCount() <= HoodiePainter.CACHE_LIMIT)
        }
        val recreated = HoodiePainter.painted(base)
        assertNotSame("least recently used pose must be evicted", first, recreated)
        assertArrayEquals(pixels, recreated.image.pixels)
        assertEquals(first.anchors, recreated.anchors)
        assertSame(recreated, HoodiePainter.painted(base))
    }
}
