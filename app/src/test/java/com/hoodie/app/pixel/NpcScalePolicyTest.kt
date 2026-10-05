package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.AmbientScale
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcScalePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class NpcScalePolicyTest {
    @Test fun raccoonBackgroundFarIsClampedToItsLegibilityMinimum() {
        assertEquals(1.00f, NpcScalePolicy.scale(NpcCharacterRegistry.RACCOON_COMMUTER, NpcDepth.BACKGROUND_FAR), 0f)
    }

    @Test fun rabbitBackgroundUsesItsSpeciesMinimum() {
        assertEquals(0.95f, NpcScalePolicy.scale(NpcCharacterRegistry.RABBIT_ANALYST, NpcDepth.BACKGROUND), 0f)
    }

    @Test fun bulldogInTheScenePlaneKeepsFullScale() {
        assertEquals(1.00f, NpcScalePolicy.scale(NpcCharacterRegistry.BULLDOG_EXEC, NpcDepth.SCENE), 0f)
    }

    @Test fun mouseAnatomyIsNotReducedByAmbientDepth() {
        assertEquals(1.00f, NpcScalePolicy.scale(NpcCharacterRegistry.MOUSE_COMMUTER, NpcDepth.BACKGROUND), 0f)
    }

    @Test fun productionScaleSetIsQuantized() {
        assertEquals(listOf(0.90f, 0.95f, 1.00f), AmbientScale.allowed)
    }
}
