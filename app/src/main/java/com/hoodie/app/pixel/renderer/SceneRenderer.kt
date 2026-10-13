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
import com.hoodie.app.pixel.npc.shopping.ShoppingNavigationGraph
import com.hoodie.app.pixel.npc.shopping.StoreNavigationGraph
import com.hoodie.app.pixel.npc.shopping.StoreSpeechLibrary
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.leisure.LeisureNpcDirector
import com.hoodie.app.pixel.npc.leisure.LeisureNpcSession
import com.hoodie.app.pixel.npc.venue.VenueNpcDirector
import com.hoodie.app.pixel.npc.venue.VenueNpcSession
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.pixel.scene.Prop
import com.hoodie.app.pixel.performance.ScenePerformanceMonitor
import com.hoodie.app.BuildConfig
import java.util.LinkedHashMap

/** Objeto de microevento no chão: a âncora fica um pouco acima dos pés de quem o largou. */
private const val FLOOR_ITEM_LIFT = 2

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
    private data class ShoppingKey(val scene: SceneId, val day: Int, val variant: Int,
        val venue: com.hoodie.app.core.model.PlaceType?)
    private var shoppingKey: ShoppingKey? = null
    private var shoppingSessionStartedAt: Long? = null
    /** Passeio 2.0: simulação dos visitantes deste renderer (uma por dia/variante/noite). */
    private var leisureSession: LeisureNpcSession? = null
    /** Estabelecimentos (padaria, praia, cinema...): uma sessão por cena/lugar/dia/variante/noite. */
    private var venueSession: VenueNpcSession? = null
    /** Restaurante: agenda dos microeventos (copo derramado) e o garçom que só aparece neles. */
    private var restaurantStory: com.hoodie.app.pixel.npc.restaurant.RestaurantStorySession? = null

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
        if (scene.id == SceneId.SHOPPING || scene.id == SceneId.STORE) {
            // Mercado e loja de roupas: o mesmo comprador, cada um com a sua planta e as suas falas.
            val store = scene.id == SceneId.STORE
            val floor = if (store) StoreNavigationGraph else ShoppingNavigationGraph
            val key = ShoppingKey(scene.id, env.daySeed, env.variant, env.shoppingVenue)
            val current = shoppingSession
            // Lighting changes immediately; the shopper finishes the current story without restarting.
            val session = current?.takeIf { shoppingKey == key && it.floor == floor }
                ?: (if (store) ShoppingNpcDirector.createSession(env, StoreNavigationGraph, StoreSpeechLibrary, StoreNavigationGraph.BASELINE)
                else ShoppingNpcDirector.createSession(env)).also {
                    shoppingSession = it
                    shoppingKey = key
                    shoppingSessionStartedAt = null
                }
            return session.npcSlots
        }
        shoppingSession = null
        shoppingKey = null
        shoppingSessionStartedAt = null
        if (VenueNpcDirector.supports(scene.id)) {
            officeSession?.dispose(); officeSession = null; officeRenderPlan = null
            leisureSession?.dispose(); leisureSession = null
            val key = VenueNpcDirector.sessionKey(scene.id, env)
            val session = venueSession?.takeIf { it.key == key } ?: run {
                venueSession?.dispose()
                VenueNpcDirector.createSession(key).also { venueSession = it }
            }
            return session.npcSlots
        }
        venueSession?.dispose()
        venueSession = null
        if (scene.id == SceneId.LEISURE && HoodieConfig.LEISURE_LIVING_WORLD_V2) {
            officeSession?.dispose()
            officeSession = null
            officeRenderPlan = null
            val key = LeisureNpcDirector.sessionKey(env)
            val session = leisureSession?.takeIf { it.key == key } ?: run {
                leisureSession?.dispose()
                LeisureNpcDirector.createSession(env).also { leisureSession = it }
            }
            return session.npcSlots
        }
        leisureSession?.dispose()
        leisureSession = null
        if (scene.id != SceneId.OFFICE) {
            officeSession?.dispose()
            officeSession = null
            officeRenderPlan = null
            if (scene.id == SceneId.RESTAURANT && HoodieConfig.SCENE_STORY_EVENTS) {
                // Restaurante: o cliente é refeito a cada quadro, mas a agenda de microeventos é deste renderer.
                val base = scene.ambientNpcs(env)
                val guest = base.firstNotNullOfOrNull { it.restaurantBrain } ?: return base
                val key = com.hoodie.app.pixel.npc.restaurant.RestaurantStorySession.key(env)
                val story = restaurantStory?.takeIf { it.key == key } ?: run {
                    restaurantStory?.dispose()
                    com.hoodie.app.pixel.npc.restaurant.RestaurantStorySession(key, guest).also { restaurantStory = it }
                }
                return story.slots(base)
            }
            restaurantStory?.dispose(); restaurantStory = null
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
        shoppingKey = null
        shoppingSessionStartedAt = null
        leisureSession?.dispose()
        leisureSession = null
        venueSession?.dispose()
        venueSession = null
        restaurantStory?.dispose()
        restaurantStory = null
    }

    /** Objetos de um microevento largados no chão (a sacola caída), desenhados na profundidade de onde estão. */
    private fun storyFloorObjects(env: SceneEnv): List<Pair<Int, () -> Unit>> =
        env.sceneStory?.objects.orEmpty()
            .filter { it.holder == com.hoodie.app.pixel.npc.events.StoryObjectHolder.ON_FLOOR && it.x != null && it.y != null }
            .map { o -> o.y!! to { HoodiePainter.drawItemAt(buffer, o.item, com.hoodie.app.pixel.sprite.Point(o.x!!, o.y!! - FLOOR_ITEM_LIFT)) } }

    /** Profundidade de desenho: visitantes do Passeio e de estabelecimentos usam o chão onde estão agora. */
    private fun npcDepth(slot: AmbientNpcSlot, timeMs: Long): Int =
        slot.story?.movementAt(slot.definition.id, timeMs)?.floorY
            ?: slot.leisureActor?.frameStateAt(timeMs)?.floorY ?: slot.venueActor?.frameStateAt(timeMs)?.floorY ?: slot.baseline

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
        val measureScene = BuildConfig.DEBUG && (scene.id == SceneId.OFFICE || scene.id == SceneId.STORE)
        val traceScene = if (scene.id == SceneId.STORE) "Store" else "Office"
        val renderStart = if (measureScene) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureScene) ScenePerformanceMonitor.traceBegin("Hoodie.$traceScene.render")
        val planningStart = if (measureScene) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureScene) ScenePerformanceMonitor.traceBegin("Hoodie.$traceScene.npcPlanning")
        val npcs = npcSlots(scene, frame.env)
        if (measureScene) ScenePerformanceMonitor.traceEnd()
        val planningNanos = if (measureScene) ScenePerformanceMonitor.nowNanos() - planningStart else 0L
        val shoppingScene = scene.id == SceneId.SHOPPING || scene.id == SceneId.STORE
        if (shoppingScene && shoppingSessionStartedAt == null) shoppingSessionStartedAt = absoluteTimeMs
        val timeMs = if (shoppingScene) (absoluteTimeMs - checkNotNull(shoppingSessionStartedAt)).coerceAtLeast(0L) else absoluteTimeMs
        val stateStart = if (measureScene) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureScene) ScenePerformanceMonitor.traceBegin("Hoodie.$traceScene.stateUpdate")
        // O Passeio divide o banco com o Hoodie pelo destino da microação dele.
        if (scene.id == SceneId.LEISURE) leisureSession?.observeHoodie(timeMs, frame.occupancy)
        val env = envWithAmbientNpcState(frame.env, npcs, timeMs)
        if (measureScene) ScenePerformanceMonitor.traceEnd()
        val officePlan = if (scene.id == SceneId.OFFICE) {
            officePlan(scene, npcs).also { plan ->
                plan.update(timeMs)
            }
        } else null
        val stateNanos = if (measureScene) ScenePerformanceMonitor.nowNanos() - stateStart else 0L
        val drawingStart = if (measureScene) ScenePerformanceMonitor.nowNanos() else 0L
        if (measureScene) ScenePerformanceMonitor.beginOfficeFrame()
        if (measureScene) ScenePerformanceMonitor.traceBegin("Hoodie.$traceScene.draw")
        var stageStarted = if (measureScene) ScenePerformanceMonitor.nowNanos() else 0L
        buffer.copyFrom(background(scene, env))
        var sceneCopyNanos = 0L
        var sceneObjectsNanos = 0L
        var sceneHoodieNanos = 0L
        var sceneLightingNanos = 0L
        var scenePostNanos = 0L
        if (measureScene) {
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
            val behindNpcs = npcs.filter { npcDepth(it, timeMs) <= frame.y }
                (behindProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
                    behindNpcs.map { npcDepth(it, timeMs) to { npcDrawingNanos += drawAmbientNpc(it, timeMs, measureScene) } } +
                    storyFloorObjects(env).filter { it.first <= frame.y })
                    .sortedBy { it.first }.forEach { it.second() }
        }
        if (measureScene) {
            npcBeforeCharacterNanos = npcDrawingNanos
            objectNanos = (ScenePerformanceMonitor.nowNanos() - stageStarted - npcBeforeCharacterNanos).coerceAtLeast(0L)
        }

        // O ponto dos pés do frame é alinhado ao chão: sprite sheets e procedural usam o mesmo contrato.
        if (measureScene) {
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
        if (measureScene) {
            sceneHoodieNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        if (scene.id == SceneId.OFFICE) {
            officeRenderPlan?.entries?.forEach { entry ->
                if (entry.baseline > frame.y) npcDrawingNanos += drawOfficeEntry(entry, env, timeMs)
            }
        } else {
            val frontProps = scene.sortedProps.filter { it.baseline > frame.y }
            val frontNpcs = npcs.filter { npcDepth(it, timeMs) > frame.y }
            (frontProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
                frontNpcs.map { npcDepth(it, timeMs) to { npcDrawingNanos += drawAmbientNpc(it, timeMs, measureScene) } } +
                storyFloorObjects(env).filter { it.first > frame.y })
                .sortedBy { it.first }.forEach { it.second() }
        }

        if (measureScene) {
            val frontObjectsNanos = ScenePerformanceMonitor.nowNanos() - stageStarted -
                (npcDrawingNanos - npcBeforeCharacterNanos)
            sceneObjectsNanos = objectNanos + frontObjectsNanos.coerceAtLeast(0L)
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        Lighting.apply(buffer, Lighting.map(scene, env))
        if (scene.usesTransportLightingProfile) applyTransportLighting(buffer, env.transportAmbient?.lighting)
        if (measureScene) {
            sceneLightingNanos = ScenePerformanceMonitor.nowNanos() - stageStarted
            stageStarted = ScenePerformanceMonitor.nowNanos()
        }
        scene.drawPostLighting(buffer, env, timeMs)
        frame.effects.forEach { (kind, pos) -> Effects.draw(buffer, kind, pos.first, pos.second, timeMs) }
        Lighting.fade(buffer, frame.fade)
        if (measureScene) {
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
        val npcs = slots.map { slot -> npcDepth(slot, timeMs) to {
            NpcRenderer.draw(buffer, slot, timeMs, movementOverride(slot, timeMs, facingOverride))
        } }
        (props + npcs + storyFloorObjects(drawEnv))
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

    private fun drawAmbientNpc(slot: com.hoodie.app.pixel.npc.AmbientNpcSlot, timeMs: Long, measure: Boolean): Long {
        val started = if (measure) ScenePerformanceMonitor.nowNanos() else 0L
        NpcRenderer.draw(buffer, slot, timeMs)
        return if (measure) ScenePerformanceMonitor.nowNanos() - started else 0L
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
        val leisureAmbient = npcs.firstNotNullOfOrNull { it.leisureActor }?.session?.snapshotAt(timeMs)?.ambient
        val venueAmbient = npcs.firstNotNullOfOrNull { it.venueActor }?.session?.snapshotAt(timeMs)?.ambient
        val story = npcs.firstNotNullOfOrNull { it.officeBrain?.socialSession?.deliveries?.snapshotAt(timeMs) }
            ?: npcs.firstNotNullOfOrNull { it.story?.snapshotAt(timeMs) }
            ?: npcs.firstNotNullOfOrNull { it.venueActor }?.session?.storySnapshotAt(timeMs)
            ?: npcs.firstNotNullOfOrNull { it.leisureActor }?.session?.storySnapshotAt(timeMs)
        return env.copy(
            sceneStory = story ?: env.sceneStory,
            leisureAmbient = leisureAmbient ?: env.leisureAmbient,
            venueAmbient = venueAmbient ?: env.venueAmbient,
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
