package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.JourneyReplayState
import com.hoodie.app.pixel.diary.DiaryMapPerf
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Tudo o que o renderer precisa para um quadro da jornada. */
data class JourneyScene(
    val data: JourneyMapData,
    val layout: JourneyLayout,
    val replay: JourneyReplayState,
    val light: JourneyLight = JourneyLightingRenderer.DAYLIGHT,
    val selectedNodeId: String? = null,
    val timeMs: Long = 0L,
)

/** Camada estática por layout: chão, ruas e decoração parada (desenhados uma vez, copiados a cada quadro). */
class JourneyRenderCache private constructor(val layoutKey: Int, val staticLayer: PixelBuffer, val decor: JourneyDecor) {
    fun matches(layout: JourneyLayout) = layoutKey == layout.hashCode()

    companion object {
        fun create(layout: JourneyLayout): JourneyRenderCache {
            DiaryMapPerf.staticBuilds++
            val decor = JourneyAmbientRenderer.place(layout)
            val buf = PixelBuffer(layout.width, layout.height)
            JourneyAmbientRenderer.paintGround(buf, layout, decor)
            layout.segments.forEach { JourneySegmentRenderer.paintRoad(buf, it.path) }
            return JourneyRenderCache(layout.hashCode(), buf, decor)
        }
    }
}

/**
 * Jornada Pixel, em camadas (plano §7):
 *  1 fundo (céu do horário + chão em cache) · 2 ambientação viva · 3 traço dos
 *  trechos · 4 paradas · 5 luz do horário e brilhos · 6 Hoodie.
 * A camada 7 (cartões, selos, balão do app, HUD) é Compose, por cima.
 * Mesma cena → mesmos pixels.
 */
object JourneyMapRenderer {

    fun render(scene: JourneyScene, cache: JourneyRenderCache? = null, out: PixelBuffer? = null): PixelBuffer {
        val start = System.nanoTime()
        val layout = scene.layout
        val c = cache?.takeIf { it.matches(layout) }?.also { DiaryMapPerf.cacheHits++ } ?: JourneyRenderCache.create(layout)
        DiaryMapPerf.lastCacheHit = c === cache
        val b = out?.takeIf { it.width == layout.width && it.height == layout.height } ?: PixelBuffer(layout.width, layout.height)
        val light = scene.light
        c.staticLayer.pixels.copyInto(b.pixels)
        JourneyAmbientRenderer.paintSky(b, light, scene.timeMs)
        JourneyAmbientRenderer.paintAnimated(b, layout, c.decor, light, scene.timeMs)
        layout.segments.forEach { seg ->
            val state = segmentState(scene.replay, seg.index)
            val progress = if (state == SegmentState.ACTIVE) scene.replay.segmentProgress else 1f
            JourneySegmentRenderer.paintRoute(b, seg.path, scene.data.segmentAfter(seg.index)?.movementMode, state, progress, scene.timeMs)
        }
        layout.nodes.forEach { n ->
            val node = scene.data.nodes.getOrNull(n.index)
            JourneyNodeRenderer.paint(
                b, n, nodeState(scene.replay, n.index), selected = n.nodeId == scene.selectedNodeId,
                isReturn = node?.isReturn == true, lightsOn = light.lightsOn, timeMs = scene.timeMs,
            )
        }
        JourneyLightingRenderer.applyTint(b, light)
        JourneyAmbientRenderer.paintLights(b, layout, c.decor, light, scene.timeMs)
        marker(scene)?.let { JourneyHoodieMarker.paint(b, it, scene.timeMs) }
        val now = System.nanoTime()
        DiaryMapPerf.lastRenderNanos = now - start
        DiaryMapPerf.frameRendered(now)
        return b
    }

    /** Parada: atual no replay, já alcançada ou ainda não. Sem replay o dia inteiro aparece visitado. */
    fun nodeState(replay: JourneyReplayState, index: Int): NodeState = when {
        !replay.replaying -> NodeState.VISITED
        replay.activeNodeIndex == index -> NodeState.ACTIVE
        index <= replay.reachedIndex -> NodeState.VISITED
        else -> NodeState.UPCOMING
    }

    fun segmentState(replay: JourneyReplayState, index: Int): SegmentState = when {
        !replay.replaying -> SegmentState.DONE
        replay.activeSegmentIndex == index -> SegmentState.ACTIVE
        index < replay.reachedIndex -> SegmentState.DONE
        else -> SegmentState.UPCOMING
    }

    /** O Hoodie: andando no trecho atual, parado na parada atual, esperando na primeira ou onde o dia terminou. */
    fun marker(scene: JourneyScene): JourneyMarkerState? {
        val layout = scene.layout
        if (layout.isEmpty) return null
        val r = scene.replay
        r.activeSegmentIndex?.let { i ->
            layout.segments.firstOrNull { it.index == i }?.let { seg ->
                return JourneyHoodieMarker.onSegment(seg, scene.data.segmentAfter(i)?.movementMode, r.segmentProgress)
            }
        }
        val at = r.activeNodeIndex ?: r.reachedIndex.coerceIn(0, layout.nodes.lastIndex)
        return layout.nodes.getOrNull(at)?.let(JourneyHoodieMarker::atNode)
    }
}
