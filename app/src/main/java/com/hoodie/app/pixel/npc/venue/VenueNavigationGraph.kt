package com.hoodie.app.pixel.npc.venue

import kotlin.math.abs
import kotlin.math.max

/**
 * Ponto alcançável de uma cena (pés do personagem). [capacity] > 1 só para passagens (portas,
 * cruzamentos); pontos de atividade comportam uma pessoa. [interaction] nomeia o que se faz ali
 * (mesa, caixa, vitrine…). [offstage] marca a borda/porta por onde se entra e sai.
 */
data class VenueSpot(
    val id: String,
    val x: Int,
    val floorY: Int,
    val capacity: Int = 1,
    val interaction: String? = null,
    val offstage: Boolean = false,
)

/** Área sólida onde ninguém pisa (balcão, vitrine, parede, mar). Coordenadas dos pés. */
data class VenueObstacle(val name: String, val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    fun contains(x: Int, y: Int) = x in x0..x1 && y in y0..y1
}

/**
 * Grafo de pontos de uma cena. Os trechos (arestas) são retas entre pontos e nunca atravessam um
 * obstáculo (checado no init e nos testes). Rotas por BFS, em cache: nenhuma rota é calculada por
 * quadro.
 */
class VenueNavigationGraph(
    val name: String,
    spots: List<VenueSpot>,
    edges: List<Pair<String, String>>,
    val obstacles: List<VenueObstacle> = emptyList(),
) {
    val spots: Map<String, VenueSpot> = spots.associateBy { it.id }
    private val neighbors: Map<String, List<String>> = buildMap<String, MutableList<String>> {
        edges.forEach { (a, b) ->
            require(a in this@VenueNavigationGraph.spots && b in this@VenueNavigationGraph.spots) { "$name: aresta $a–$b com ponto desconhecido" }
            getOrPut(a) { mutableListOf() } += b
            getOrPut(b) { mutableListOf() } += a
        }
    }
    val edges: List<Pair<String, String>> = edges.toList()
    private val routes = HashMap<Pair<String, String>, List<String>>()

    init {
        require(spots.size == this.spots.size) { "$name: ponto repetido" }
        edges.forEach { (a, b) ->
            val p = spot(a); val q = spot(b)
            obstacles.firstOrNull { o -> crosses(p.x, p.floorY, q.x, q.floorY, o) }?.let { o ->
                error("$name: trecho $a–$b atravessa ${o.name}")
            }
        }
        this.spots.values.filter { !it.offstage }.forEach { s ->
            obstacles.firstOrNull { it.contains(s.x, s.floorY) }?.let { o -> error("$name: ${s.id} dentro de ${o.name}") }
        }
    }

    fun spot(id: String): VenueSpot = spots[id] ?: error("$name: ponto $id não existe")

    fun isAdjacent(a: String, b: String): Boolean = neighbors[a]?.contains(b) == true

    /** Caminho mais curto em número de trechos, incluindo origem e destino. */
    @Synchronized
    fun route(from: String, to: String): List<String> = routes.getOrPut(from to to) {
        if (from == to) return@getOrPut listOf(from)
        val previous = HashMap<String, String>()
        val queue = ArrayDeque(listOf(from))
        val seen = hashSetOf(from)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node == to) break
            for (n in neighbors[node].orEmpty()) if (seen.add(n)) { previous[n] = node; queue.addLast(n) }
        }
        check(to in seen) { "$name: sem rota de $from para $to" }
        generateSequence(to) { previous[it] }.toList().reversed()
    }

    fun nearest(x: Int, y: Int): String = spots.values.filter { !it.offstage }.minBy { max(abs(it.x - x), abs(it.floorY - y)) }.id

    companion object {
        /** Amostra a reta a cada pixel: basta um ponto dentro do obstáculo para o trecho ser inválido. */
        fun crosses(x0: Int, y0: Int, x1: Int, y1: Int, o: VenueObstacle): Boolean {
            val n = max(abs(x1 - x0), abs(y1 - y0)).coerceAtLeast(1)
            for (i in 0..n) {
                val x = x0 + (x1 - x0) * i / n
                val y = y0 + (y1 - y0) * i / n
                if (o.contains(x, y)) return true
            }
            return false
        }
    }
}
