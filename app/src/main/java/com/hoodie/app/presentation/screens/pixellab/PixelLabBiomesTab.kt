package com.hoodie.app.presentation.screens.pixellab

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.journey.JourneyPalette
import com.hoodie.app.pixel.diary.overworld.BiomeState
import com.hoodie.app.pixel.diary.overworld.OverworldBiomeCatalog
import com.hoodie.app.pixel.diary.overworld.OverworldPalette
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Pixel Lab · Biomas (Jornada 3.0): cada construção nos estados futura, visitada,
 * atual e fantasma, de dia e de noite, com a área de toque desenhada.
 */
@Composable
internal fun BiomesLab() {
    var night by remember { mutableStateOf(false) }
    var showHit by remember { mutableStateOf(true) }
    ChipRow(listOf("Dia", "Noite"), if (night) 1 else 0, { night = it == 1 })
    ChipRow(listOf(if (showHit) "☑ Área de toque" else "☐ Área de toque"), null, { showHit = !showHit })
    val time = rememberLabClock()
    Column(Modifier.fillMaxWidth().testTag("pixel-lab-biomes"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BiomeType.entries.forEach { biome ->
            SectionLabel(biome.name)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BiomeState.entries.forEach { state ->
                    Column {
                        Image(
                            render(biome, state, night, showHit, time), contentDescription = "${biome.name} ${state.name}",
                            filterQuality = FilterQuality.None, modifier = Modifier.size(72.dp),
                        )
                        Text(state.name.lowercase(), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
                    }
                }
            }
        }
    }
}

/** 32×32 com 4 px de margem; à noite aplica a mesma luz do overworld. */
private fun render(biome: BiomeType, state: BiomeState, night: Boolean, showHit: Boolean, timeMs: Long): androidx.compose.ui.graphics.ImageBitmap {
    val buf = PixelBuffer(40, 40)
    buf.fill(OverworldPalette.GRASS)
    OverworldBiomeCatalog.paint(buf, biome, 4, 4, state, night, timeMs)
    if (night) JourneyLightingRenderer.applyTint(buf, JourneyLightingRenderer.at(22 * 60), fromY = 0)
    if (showHit) {
        val c = JourneyPalette.SELECTED
        buf.hline(2, 37, 2, c); buf.hline(2, 37, 37, c); buf.vline(2, 2, 37, c); buf.vline(37, 2, 37, c)
    }
    return Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).also { it.setPixels(buf.pixels, 0, 40, 0, 0, 40, 40) }.asImageBitmap()
}

@Composable
private fun rememberLabClock(): Long = com.hoodie.app.presentation.screens.diary.rememberDiaryMapClock(activeReplay = false)
