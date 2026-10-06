package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.engine.diary.HoodieAt
import com.hoodie.app.engine.diary.OverworldReplayState
import com.hoodie.app.engine.diary.StopPhase
import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.pixel.diary.DiaryHoodieMarker
import com.hoodie.app.pixel.diary.DiaryMapPerf
import com.hoodie.app.pixel.diary.MarkerDirection
import com.hoodie.app.pixel.diary.journey.JourneyHoodieMarker
import com.hoodie.app.pixel.diary.journey.JourneyLight
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.journey.JourneyMarkerState
import com.hoodie.app.pixel.diary.journey.JourneyPalette
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Tudo o que o renderer precisa para um quadro de um mapa do overworld (dia ou capítulo). */
data class OverworldScene(
    val plan: JourneyPlan,
    val layout: OverworldLayout,
    val replay: OverworldReplayState,
    val light: JourneyLight = JourneyLightingRenderer.DAYLIGHT,
    val selectedStopId: String? = null,
    val timeMs: Long = 0L,
    /** Seed do chão/decoração (a data): mesmo dia → mesmos pixels. */
    val seed: Long = 0L,
    /** Capítulo: moldura de HUD e portais. */
    val framed: Boolean = false,
)

/** Camadas 1–3 (chão, decoração, trilhas no estado "futuro") por layout + seed. */
class OverworldRenderCache private constructor(val key: Int, val staticLayer: PixelBuffer, val decor: List<OverworldDecor>) {
    fun matches(scene: OverworldScene) = key == keyOf(scene)

    companion object {
        fun keyOf(scene: OverworldScene) = 31 * scene.layout.hashCode() + scene.seed.hashCode()

        fun create(scene: OverworldScene): OverworldRenderCache {
            DiaryMapPerf.staticBuilds++
            val layout = scene.layout
            val buf = PixelBuffer(layout.width, layout.height)
            OverworldDecorPlacer.paintGround(buf, scene.seed)
            val decor = OverworldDecorPlacer.place(layout, scene.seed)
            layout.links.forEach { OverworldTrailPainter.paintBed(buf, it.path, modeOf(scene.plan, it.legId)) }
            layout.stops.forEach { OverworldTrailPainter.paintPlaza(buf, it.point) }
            decor.forEach { OverworldDecorPlacer.paintStatic(buf, it) }
            return OverworldRenderCache(keyOf(scene), buf, decor)
        }

        fun modeOf(plan: JourneyPlan, legId: String?): MovementMode? = plan.leg(legId)?.mode
    }
}

/**
 * Overworld em camadas (plano §5.1):
 *  1 chão · 2 decoração · 3 trilhas (cache) · 4 rastro dourado · 5 construções e placas
 *  · 6 vida · 7 luz do minuto · 8 Hoodie, seta "você está aqui" e moldura.
 * Os textos das placas são Compose, por cima. Mesma cena → mesmos pixels.
 */
object OverworldJourneyRenderer {

    fun render(scene: OverworldScene, cache: OverworldRenderCache? = null, out: PixelBuffer? = null): PixelBuffer {
        val start = System.nanoTime()
        val layout = scene.layout
        val c = cache?.takeIf { it.matches(scene) }?.also { DiaryMapPerf.cacheHits++ } ?: OverworldRenderCache.create(scene)
        DiaryMapPerf.lastCacheHit = c === cache
        val b = out?.takeIf { it.width == layout.width && it.height == layout.height } ?: PixelBuffer(layout.width, layout.height)
        c.staticLayer.pixels.copyInto(b.pixels)
        val night = scene.light.lightsOn

        // 4. Rastro dourado do que já foi percorrido.
        layout.links.forEach { link -> OverworldTrailPainter.paintGold(b, link.path, scene.replay.linkProgress[link.id] ?: 1f) }
        // 6a. Vida do cenário (água, copas).
        c.decor.forEach { OverworldDecorPlacer.paintAnimated(b, it, scene.timeMs) }
        // 5. Construções e placas, de cima para baixo (profundidade).
        layout.stops.sortedBy { it.point.y }.forEach { s ->
            val stop = scene.plan.stop(s.stopId) ?: return@forEach
            val state = stateOf(scene.replay.phases[s.stopId])
            val biome = OverworldBiomeCatalog.biomeOf(stop, scene.plan)
            OverworldBiomeCatalog.paint(b, biome, s.building.x, s.building.y, state, night, scene.timeMs)
            val returnBadge = (stop as? JourneyStop.Visit)?.returnIndex?.let { it > 1 } == true
            SignpostPainter.paint(b, s.sign, s.signSide, state, returnBadge)
            if (s.stopId == scene.selectedStopId) selection(b, s.building)
        }
        // 6b. Passarinhos cruzando o mapa.
        birds(b, scene.timeMs)
        // 7. Luz do minuto: a tinta escurece o mundo, mas janelas acesas e fogo continuam brilhando.
        val glow = if (night) b.pixels.indices.filter { b.pixels[it] in GLOWING } else emptyList()
        val glowColors = glow.map { b.pixels[it] }
        JourneyLightingRenderer.applyTint(b, scene.light, fromY = 0)
        glow.forEachIndexed { k, i -> b.pixels[i] = glowColors[k] }
        if (night) fireflies(b, c.decor, scene.timeMs)
        // 8. Moldura e portais, seta "você está aqui" e o Hoodie.
        if (scene.framed) {
            ChapterFramePainter.paintFrame(b)
            layout.portalIn?.let { ChapterFramePainter.paintEntry(b, it, scene.timeMs) }
            layout.portalOut?.let { ChapterFramePainter.paintExit(b, it, scene.timeMs) }
        }
        scene.replay.phases.entries.firstOrNull { it.value == StopPhase.CURRENT }?.let { (id, _) ->
            layout.stop(id)?.let { youAreHere(b, it.building.x + it.building.w / 2, it.building.y - 2, scene.timeMs) }
        }
        marker(scene)?.let { JourneyHoodieMarker.paint(b, it, scene.timeMs) }

        val now = System.nanoTime()
        DiaryMapPerf.lastRenderNanos = now - start
        DiaryMapPerf.frameRendered(now)
        return b
    }

    fun stateOf(phase: StopPhase?): BiomeState = when (phase) {
        StopPhase.FUTURE -> BiomeState.FUTURE
        StopPhase.CURRENT -> BiomeState.CURRENT
        StopPhase.GHOST -> BiomeState.GHOST
        else -> BiomeState.VISITED
    }

    /** O Hoodie no link (andando, com o veículo do meio) ou na porta da parada. */
    fun marker(scene: OverworldScene): JourneyMarkerState? {
        val h: HoodieAt = scene.replay.hoodie ?: return null
        h.linkId?.let { id ->
            val link = scene.layout.link(id) ?: return null
            val p = h.linkProgress.coerceIn(0f, 1f)
            val (dx, dy) = link.path.directionAt(p)
            val sign = link.path.horizontalSign
            return JourneyMarkerState(
                position = link.path.pointAt(p),
                direction = DiaryHoodieMarker.direction(dx, dy),
                moving = p > 0f && p < 1f,
                vehicle = JourneyHoodieMarker.vehicleFor(h.mode),
                facingRight = if (sign != 0f) sign > 0f else dx >= 0f,
                running = h.mode == MovementMode.RUNNING,
            )
        }
        val s = scene.layout.stop(h.stopId) ?: return null
        return JourneyMarkerState(s.point, MarkerDirection.FRONT, moving = false, vehicle = JourneyVehicle.ON_FOOT, facingRight = s.col == 0)
    }

    private fun selection(b: PixelBuffer, r: com.hoodie.app.pixel.diary.journey.JourneyRect) {
        val c = JourneyPalette.SELECTED
        b.hline(r.x - 2, r.right + 1, r.y - 2, c); b.hline(r.x - 2, r.right + 1, r.bottom + 1, c)
        b.vline(r.x - 2, r.y - 2, r.bottom + 1, c); b.vline(r.right + 1, r.y - 2, r.bottom + 1, c)
    }

    /** Seta pulsando acima da construção atual. */
    private fun youAreHere(b: PixelBuffer, cx: Int, y: Int, timeMs: Long) {
        val up = ((timeMs / 250) % 2).toInt()
        val top = (y - 9 - up).coerceAtLeast(0)
        b.box(cx - 1, top, cx + 1, top + 3, OverworldPalette.GOLD)
        b.hline(cx - 3, cx + 3, top + 4, OverworldPalette.GOLD)
        b.hline(cx - 2, cx + 2, top + 5, OverworldPalette.GOLD)
        b.set(cx, top + 6, OverworldPalette.GOLD)
        b.set(cx - 4, top + 4, OverworldPalette.OUTLINE); b.set(cx + 4, top + 4, OverworldPalette.OUTLINE)
    }

    private fun birds(b: PixelBuffer, timeMs: Long) {
        val x = (b.width + 20 - ((timeMs / 60) % (b.width + 40))).toInt()
        val y = 10 + ((timeMs / 2_000) % 3).toInt() * 6
        val wing = ((timeMs / 200) % 2).toInt()
        for (k in 0..1) {
            val bx = x + k * 9; val by = y + k * 3
            b.set(bx - 1, by - wing, OverworldPalette.BIRD); b.set(bx, by, OverworldPalette.BIRD); b.set(bx + 1, by - wing, OverworldPalette.BIRD)
        }
    }

    private fun fireflies(b: PixelBuffer, decor: List<OverworldDecor>, timeMs: Long) {
        decor.filter { it.kind == OverworldDecor.Kind.TREE || it.kind == OverworldDecor.Kind.BUSH }.take(10).forEachIndexed { i, d ->
            if (((timeMs / 300) + i) % 3 != 0L) return@forEachIndexed
            val dx = ((timeMs / 200 + i * 7) % 9).toInt() - 4
            b.set(d.x + dx, d.y - 6 - (i % 4), OverworldPalette.FIREFLY)
        }
    }

    /** Altura do Hoodie acima dos pés (balão do app). */
    fun markerHeight(m: JourneyMarkerState) = JourneyHoodieMarker.heightOf(m)

    /** Cores que emitem luz própria à noite (janelas, chamas). */
    private val GLOWING = setOf(OverworldPalette.WINDOW_LIT, OverworldPalette.FIRE, OverworldPalette.FIRE_CORE)
}
