package com.hoodie.app.pixel.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.DiaryMapIcons

/**
 * Sprite de ícone da interface: grade de texto onde `#` é a tinta, `o` o preenchimento e `.` vazio.
 * Substitui o emoji do sistema (que muda de um Android para outro) por pixel art do próprio Hoodie.
 */
class PixelSprite(val rows: List<String>) {
    val width: Int = rows.first().length
    val height: Int = rows.size
}

object PixelIcons {
    val CAT = PixelSprite(listOf(
        "##......##",
        "#o#....#o#",
        "#oo####oo#",
        "#oooooooo#",
        "#o#oooo#o#",
        "#oooooooo#",
        "#ooo##ooo#",
        ".#oooooo#.",
        "..######..",
        "..........",
    ))
    val CALENDAR = PixelSprite(listOf(
        ".#......#.",
        "##########",
        "#oooooooo#",
        "##########",
        "#.#.#.#..#",
        "#........#",
        "#.#.#.#..#",
        "#........#",
        "#.#.#....#",
        "##########",
    ))
    val PIN = PixelSprite(listOf(
        "...####...",
        "..#oooo#..",
        ".#oo..oo#.",
        ".#oo..oo#.",
        ".#oooooo#.",
        "..#oooo#..",
        "..#oooo#..",
        "...#oo#...",
        "....##....",
        "..........",
    ))
    val MAP = PixelSprite(listOf(
        "..........",
        "##########",
        "#oo#oo#oo#",
        "#oo#oo#oo#",
        "#oo#o.#oo#",
        "#oo#oo#oo#",
        "#oo#oo#oo#",
        "#oo#oo#oo#",
        "##########",
        "..........",
    ))
    val GEAR = PixelSprite(listOf(
        "....##....",
        ".#.####.#.",
        "..#oooo#..",
        ".#oo..oo#.",
        "####..####",
        "####..####",
        ".#oo..oo#.",
        "..#oooo#..",
        ".#.####.#.",
        "....##....",
    ))
    val SPARKLE = PixelSprite(listOf(
        "....##....",
        "...#oo#...",
        "...#oo#...",
        "###oooo###",
        "#oooooooo#",
        "#oooooooo#",
        "###oooo###",
        "...#oo#...",
        "...#oo#...",
        "....##....",
    ))
    val WALK = PixelSprite(listOf(
        "...##.....",
        "..#oo#....",
        "..#oo#....",
        "...##.....",
        "..####....",
        ".#.##.#...",
        "...##.....",
        "..#..#....",
        ".#....#...",
        ".#....#...",
    ))
    val SUN = PixelSprite(listOf(
        "....##....",
        ".#..##..#.",
        "..######..",
        "..#oooo#..",
        "###oooo###",
        "###oooo###",
        "..#oooo#..",
        "..######..",
        ".#..##..#.",
        "....##....",
    ))
    val SUNRISE = PixelSprite(listOf(
        "....##....",
        ".#..##..#.",
        "...####...",
        "..#oooo#..",
        ".#oooooo#.",
        "##########",
        "..........",
        ".########.",
        "..........",
        "..######..",
    ))
    val MOON = PixelSprite(listOf(
        "...####...",
        "..#oo#....",
        ".#oo#.....",
        ".#oo#.....",
        ".#oo#.....",
        ".#oo#.....",
        "..#oo#....",
        "...#ooo##.",
        "....####..",
        "..........",
    ))

    /** Todas as sprites próprias (10×10); as de lugar vêm de [DiaryMapIcons] (7×7). */
    val OWN: List<PixelSprite> = listOf(CAT, CALENDAR, PIN, MAP, GEAR, SPARKLE, WALK, SUN, SUNRISE, MOON)

    fun of(type: PlaceType): PixelSprite = placeSprites.getValue(type)

    fun of(context: UserContextType): PixelSprite = when (context) {
        UserContextType.HOME -> of(PlaceType.HOME)
        UserContextType.WORK -> of(PlaceType.WORK)
        UserContextType.COMMUTING -> WALK
        UserContextType.LUNCH, UserContextType.DINING -> of(PlaceType.RESTAURANT)
        UserContextType.GYM -> of(PlaceType.GYM)
        UserContextType.STUDY -> of(PlaceType.SCHOOL)
        UserContextType.SHOPPING -> of(PlaceType.MARKET)
        UserContextType.LEISURE -> of(PlaceType.LEISURE)
        UserContextType.VISITING -> of(PlaceType.FAMILY)
        UserContextType.TRAVEL -> MAP
        UserContextType.UNKNOWN -> of(PlaceType.OTHER)
    }

    fun of(period: DayPeriod): PixelSprite = when (period) {
        DayPeriod.MORNING, DayPeriod.EVENING -> SUNRISE
        DayPeriod.DAY -> SUN
        DayPeriod.NIGHT -> MOON
    }

    private val placeSprites: Map<PlaceType, PixelSprite> = DiaryMapIcons.ICONS.mapValues { PixelSprite(it.value) }
}

/** Ícone decorativo: quem usa mantém o rótulo/descrição de acessibilidade. Escala sempre inteira, sem filtro. */
@Composable
fun PixelIconView(
    sprite: PixelSprite,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current,
    fill: Color = tint.copy(alpha = 0.55f),
) {
    Canvas(modifier.size(size)) {
        val cell = (minOf(this.size.width / sprite.width, this.size.height / sprite.height)).toInt().coerceAtLeast(1)
        val dx = ((this.size.width - cell * sprite.width) / 2f).toInt().toFloat()
        val dy = ((this.size.height - cell * sprite.height) / 2f).toInt().toFloat()
        sprite.rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                val color = when (ch) { '#' -> tint; 'o' -> fill; else -> null } ?: return@forEachIndexed
                drawRect(color, Offset(dx + x * cell, dy + y * cell), Size(cell.toFloat(), cell.toFloat()))
            }
        }
    }
}

/** Ícone + texto na mesma linha (no lugar de "🏠 Casa"). */
@Composable
fun IconLabel(
    sprite: PixelSprite,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    iconSize: Dp = 18.dp,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PixelIconView(sprite, size = iconSize, tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}
