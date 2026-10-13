package com.hoodie.app.pixel.npc.events

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.NpcReaction
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import kotlin.math.abs
import kotlin.math.max

/** Um trecho do roteiro de um personagem num microevento (parado, andando ou virando). */
data class StorySegment(
    val start: Long,
    val end: Long,
    val fromX: Int, val fromY: Int, val toX: Int, val toY: Int,
    val animation: NpcAnimation,
    val facing: Facing?,
    val facingRight: Boolean,
    val seated: Boolean,
    val item: Item,
    val phase: PathPhase,
    val walkedStart: Float,
    val reaction: NpcReaction? = null,
    val speech: String? = null,
    /** Fora de cena (o ajudante antes de entrar e depois de sair). */
    val hidden: Boolean = false,
    /** Continua o relógio da pose de um trecho anterior (um gesto dividido em dois trechos). */
    val localOffsetMs: Long = 0L,
)

/** Roteiro contínuo de um personagem: posição sempre interpolada, nunca um salto entre quadros. */
class StoryTrack(val actorId: String, val segments: List<StorySegment>) {
    val start: Long get() = segments.first().start
    val end: Long get() = segments.last().end

    fun segmentAt(t: Long): StorySegment? = segments.firstOrNull { t >= it.start && t < it.end }

    fun movementAt(t: Long): NpcMovement? {
        val seg = segmentAt(t) ?: return null
        val local = t - seg.start
        val f = (local.toFloat() / (seg.end - seg.start).coerceAtLeast(1)).coerceIn(0f, 1f)
        val distance = max(abs(seg.toX - seg.fromX), abs(seg.toY - seg.fromY))
        val turning = seg.animation == NpcAnimation.TURN_LEFT || seg.animation == NpcAnimation.TURN_RIGHT
        return NpcMovement(
            seg.fromX + ((seg.toX - seg.fromX) * f).toInt(), seg.fromY + ((seg.toY - seg.fromY) * f).toInt(),
            if (turning && seg.facing == null && local < NpcPoseLibrary.TURN_MS / 3) !seg.facingRight else seg.facingRight,
            seg.animation, local + seg.localOffsetMs, seg.walkedStart + distance * f, seg.phase, seg.seated, seg.facing,
            reaction = seg.reaction, heldItem = seg.item,
        )
    }

    fun hiddenAt(t: Long): Boolean = segmentAt(t)?.hidden == true

    /** Fala do trecho atual: (linha, início, duração). */
    fun speechAt(t: Long): Triple<String, Long, Long>? = segmentAt(t)?.let { s -> s.speech?.let { Triple(it, s.start, s.end - s.start) } }
}

/**
 * Monta um [StoryTrack] passo a passo, como um roteiro de palco: parar, virar, andar. Mudar de lado
 * sempre passa por uma virada animada; sair de frente (sentado, balcão) também.
 */
class StoryTrackBuilder(
    private val actorId: String,
    start: Long,
    private var x: Int,
    private var y: Int,
    private var facingRight: Boolean,
    private var front: Boolean = false,
    private var seated: Boolean = false,
    private val msPerPixel: Long = 24L,
) {
    var time: Long = start
        private set
    private var walked = 0f
    private val segments = mutableListOf<StorySegment>()

    val currentX: Int get() = x
    val currentY: Int get() = y

    fun stay(
        ms: Long, animation: NpcAnimation, facing: Facing? = if (front) Facing.FRONT else Facing.SIDE,
        item: Item = Item.NONE, reaction: NpcReaction? = null, speech: String? = null, seated: Boolean = this.seated,
        hidden: Boolean = false, faceRight: Boolean = facingRight, localOffsetMs: Long = 0L,
    ): StoryTrackBuilder {
        if (ms <= 0) return this
        segments += StorySegment(time, time + ms, x, y, x, y, animation, facing, faceRight, seated, item,
            if (hidden) PathPhase.IDLE else PathPhase.STOP, walked, reaction, speech, hidden, localOffsetMs)
        time += ms
        facingRight = faceRight
        front = facing == Facing.FRONT
        this.seated = seated
        return this
    }

    /**
     * Entrega com o gesto SERVE: o objeto sai da mão no meio do gesto ([releaseMs]); a pose continua
     * contínua, mas a partir dali a mão está vazia. Devolve o instante em que o objeto foi solto.
     */
    fun serve(ms: Long, releaseMs: Long, item: Item, facing: Facing? = if (front) Facing.FRONT else Facing.SIDE): Long {
        stay(releaseMs, NpcAnimation.SERVE, facing, item)
        val released = time
        stay(ms - releaseMs, NpcAnimation.SERVE, facing, localOffsetMs = releaseMs)
        return released
    }

    /** Fica fora de cena (sem desenho) até [until]. */
    fun hiddenUntil(until: Long): StoryTrackBuilder = stay(until - time, NpcAnimation.IDLE, Facing.SIDE, hidden = true)

    fun waitUntil(until: Long, animation: NpcAnimation, facing: Facing? = if (front) Facing.FRONT else Facing.SIDE, item: Item = Item.NONE): StoryTrackBuilder =
        stay(until - time, animation, facing, item)

    fun standUp(item: Item = Item.NONE): StoryTrackBuilder {
        if (!seated) return this
        stay(STAND_MS, NpcAnimation.STAND_UP, Facing.FRONT, item, seated = false)
        return this
    }

    fun sitDown(item: Item = Item.NONE): StoryTrackBuilder {
        if (seated) return this
        if (!front) turnFront(item)
        stay(SIT_MS, NpcAnimation.SIT, Facing.FRONT, item, seated = true)
        return this
    }

    fun turnFront(item: Item = Item.NONE): StoryTrackBuilder {
        if (front) return this
        segments += StorySegment(time, time + NpcPoseLibrary.TURN_MS, x, y, x, y, NpcAnimation.TURN_LEFT, Facing.FRONT, facingRight,
            false, item, PathPhase.TURN, walked)
        time += NpcPoseLibrary.TURN_MS
        front = true
        return this
    }

    fun turnSide(right: Boolean, item: Item = Item.NONE): StoryTrackBuilder {
        if (!front && facingRight == right) return this
        segments += StorySegment(time, time + NpcPoseLibrary.TURN_MS, x, y, x, y,
            if (right) NpcAnimation.TURN_RIGHT else NpcAnimation.TURN_LEFT, null, right, false, item, PathPhase.TURN, walked)
        time += NpcPoseLibrary.TURN_MS
        facingRight = right
        front = false
        return this
    }

    /** Anda em linha reta (o chamador garante um trecho livre de obstáculos). */
    fun walkTo(tx: Int, ty: Int, item: Item = Item.NONE, phase: PathPhase = PathPhase.WALK): StoryTrackBuilder {
        if (tx == x && ty == y) return this
        standUp(item)
        val right = if (tx != x) tx > x else facingRight
        turnSide(right, item)
        val distance = max(abs(tx - x), abs(ty - y))
        segments += StorySegment(time, time + distance * msPerPixel, x, y, tx, ty, NpcAnimation.WALK, null, right, false, item, phase, walked)
        time += distance * msPerPixel
        walked += distance
        x = tx; y = ty
        return this
    }

    /** Anda por uma lista de pontos. */
    fun walkPath(points: List<Pair<Int, Int>>, item: Item = Item.NONE, lastPhase: PathPhase = PathPhase.WALK): StoryTrackBuilder {
        points.forEachIndexed { i, (px, py) -> walkTo(px, py, item, if (i == points.lastIndex) lastPhase else PathPhase.WALK) }
        return this
    }

    fun build(): StoryTrack = StoryTrack(actorId, segments.toList())

    companion object {
        const val STAND_MS = 640L
        const val SIT_MS = 640L
    }
}

/** Onde um objeto da história está a partir de [at] (posição opcional para desenhá-lo no chão/mesa). */
data class StoryObjectStep(val at: Long, val holder: StoryObjectHolder, val x: Int? = null, val y: Int? = null)

data class StoryObjectTimeline(val objectId: String, val item: Item, val steps: List<StoryObjectStep>) {
    init { require(steps.zipWithNext().all { (a, b) -> a.at <= b.at }) { "passos fora de ordem" } }
    fun stateAt(t: Long): StoryObjectState? = steps.lastOrNull { it.at <= t }?.let { StoryObjectState(objectId, it.holder, item, it.x, it.y) }
}

/** Um microevento planejado por inteiro: evento, roteiro de cada participante e objetos. */
data class ScriptedStory(
    val event: SceneStoryEvent,
    val tracks: List<StoryTrack>,
    val objects: List<StoryObjectTimeline> = emptyList(),
    /** O estado dos objetos continua valendo um pouco depois do fim (a mesa limpa, a sacola na mão). */
    val lingerMs: Long = 0L,
) {
    fun track(actorId: String): StoryTrack? = tracks.firstOrNull { it.actorId == actorId }
    fun covers(t: Long): Boolean = t >= event.startAt && t < event.endsAt + lingerMs
    fun snapshotAt(t: Long): SceneStorySnapshot = SceneStorySnapshot(event, SceneEventDirector.phaseAt(event, t), objects.mapNotNull { it.stateAt(t) })
}

/** Cenas que sobrepõem microeventos ao comportamento normal dos NPCs (o roteiro tem a vez enquanto dura). */
interface SceneStoryOverlay : SceneStoryController {
    /** Movimento do personagem no evento (null = segue a própria rotina). */
    fun movementAt(npcId: String, timeMs: Long): NpcMovement?
    /** O personagem existe só para o evento e está fora de cena agora. */
    fun hiddenAt(npcId: String, timeMs: Long): Boolean = false
    fun speechAt(npcId: String, timeMs: Long): Triple<String, Long, Long>? = null
}

/**
 * Agenda de microeventos em janelas fixas: a janela k tenta um evento (o [planner] confere quem está
 * livre e devolve o roteiro, ou null). Planejado em ordem, com orçamento e cooldown do
 * [SceneEventDirector]; o mesmo tempo dá sempre o mesmo evento.
 */
open class PeriodicStoryOverlay(
    private val periodMs: Long,
    private val planner: (window: Long, accepted: List<SceneStoryEvent>) -> ScriptedStory?,
    private val actors: Set<String>,
    /** Personagens que só existem para os eventos (o garçom, o outro comprador): fora deles, fora de cena. */
    private val helpers: Set<String> = emptySet(),
) : SceneStoryOverlay {
    private val planned = ArrayList<ScriptedStory?>()
    private val accepted = ArrayList<SceneStoryEvent>()
    @Volatile private var disposed = false

    override fun canStart(type: SceneStoryEventType, at: Long): Boolean =
        !disposed && SceneEventDirector.budgetAllows(accepted, at) && SceneEventDirector.cooldownAllows(accepted, type, at)

    @Synchronized fun storyAt(timeMs: Long): ScriptedStory? {
        if (disposed || timeMs < 0) return null
        val window = timeMs / periodMs
        ensure(window)
        // Um evento pode passar do fim da janela dele: olha também a anterior.
        for (w in window downTo maxOf(0, window - 1)) planned[w.toInt()]?.takeIf { it.covers(timeMs) }?.let { return it }
        return null
    }

    @Synchronized fun storiesUntil(timeMs: Long): List<ScriptedStory> {
        ensure(timeMs / periodMs)
        return planned.filterNotNull().filter { it.event.startAt <= timeMs }
    }

    private fun ensure(window: Long) {
        while (planned.size <= window) {
            val story = planner(planned.size.toLong(), accepted.toList())
                ?.takeIf { canStart(it.event.type, it.event.startAt) }
            if (story != null) accepted += story.event
            planned += story
        }
    }

    override fun snapshotAt(timeMs: Long): SceneStorySnapshot? = storyAt(timeMs)?.snapshotAt(timeMs)

    override fun movementAt(npcId: String, timeMs: Long): NpcMovement? {
        if (npcId !in actors && npcId !in helpers) return null
        val story = storyAt(timeMs) ?: return null
        return story.track(npcId)?.takeIf { timeMs >= it.start && timeMs < it.end }?.movementAt(timeMs)
    }

    override fun hiddenAt(npcId: String, timeMs: Long): Boolean {
        if (npcId !in helpers) return false
        val track = storyAt(timeMs)?.track(npcId) ?: return true
        return timeMs < track.start || timeMs >= track.end || track.hiddenAt(timeMs)
    }

    override fun speechAt(npcId: String, timeMs: Long): Triple<String, Long, Long>? =
        storyAt(timeMs)?.track(npcId)?.speechAt(timeMs)

    @Synchronized override fun dispose() {
        disposed = true
        planned.clear(); accepted.clear()
    }
}
