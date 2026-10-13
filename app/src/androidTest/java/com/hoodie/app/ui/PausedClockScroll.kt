package com.hoodie.app.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule

/** Scrolls real controls while allowing a manually controlled inspector to draw. */
internal fun ComposeTestRule.scrollWithPausedClock(
    node: SemanticsNodeInteraction,
): SemanticsNodeInteraction {
    var lastGeometry = ""
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
        val inset = 16f * scroll.layoutInfo.density.density
        val viewportTop = viewport.top + inset
        val viewportBottom = viewport.bottom - inset
        val top = target.positionInRoot.y
        val bottom = top + target.size.height
        val center = (top + bottom) / 2f
        lastGeometry = "target=$top..$bottom viewport=$viewport centre=$center"
        // The touch centre needs the system-bar margin; requiring that margin
        // around the whole last button can be impossible at maximum scroll.
        if (top >= viewport.top && bottom <= viewport.bottom && center in viewportTop..viewportBottom) {
            node.assertIsDisplayed()
            return node
        }
        val delta = when {
            top < viewport.top -> top - viewport.top
            bottom > viewport.bottom -> if (target.size.height > viewport.height) top - viewport.top else bottom - viewport.bottom
            center < viewportTop -> center - viewportTop
            center > viewportBottom -> center - viewportBottom
            else -> 0f
        }
        if (kotlin.math.abs(delta) < 0.5f) {
            node.assertIsDisplayed()
            return node
        }
        runOnUiThread { scroll.config[SemanticsActions.ScrollBy].action?.invoke(0f, delta) }
        mainClock.advanceTimeBy(400L)
    }
    throw AssertionError("O controle não ficou visível após 12 avanços de rolagem: $lastGeometry")
}
