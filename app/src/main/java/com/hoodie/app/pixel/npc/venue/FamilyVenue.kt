package com.hoodie.app.pixel.npc.venue

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Facing

/** Sala da família: só a fala do Hoodie (os parentes falam pelos próprios balões). */
data class FamilyAmbient(
    override val timeMs: Long,
    /** Fala do Hoodie dentro de uma conversa com a família (balão sobre ele, se estiver no sofá). */
    val hoodieSpeech: VenueSpeechEvent?,
) : VenueAmbientState {
    override val scene: SceneId get() = SceneId.FAMILY
}

/**
 * Família do Hoodie na sala (FamilyScene): o pai lê e descansa na poltrona, a mãe fica no sofá ao
 * lado do Hoodie e o irmão caçula anda pela sala (TV, tapete, petiscos) e às vezes sai e volta.
 * De tempos em tempos eles conversam — entre si e com o Hoodie, que responde num balão próprio.
 *
 * Falas: até 22 caracteres, sem acento, `?` ou `!` (o balão em pixel só desenha A-Z, 0-9, `,` e `.`).
 */
internal object FamilyVenue : VenueBlueprint(SceneId.FAMILY) {
    const val DOOR = "door"
    const val ENTRY = "entry"
    const val TV_SPOT = "tv"
    const val RIGHT = "right"
    const val BOTTOM_R = "bottom_r"
    const val TABLE = "table"
    const val BOTTOM_L = "bottom_l"
    const val RUG = "rug"
    const val LEFT = "left"
    const val ARMCHAIR = "armchair"
    const val SOFA_FRONT = "sofa_front"
    const val SOFA_MOM = "sofa_mom"
    /** Onde o irmão para para falar com a mãe e o Hoodie (ao lado do sofá, sem tapar ninguém). */
    const val BY_SOFA = "by_sofa"

    /** Lugar do Hoodie no sofá (FamilyScene.FAMILY_SOFA), ao lado da mãe. */
    const val HOODIE_SOFA_X = 168
    const val SOFA_Y = 202
    const val MOM_SOFA_X = 132
    const val ARMCHAIR_X = 18
    const val ARMCHAIR_Y = 204

    /** Intervalos do coordenador com a fala do Hoodie (o texto vai no tag). */
    const val HOODIE_SPEECH = "hoodie_speech"

    private val graph = VenueNavigationGraph(
        "família",
        spots = listOf(
            VenueSpot(DOOR, 214, 158, capacity = 4, offstage = true),
            VenueSpot(ENTRY, 214, 178, capacity = 2),
            VenueSpot(TV_SPOT, 216, 204, interaction = "TV"),
            VenueSpot(RIGHT, 212, 232, capacity = 2),
            VenueSpot(BOTTOM_R, 190, 282, capacity = 2),
            VenueSpot(TABLE, 150, 280, interaction = "petiscos"),
            VenueSpot(BOTTOM_L, 40, 284, capacity = 2),
            VenueSpot(RUG, 56, 252, interaction = "tapete"),
            VenueSpot(LEFT, 28, 232, capacity = 2),
            VenueSpot(ARMCHAIR, ARMCHAIR_X, ARMCHAIR_Y, interaction = "poltrona"),
            VenueSpot(SOFA_FRONT, 128, 216, capacity = 2),
            VenueSpot(SOFA_MOM, MOM_SOFA_X, SOFA_Y, interaction = "sofá"),
            VenueSpot(BY_SOFA, 94, 222, interaction = "conversa no sofá"),
        ),
        edges = listOf(
            DOOR to ENTRY, ENTRY to TV_SPOT, TV_SPOT to RIGHT, RIGHT to BOTTOM_R, BOTTOM_R to TABLE,
            TABLE to BOTTOM_L, BOTTOM_L to RUG, RUG to LEFT, LEFT to ARMCHAIR,
            LEFT to SOFA_FRONT, SOFA_FRONT to RIGHT, SOFA_FRONT to SOFA_MOM, LEFT to BY_SOFA, BY_SOFA to SOFA_FRONT,
        ),
        obstacles = listOf(
            VenueObstacle("parede", 0, 0, 239, 142),
            VenueObstacle("sofá", 104, 160, 204, 200),
            VenueObstacle("poltrona", -10, 150, 46, 200),
            VenueObstacle("mesinha lateral", 48, 196, 64, 210),
            VenueObstacle("luminária", 96, 178, 104, 196),
            VenueObstacle("mesa de centro", 76, 226, 172, 262),
            VenueObstacle("planta", 212, 286, 236, 304),
        ),
    )

    override fun graph(variant: Int) = graph
    override fun hoodieSpots(variant: Int): Set<String> = emptySet()

    override fun actors(key: VenueSessionKey): List<VenueActorSpec> = listOf(
        VenueActorSpec(
            NpcCharacterRegistry.FAMILY_DAD,
            VenueNpcProfile(DAD_ID, VenueRole.FAMILY_DAD,
                mapOf(VenueAction.READ to 35, VenueAction.REST to 30, VenueAction.PHONE to 20, VenueAction.CONSUME to 15), 0.45f, 4_000, 8_000),
            slotSeed = 901, start = VenueStart.INSIDE, startSpot = ARMCHAIR,
        ),
        VenueActorSpec(
            NpcCharacterRegistry.FAMILY_MOM,
            VenueNpcProfile(MOM_ID, VenueRole.FAMILY_MOM,
                mapOf(VenueAction.REST to 35, VenueAction.PHONE to 20, VenueAction.CONSUME to 25, VenueAction.OBSERVE to 20), 0.5f, 3_000, 7_000),
            slotSeed = 907, start = VenueStart.INSIDE, startSpot = SOFA_MOM,
        ),
        VenueActorSpec(
            NpcCharacterRegistry.FAMILY_BROTHER,
            VenueNpcProfile(BROTHER_ID, VenueRole.FAMILY_BROTHER,
                mapOf(VenueAction.STROLL to 30, VenueAction.OBSERVE to 25, VenueAction.CONSUME to 20, VenueAction.REST to 15, VenueAction.EXIT to 10), 0.55f, 2_000, 4_500),
            slotSeed = 911, start = VenueStart.INSIDE, startSpot = RUG,
        ),
    )

    override fun initialize(s: VenueNpcSession, c: VenueCursor) {
        super.initialize(s, c)
        // Pai e mãe já estão sentados quando a cena abre.
        if (c.role != VenueRole.FAMILY_BROTHER) { c.seated = true; c.front = true }
    }

    override fun step(s: VenueNpcSession, c: VenueCursor) {
        when {
            c.offstage -> s.enter(c, DOOR, ENTRY, VenueAction.STROLL)
            talk(s, c) -> Unit
            c.role == VenueRole.FAMILY_BROTHER -> brother(s, c)
            else -> parent(s, c, if (c.role == VenueRole.FAMILY_DAD) ARMCHAIR else SOFA_MOM)
        }
        c.decisions++
    }

    // ───── Pai e mãe: sentados, com uma ida à mesinha de vez em quando ─────

    private fun parent(s: VenueNpcSession, c: VenueCursor, seat: String) {
        if (c.node != seat || !c.seated) {
            s.endWait(c)
            s.walkTo(c, seat, VenueNpcState.NAVIGATING, VenueAction.REST)
            s.sit(c, VenueAction.REST)
            return
        }
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val available = buildSet {
            add(VenueAction.REST); add(VenueAction.PHONE); add(VenueAction.READ)
            if (s.isFree(c, TABLE, c.time, c.time + 12_000)) add(VenueAction.CONSUME)
            if (c.role == VenueRole.FAMILY_MOM && s.isFree(c, TV_SPOT, c.time, c.time + 12_000)) add(VenueAction.OBSERVE)
        }
        val action = s.choose(c, available) ?: VenueAction.REST
        c.remember(action)
        when (action) {
            VenueAction.CONSUME -> visit(s, c, TABLE, VenueAction.CONSUME) {
                s.stay(c, PICK_MS, VenueNpcState.PERFORMING_ACTION, VenueAction.CONSUME, NpcAnimation.STAND_PICK_PRODUCT)
                s.stay(c, 1_400, VenueNpcState.PERFORMING_ACTION, VenueAction.CONSUME, NpcAnimation.LOOK)
            }
            VenueAction.OBSERVE -> visit(s, c, TV_SPOT, VenueAction.OBSERVE) {
                s.stay(c, 2_600, VenueNpcState.PERFORMING_ACTION, VenueAction.OBSERVE, NpcAnimation.LOOK_WINDOW)
            }
            else -> {
                val night = s.key.night
                val anim = when (action) {
                    VenueAction.READ -> NpcAnimation.SIT_READ_MENU
                    VenueAction.PHONE -> NpcAnimation.SIT_PHONE
                    // O pai cochila na poltrona às vezes, à noite.
                    else -> if (night && c.role == VenueRole.FAMILY_DAD && s.rand(c, 19) < 0.4f) NpcAnimation.SIT_SLEEP else NpcAnimation.SIT_LOOK
                }
                s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, action, anim, Facing.FRONT)
            }
        }
    }

    /** Levanta, vai até [spot], faz [act] e volta a sentar no lugar de antes. */
    private inline fun visit(s: VenueNpcSession, c: VenueCursor, spot: String, action: VenueAction, act: () -> Unit) {
        val seat = c.node ?: return
        s.reserveFor(c, spot, 4_000)
        s.walkTo(c, spot, VenueNpcState.NAVIGATING, action)
        act()
        c.visited(spot)
        s.walkTo(c, seat, VenueNpcState.NAVIGATING, VenueAction.REST)
        s.sit(c, VenueAction.REST)
    }

    // ───── Irmão: anda pela sala, vê TV, pega biscoito, brinca no tapete, sai e volta ─────

    private fun brother(s: VenueNpcSession, c: VenueCursor) {
        val hold = VenueNpcBrain.idleMs(c.profile, s.seed, c.decisions)
        val action = s.choose(c, setOf(VenueAction.STROLL, VenueAction.OBSERVE, VenueAction.CONSUME, VenueAction.REST, VenueAction.EXIT))
            ?: VenueAction.STROLL
        c.remember(action)
        if (action == VenueAction.EXIT) {
            s.endWait(c)
            s.leave(c, DOOR, 18_000 + (s.rand(c, 43) * 24_000).toLong())
            return
        }
        val options = when (action) {
            VenueAction.OBSERVE -> listOf(TV_SPOT)
            VenueAction.CONSUME -> listOf(TABLE)
            VenueAction.REST -> listOf(RUG)
            else -> listOf(BOTTOM_L, BOTTOM_R, RIGHT, LEFT)
        }
        val spot = s.chooseFree(c, options, hold + 1_500)
        if (spot == null) { s.waitSlice(c, action, NpcAnimation.LOOK); return }
        s.endWait(c)
        s.reserveFor(c, spot, hold + 1_500)
        s.walkTo(c, spot, VenueNpcState.NAVIGATING, action)
        when (action) {
            VenueAction.OBSERVE -> s.stay(c, hold + 1_500, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.LOOK_WINDOW)
            VenueAction.CONSUME -> {
                s.stay(c, PICK_MS, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.STAND_PICK_PRODUCT)
                s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.IDLE)
            }
            VenueAction.REST -> {
                s.stay(c, 1_400, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.STRETCH)
                s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.LOOK)
            }
            else -> s.stay(c, hold, VenueNpcState.PERFORMING_ACTION, action, NpcAnimation.LOOK)
        }
        c.visited(spot)
    }

    // ───── Conversas ─────

    internal enum class Who { DAD, MOM, BROTHER, HOODIE }

    /** Uma conversa curta: quem fala o quê, em ordem. O irmão vai até [brotherSpot] para falar. */
    internal data class Talk(val lines: List<Pair<Who, String>>, val brotherSpot: String? = null) {
        val cast: Set<Who> get() = lines.map { it.first }.filter { it != Who.HOODIE }.toSet()
    }

    internal val TALKS = listOf(
        Talk(listOf(Who.MOM to "Jantar fica pronto ja", Who.DAD to "Que cheiro bom", Who.MOM to "Fiz aquela sopa")),
        Talk(listOf(Who.DAD to "E o trabalho, filho", Who.HOODIE to "Tudo indo bem, pai", Who.DAD to "Que orgulho")),
        Talk(listOf(Who.MOM to "Esta comendo direito", Who.HOODIE to "Estou sim, mae", Who.MOM to "Leva um bolo depois")),
        Talk(listOf(Who.BROTHER to "Mae, posso um biscoito", Who.MOM to "So um, ta bom", Who.BROTHER to "Valeu"), brotherSpot = BY_SOFA),
        Talk(listOf(Who.BROTHER to "Joga comigo depois", Who.HOODIE to "Claro, maninho", Who.BROTHER to "Eba, vou buscar"), brotherSpot = BY_SOFA),
        Talk(listOf(Who.DAD to "Ja fez a licao", Who.BROTHER to "Quase terminando", Who.DAD to "Muito bem"), brotherSpot = LEFT),
        Talk(listOf(Who.MOM to "Que saudade de todos", Who.DAD to "Casa cheia de novo", Who.BROTHER to "Adoro quando vem aqui"), brotherSpot = BY_SOFA),
        Talk(listOf(Who.DAD to "Viu o jogo ontem", Who.HOODIE to "Vi sim, que jogo", Who.DAD to "Foi demais")),
    )

    private fun who(c: VenueCursor) = when (c.role) {
        VenueRole.FAMILY_DAD -> Who.DAD
        VenueRole.FAMILY_MOM -> Who.MOM
        else -> Who.BROTHER
    }

    private fun cursor(s: VenueNpcSession, w: Who): VenueCursor? = s.allCursors().firstOrNull { who(it) == w }

    /** Pronto para conversar: pais sentados no lugar deles, irmão dentro da sala parado num ponto. */
    private fun ready(s: VenueNpcSession, c: VenueCursor, starter: VenueCursor): Boolean {
        if (c.offstage || c.node == null) return false
        if ((c.readyAt[VenueAction.SOCIAL] ?: 0L) > starter.time) return false
        if (c.time - starter.time > MAX_WAIT_FOR_PARTNER_MS) return false
        return when (c.role) {
            VenueRole.FAMILY_DAD -> c.seated && c.node == ARMCHAIR
            VenueRole.FAMILY_MOM -> c.seated && c.node == SOFA_MOM
            else -> true
        }
    }

    /** Tenta começar uma conversa da qual [c] participa. true = planejou a conversa. */
    private fun talk(s: VenueNpcSession, c: VenueCursor): Boolean {
        if (s.rand(c, 71) >= c.profile.socialProbability) return false
        val me = who(c)
        val options = TALKS.filter { t ->
            me in t.cast && t.cast.all { w -> cursor(s, w)?.let { ready(s, it, c) } == true } &&
                (t.brotherSpot == null || Who.BROTHER !in t.cast || s.isFree(cursor(s, Who.BROTHER)!!, t.brotherSpot, c.time, c.time + 20_000))
        }
        if (options.isEmpty()) return false
        val chosen = options[Math.floorMod(s.seed + c.decisions.toInt() * 7 + c.spec.slotSeed, options.size)]
        play(s, chosen)
        return true
    }

    private fun play(s: VenueNpcSession, talk: Talk) {
        val cast = talk.cast.map { cursor(s, it)!! }
        val brother = cursor(s, Who.BROTHER)?.takeIf { Who.BROTHER in talk.cast }
        if (brother != null && talk.brotherSpot != null) {
            s.endWait(brother)
            val talkSpot = talk.brotherSpot
            s.reserveFor(brother, talkSpot, 12_000)
            s.walkTo(brother, talkSpot, VenueNpcState.NAVIGATING, VenueAction.SOCIAL)
            // Vira para quem vai ouvir (o primeiro dos pais na conversa; senão, o Hoodie no sofá).
            val target = cast.firstOrNull { it !== brother }?.x ?: HOODIE_SOFA_X
            s.turnSide(brother, target > brother.x, VenueNpcState.INTERACTING, VenueAction.SOCIAL)
        }
        val talkMs = talk.lines.sumOf { s.lineMs(it.second) + VenueInteractionCoordinator.LINE_GAP_MS }
        val meet = s.coordinator.firstSpeechFreeFrom(cast.maxOf { it.time } + 300, talkMs)
        if (cast.size >= 2) s.coordinator.register(cast[0].npcId, cast[1].npcId, meet, meet + talkMs)
        cast.forEach { c -> if (c.time < meet) listen(s, c, meet - c.time) }
        for ((speaker, text) in talk.lines) {
            val ms = s.lineMs(text)
            val start = cast.first().time
            s.coordinator.addSpeech(start, start + ms)
            if (speaker == Who.HOODIE) s.coordinator.addInterval(HOODIE_SPEECH, start, start + ms, text)
            cast.forEach { c ->
                if (who(c) == speaker) {
                    s.stay(c, ms, VenueNpcState.INTERACTING, VenueAction.SOCIAL, NpcAnimation.TALK,
                        if (c.seated) Facing.FRONT else Facing.SIDE, speech = text, seated = c.seated)
                } else listen(s, c, ms)
            }
            cast.forEach { c -> listen(s, c, VenueInteractionCoordinator.LINE_GAP_MS) }
        }
        val sync = cast.maxOf { it.time }
        cast.forEach { c ->
            if (c.time < sync) listen(s, c, sync - c.time)
            c.readyAt[VenueAction.SOCIAL] = c.time + TALK_COOLDOWN_MS + (s.rand(c, 83) * TALK_COOLDOWN_SPREAD_MS).toLong()
            c.remember(VenueAction.SOCIAL)
        }
    }

    private fun listen(s: VenueNpcSession, c: VenueCursor, ms: Long) =
        s.stay(c, ms, VenueNpcState.INTERACTING, VenueAction.SOCIAL, if (c.seated) NpcAnimation.SIT_LOOK else NpcAnimation.IDLE,
            if (c.seated) Facing.FRONT else Facing.SIDE, seated = c.seated)

    override fun ambient(s: VenueNpcSession, t: Long): FamilyAmbient {
        val line = s.coordinator.intervals(HOODIE_SPEECH).firstOrNull { t >= it.start && t < it.end }
        return FamilyAmbient(t, line?.let { VenueSpeechEvent(it.tag.orEmpty(), it.start, it.end - it.start) })
    }

    /** O Hoodie está sentado no lugar dele do sofá (só então a fala dele aparece). */
    fun hoodieOnSofa(x: Int, y: Int): Boolean = kotlin.math.abs(x - HOODIE_SOFA_X) <= 3 && kotlin.math.abs(y - SOFA_Y) <= 3

    const val DAD_ID = "family_dad"
    const val MOM_ID = "family_mom"
    const val BROTHER_ID = "family_brother"
    private const val PICK_MS = 900L
    private const val MAX_WAIT_FOR_PARTNER_MS = 4_000L
    private const val TALK_COOLDOWN_MS = 30_000L
    private const val TALK_COOLDOWN_SPREAD_MS = 30_000L
}
