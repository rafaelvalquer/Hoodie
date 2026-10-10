package com.hoodie.app.pixel.renderer

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.transport.TransportLighting
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSession
import com.hoodie.app.pixel.npc.office.OfficeSessionKey
import com.hoodie.app.pixel.npc.office.OfficeNpcFrameState
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcSession
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.scene.Prop
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.BuildConfig
import java.util.LinkedHashMap

/**
 * Compõe um frame: fundo (cacheado) → objetos atrás → Hoodie → objetos na frente
 * (ordenados por Y) → iluminação → efeitos → fade.
 */
class SceneRenderer {
    val buffer = PixelBuffer(PixelScene.SCENE_W, PixelScene.SCENE_H)

    private data class BgKey(val scene: SceneId, val period: DayPeriod, val variant: Int)
    private val backgrounds = LinkedHashMap<BgKey, PixelBuffer>(20, .75f, true)
    private val backgroundCacheLimit = 16
    /** Office state belongs to this renderer: previews and other renderers never share brains. */
    private var officeSession: OfficeNpcSession? = null
    private var officeRenderPlan: OfficeRenderPlan? = null
    private var shoppingSession: ShoppingNpcSession? = null
    private var shoppingSessionStartedAt: Long = 0L

    private class OfficeDrawEntry(
        val prop: Prop? = null,
        val npcIndex: Int = -1,
        var slot: AmbientNpcSlot? = null,
        var frameState: OfficeNpcFrameState? = null,
        var baseline: Int,
    )

    /** One preallocated composition list; only baselines and current slot refs change per frame. */
    private class OfficeRenderPlan(val scene: PixelScene, val slots: List<AmbientNpcSlot>) {
        val entries = ArrayList<OfficeDrawEntry>(scene.sortedProps.size + slots.size).apply {
            scene.sortedProps.forEach { add(OfficeDrawEntry(prop = it, baseline = it.baseline)) }
            slots.forEachIndexed { index, slot -> add(OfficeDrawEntry(npcIndex = index, slot = slot, baseline = slot.baseline)) }
        }

        fun update(timeMs: Long) {
            for (entry in entries) {
                val slot = entry.slot ?: continue
                val current = slots.getOrNull(entry.npcIndex) ?: slot
                entry.slot = current
                val state = current.officeBrain?.frameStateAt(timeMs)
                entry.frameState = state
                entry.baseline = state?.floorY ?: current.baseline
            }
            // Stable insertion sort avoids per-frame filtered/combined/mapped collections.
            for (i in 1 until entries.size) {
                val item = entries[i]
                var j = i - 1
                while (j >= 0 && entries[j].baseline > item.baseline) {
                    entries[j + 1] = entries[j]
                    j--
                }
                entries[j + 1] = item
            }
        }

    }

    private fun npcSlots(scene: PixelScene, env: SceneEnv): List<com.hoodie.app.pixel.npc.AmbientNpcSlot> {
        if (scene.id == SceneId.SHOPPING) {
            val current = shoppingSession
            val session = current?.takeIf { it.venue == env.shoppingVenue && it.seed == (env.daySeed * 31 + env.variant * 17 + env.period.ordinal) }
                ?: ShoppingNpcDirector.createSession(env).also {
                    shoppingSession = it
                    shoppingSessionStartedAt = 0L
                }
            return session.npcSlots
        }
        shoppingSession = null
        if (scene.id != SceneId.OFFICE) {
            officeSession?.dispose()
            officeSession = null
            officeRenderPlan = null
            return scene.ambientNpcs(env)
        }
        val key = OfficeSessionKey(env.daySeed, env.variant)
        val current = officeSession
        val session = current?.takeIf { it.key == key } ?: run {
            current?.dispose()
            officeRenderPlan = null
            OfficeNpcDirector.createSession(env).also { officeSession = it }
        }
        return session.npcSlots(env.clockMinute)
    }

    /** Releases scene-local mutable brains and their planned timeline on renderer disposal. */
    @Synchronized
    fun dispose() {
        officeSession?.dispose()
        officeSession = null
        officeRenderPlan = null
        backgrounds.clear()
        shoppingSession = null
    }

    private fun background(scene: PixelScene, env: SceneEnv): PixelBuffer = synchronized(backgrounds) {
        val visualVariant = if (scene.id == SceneId.SHOPPING) {
            if (env.shoppingVenue == com.hoodie.app.core.model.PlaceType.STORE) 1 else 0
        } else env.variant
        val key = BgKey(scene.id, env.period, visualVariant.mod(scene.backgroundVariants))
        backgrounds[key]?.let { return@synchronized it }
        val rendered = PixelBuffer(scene.width, scene.height).also { scene.drawBackground(it, env.copy(variant = visualVariant)) }
        backgrounds[key] = rendered
        if (backgrounds.size > backgroundCacheLimit) {
            val eldest = backgrounds.entries.iterator()
            if (eldest.hasNext()) { eldest.next(); eldest.remove() }
        }
        rendered
    }

    @Synchronized
    fun render(frame: RenderFrame, absoluteTimeMs: Long): PixelBuffer {
        val scene = frame.scene
        val measureOffice = BuildConfig.DEBUG && scene.id == SceneId.OFFICE
        val renderStart = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureOffice) ScenePerformanceMonitor.traceBegin("Hoodie.Office.render")
        val planningStart = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureOffice) ScenePerformanceMonitor.traceBegin("Hoodie.Office.npcPlanning")
        val npcs = npcSlots(scene, frame.env)
        if (measureOffice) ScenePerformanceMonitor.traceEnd()
        val planningNanos = if (measureOffice) ScenePerformanceMonitor.nowNanos() - planningStart else 0L
        if (scene.id == SceneId.SHOPPING && shoppingSessionStartedAt == 0L) shoppingSessionStartedAt = absoluteTimeMs
        val timeMs = if (scene.id == SceneId.SHOPPING) (absoluteTimeMs - shoppingSessionStartedAt).coerceAtLeast(0L) else absoluteTimeMs
        val stateStart = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureOffice) ScenePerformanceMonitor.traceBegin("Hoodie.Office.stateUpdate")
        val env = envWithAmbientNpcState(frame.env, npcs, timeMs)
        if (measureOffice) ScenePerformanceMonitor.traceEnd()
        val officePlan = if (scene.id == SceneId.OFFICE) {
            officePlan(scene, npcs).also { plan ->
                plan.update(timeMs)
            }
        } else null
        val stateNanos = if (measureOffice) ScenePerformanceMonitor.nowNanos() - stateStart else 0L
        val drawingStart = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureOffice) ScenePerformanceMonitor.beginOfficeFrame()
        if (measureOffice) ScenePerformanceMonitor.traceBegin("Hoodie.Office.draw")
        var stageStarted = if (measureOffice) ScenePerformanceMonitor.nowNanos() else 0L
        buffer.copyFrom(background(scene, env))
        var sceneCopyNanos = 0L
        var sceneObjectsNanos = 0L
        var sceneHoodieNanos = 0L
        var sceneLightingNanos = 0L
        var scenePostNanos = 0L
        if (measureOffice) {
            sceneCopyNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        var npcDrawingNanos = 0L
        var objectNanos = 0L
        var npcBeforeCharacterNanos = 0L
        if (scene.id == SceneId.OFFICE) {
            officePlan?.entries?.forEach { entry ->
                if (entry.baseline <= frame.y) npcDrawingNanos += drawOfficeEntry(entry, env, timeMs)
            }
        } else {
            val behindProps = scene.sortedProps.filter { it.baseline <= frame.y }
            val behindNpcs = npcs.filter { it.baseline <= frame.y }
                (behindProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
                    behindNpcs.map { it.baseline to { NpcRenderer.draw(buffer, it, timeMs) } })
                    .sortedBy { it.first }.forEach { it.second() }
        }
        if (measureOffice) {
            npcBeforeCharacterNanos = npcDrawingNanos
            objectNanos = (ScenePerformanceMonitor.nowNanos() - stageStarted - npcBeforeCharacterNanos).coerceAtLeast(0L)
        }

        // O ponto dos pés do frame é alinhado ao chão: sprite sheets e procedural usam o mesmo contrato.
        if (measureOffice) {
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        val sprite = frame.sprite
        val left = frame.x - sprite.anchors.feet.x
        val top = frame.y - sprite.anchors.feet.y
        scene.drawCharacter(buffer, sprite, left, top, timeMs, env)
        sprite.itemOverlay?.let { item ->
            val hand = sprite.anchors.rightHand
            HoodiePainter.drawItemAt(buffer, item, com.hoodie.app.pixel.sprite.Point(left + hand.x, top + hand.y))
        }
        if (measureOffice) {
            sceneHoodieNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        if (scene.id == SceneId.OFFICE) {
            officeRenderPlan?.entries?.forEach { entry ->
                if (entry.baseline > frame.y) npcDrawingNanos += drawOfficeEntry(entry, env, timeMs)
            }
        } else {
            val frontProps = scene.sortedProps.filter { it.baseline > frame.y }
            val frontNpcs = npcs.filter { it.baseline > frame.y }
            (frontProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
                frontNpcs.map { it.baseline to { NpcRenderer.draw(buffer, it, timeMs) } })
                .sortedBy { it.first }.forEach { it.second() }
        }

        if (measureOffice) {
            val frontObjectsNanos = ScenePerformanceMonitor.nowNanos() - stageStarted -
                (npcDrawingNanos - npcBeforeCharacterNanos)
            sceneObjectsNanos = objectNanos + frontObjectsNanos.coerceAtLeast(0L)
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        Lighting.apply(buffer, Lighting.map(scene, env))
        if (scene.usesTransportLightingProfile) applyTransportLighting(buffer, env.transportAmbient?.lighting)
        if (measureOffice) {
            sceneLightingNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        scene.drawPostLighting(buffer, env, timeMs)
        frame.effects.forEach { (kind, pos) -> Effects.draw(buffer, kind, pos.first, pos.second, timeMs) }
        Lighting.fade(buffer, frame.fade)
        if (measureOffice) {
            scenePostNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            ScenePerformanceMonitor.traceEnd()
            val finishedAt = ScenePerformanceMonitor.nowNanos()
            ScenePerformanceMonitor.recordRender(
                renderStart, planningNanos, stateNanos, finishedAt - drawingStart,
                npcDrawingNanos, finishedAt, sceneCopyNanos, sceneObjectsNanos,
                sceneHoodieNanos, sceneLightingNanos, scenePostNanos,
            )
            ScenePerformanceMonitor.traceEnd()
        }
        return buffer
    }

    /** Cena sem personagem (galeria de cenas / thumbnails). */
    @Synchronized
    fun renderEmpty(
        scene: PixelScene,
        env: SceneEnv,
        timeMs: Long,
        includeAmbientNpcs: Boolean = true,
        facingOverride: Pair<String, Facing>? = null,
    ): PixelBuffer {
        val slots = if (includeAmbientNpcs) npcSlots(scene, env) else emptyList()
        val drawEnv = envWithAmbientNpcState(env, slots, timeMs)
        buffer.copyFrom(background(scene, drawEnv))
        val props = scene.sortedProps.map { it.baseline to { it.draw(buffer, drawEnv, timeMs) } }
        val npcs = slots.map { slot -> slot.baseline to {
            NpcRenderer.draw(buffer, slot, timeMs, movementOverride(slot, timeMs, facingOverride))
        } }
        (props + npcs)
            .sortedBy { it.first }.forEach { it.second() }
        Lighting.apply(buffer, Lighting.map(scene, drawEnv))
        if (scene.usesTransportLightingProfile) applyTransportLighting(buffer, drawEnv.transportAmbient?.lighting)
        scene.drawPostLighting(buffer, drawEnv, timeMs)
        return buffer
    }

    private fun officePlan(scene: PixelScene, slots: List<AmbientNpcSlot>): OfficeRenderPlan {
        val existing = officeRenderPlan
        if (existing != null && existing.scene === scene && existing.slots === slots) return existing
        return OfficeRenderPlan(scene, slots).also { officeRenderPlan = it }
    }

    private fun drawOfficeEntry(entry: OfficeDrawEntry, env: SceneEnv, timeMs: Long): Long {
        val prop = entry.prop
        if (prop != null) {
            prop.draw(buffer, env, timeMs)
            return 0L
        }
        val slot = entry.slot ?: return 0L
        val state = entry.frameState
        val started = if (BuildConfig.DEBUG) ScenePerformanceMonitor.nowNanos() else 0L
        NpcRenderer.draw(buffer, slot, timeMs, state?.movement, state)
        if (!BuildConfig.DEBUG) return 0L
        val elapsed = ScenePerformanceMonitor.nowNanos() - started
        ScenePerformanceMonitor.recordNpcDrawing(slot.definition.id, elapsed)
        return elapsed
    }

    /** Frame isolado do NPC para ferramentas de revisão; usa os mesmos props e estado da cena. */
    @Synchronized
    fun renderAmbientNpc(scene: PixelScene, env: SceneEnv, timeMs: Long, facingOverride: Pair<String, Facing>? = null): PixelBuffer {
        val slots = npcSlots(scene, env)
        val drawEnv = envWithAmbientNpcState(env, slots, timeMs)
        buffer.copyFrom(background(scene, drawEnv))
        scene.sortedProps.forEach { it.draw(buffer, drawEnv, timeMs) }
        slots.forEach { NpcRenderer.draw(buffer, it, timeMs, movementOverride(it, timeMs, facingOverride)) }
        Lighting.apply(buffer, Lighting.map(scene, drawEnv))
        if (scene.usesTransportLightingProfile) applyTransportLighting(buffer, drawEnv.transportAmbient?.lighting)
        scene.drawPostLighting(buffer, drawEnv, timeMs)
        return buffer
    }

    private fun movementOverride(
        slot: com.hoodie.app.pixel.npc.AmbientNpcSlot,
        timeMs: Long,
        facingOverride: Pair<String, Facing>?,
    ): NpcMovement? = facingOverride?.takeIf { it.first == slot.definition.id }?.let { (_, facing) ->
        NpcMotionController.movement(slot, timeMs).copy(facing = facing)
    }

    private fun envWithAmbientNpcState(
        env: SceneEnv,
        npcs: List<com.hoodie.app.pixel.npc.AmbientNpcSlot>,
        timeMs: Long,
    ): SceneEnv {
        val shopper = npcs.firstNotNullOfOrNull { it.shoppingBrain }
        val shopperState = shopper?.visualStateAt(timeMs)
        val restaurant = npcs.firstNotNullOfOrNull { it.restaurantBrain }
        val tableState = restaurant?.tableStateAt(timeMs)
        val officeDoorFrame = npcs.firstNotNullOfOrNull { it.officeBrain?.officeDoorFrameAt(timeMs)?.takeIf { frame -> frame > 0 } }
        return env.copy(
            shoppingNpc = shopperState ?: env.shoppingNpc,
            doorFrame = maxOf(env.doorFrame, officeDoorFrame ?: 0,
                if (shopperState?.doorOpen == true) SceneEnv.DOOR_OPEN else 0),
            restaurantGuestTable = tableState ?: env.restaurantGuestTable,
        )
    }

    /** Ajusta cabine aberta, interior diurno ou túnel após a iluminação de horário da cena. */
    private fun applyTransportLighting(buffer: PixelBuffer, lighting: TransportLighting?) {
        val factor = when (lighting) {
            TransportLighting.OPEN_AIR, null -> null
            TransportLighting.DAYLIGHT_INTERIOR -> intArrayOf(244, 248, 252)
            TransportLighting.TUNNEL -> intArrayOf(176, 194, 232)
        } ?: return
        val pixels = buffer.pixels
        for (i in pixels.indices) {
            val color = pixels[i]
            val red = ((color ushr 16) and 0xFF) * factor[0] / 256
            val green = ((color ushr 8) and 0xFF) * factor[1] / 256
            val blue = (color and 0xFF) * factor[2] / 256
            pixels[i] = (color and -0x1000000) or (red shl 16) or (green shl 8) or blue
        }
    }
}
