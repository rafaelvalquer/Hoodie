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

/**
 * Compõe um frame: fundo (cacheado) → objetos atrás → Hoodie → objetos na frente
 * (ordenados por Y) → iluminação → efeitos → fade.
 */
class SceneRenderer {
    val buffer = PixelBuffer(PixelScene.SCENE_W, PixelScene.SCENE_H)

    private data class BgKey(val scene: SceneId, val period: DayPeriod, val variant: Int)
    private val backgrounds = HashMap<BgKey, PixelBuffer>()

    private fun background(scene: PixelScene, env: SceneEnv) = backgrounds.getOrPut(BgKey(scene.id, env.period, env.variant.mod(scene.backgroundVariants))) {
        PixelBuffer(scene.width, scene.height).also { scene.drawBackground(it, env) }
    }

    fun render(frame: RenderFrame, timeMs: Long): PixelBuffer {
        val scene = frame.scene
        val npcs = scene.ambientNpcs(frame.env)
        val env = envWithAmbientNpcState(frame.env, npcs, timeMs)
        buffer.copyFrom(background(scene, env))
        val behindProps = scene.sortedProps.filter { it.baseline <= frame.y }
        val behindNpcs = npcs.filter { it.baseline <= frame.y }
        (behindProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
            behindNpcs.map { it.baseline to { NpcRenderer.draw(buffer, it, timeMs) } })
            .sortedBy { it.first }.forEach { it.second() }

        // O ponto dos pés do frame é alinhado ao chão: sprite sheets e procedural usam o mesmo contrato.
        val sprite = frame.sprite
        val left = frame.x - sprite.anchors.feet.x
        val top = frame.y - sprite.anchors.feet.y
        scene.drawCharacter(buffer, sprite, left, top, timeMs, env)
        sprite.itemOverlay?.let { item ->
            val hand = sprite.anchors.rightHand
            HoodiePainter.drawItemAt(buffer, item, com.hoodie.app.pixel.sprite.Point(left + hand.x, top + hand.y))
        }
        val frontProps = scene.sortedProps.filter { it.baseline > frame.y }
        val frontNpcs = npcs.filter { it.baseline > frame.y }
        (frontProps.map { it.baseline to { it.draw(buffer, env, timeMs) } } +
            frontNpcs.map { it.baseline to { NpcRenderer.draw(buffer, it, timeMs) } })
            .sortedBy { it.first }.forEach { it.second() }

        Lighting.apply(buffer, Lighting.map(scene, env))
        if (scene.usesTransportLightingProfile) applyTransportLighting(buffer, env.transportAmbient?.lighting)
        scene.drawPostLighting(buffer, env, timeMs)
        frame.effects.forEach { (kind, pos) -> Effects.draw(buffer, kind, pos.first, pos.second, timeMs) }
        Lighting.fade(buffer, frame.fade)
        return buffer
    }

    /** Cena sem personagem (galeria de cenas / thumbnails). */
    fun renderEmpty(
        scene: PixelScene,
        env: SceneEnv,
        timeMs: Long,
        includeAmbientNpcs: Boolean = true,
        facingOverride: Pair<String, Facing>? = null,
    ): PixelBuffer {
        val slots = if (includeAmbientNpcs) scene.ambientNpcs(env) else emptyList()
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

    /** Frame isolado do NPC para ferramentas de revisão; usa os mesmos props e estado da cena. */
    fun renderAmbientNpc(scene: PixelScene, env: SceneEnv, timeMs: Long, facingOverride: Pair<String, Facing>? = null): PixelBuffer {
        val slots = scene.ambientNpcs(env)
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
