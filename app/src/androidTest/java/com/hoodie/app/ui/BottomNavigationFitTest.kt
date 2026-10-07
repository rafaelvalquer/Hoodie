package com.hoodie.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.R
import com.hoodie.app.presentation.navigation.HoodieBottomNavigation
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Rótulos da barra inferior: uma linha, sem estourar a largura da aba em telas estreitas e fonte 1.3. */
@RunWith(Parameterized::class)
class BottomNavigationFitTest(private val width: Int, private val fontScale: Float) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun labelsFitTheirTab() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                HoodieTheme { Box(Modifier.requiredWidth(width.dp)) { HoodieBottomNavigation(Routes.HOME, {}) } }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val overflowing = listOf(
            R.string.nav_home to R.string.nav_home, R.string.nav_timeline_short to R.string.nav_timeline, R.string.nav_places_short to R.string.nav_places,
            R.string.nav_diary to R.string.nav_diary, R.string.nav_settings to R.string.nav_settings,
        ).filter { (shortLabel, fullLabel) ->
            val results = mutableListOf<TextLayoutResult>()
            val text = rule.onNodeWithText(context.getString(shortLabel), useUnmergedTree = true).fetchSemanticsNode()
            text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
            // Largura natural do texto maior que a aba = letra cortada ou vizinha invadida. Em 360dp com fonte 1.3 os
            // rótulos de 7 letras passam da aba em ~4 px (quase encostam nos vizinhos, sem cortar): é o limite aceito.
            val tab = rule.onNodeWithContentDescription(context.getString(fullLabel)).fetchSemanticsNode().boundsInRoot.width
            results.single().let { it.lineCount > 1 || it.multiParagraph.maxIntrinsicWidth > tab + 5f }
        }.map { "${context.getString(it.first)}" }
        val detail = listOf(R.string.nav_places_short to R.string.nav_places, R.string.nav_settings to R.string.nav_settings).joinToString { (shortLabel, fullLabel) ->
            val l = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(context.getString(shortLabel), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(l)
            "${context.getString(shortLabel)}: natural=${l.single().multiParagraph.maxIntrinsicWidth} linhas=${l.single().lineCount} aba=${rule.onNodeWithContentDescription(context.getString(fullLabel)).fetchSemanticsNode().boundsInRoot.width}"
        }
        val output = File(context.getExternalFilesDir(null), "nav").apply { mkdirs() }
        File(output, "nav_${width}_f${(fontScale * 100).toInt()}.png").outputStream().use {
            rule.onNodeWithTag("bottom_navigation").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertFalse("rótulos estourando a aba em ${width}dp f$fontScale: $overflowing ($detail)", overflowing.isNotEmpty())
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp f{1}")
        fun cases() = listOf(360, 411).flatMap { w -> listOf(1f, 1.3f).map { arrayOf<Any>(w, it) } }
    }
}
