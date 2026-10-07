package com.hoodie.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Scrolls real controls while allowing a manually controlled inspector to draw. */
internal fun ComposeContentTestRule.scrollWithPausedClock(
    node: SemanticsNodeInteraction,
): SemanticsNodeInteraction {
    repeat(12) {
        val target = node.fetchSemanticsNode()
        val scroll = generateSequence(target.parent) { it.parent }
            .firstOrNull { it.config.contains(SemanticsActions.ScrollBy) }
            ?: throw AssertionError("O controle não tem ancestral com rolagem")
        val coordinates = scroll.layoutInfo.coordinates
        val viewport = coordinates.boundsInParent().translate(
            coordinates.parentLayoutCoordinates?.positionInRoot() ?: Offset.Zero,
        ).intersect(generateSequence(target) { it.parent }.last().boundsInRoot)
        if (viewport.isEmpty) {
            mainClock.advanceTimeBy(400L)
            return@repeat
        }
        // Keep touch centres away from edge-to-edge status/navigation bars.
        val inset = 32f * scroll.layoutInfo.density.density
        val viewportTop = viewport.top + inset
        val viewportBottom = viewport.bottom - inset
        val top = target.positionInRoot.y
        val bottom = top + target.size.height
        val delta = when {
            top < viewportTop -> top - viewportTop
            bottom > viewportBottom -> if (target.size.height > viewportBottom - viewportTop) top - viewportTop else bottom - viewportBottom
            else -> 0f
        }
        if (kotlin.math.abs(delta) < 0.5f) {
            node.assertIsDisplayed()
            return node
        }
        runOnUiThread { scroll.config[SemanticsActions.ScrollBy].action?.invoke(0f, delta) }
        mainClock.advanceTimeBy(400L)
    }
    throw AssertionError("O controle não ficou visível após 12 avanços de rolagem")
}
