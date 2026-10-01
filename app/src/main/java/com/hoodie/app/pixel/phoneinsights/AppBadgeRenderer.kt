package com.hoodie.app.pixel.phoneinsights

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ícones reais dos apps (PackageManager), em cache. O Hoodie não redesenha
 * marcas: só emoldura o ícone instalado no estilo do jogo.
 */
object AppIconCache {
    private const val ICON_PX = 96
    private val cache = LruCache<String, ImageBitmap>(64)
    private val missing = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        if (packageName in missing) return null
        return withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }
                .getOrNull()
                ?.also { cache.put(packageName, it) }
                .also { if (it == null) missing += packageName }
        }
    }
}

/**
 * Badge de app: moldura pixel art (contorno + sombra dura), fundo com a cor da
 * categoria e um "led" da categoria no canto. Sem ícone → ícone genérico.
 */
@Composable
fun AppBadge(
    iconSource: AppIconSource,
    category: HoodieAppCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showCategoryDot: Boolean = true,
) {
    val context = LocalContext.current
    val pkg = (iconSource as? AppIconSource.Installed)?.packageName
    val icon by produceState(initialValue = pkg?.let(AppIconCache::peek), pkg) {
        value = pkg?.let { AppIconCache.load(context, it) }
    }
    val catColor = RetroUiTheme.category(category)
    Box(
        modifier
            .size(size)
            .drawBehind {
                val s = 3.dp.toPx()
                drawRect(HoodieColors.Outline, Offset(s, s), this.size)
            }
            .background(RetroUiTheme.shade(catColor).copy(alpha = 1f))
            .border(2.dp, HoodieColors.Outline),
        contentAlignment = Alignment.Center,
    ) {
        val current = icon
        if (current != null) {
            Image(current, contentDescription = null, modifier = Modifier.fillMaxSize().padding(size / 9))
        } else {
            GenericAppIcon(Modifier.fillMaxSize().padding(size / 6))
        }
        if (showCategoryDot) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value / 4).coerceAtLeast(8f).dp)
                    .background(HoodieColors.Outline)
                    .padding(1.5.dp)
                    .background(catColor),
            )
        }
    }
}

/** Celularzinho 10×14 desenhado em pixels: usado quando o app foi desinstalado ou não tem ícone. */
@Composable
fun GenericAppIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val grid = arrayOf(
            "..########..",
            ".#OOOOOOOO#.",
            ".#OSSSSSSO#.",
            ".#OSLLLLSO#.",
            ".#OSLSSLSO#.",
            ".#OSSSLLSO#.",
            ".#OSSSLSSO#.",
            ".#OSSSSSSO#.",
            ".#OSSSLSSO#.",
            ".#OSSSSSSO#.",
            ".#OOOOOOOO#.",
            ".#OOO##OOO#.",
            "..########..",
        )
        val cols = grid[0].length
        val px = minOf(size.width / cols, size.height / grid.size)
        val ox = (size.width - px * cols) / 2
        val oy = (size.height - px * grid.size) / 2
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val c = when (ch) {
                    '#' -> HoodieColors.Outline
                    'O' -> HoodieColors.Hood
                    'S' -> Color(0xFF36465A)
                    'L' -> HoodieColors.Gold
                    else -> null
                } ?: return@forEachIndexed
                drawRect(c, Offset(ox + x * px, oy + y * px), Size(px + 0.5f, px + 0.5f))
            }
        }
    }
}
