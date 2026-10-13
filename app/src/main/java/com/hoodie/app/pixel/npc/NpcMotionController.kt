package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Item
import kotlin.math.abs

/**
 * Onde o NPC está e o que faz em cada instante. Tudo é função de (cena, seed, tempo):
 * o comportamento nunca muda erraticamente entre quadros.
 */
object NpcMotionController {
    private const val SCENE_WIDTH = 240

    fun movement(slot: AmbientNpcSlot, timeMs: Long): NpcMovement {
        slot.shoppingBrain?.let { return it.movementAt(timeMs) }
        slot.officeBrain?.let { return it.movementAt(timeMs) }
        slot.restaurantBrain?.let { return it.movementAt(timeMs) }
        slot.leisureActor?.let { return it.movementAt(timeMs) }
        slot.venueActor?.let { return it.movementAt(timeMs) }
        val profile = slot.definition.behaviorProfile
        val path = slot.path ?: return stationary(slot, timeMs)
        val turns = turnFlags(path)
        val duration = pathCycleMs(path)
        val cycle = duration.coerceAtLeast(1L)
        var elapsed = if (path.repeat) Math.floorMod(timeMs + slot.seed * 977L, cycle) else timeMs.coerceIn(0, cycle - 1)
        var walked = 0f
        val first = path.points.first()
        if (elapsed < first.holdMs) {
            return stop(first, true, elapsed, walked, slot, timeMs)
        }
        elapsed -= first.holdMs
        for ((i, pair) in path.points.zipWithNext().withIndex()) {
            val (from, to) = pair
            val right = to.x >= from.x
            if (turns[i]) {
                if (elapsed < NpcPoseLibrary.TURN_MS) {
                    return NpcMovement(
                        from.x, from.floorY, facingRight = if (elapsed < NpcPoseLibrary.TURN_MS / 3) !right else right,
                        animation = if (right) NpcAnimation.TURN_RIGHT else NpcAnimation.TURN_LEFT,
                        localTimeMs = elapsed, walkedPx = walked, phase = PathPhase.TURN,
                    )
                }
                elapsed -= NpcPoseLibrary.TURN_MS
            }
            val travel = travelTime(from, to, path.millisPerPixel)
            val dist = distance(from, to).toFloat()
            if (elapsed < travel) {
                val t = elapsed.toFloat() / travel
                val phase = when {
                    offscreen(from.x) -> PathPhase.ENTER
                    offscreen(to.x) -> PathPhase.EXIT
                    else -> PathPhase.WALK
                }
                return NpcMovement(
                    (from.x + (to.x - from.x) * t).toInt(),
                    (from.floorY + (to.floorY - from.floorY) * t).toInt(),
                    facingRight = right, animation = NpcAnimation.WALK, localTimeMs = elapsed,
                    walkedPx = walked + dist * t, phase = phase,
                )
            }
            elapsed -= travel
            walked += dist
            if (elapsed < to.holdMs) return stop(to, right, elapsed, walked, slot, timeMs)
            elapsed -= to.holdMs
        }
        val last = path.points.last()
        return NpcMovement(last.x, last.floorY, facingRight = first.x >= last.x, animation = last.stop ?: NpcAnimation.IDLE, localTimeMs = elapsed, walkedPx = walked)
    }

    /** Parada num ponto do caminho: a animação da parada (ou reação rara). */
    private fun stop(point: NpcPathPoint, right: Boolean, local: Long, walked: Float, slot: AmbientNpcSlot, timeMs: Long): NpcMovement {
        val animation = point.stop ?: NpcAnimation.IDLE
        val reaction = if (slot.definition.behaviorProfile.reactions && animation == NpcAnimation.IDLE) NpcReactions.at(slot.seed, timeMs) else null
        return NpcMovement(
            point.x, point.floorY, right,
            if (reaction != null) NpcAnimation.REACTION else animation,
            reaction?.second ?: local, walked, PathPhase.STOP, point.seated, reaction = reaction?.first,
        )
    }

    /** NPC sem caminho: sequência de comportamento (ou a animação principal) + reações raras. */
    private fun stationary(slot: AmbientNpcSlot, timeMs: Long): NpcMovement {
        val profile = slot.definition.behaviorProfile
        val sequence = profile.sequence
        if (sequence == null) {
            val local = Math.floorMod(timeMs + slot.seed * 733L, 12_000L)
            return NpcMovement(
                slot.x, slot.floorY, facingRight = false, profile.animation, local,
                walkedPx = local / profile.motion.msPerPixel.toFloat(),
            )
        }
        val (step, local) = sequence.at(timeMs, slot.seed)
        val reactable = profile.reactions && step.animation != NpcAnimation.SIT_SLEEP && step.animation != NpcAnimation.WALK &&
            step.animation != NpcAnimation.SIT_HEAD_DROP
        val reaction = step.reaction?.let { it to local } ?: if (reactable) NpcReactions.at(slot.seed, timeMs) else null
        return NpcMovement(
            slot.x, slot.floorY, facingRight = slot.facingRight,
            animation = if (reaction != null) NpcAnimation.REACTION else step.animation,
            localTimeMs = reaction?.second ?: local,
            walkedPx = local / profile.motion.msPerPixel.toFloat(),
            seated = step.seated, facing = step.facing, reaction = reaction?.first,
        )
    }

    /** Quadro completo (pose + movimento secundário) do NPC neste instante. */
    fun frame(slot: AmbientNpcSlot, timeMs: Long, movement: NpcMovement = movement(slot, timeMs)): NpcFrame {
        val result = NpcPoseLibrary.frame(
            movement.animation, movement.localTimeMs, slot.seed, slot.definition.behaviorProfile.motion,
            walkedPx = movement.walkedPx, scale = slot.scale, seated = movement.seated,
            facing = movement.facing, reaction = movement.reaction,
        )
        if (movement.shoppingProductHeld) return result.copy(pose = result.pose.copy(
            item = Item.PRODUCT, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_DOWN,
        ))
        return carrying(result, movement)
    }

    /**
     * Objeto carregado entre ações: a pose da ação (beber, comer, pipoca) já traz o dela; nas
     * outras (andar, esperar) a mão segura o objeto — sacola e mala pendem, o resto vai no peito.
     */
    private fun carrying(frame: NpcFrame, movement: NpcMovement): NpcFrame {
        val item = movement.heldItem
        // Ler a folha recebida: a pose de leitura mostra a mesma folha, não a pasta dela (objeto único).
        if (item == Item.DOCUMENT && movement.animation == NpcAnimation.READ_DOCUMENT) return frame.copy(pose = frame.pose.copy(item = item))
        if (item == Item.NONE || frame.pose.item != Item.NONE) return frame
        // Entrega: o objeto sai da mão no meio do gesto.
        if (movement.animation == NpcAnimation.SERVE) return if (movement.localTimeMs < SERVE_RELEASE_MS) frame.copy(pose = frame.pose.copy(item = item)) else frame
        // Poses que já mexem o braço (falar, preparar, reagir) só ganham o objeto na mão.
        if (movement.animation !in CARRY_POSES) return frame.copy(pose = frame.pose.copy(item = item))
        val low = item in LOW_CARRY
        return frame.copy(pose = frame.pose.copy(item = item, rightArm = if (low) Arm.FORWARD_DOWN else Arm.HOLD_CHEST))
    }

    /** Objetos levados com o braço estendido para baixo (não no peito). */
    private val LOW_CARRY = setOf(Item.SHOPPING_BAG, Item.BASKET, Item.SUITCASE)

    /** Poses neutras em que a mão passa a segurar o objeto carregado. */
    private val CARRY_POSES = setOf(
        NpcAnimation.WALK, NpcAnimation.ENTER, NpcAnimation.EXIT, NpcAnimation.IDLE, NpcAnimation.LOOK, NpcAnimation.LOOK_WINDOW,
        NpcAnimation.STAND, NpcAnimation.CHECKOUT_WAIT, NpcAnimation.SIT_LOOK, NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT,
    )
    private const val SERVE_RELEASE_MS = 650L

    /** Pose isolada (Pixel Lab, testes): mesma biblioteca usada na cena. */
    fun pose(animation: NpcAnimation, elapsedMs: Long, seed: Int, motion: SpeciesMotionProfile): CharacterPose =
        NpcPoseLibrary.frame(animation, elapsedMs, seed, motion).pose

    private fun turnFlags(path: NpcPath): List<Boolean> {
        val segs = path.points.zipWithNext()
        return segs.mapIndexed { i, (a, b) ->
            // Caminho que se repete: o primeiro trecho continua o último (vira na volta também).
            if (i == 0 && !(path.repeat && segs.size > 1 && segs.last().second.x == a.x)) false else {
                val (pa, pb) = if (i == 0) segs.last() else segs[i - 1]
                val prevRight = pb.x >= pa.x; val right = b.x >= a.x
                prevRight != right && a.x != b.x && pa.x != pb.x && !offscreen(a.x)
            }
        }
    }

    private fun offscreen(x: Int) = x < 0 || x >= SCENE_WIDTH

    private fun segmentTime(a: NpcPathPoint, b: NpcPathPoint, millisPerPixel: Long, turn: Boolean) =
        travelTime(a, b, millisPerPixel) + if (turn) NpcPoseLibrary.TURN_MS else 0L

    private fun distance(a: NpcPathPoint, b: NpcPathPoint) = maxOf(abs(b.x - a.x), abs(b.floorY - a.floorY))

    private fun travelTime(a: NpcPathPoint, b: NpcPathPoint, millisPerPixel: Long) =
        (distance(a, b) * millisPerPixel).coerceAtLeast(1)

    /** Largura útil para checar se o NPC está visível (pés dentro da cena). */
    fun visible(movement: NpcMovement) = movement.x in -CharacterCanvas.WIDTH..SCENE_WIDTH + CharacterCanvas.WIDTH

    /** Duração determinística de uma volta completa, incluindo pausas e viradas. */
    internal fun pathCycleMs(path: NpcPath): Long {
        val turns = turnFlags(path)
        return path.points.first().holdMs + path.points.zipWithNext().withIndex()
            .fold(0L) { total, (i, pair) -> total + segmentTime(pair.first, pair.second, path.millisPerPixel, turns[i]) + pair.second.holdMs }
            .coerceAtLeast(1L)
    }
}
