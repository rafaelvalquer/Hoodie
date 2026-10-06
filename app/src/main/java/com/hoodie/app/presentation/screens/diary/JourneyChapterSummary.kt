package com.hoodie.app.presentation.screens.diary

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.ChapterPlan
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.pixel.diary.overworld.BiomeState
import com.hoodie.app.pixel.diary.overworld.OverworldBiomeCatalog
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.theme.HoodieColors

/** Mini ícones 32×32 das construções (pintados uma vez por bioma). */
object BiomeIcons {
    private val cache = HashMap<BiomeType, ImageBitmap>()
    fun of(b: BiomeType): ImageBitmap = cache.getOrPut(b) {
        val buf = PixelBuffer(32, 32).also { OverworldBiomeCatalog.paint(it, b, 0, 0, BiomeState.VISITED, false, 0L) }
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).also { it.setPixels(buf.pixels, 0, 32, 0, 0, 32, 32) }.asImageBitmap()
    }
}

/**
 * Capítulo fechado: nome, paradas, tempo e a fileira de mini ícones. É um botão
 * ("Tarde, 5 paradas, 4 h 10, toque para abrir"); vazio não abre.
 */
@Composable
fun JourneyChapterSummary(plan: JourneyPlan, chapter: ChapterPlan, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(chapter.chapter.labelRes())
    val stops = pluralStringResource(R.plurals.journey_chapter_stops, chapter.visitCount, chapter.visitCount)
    val time = formatDuration(chapter.totalStayMs)
    val ghostName = chapter.ghostOfStopId?.let { plan.visitOf(it)?.label }
    val subtitle = when {
        chapter.isEmpty -> stringResource(R.string.journey_chapter_empty)
        chapter.onlyGhost && ghostName != null -> stringResource(R.string.journey_chapter_whole, name, ghostName)
        else -> "$stops · $time"
    }
    val description = if (chapter.isEmpty) stringResource(R.string.journey_chapter_empty_description, name)
    else stringResource(R.string.journey_chapter_description, name, if (chapter.onlyGhost) subtitle else stops, time)
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .alpha(if (chapter.isEmpty) 0.6f else 1f)
            .border(2.dp, HoodieColors.Outline)
            .background(HoodieColors.Panel)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                if (chapter.isEmpty) disabled()
            }
            .then(if (chapter.isEmpty) Modifier else Modifier.clickable(onClick = onOpen))
            .testTag("journey_chapter_${chapter.chapter.name.lowercase()}")
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, color = HoodieColors.Hood)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 2.dp))
        }
        val icons = (chapter.ghostOfStopId?.let { plan.visitOf(it)?.biome }?.let(::listOf).orEmpty() + chapter.biomes).take(MAX_ICONS)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            icons.forEach { Image(BiomeIcons.of(it), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.size(18.dp)) }
            val more = chapter.biomes.size + (if (chapter.ghostOfStopId != null) 1 else 0) - icons.size
            if (more > 0) Text("+$more", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
}

private const val MAX_ICONS = 6
