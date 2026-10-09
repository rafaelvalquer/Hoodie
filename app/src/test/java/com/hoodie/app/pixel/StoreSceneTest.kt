package com.hoodie.app.pixel

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.shopping.ShoppingNavigationGraph
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcIntent
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcSpot
import com.hoodie.app.pixel.npc.shopping.ShoppingNpcVisualState
import com.hoodie.app.pixel.npc.shopping.ShoppingSpeechLibrary
import com.hoodie.app.pixel.npc.shopping.StoreNavigationGraph
import com.hoodie.app.pixel.npc.shopping.StoreSpeechLibrary
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

/** Loja de roupas: cena própria (separada do mercado), animações, props, NPC reaproveitado e goldens. */
class StoreSceneTest {

    private fun store(a: HoodieActivity, mood: Int = 70, energy: Int = 70) =
        VisualDirector.resolve(a, UserContextType.SHOPPING, energy = energy, mood = mood, placeType = PlaceType.STORE)

    /** Todos os estados visuais da loja (atividade × humor/energia extremos). */
    private fun storeVisuals(): List<VisualState> = HoodieActivity.entries.flatMap { a -> listOf(10, 90).map { store(a, it, it) } }

    // ───── Roteamento ─────

    @Test
    fun `loja abre a cena propria e o mercado continua no mercado`() {
        HoodieActivity.entries.forEach { a ->
            assertEquals("$a loja", SceneId.STORE, store(a).scene)
            assertEquals("$a mercado", SceneId.SHOPPING, VisualDirector.resolve(a, UserContextType.SHOPPING, placeType = PlaceType.MARKET).scene)
            // Sem lugar conhecido (compras manuais / rotina provável): mercado, como antes.
            assertEquals("$a sem lugar", SceneId.SHOPPING, VisualDirector.resolve(a, UserContextType.SHOPPING).scene)
        }
        // O tipo do lugar só muda a cena de compras.
        assertEquals(SceneId.OFFICE, VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, placeType = PlaceType.STORE).scene)
        assertEquals(SpotId.RACK_A, store(HoodieActivity.SHOPPING).spot)
    }

    @Test
    fun `cena tem spots, props, porta e luz`() {
        val s = SceneRegistry[SceneId.STORE]
        listOf(SpotId.RACK_A, SpotId.RACK_B, SpotId.MIRROR, SpotId.FITTING_ROOM, SpotId.CHECKOUT, SpotId.DOOR).forEach { assertTrue("$it", it in s.spots) }
        assertTrue(s.props().size >= 6)
        assertTrue(s.hasAnimatedDoor)
        assertTrue(s.lights(SceneEnv(DayPeriod.NIGHT, 600)).isNotEmpty())
    }

    // ───── Animações ─────

    @Test
    fun `quatro animacoes proprias, todas alcancaveis pelo diretor`() {
        val own = AnimationId.entries.filter { it.group == AnimGroup.STORE }
        assertEquals(4, own.size)
        val reachable = storeVisuals().flatMap { v -> v.actions.flatMap { listOf(it.anim) + it.enter + it.exit } + v.approach + v.enter + v.exit }.toSet()
        own.forEach { assertTrue("$it órfã", it in reachable) }
        // Reaproveita o olhar e o pagamento do mercado.
        assertTrue(AnimationId.SHOP_LOOK in reachable && AnimationId.SHOP_PAY in reachable)
    }

    @Test
    fun `humor alto puxa espelho e provador, cansaco puxa o caixa`() {
        fun weight(v: VisualState, a: AnimationId) = v.actions.filter { it.anim == a }.sumOf { it.weight }
        val happy = store(HoodieActivity.SHOPPING, mood = 90, energy = 90)
        val tired = store(HoodieActivity.SHOPPING, mood = 40, energy = 10)
        assertTrue(weight(happy, AnimationId.STORE_FITTING_ROOM) > weight(tired, AnimationId.STORE_FITTING_ROOM))
        assertTrue(weight(tired, AnimationId.SHOP_PAY) > weight(happy, AnimationId.SHOP_PAY))
    }

    private class Player(val sm: AnimationStateMachine) {
        var t = 1L
        val seen = mutableListOf<Set<SceneFlag>>()
        fun run(ms: Long) {
            val end = t + ms
            while (t < end) { t += 33; sm.frame(t, 600, DayPeriod.DAY); seen += sm.sceneFlags.toSet() }
        }
    }

    private fun play(spot: SpotId, anim: AnimationId, steady: Set<SceneFlag> = emptySet()): Player {
        val sm = AnimationStateMachine(Random(1))
        sm.setVisual(VisualState(SceneId.STORE, spot, listOf(MicroAction(anim, 1, 60_000, 60_000)), steadyFlags = steady), 1)
        return Player(sm).also { it.run(anim.durationMs + 200) }
    }

    @Test
    fun `peca sai da arara, cortina fecha e abre, sacola sai do balcao`() {
        val browse = play(SpotId.RACK_A, AnimationId.STORE_BROWSE_RACK)
        assertTrue(browse.seen.any { SceneFlag.GARMENT_HELD in it })

        val fitting = play(SpotId.FITTING_ROOM, AnimationId.STORE_FITTING_ROOM, setOf(SceneFlag.GARMENT_HELD))
        val closed = fitting.seen.indexOfFirst { SceneFlag.CURTAIN_CLOSED in it }
        assertTrue(closed >= 0)
        assertFalse("cortina ficou fechada", SceneFlag.CURTAIN_CLOSED in fitting.seen.last())

        val bag = play(SpotId.CHECKOUT, AnimationId.STORE_BAG_EXIT, setOf(SceneFlag.GARMENT_HELD))
        val taken = bag.seen.indexOfFirst { SceneFlag.BAG_HELD in it }
        assertTrue(taken >= 0)
        assertFalse(SceneFlag.GARMENT_HELD in bag.seen[taken])
    }

    // ───── Render ─────

    private fun empty(flags: Set<SceneFlag> = emptySet(), period: DayPeriod = DayPeriod.DAY, t: Long = 1_000, npc: ShoppingNpcVisualState = ShoppingNpcVisualState.EMPTY) =
        SceneRenderer().renderEmpty(SceneRegistry[SceneId.STORE], SceneEnv(period, 600, flags = flags, shoppingNpc = npc), t).pixels.toList()

    @Test
    fun `renderiza nos quatro periodos e e diferente do mercado`() {
        assertEquals(DayPeriod.entries.size, DayPeriod.entries.map { empty(period = it) }.toSet().size)
        val market = SceneRenderer().renderEmpty(SceneRegistry[SceneId.SHOPPING], SceneEnv(DayPeriod.DAY, 600), 1_000).pixels.toList()
        val diff = empty().zip(market).count { (a, b) -> a != b }
        assertTrue("loja quase igual ao mercado ($diff px diferentes)", diff > empty().size / 2)
    }

    @Test
    fun `props reagem as flags e ao comprador`() {
        listOf(SceneFlag.GARMENT_HELD, SceneFlag.CURTAIN_CLOSED, SceneFlag.BAG_HELD, SceneFlag.CHECKOUT_ACTIVE).forEach { flag ->
            assertNotEquals("ignora $flag", empty(), empty(setOf(flag)))
        }
        // O renderer recalcula o comprador a cada quadro; aqui os props recebem o estado dele direto.
        fun props(npc: ShoppingNpcVisualState) = PixelBuffer(240, 320).also { b ->
            val env = SceneEnv(DayPeriod.DAY, 600, shoppingNpc = npc)
            SceneRegistry[SceneId.STORE].sortedProps.forEach { it.draw(b, env, 1_000) }
        }.pixels.toList()
        val none = props(ShoppingNpcVisualState.EMPTY)
        assertNotEquals("arara A ignora o comprador", none, props(ShoppingNpcVisualState(productRemovedA = true)))
        assertNotEquals("arara B ignora o comprador", none, props(ShoppingNpcVisualState(productRemovedB = true)))
        assertNotEquals("caixa ignora o comprador", none, props(ShoppingNpcVisualState(checkoutActive = true)))
    }

    @Test
    fun `cena viva parada - etiqueta, cortina e cabides mexem com o tempo`() {
        assertNotEquals(empty(t = 0), empty(t = 700))
        assertNotEquals(empty(t = 0), empty(t = 900))
    }

    private fun shot(spot: SpotId, anim: AnimationId, at: Long, period: DayPeriod = DayPeriod.DAY, steady: Set<SceneFlag> = emptySet(),
                     direction: Direction = Direction.FRONT): PixelBuffer {
        val sm = AnimationStateMachine(Random(7))
        sm.setVisual(VisualState(SceneId.STORE, spot, listOf(MicroAction(anim, 1, 60_000, 60_000, direction = direction)), steadyFlags = steady), 1)
        var t = 1L
        var frame = sm.frame(t, 10 * 60 + 8, period)!!
        while (t < at) { t += 33; frame = sm.frame(t, 10 * 60 + 8, period)!! }
        return PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().render(frame, t)) }
    }

    @Test
    fun `cortina fechada esconde o Hoodie no provador`() {
        // 1,5 s dentro do provador: cortina fechada (CURTAIN_CLOSE em 0,7 s, CURTAIN_OPEN em 2,3 s).
        val inside = shot(SpotId.FITTING_ROOM, AnimationId.STORE_FITTING_ROOM, 1_500)
        val curtainOnly = PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().renderEmpty(SceneRegistry[SceneId.STORE], SceneEnv(DayPeriod.DAY, 10 * 60 + 8, flags = setOf(SceneFlag.CURTAIN_CLOSED)), 1_500)) }
        val spot = SceneRegistry[SceneId.STORE].spot(SpotId.FITTING_ROOM)
        // Onde o corpo do Hoodie estaria, só se vê a cortina.
        for (y in spot.y - 40..spot.y - 10) for (x in spot.x - 6..spot.x + 6) {
            assertEquals("pixel ($x,$y)", curtainOnly[x, y], inside[x, y])
        }
    }

    // ───── NPC reaproveitado ─────

    @Test
    fun `o comprador do mercado tambem visita a loja, com planta e falas proprias`() {
        val env = SceneEnv(DayPeriod.DAY, 600, daySeed = 3)
        val slot = NpcDirector.plan(SceneId.STORE, env).single()
        val brain = slot.shoppingBrain!!
        assertEquals(StoreNavigationGraph, brain.floor)
        assertEquals(StoreSpeechLibrary, brain.speech)
        // O mercado segue com a planta e as falas dele.
        assertEquals(ShoppingNavigationGraph, NpcDirector.plan(SceneId.SHOPPING, env).single().shoppingBrain!!.floor)
        assertEquals(ShoppingSpeechLibrary, NpcDirector.plan(SceneId.SHOPPING, env).single().shoppingBrain!!.speech)

        val visited = mutableSetOf<ShoppingNpcSpot>()
        val lines = mutableSetOf<String>()
        var paid = false
        for (t in 0L until 300_000L step 250) {
            val s = brain.stateAt(t)
            visited += s.currentSpot
            s.speechLine?.let(lines::add)
            if (s.currentIntent == ShoppingNpcIntent.PAY) paid = true
            val m = brain.movementAt(t)
            // Nunca atravessa as araras (x 50..182, do varão ao pé em y 112..214).
            assertFalse("dentro da arara em t=$t: (${m.x},${m.floorY})", m.x in 54..178 && m.floorY in 112..218)
        }
        assertTrue("só visitou $visited", visited.any { it.name.startsWith("AISLE_A") } && visited.any { it.name.startsWith("AISLE_B") })
        assertTrue(paid)
        val storeLines = ShoppingNpcIntent.entries.flatMap { i -> DayPeriod.entries.flatMap { StoreSpeechLibrary.lines(i, it) } }.toSet()
        assertTrue("falas fora da loja: ${lines - storeLines}", storeLines.containsAll(lines))
    }

    @Test
    fun `todas as rotas da planta da loja existem`() {
        ShoppingNpcSpot.entries.forEach { a ->
            ShoppingNpcSpot.entries.forEach { b ->
                val route = StoreNavigationGraph.route(a, b)
                assertEquals(b, route.last())
                route.zipWithNext().forEach { (p, q) -> assertTrue("$p→$q", StoreNavigationGraph.isAdjacent(p, q)) }
            }
        }
    }

    // ───── Goldens ─────

    private val goldens: Map<String, () -> PixelBuffer> = linkedMapOf(
        "store_browse" to { shot(SpotId.RACK_A, AnimationId.STORE_BROWSE_RACK, 2_300) },
        "store_mirror" to { shot(SpotId.MIRROR, AnimationId.STORE_HOLD_GARMENT, 900, DayPeriod.MORNING, setOf(SceneFlag.GARMENT_HELD), Direction.LEFT) },
        "store_fitting" to { shot(SpotId.FITTING_ROOM, AnimationId.STORE_FITTING_ROOM, 2_800, DayPeriod.EVENING, setOf(SceneFlag.GARMENT_HELD)) },
        "store_pay" to { shot(SpotId.CHECKOUT, AnimationId.SHOP_PAY, 1_400, DayPeriod.EVENING, setOf(SceneFlag.GARMENT_HELD)) },
        "store_bag" to { shot(SpotId.CHECKOUT, AnimationId.STORE_BAG_EXIT, 1_200, DayPeriod.NIGHT, setOf(SceneFlag.GARMENT_HELD)) },
    )

    private fun digest(buf: PixelBuffer): String {
        val md = MessageDigest.getInstance("SHA-256")
        buf.pixels.forEach { p -> md.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
        return HexFormat.of().formatHex(md.digest())
    }

    @Test
    fun `goldens da loja`() {
        val actual = goldens.mapValues { (_, render) -> render() }
        // Folha para revisão visual (build/pixel-preview/store_scene.png).
        PreviewExport.sheet("store_scene", actual.values.toList(), columns = 5, scale = 2)
        val lines = actual.map { (name, buf) -> "$name\t${digest(buf)}" }
        if (System.getenv("RECORD_STORE_GOLDENS") == "true") {
            File("src/test/resources/store-scene-goldens-v1.sha256").writeText(lines.joinToString("\n", postfix = "\n"))
            return
        }
        val expected = requireNotNull(javaClass.getResourceAsStream("/store-scene-goldens-v1.sha256")) { "rode com RECORD_STORE_GOLDENS=true depois de revisar a folha" }
            .bufferedReader().readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t').trim() }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (name, buf) -> assertEquals("golden mudou: $name", expected.getValue(name), digest(buf)) }
    }
}
