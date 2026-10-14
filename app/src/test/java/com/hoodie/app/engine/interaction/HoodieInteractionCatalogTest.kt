package com.hoodie.app.engine.interaction

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationRegistry
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.*
import org.junit.Test

class HoodieInteractionCatalogTest {
    @Test fun `complete catalog has every variant and only registered harmless animations`() {
        val rules = interactionRules()
        assertEquals(110, rules.size)
        assertEquals(110, rules.map { it.id }.distinct().size)
        val interactiveScenes = setOf(SceneId.HOME, SceneId.OFFICE, SceneId.RESTAURANT, SceneId.GYM, SceneId.LEISURE, SceneId.SHOPPING, SceneId.STORE, SceneId.SCHOOL, SceneId.FAMILY)
        interactiveScenes.forEach { scene -> InteractionGesture.entries.forEach { g -> assertTrue(rules.any { it.scene == scene && it.gesture == g }) } }
        rules.forEach { rule -> rule.animations.forEach { assertTrue(it in AnimationRegistry.clips) } }
        listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE).forEach { animation ->
            assertFalse(animation.loop)
            assertTrue(animation.frames.all { it.events.isEmpty() && it.pose.legs == com.hoodie.app.pixel.sprite.Legs.INHERIT })
        }
    }
    @Test fun `invalid animation id and side effects cannot enter catalog`() {
        val template = """[{"id":"x","scene":null,"gesture":"TAP","activity":null,"condition":"ANY","text":"Oi!","animations":["%s"],"priority":1,"weight":1}]"""
        assertTrue(runCatching { HoodieInteractionCatalog.parse(template.format("NO_SUCH_ANIMATION")) }.isFailure)
        assertTrue(runCatching { HoodieInteractionCatalog.parse(template.format("SHOP_PAY")) }.isFailure)
    }

    @Test fun `catalog contains every approved phrase verbatim`() {
        val expected = java.io.File("src/test/resources/hoodie-interaction-required-phrases.txt").readLines().filter { it.isNotBlank() }.toSet()
        assertEquals(expected, interactionRules().map { it.text }.toSet())
    }

    @Test fun `petting sequences retain their final animation from the approved table`() {
        val rules = interactionRules()
        val expected = mapOf(
            "office_break_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE),
            "restaurant_resting_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.PURR),
            "shopping_free_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.SMILE, AnimationId.NUZZLE),
            "store_free_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.SMILE),
            "school_resting_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.PURR),
            "family_resting_long_press" to listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE),
        )
        expected.forEach { (prefix, animations) -> rules.filter { it.id.startsWith(prefix) }.forEach { assertEquals(it.id, animations, it.animations) } }
    }
}
