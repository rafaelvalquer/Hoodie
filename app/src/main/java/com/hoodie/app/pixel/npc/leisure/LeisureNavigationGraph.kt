package com.hoodie.app.pixel.npc.leisure

import java.util.concurrent.ConcurrentHashMap

/**
 * Rotas do Passeio (240×320). Os visitantes circulam pela faixa de terra (y 254–290) e só
 * sobem para o gramado por "colunas" livres: até o lago/chafariz/flores pela esquerda, até o
 * banco pela frente dele e até o mirante pela direita. Nenhuma aresta atravessa obstáculo;
 * a única exceção é o passo da frente do banco até o assento ([isBenchAccess]).
 */
object LeisureNavigationGraph {
    /** Abaixo disto o NPC está fora da tela (não é desenhado): usado para quem saiu de cena. */
    const val OFFSTAGE_LEFT_X = -90
    const val OFFSTAGE_RIGHT_X = 330

    /** Formas sólidas do cenário, para validação (testes) e para nunca planejar rota por cima. */
    sealed interface Obstacle {
        val name: String
        fun contains(x: Int, y: Int): Boolean
    }

    data class Box(override val name: String, val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Obstacle {
        override fun contains(x: Int, y: Int) = x in x0..x1 && y in y0..y1
    }

    data class Ellipse(override val name: String, val cx: Int, val cy: Int, val rx: Int, val ry: Int) : Obstacle {
        override fun contains(x: Int, y: Int): Boolean {
            val dx = (x - cx).toDouble() / rx; val dy = (y - cy).toDouble() / ry
            return dx * dx + dy * dy <= 1.0
        }
    }

    private fun natureView(variant: Int): LeisureSpot = when (variant.mod(3)) {
        0 -> LeisureSpot(60, 216, attractionX = 70)    // margem do lago
        1 -> LeisureSpot(70, 214, attractionX = 120)   // à esquerda do chafariz
        else -> LeisureSpot(60, 232, attractionX = 96) // entre as flores
    }

    private fun spots(variant: Int): Map<LeisureNpcSpot, LeisureSpot> {
        val nature = natureView(variant)
        return mapOf(
            LeisureNpcSpot.ENTRANCE_LEFT to LeisureSpot(-36, 268, offstage = true),
            LeisureNpcSpot.ENTRANCE_RIGHT to LeisureSpot(276, 270, offstage = true),
            LeisureNpcSpot.PATH_LEFT to LeisureSpot(16, 268),
            LeisureNpcSpot.JUNCTION_WEST to LeisureSpot(nature.x, 270, capacity = Int.MAX_VALUE),
            LeisureNpcSpot.PATH_CENTER to LeisureSpot(86, 280),
            LeisureNpcSpot.JUNCTION_BENCH to LeisureSpot(110, 272, capacity = Int.MAX_VALUE),
            LeisureNpcSpot.BENCH_APPROACH to LeisureSpot(110, 246),
            LeisureNpcSpot.BENCH_SEAT to LeisureSpot(110, 225),
            // 30 px entre os dois: frente a frente sem um sprite cobrir o outro.
            LeisureNpcSpot.SOCIAL_LEFT to LeisureSpot(139, 281),
            LeisureNpcSpot.SOCIAL_RIGHT to LeisureSpot(169, 281),
            LeisureNpcSpot.PATH_RIGHT to LeisureSpot(222, 268),
            LeisureNpcSpot.VIEWPOINT to LeisureSpot(226, 240, attractionX = 239),
            LeisureNpcSpot.NATURE_VIEW to nature,
        )
    }

    private val spotsByVariant = Array(3) { spots(it) }

    fun spot(variant: Int, id: LeisureNpcSpot): LeisureSpot = spotsByVariant[variant.mod(3)].getValue(id)

    private val edges: List<Pair<LeisureNpcSpot, LeisureNpcSpot>> = listOf(
        LeisureNpcSpot.ENTRANCE_LEFT to LeisureNpcSpot.PATH_LEFT,
        LeisureNpcSpot.PATH_LEFT to LeisureNpcSpot.JUNCTION_WEST,
        LeisureNpcSpot.JUNCTION_WEST to LeisureNpcSpot.NATURE_VIEW,
        LeisureNpcSpot.JUNCTION_WEST to LeisureNpcSpot.PATH_CENTER,
        LeisureNpcSpot.PATH_CENTER to LeisureNpcSpot.JUNCTION_BENCH,
        LeisureNpcSpot.JUNCTION_BENCH to LeisureNpcSpot.BENCH_APPROACH,
        LeisureNpcSpot.BENCH_APPROACH to LeisureNpcSpot.BENCH_SEAT,
        LeisureNpcSpot.JUNCTION_BENCH to LeisureNpcSpot.SOCIAL_LEFT,
        LeisureNpcSpot.SOCIAL_LEFT to LeisureNpcSpot.SOCIAL_RIGHT,
        LeisureNpcSpot.SOCIAL_RIGHT to LeisureNpcSpot.PATH_RIGHT,
        LeisureNpcSpot.PATH_RIGHT to LeisureNpcSpot.VIEWPOINT,
        LeisureNpcSpot.PATH_RIGHT to LeisureNpcSpot.ENTRANCE_RIGHT,
    )

    /** Todas as arestas, nos dois sentidos (para testes de obstáculo). */
    val allEdges: List<Pair<LeisureNpcSpot, LeisureNpcSpot>> get() = edges

    private val neighbors: Map<LeisureNpcSpot, List<LeisureNpcSpot>> = LeisureNpcSpot.entries.associateWith { s ->
        edges.mapNotNull { (a, b) -> if (a == s) b else if (b == s) a else null }
    }

    /** O único trecho que entra na área do banco: da frente do banco até o assento. */
    fun isBenchAccess(a: LeisureNpcSpot, b: LeisureNpcSpot) =
        setOf(a, b) == setOf(LeisureNpcSpot.BENCH_APPROACH, LeisureNpcSpot.BENCH_SEAT)

    fun obstacles(variant: Int): List<Obstacle> = buildList {
        add(Box("banco", 90, 198, 154, 241))
        add(Box("poste", 18, 160, 28, 250))
        add(Box("mirante", 170, 190, 236, 214))
        when (variant.mod(3)) {
            0 -> add(Ellipse("lago", 70, 192, 46, 15))
            1 -> add(Box("chafariz", 86, 166, 154, 206))
            else -> Unit
        }
    }

    private val routeCache = ConcurrentHashMap<Int, List<LeisureNpcSpot>>()

    /** Menor rota (em número de trechos) entre dois pontos; inclui origem e destino. */
    fun route(from: LeisureNpcSpot, to: LeisureNpcSpot): List<LeisureNpcSpot> {
        val n = LeisureNpcSpot.entries.size
        val key = from.ordinal * n + to.ordinal
        return routeCache[key] ?: computeRoute(from, to).also { routeCache.putIfAbsent(key, it) }
    }

    private fun computeRoute(from: LeisureNpcSpot, to: LeisureNpcSpot): List<LeisureNpcSpot> {
        if (from == to) return listOf(from)
        val previous = HashMap<LeisureNpcSpot, LeisureNpcSpot?>()
        val queue = ArrayDeque<LeisureNpcSpot>()
        previous[from] = null; queue += from
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            for (next in neighbors.getValue(at)) {
                if (next in previous) continue
                previous[next] = at
                if (next == to) {
                    val path = ArrayList<LeisureNpcSpot>()
                    var cursor: LeisureNpcSpot? = to
                    while (cursor != null) { path += cursor; cursor = previous[cursor] }
                    return path.asReversed()
                }
                queue += next
            }
        }
        error("rota inexistente $from → $to")
    }

    /** Nó do grafo mais próximo de uma posição livre (ex.: onde uma caminhada foi interrompida). */
    fun nearestNode(variant: Int, x: Int, y: Int): LeisureNpcSpot = LeisureNpcSpot.entries
        .filter { !spot(variant, it).offstage }
        .minBy { val s = spot(variant, it); kotlin.math.abs(s.x - x) + kotlin.math.abs(s.floorY - y) * 2 }
}
