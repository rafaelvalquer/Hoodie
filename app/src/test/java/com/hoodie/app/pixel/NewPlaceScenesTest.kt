package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.ReactionDirector
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneFlag
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.random.Random

/** Escola, Compras, Família e Passeio: cenas, microanimações, props dinâmicos e goldens. */
class NewPlaceScenesTest {

    private val newScenes = listOf(SceneId.SCHOOL, SceneId.SHOPPING, SceneId.FAMILY, SceneId.LEISURE)
    private val newGroups = listOf(AnimGroup.STUDY, AnimGroup.SHOPPING, AnimGroup.VISIT, AnimGroup.LEISURE)
    private val contextScene = mapOf(
        UserContextType.STUDY to SceneId.SCHOOL,
        UserContextType.SHOPPING to SceneId.SHOPPING,
        UserContextType.VISITING to SceneId.FAMILY,
        UserContextType.LEISURE to SceneId.LEISURE,
    )

    /** Todos os estados visuais possíveis (atividade × contexto × necessidades extremas). */
    private fun allVisuals(): List<VisualState> = buildList {
        HoodieActivity.entries.forEach { a ->
            UserContextType.entries.forEach { c ->
                listOf(10, 90).forEach { level ->
                    add(VisualDirector.resolve(a, c, energy = level, mood = level, social = level, hunger = 100 - level, focus = level))
                }
            }
        }
    }

    // ───── PlaceVisualCoverage ─────

    @Test
    fun `cada contexto novo abre a sua cena e os genericos ficam como fallback`() {
        contextScene.forEach { (ctx, scene) ->
            HoodieActivity.entries.forEach { a ->
                assertEquals("$a/$ctx", scene, VisualDirector.resolve(a, ctx).scene)
            }
        }
        assertEquals(SceneId.GENERIC_OUTDOOR, VisualDirector.resolve(HoodieActivity.WALKING, UserContextType.TRAVEL).scene)
        assertEquals(SceneId.UNKNOWN, VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.UNKNOWN).scene)
        // GENERIC_INDOOR não é mais destino de nenhum contexto.
        assertFalse(allVisuals().any { it.scene == SceneId.GENERIC_INDOOR })
        // GENERIC_OUTDOOR só para viagem ou caminhada a partir de casa.
        HoodieActivity.entries.forEach { a ->
            UserContextType.entries.filter { it != UserContextType.TRAVEL && it != UserContextType.HOME }.forEach { c ->
                assertNotEquals("$a/$c", SceneId.GENERIC_OUTDOOR, VisualDirector.resolve(a, c).scene)
            }
        }
    }

    @Test
    fun `atividade principal de cada lugar usa a cena e o spot proprios`() {
        assertEquals(SpotId.DESK, VisualDirector.resolve(HoodieActivity.STUDYING, UserContextType.STUDY).spot)
        assertEquals(SpotId.AISLE_A, VisualDirector.resolve(HoodieActivity.SHOPPING, UserContextType.SHOPPING).spot)
        assertEquals(SpotId.FAMILY_SOFA, VisualDirector.resolve(HoodieActivity.SOCIALIZING, UserContextType.VISITING).spot)
        assertEquals(SpotId.PATH_A, VisualDirector.resolve(HoodieActivity.SIGHTSEEING, UserContextType.LEISURE).spot)
    }

    @Test
    fun `cenas novas tem spots, props, porta e variantes`() {
        newScenes.forEach { id ->
            val s = SceneRegistry[id]
            assertTrue("$id spots", s.spots.size >= 3)
            assertTrue("$id props", s.props().size >= 3)
            assertTrue("$id defaultSpot", s.defaultSpot in s.spots)
            if (s.hasAnimatedDoor) assertTrue("$id porta sem spot", SpotId.DOOR in s.spots)
        }
        assertEquals(3, SceneRegistry[SceneId.LEISURE].backgroundVariants)
    }

    @Test
    fun `cada lugar novo tem pelo menos 4 microanimacoes proprias`() {
        val visuals = allVisuals()
        contextScene.forEach { (ctx, scene) ->
            val group = newGroups[newScenes.indexOf(scene)]
            val used = visuals.filter { it.scene == scene }
                .flatMap { v -> v.actions.map { it.anim } + v.approach + v.enter + v.exit }
                .filter { it.group == group }.toSet() +
                (if (scene == SceneId.LEISURE) setOf(VisualDirector.locomotion(scene, false)) else emptySet())
            assertTrue("$ctx usa só $used", used.size >= 4)
        }
    }

    @Test
    fun `todas as animacoes novas sao alcancaveis pelo diretor`() {
        val reachable = allVisuals().flatMap { v -> v.actions.flatMap { listOf(it.anim) + it.enter + it.exit } + v.approach + v.enter + v.exit }.toSet() +
            newScenes.map { VisualDirector.locomotion(it, false) }
        AnimationId.entries.filter { it.group in newGroups }.forEach { assertTrue("$it órfã", it in reachable) }
        newGroups.forEach { g -> assertEquals("$g", 4, AnimationId.entries.count { it.group == g }) }
        assertEquals(142, AnimationId.entries.size)
    }

    @Test
    fun `passeio anda devagar entre spots e o resto mantem o andar normal`() {
        assertEquals(AnimationId.LEISURE_WALK, VisualDirector.locomotion(SceneId.LEISURE, false))
        assertEquals(AnimationId.WALK, VisualDirector.locomotion(SceneId.SCHOOL, false))
        assertEquals(AnimationId.WALK_BACKPACK, VisualDirector.locomotion(SceneId.STREET, false))
        assertEquals(AnimationId.WALK_BACKPACK, VisualDirector.locomotion(SceneId.LEISURE, true))
    }

    @Test
    fun `necessidades mudam as escolhas visuais`() {
        fun weightOf(v: VisualState, a: AnimationId) = v.actions.filter { it.anim == a }.sumOf { it.weight }
        val calm = VisualDirector.resolve(HoodieActivity.SOCIALIZING, UserContextType.VISITING, social = 80, hunger = 20)
        val lonelyHungry = VisualDirector.resolve(HoodieActivity.SOCIALIZING, UserContextType.VISITING, social = 20, hunger = 80)
        assertTrue(weightOf(lonelyHungry, AnimationId.VISIT_CHAT) > weightOf(calm, AnimationId.VISIT_CHAT))
        assertTrue(weightOf(lonelyHungry, AnimationId.VISIT_SNACK) > weightOf(calm, AnimationId.VISIT_SNACK))
        val focused = VisualDirector.resolve(HoodieActivity.STUDYING, UserContextType.STUDY, focus = 80)
        val distracted = VisualDirector.resolve(HoodieActivity.STUDYING, UserContextType.STUDY, focus = 10)
        assertTrue(weightOf(distracted, AnimationId.STUDY_READ) < weightOf(focused, AnimationId.STUDY_READ))
        assertTrue(weightOf(distracted, AnimationId.PHONE_READ) > 0)
        val rested = VisualDirector.resolve(HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, energy = 80)
        val tired = VisualDirector.resolve(HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, energy = 15)
        assertTrue(weightOf(tired, AnimationId.LEISURE_BENCH) > weightOf(rested, AnimationId.LEISURE_BENCH))
    }

    @Test
    fun `reacoes contextuais dos grupos novos`() {
        assertEquals(listOf(AnimationId.GLANCE, AnimationId.NOD), ReactionDirector.contextual(AnimationId.STUDY_READ))
        assertEquals(listOf(AnimationId.NOTICE, AnimationId.SMILE), ReactionDirector.contextual(AnimationId.SHOP_LOOK))
        assertEquals(listOf(AnimationId.WAVE), ReactionDirector.contextual(AnimationId.VISIT_CHAT))
        assertEquals(listOf(AnimationId.WAVE, AnimationId.HAPPY), ReactionDirector.contextual(AnimationId.LEISURE_LOOK))
    }

    // ───── Renderização ─────

    private fun empty(id: SceneId, flags: Set<SceneFlag> = emptySet(), period: DayPeriod = DayPeriod.DAY, variant: Int = 0, t: Long = 1_000) =
        SceneRenderer().renderEmpty(SceneRegistry[id], SceneEnv(period, 600, variant = variant, flags = flags), t).pixels.toList()

    @Test
    fun `cenas novas renderizam em todos os periodos e mudam com o horario`() {
        newScenes.forEach { id ->
            val shots = DayPeriod.entries.map { empty(id, period = it) }
            assertEquals("$id: períodos iguais", DayPeriod.entries.size, shots.toSet().size)
        }
    }

    @Test
    fun `props dinamicos reagem as flags`() {
        val cases = listOf(
            SceneId.SCHOOL to SceneFlag.BOOK_OPEN,
            SceneId.SHOPPING to SceneFlag.ITEM_HELD,
            SceneId.SHOPPING to SceneFlag.ITEM_IN_CART,
            SceneId.SHOPPING to SceneFlag.CHECKOUT_ACTIVE,
            SceneId.FAMILY to SceneFlag.SNACK_IN_HAND,
            SceneId.LEISURE to SceneFlag.CAMERA_ACTIVE,
        )
        cases.forEach { (id, flag) -> assertNotEquals("$id ignora $flag", empty(id), empty(id, setOf(flag))) }
        // A página vira só com o livro aberto.
        assertNotEquals(empty(SceneId.SCHOOL, setOf(SceneFlag.BOOK_OPEN)), empty(SceneId.SCHOOL, setOf(SceneFlag.BOOK_OPEN, SceneFlag.PAGE_TURNED)))
    }

    @Test
    fun `passeio tem tres fundos e o cache respeita a variante`() {
        val r = SceneRenderer()
        val s = SceneRegistry[SceneId.LEISURE]
        val shots = (0 until 3).map { v -> r.renderEmpty(s, SceneEnv(DayPeriod.DAY, 600, variant = v), 1_000).pixels.toList() }
        assertEquals(3, shots.toSet().size)
        // variant 3 == variant 0 (mesmo renderer, cache quente).
        assertEquals(shots[0], r.renderEmpty(s, SceneEnv(DayPeriod.DAY, 600, variant = 3), 1_000).pixels.toList())
    }

    @Test
    fun `microanimacoes do cenario mexem com o tempo`() {
        assertNotEquals(empty(SceneId.LEISURE, variant = 1, t = 0), empty(SceneId.LEISURE, variant = 1, t = 700))
        assertNotEquals(empty(SceneId.SHOPPING, t = 0), empty(SceneId.SHOPPING, t = 700))
        assertNotEquals(empty(SceneId.FAMILY, t = 0), empty(SceneId.FAMILY, t = 300))
    }

    // ───── Eventos → flags ─────

    private class Player(val sm: AnimationStateMachine) {
        var t = 1L
        val seen = mutableListOf<Set<SceneFlag>>()
        fun run(ms: Long) {
            val end = t + ms
            while (t < end) { t += 33; sm.frame(t, 600, DayPeriod.DAY); seen += sm.sceneFlags.toSet() }
        }
    }

    private fun play(scene: SceneId, spot: SpotId, anim: AnimationId, steady: Set<SceneFlag> = emptySet()): Player {
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(VisualState(scene, spot, listOf(MicroAction(anim, 1, 60_000, 60_000)), steadyFlags = steady), 1)
        return Player(sm).also { it.run(anim.durationMs + 200) }
    }

    @Test
    fun `compras levam o produto da prateleira ao carrinho e ao caixa`() {
        val pick = play(SceneId.SHOPPING, SpotId.AISLE_A, AnimationId.SHOP_PICK)
        val held = pick.seen.indexOfFirst { SceneFlag.ITEM_HELD in it }
        val cart = pick.seen.indexOfFirst { SceneFlag.ITEM_IN_CART in it }
        assertTrue("held=$held cart=$cart", held >= 0 && cart > held)
        assertFalse(SceneFlag.ITEM_HELD in pick.seen[cart])

        val pay = play(SceneId.SHOPPING, SpotId.CHECKOUT, AnimationId.SHOP_PAY, setOf(SceneFlag.ITEM_IN_CART))
        val checkout = pay.seen.indexOfFirst { SceneFlag.CHECKOUT_ACTIVE in it }
        assertTrue(checkout >= 0)
        assertFalse(SceneFlag.ITEM_IN_CART in pay.seen[checkout])
        assertFalse("pagamento não fechou o caixa", SceneFlag.CHECKOUT_ACTIVE in pay.seen.last())
    }

    @Test
    fun `estudo abre o livro e vira a pagina`() {
        val read = play(SceneId.SCHOOL, SpotId.DESK, AnimationId.STUDY_READ)
        assertTrue(read.seen.any { SceneFlag.BOOK_OPEN in it })
        val turn = play(SceneId.SCHOOL, SpotId.DESK, AnimationId.STUDY_PAGE_TURN, setOf(SceneFlag.BOOK_OPEN))
        assertTrue(turn.seen.any { SceneFlag.PAGE_TURNED in it })
    }

    @Test
    fun `petisco sai do prato e acaba`() {
        val snack = play(SceneId.FAMILY, SpotId.FAMILY_SOFA, AnimationId.VISIT_SNACK)
        val picked = snack.seen.indexOfFirst { SceneFlag.SNACK_IN_HAND in it }
        assertTrue(picked >= 0)
        assertTrue(snack.seen.drop(picked).any { SceneFlag.SNACK_IN_HAND !in it })
    }

    @Test
    fun `foto liga e desliga a camera`() {
        val photo = play(SceneId.LEISURE, SpotId.VIEWPOINT, AnimationId.LEISURE_PHOTO)
        val ready = photo.seen.indexOfFirst { SceneFlag.CAMERA_ACTIVE in it }
        assertTrue(ready >= 0)
        assertTrue(photo.seen.drop(ready).any { SceneFlag.CAMERA_ACTIVE !in it })
    }

    @Test
    fun `chegar na familia entra pela porta, acena e senta no sofa`() {
        val sm = AnimationStateMachine(Random(2))
        sm.setVisual(VisualDirector.resolve(HoodieActivity.IDLE, UserContextType.HOME), 1)
        val p = Player(sm)
        p.run(1_000)
        sm.setVisual(VisualDirector.resolve(HoodieActivity.SOCIALIZING, UserContextType.VISITING), p.t)
        val anims = mutableListOf<AnimationId>()
        val end = p.t + 30_000
        while (p.t < end) {
            p.t += 33
            val f = sm.frame(p.t, 600, DayPeriod.DAY)!!
            if (anims.lastOrNull() != f.animation) anims += f.animation
            if (f.scene.id == SceneId.FAMILY && sm.phase == AnimationStateMachine.Phase.LOOP) break
        }
        assertEquals(SceneId.FAMILY, sm.currentVisual!!.scene)
        val wave = anims.indexOf(AnimationId.WAVE)
        val sit = anims.lastIndexOf(AnimationId.SIT_DOWN)
        assertTrue("sequência: $anims", wave >= 0 && sit > wave)
    }

    @Test
    fun `passeio abre ja sentado no banco quando sorteia o banco`() {
        val v = VisualState(
            SceneId.LEISURE, SpotId.PATH_A,
            listOf(MicroAction(AnimationId.LEISURE_BENCH, 1, 60_000, 60_000, SpotId.BENCH, direction = Direction.FRONT)),
        )
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(v, 1)
        val f = sm.frame(40, 600, DayPeriod.DAY)!!
        val bench = SceneRegistry[SceneId.LEISURE].spot(SpotId.BENCH)
        assertEquals(bench.x, f.x)
        assertEquals(bench.y, f.y)
    }

    // ───── Goldens (hash do frame renderizado) ─────

    private fun shot(scene: SceneId, spot: SpotId, anim: AnimationId, at: Long, period: DayPeriod = DayPeriod.DAY, variant: Int = 0,
                     steady: Set<SceneFlag> = emptySet(), direction: Direction = Direction.FRONT): PixelBuffer {
        val sm = AnimationStateMachine(Random(7))
        sm.setVisual(VisualState(scene, spot, listOf(MicroAction(anim, 1, 60_000, 60_000, direction = direction)), variant = variant, steadyFlags = steady, tvOn = true), 1)
        var t = 1L
        var frame = sm.frame(t, 10 * 60 + 8, period)!!
        while (t < at) { t += 33; frame = sm.frame(t, 10 * 60 + 8, period)!! }
        return PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().render(frame, t)) }
    }

    private val goldens: Map<String, () -> PixelBuffer> = linkedMapOf(
        "school_study" to { shot(SceneId.SCHOOL, SpotId.DESK, AnimationId.STUDY_READ, 1_500, steady = setOf(SceneFlag.CHAIR_OCCUPIED)) },
        "school_page_turn" to { shot(SceneId.SCHOOL, SpotId.DESK, AnimationId.STUDY_PAGE_TURN, 900, DayPeriod.NIGHT, steady = setOf(SceneFlag.CHAIR_OCCUPIED, SceneFlag.BOOK_OPEN)) },
        "shopping_look" to { shot(SceneId.SHOPPING, SpotId.AISLE_A, AnimationId.SHOP_LOOK, 1_200) },
        "shopping_pick" to { shot(SceneId.SHOPPING, SpotId.AISLE_A, AnimationId.SHOP_PICK, 1_200) },
        "shopping_cart" to { shot(SceneId.SHOPPING, SpotId.CART, AnimationId.SHOP_CART, 600, steady = setOf(SceneFlag.ITEM_IN_CART)) },
        "shopping_pay" to { shot(SceneId.SHOPPING, SpotId.CHECKOUT, AnimationId.SHOP_PAY, 1_400, DayPeriod.EVENING, steady = setOf(SceneFlag.ITEM_IN_CART)) },
        "family_chat" to { shot(SceneId.FAMILY, SpotId.FAMILY_SOFA, AnimationId.VISIT_CHAT, 900) },
        "family_laugh" to { shot(SceneId.FAMILY, SpotId.FAMILY_SOFA, AnimationId.VISIT_LAUGH, 500, DayPeriod.EVENING) },
        "family_snack" to { shot(SceneId.FAMILY, SpotId.FAMILY_SOFA, AnimationId.VISIT_SNACK, 900) },
        "leisure_walk" to { shot(SceneId.LEISURE, SpotId.WALK, AnimationId.LEISURE_WALK, 700, direction = Direction.RIGHT) },
        "leisure_bench" to { shot(SceneId.LEISURE, SpotId.BENCH, AnimationId.LEISURE_BENCH, 1_000, DayPeriod.EVENING, variant = 1) },
        "leisure_photo" to { shot(SceneId.LEISURE, SpotId.VIEWPOINT, AnimationId.LEISURE_PHOTO, 1_000, DayPeriod.MORNING, variant = 2) },
    )

    private fun digest(buf: PixelBuffer): String {
        val md = MessageDigest.getInstance("SHA-256")
        buf.pixels.forEach { p -> md.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
        return HexFormat.of().formatHex(md.digest())
    }

    @Test
    fun `goldens das cenas novas`() {
        val actual = goldens.mapValues { (_, render) -> render() }
        // Folha para revisão visual (build/pixel-preview/new_place_scenes.png).
        PreviewExport.sheet("new_place_scenes", actual.values.toList(), columns = 4, scale = 2)
        val lines = actual.map { (name, buf) -> "$name\t${digest(buf)}" }
        if (System.getenv("RECORD_SCENE_GOLDENS") == "true") {
            File("src/test/resources/scene-goldens-v1.sha256").writeText(lines.joinToString("\n", postfix = "\n"))
            return
        }
        val expected = requireNotNull(javaClass.getResourceAsStream("/scene-goldens-v1.sha256")) { "rode com RECORD_SCENE_GOLDENS=true" }
            .bufferedReader().readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (name, buf) -> assertEquals("golden mudou: $name", expected.getValue(name), digest(buf)) }
    }

    @Test
    fun `cenas existentes nao mudaram de spot nem de cena`() {
        // Regressão: os contextos antigos continuam nas cenas antigas.
        assertEquals(SceneId.OFFICE, VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK).scene)
        assertEquals(SceneId.RESTAURANT, VisualDirector.resolve(HoodieActivity.EATING, UserContextType.LUNCH).scene)
        assertEquals(SceneId.GYM, VisualDirector.resolve(HoodieActivity.TRAINING, UserContextType.GYM).scene)
        assertEquals(SceneId.HOME, VisualDirector.resolve(HoodieActivity.SLEEPING, UserContextType.HOME).scene)
        assertEquals(SceneId.STREET, VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, commute = CommuteStyle.WALK).scene)
    }
}
