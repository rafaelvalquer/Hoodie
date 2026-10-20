package com.hoodie.app.engine.diary.city

/**
 * A cidade fixa da Jornada (perspectiva 3/4 de RPG clássico): bairro, comércio e distrito de
 * trabalho ligados por calçadas, faixas de pedestre e ruas. É sempre a mesma cidade; o que muda
 * por dia é qual lugar do usuário ocupa cada prédio ([CityPlacement]).
 *
 * Coordenadas em tiles de [TS] px. Pura (sem Android), construída uma vez.
 */
object CityTown {
    const val TS = 16
    const val MW = 72
    const val MH = 46
    const val WW = MW * TS
    const val WH = MH * TS

    enum class Tile { GRASS, ROAD, SIDE, CROSS_H, CROSS_V, PLAZA, LOT, WATER, DOOR, PATH, PARKING }

    enum class Kind { HOUSE, BAKERY, GYM, MARKET, RESTAURANT, PHARMACY, SHOPS, TOWER, OFFICE_BRICK, OFFICE_TEAL }

    /** Um prédio da cidade; [door] é a coluna da porta, relativa, na última fileira. */
    data class Slot(
        val id: String, val kind: Kind, val defaultName: String,
        val x: Int, val y: Int, val w: Int, val h: Int, val door: Int,
        val roof: Int = 0, val district: District,
    ) {
        val px get() = x * TS
        val py get() = y * TS
        val pw get() = w * TS
        val ph get() = h * TS
        val doorX get() = x + door
        val doorY get() = y + h - 1
        /** Tile da calçada na frente da porta: onde o Hoodie para e de onde sai. */
        val frontY get() = doorY + 1
    }

    enum class District { HOME, COMMERCE, WORK }

    /** Objeto do cenário: [kind] é o nome do sprite no pintor; base = fundo do tile + [oy]. */
    data class PropSpec(val kind: String, val tx: Int, val ty: Int, val ox: Int = 0, val oy: Int = 0, val solid: Boolean = true, val variant: Int = 0)

    data class Signal(val tx: Int, val ty: Int, val horizontalAxis: Boolean)

    /** Faixa de trânsito: eixo, sentido, posição fixa (y para horizontais, x para verticais) e linhas de parada. */
    data class Lane(val horizontal: Boolean, val dir: Int, val pos: Int, val from: Int, val to: Int, val stops: List<Int>)

    val slots: List<Slot> = listOf(
        Slot("home", Kind.HOUSE, "Casa", 3, 6, 6, 6, 2, roof = 0, district = District.HOME),
        Slot("house2", Kind.HOUSE, "Casa", 11, 7, 5, 5, 2, roof = 1, district = District.HOME),
        Slot("house3", Kind.HOUSE, "Casa", 2, 16, 5, 5, 2, roof = 2, district = District.HOME),
        Slot("house4", Kind.HOUSE, "Casa", 9, 16, 6, 5, 3, roof = 3, district = District.HOME),
        Slot("bakery", Kind.BAKERY, "Padaria", 23, 16, 5, 5, 3, district = District.COMMERCE),
        Slot("gym", Kind.GYM, "Academia", 29, 15, 8, 6, 3, district = District.COMMERCE),
        Slot("market", Kind.MARKET, "Mercado", 38, 15, 6, 6, 2, district = District.COMMERCE),
        Slot("restaurant", Kind.RESTAURANT, "Restaurante", 23, 29, 7, 6, 3, district = District.COMMERCE),
        Slot("pharmacy", Kind.PHARMACY, "Farmácia", 31, 30, 5, 5, 2, district = District.COMMERCE),
        Slot("shops", Kind.SHOPS, "Loja", 37, 30, 7, 5, 3, district = District.COMMERCE),
        Slot("work", Kind.TOWER, "Trabalho", 51, 12, 10, 9, 4, district = District.WORK),
        Slot("office2", Kind.OFFICE_BRICK, "Escritório", 63, 14, 7, 7, 3, district = District.WORK),
        Slot("office3", Kind.OFFICE_TEAL, "Cowork", 61, 29, 8, 6, 3, district = District.WORK),
    )
    val slotById: Map<String, Slot> = slots.associateBy { it.id }

    private val grid = Array(MW * MH) { Tile.GRASS }
    private val blocked = BooleanArray(MW * MH)
    fun tile(x: Int, y: Int): Tile? = if (x < 0 || y < 0 || x >= MW || y >= MH) null else grid[y * MW + x]
    private fun set(x: Int, y: Int, t: Tile) { if (x in 0 until MW && y in 0 until MH) grid[y * MW + x] = t }
    private fun fill(x0: Int, y0: Int, x1: Int, y1: Int, t: Tile) { for (y in y0..y1) for (x in x0..x1) set(x, y, t) }
    private fun block(x: Int, y: Int) { if (x in 0 until MW && y in 0 until MH) blocked[y * MW + x] = true }

    val props = mutableListOf<PropSpec>()
    val signals: List<Signal>
    val lanes: List<Lane>
    /** Pontos de luz (postes) para a noite, em px de mundo. */
    val lampPoints = mutableListOf<IntArray>()

    private val walkKinds = setOf(Tile.SIDE, Tile.CROSS_H, Tile.CROSS_V, Tile.PLAZA, Tile.DOOR, Tile.PATH)
    val roadKinds = setOf(Tile.ROAD, Tile.CROSS_H, Tile.CROSS_V, Tile.PARKING)

    init {
        // Ruela residencial, avenida, ruas verticais, rua do comércio com vagas.
        fill(0, 12, 18, 12, Tile.SIDE); fill(0, 13, 18, 14, Tile.ROAD); fill(0, 15, 18, 15, Tile.SIDE)
        fill(18, 0, 18, 45, Tile.SIDE); fill(19, 0, 20, 45, Tile.ROAD); fill(21, 0, 21, 45, Tile.SIDE)
        fill(0, 21, 71, 21, Tile.SIDE); fill(0, 22, 71, 24, Tile.ROAD); fill(0, 25, 71, 25, Tile.SIDE)
        fill(45, 0, 45, 45, Tile.SIDE); fill(46, 0, 47, 45, Tile.ROAD); fill(48, 0, 48, 45, Tile.SIDE)
        fill(21, 35, 71, 35, Tile.SIDE); fill(22, 36, 71, 36, Tile.PARKING); fill(22, 37, 71, 37, Tile.ROAD); fill(21, 38, 71, 38, Tile.SIDE)
        fill(19, 13, 20, 14, Tile.ROAD)
        listOf(18 to 13, 18 to 14).forEach { (x, y) -> set(x, y, Tile.CROSS_V) }
        listOf(19 to 12, 20 to 12, 19 to 15, 20 to 15, 19 to 21, 20 to 21, 19 to 25, 20 to 25, 46 to 21, 47 to 21, 46 to 25, 47 to 25,
            46 to 35, 47 to 35, 46 to 38, 47 to 38, 19 to 35, 20 to 35, 19 to 38, 20 to 38).forEach { (x, y) -> set(x, y, Tile.CROSS_H) }
        listOf(18, 21, 33, 45, 48, 58).forEach { x -> for (y in 22..24) set(x, y, Tile.CROSS_V) }
        listOf(21, 45, 48).forEach { x -> set(x, 36, Tile.CROSS_V); set(x, 37, Tile.CROSS_V) }
        fill(22, 26, 44, 28, Tile.PLAZA)
        fill(49, 26, 71, 34, Tile.PLAZA)
        fill(2, 27, 16, 27, Tile.PATH); fill(2, 27, 2, 40, Tile.PATH); fill(2, 40, 17, 40, Tile.PATH); fill(16, 27, 16, 40, Tile.PATH); set(17, 27, Tile.PATH)
        fill(5, 30, 12, 36, Tile.WATER)
        fill(23, 3, 43, 12, Tile.LOT)
        slots.forEach { s ->
            for (y in s.y until s.y + s.h) for (x in s.x until s.x + s.w) { set(x, y, Tile.GRASS); block(x, y) }
            set(s.doorX, s.doorY, Tile.DOOR); blocked[s.doorY * MW + s.doorX] = false
        }
        fun p(kind: String, tx: Int, ty: Int, ox: Int = 0, oy: Int = 0, solid: Boolean = true, v: Int = 0) {
            props += PropSpec(kind, tx, ty, ox, oy, solid, v); if (solid) block(tx, ty)
        }
        // árvores do bairro
        listOf(1 to 3, 4 to 2, 8 to 3, 13 to 3, 16 to 4, 0 to 8, 17 to 7, 16 to 9, 1 to 17, 7 to 18, 16 to 18, 0 to 29, 0 to 34, 14 to 30,
            14 to 35, 4 to 42, 10 to 42, 15 to 43, 13 to 38, 3 to 37, 0 to 0, 7 to 0, 12 to 1, 0 to 44, 6 to 44)
            .forEachIndexed { i, (x, y) -> p(if (i % 5 == 3) "pine" else "tree", x, y, v = i % 3) }
        // comércio e trabalho
        listOf(23 to 2, 30 to 2, 37 to 2, 43 to 2, 22 to 14, 44 to 14, 22 to 31, 44 to 32, 49 to 3, 53 to 4, 58 to 2, 63 to 4, 68 to 3, 70 to 10,
            50 to 11, 66 to 12, 70 to 19, 49 to 40, 56 to 41, 63 to 40, 70 to 41, 24 to 41, 31 to 40, 38 to 41, 43 to 42, 27 to 44, 35 to 44,
            52 to 44, 60 to 44, 67 to 44)
            .forEachIndexed { i, (x, y) -> p(if (i % 4 == 2) "pine" else "tree", x, y, v = (i * 7) % 4) }
        listOf(24 to 27, 30 to 26, 36 to 27, 42 to 26, 51 to 27, 57 to 32, 66 to 27, 70 to 33).forEachIndexed { i, (x, y) -> p("tree", x, y, v = (i + 2) % 3) }
        // jardim da Casa e vizinhos
        listOf(1 to 8, 1 to 9, 1 to 10, 1 to 11, 10 to 8, 10 to 9, 10 to 10, 10 to 11).forEach { (x, y) -> p("fence", x, y, oy = -3) }
        p("flowers", 2, 11, oy = -2); p("mailbox", 9, 11, ox = -2, oy = -1)
        listOf(1 to 5, 9 to 5, 0 to 19).forEach { (x, y) -> p("bush", x, y) }
        p("bush", 8, 20, oy = -2); p("bush", 15, 20, oy = -2)
        listOf(0 to 16, 0 to 17, 0 to 18, 16 to 7, 16 to 8, 17 to 16, 17 to 17).forEach { (x, y) -> p("hedge", x, y) }
        p("flowers", 7, 16, oy = -2); p("pot", 8, 17); p("signFlores", 17, 11, oy = -1)
        // praças e calçadas largas
        listOf(26 to 28, 38 to 28, 54 to 31, 59 to 33, 60 to 26, 9 to 28, 13 to 28).forEach { (x, y) -> p("bench", x, y) }
        listOf(23 to 26, 34 to 26, 44 to 28, 50 to 26, 58 to 26, 68 to 26, 22 to 34, 44 to 34, 0 to 11, 16 to 11, 1 to 26, 17 to 39).forEach { (x, y) ->
            p("lamp", x, y, oy = -1); lampPoints += intArrayOf(x * TS + 8, (y + 1) * TS - 1 - 34 + 4)
        }
        p("fountain", 56, 29, ox = 8); block(57, 29); block(55, 29)
        listOf(22 to 30, 30 to 34, 36 to 34, 40 to 26, 62 to 26).forEach { (x, y) -> p("trash", x, y, oy = -2) }
        p("table", 32, 27); p("table", 35, 26)
        p("busstop", 27, 26, oy = 4); p("playground", 9, 38)
        p("kiosk", 53, 27, ox = 4); block(54, 27)
        p("cart", 66, 31); p("bikes", 64, 26, ox = 6); block(65, 26)
        p("signAvenida", 22, 20); p("hydrant", 44, 20); p("hydrant", 22, 33); p("hydrant", 50, 20)
        p("pot", 28, 20, oy = -1); p("pot", 37, 20, oy = -1)
        listOf(4 to 37, 11 to 29, 13 to 37).forEach { (x, y) -> p("rock", x, y) }
        p("dock", 8, 36, oy = 2, solid = false)
        listOf(3 to 28, 15 to 28, 3 to 39, 15 to 39).forEach { (x, y) -> p("flowers", x, y, oy = -2) }
        // estacionamento e vagas da rua do comércio
        for (i in 0 until 9) {
            if (i != 4) p("parkedCar", 24 + i * 2, 6, oy = -2, solid = false, v = i % 7)
            if (i != 6) p("parkedCar", 24 + i * 2, 10, oy = -2, solid = false, v = (i + 3) % 8)
        }
        listOf(23, 27, 31, 36, 41, 51, 57, 63, 68).forEachIndexed { i, x -> p("curbCar", x, 36, solid = false, v = (i * 3) % 8) }

        signals = listOf(Signal(17, 20, true), Signal(22, 26, true), Signal(44, 26, true), Signal(49, 20, true),
            Signal(17, 26, false), Signal(22, 19, false), Signal(44, 19, false), Signal(49, 27, false))
        signals.forEach { block(it.tx, it.ty) }
        lanes = listOf(
            Lane(true, 1, 24 * TS - 4, -70, WW + 70, listOf(18 * TS - 2, 45 * TS - 2)),
            Lane(true, -1, 22 * TS - 1, WW + 70, -70, listOf(22 * TS + 2, 49 * TS + 2)),
            Lane(false, 1, 19 * TS - 1, -40, WH + 40, listOf(21 * TS - 2)),
            Lane(false, -1, 20 * TS - 1, WH + 40, -40, listOf(26 * TS + 2)),
            Lane(false, 1, 46 * TS - 1, -40, WH + 40, listOf(21 * TS - 2)),
            Lane(false, -1, 47 * TS - 1, WH + 40, -40, listOf(26 * TS + 2)),
        )
    }

    fun walkable(x: Int, y: Int): Boolean { val t = tile(x, y) ?: return false; return t in walkKinds && !blocked[y * MW + x] }

    val walkTiles: List<IntArray> by lazy {
        buildList { for (y in 0 until MH) for (x in 0 until MW) if (walkable(x, y) && tile(x, y) != Tile.DOOR) add(intArrayOf(x, y)) }
    }

    /** Ciclo dos semáforos (s): eixo horizontal verde 0–6,5, amarelo até 8; vertical verde 8–14,5, amarelo até 16. */
    fun light(horizontalAxis: Boolean, seconds: Float): Char {
        val c = ((seconds % 16f) + 16f) % 16f
        return if (horizontalAxis) when { c < 6.5f -> 'g'; c < 8f -> 'y'; else -> 'r' }
        else when { c >= 8f && c < 14.5f -> 'g'; c >= 14.5f -> 'y'; else -> 'r' }
    }

    /**
     * Rota pelas calçadas (BFS de 4 direções), só com os cantos: o personagem anda reto entre eles,
     * como num RPG de tiles. Devolve pontos em px de mundo (pés no tile) ou null sem caminho.
     */
    fun route(fromX: Int, fromY: Int, toX: Int, toY: Int): IntArray? {
        val prev = IntArray(MW * MH) { -1 }
        val start = fromY * MW + fromX
        val queue = IntArray(MW * MH); var head = 0; var tail = 0
        queue[tail++] = start; prev[start] = start
        val dx = intArrayOf(1, -1, 0, 0); val dy = intArrayOf(0, 0, 1, -1)
        while (head < tail) {
            val k = queue[head++]; val x = k % MW; val y = k / MW
            if (x == toX && y == toY) break
            for (d in 0 until 4) {
                val nx = x + dx[d]; val ny = y + dy[d]
                if (!walkable(nx, ny)) continue
                val nk = ny * MW + nx
                if (prev[nk] != -1) continue
                prev[nk] = k; queue[tail++] = nk
            }
        }
        val end = toY * MW + toX
        if (prev[end] == -1) return null
        val tiles = ArrayList<Int>()
        var k = end
        while (true) { tiles += k; if (k == prev[k]) break; k = prev[k] }
        tiles.reverse()
        val corners = ArrayList<Int>()
        corners += tiles.first()
        for (i in 1 until tiles.size - 1) {
            val a = tiles[i - 1]; val b = tiles[i]; val c = tiles[i + 1]
            if ((b % MW - a % MW) != (c % MW - b % MW) || (b / MW - a / MW) != (c / MW - b / MW)) corners += b
        }
        if (tiles.size > 1) corners += tiles.last()
        return IntArray(corners.size * 2) { i -> val t = corners[i / 2]; if (i % 2 == 0) (t % MW) * TS + 8 else (t / MW) * TS + 12 }
    }

    fun pathLength(p: IntArray): Int { var s = 0; var i = 2; while (i < p.size) { s += kotlin.math.abs(p[i] - p[i - 2]) + kotlin.math.abs(p[i + 1] - p[i - 1]); i += 2 }; return s }

    enum class Dir { UP, DOWN, LEFT, RIGHT }

    /** Ponto a [d] px do início do caminho, com a direção do trecho. */
    fun pointAt(p: IntArray, d: Float, out: FloatArray): Dir {
        var rest = d
        var i = 2
        if (p.size < 4) { out[0] = p[0].toFloat(); out[1] = p[1].toFloat(); return Dir.DOWN }
        while (i < p.size) {
            val ax = p[i - 2]; val ay = p[i - 1]; val bx = p[i]; val by = p[i + 1]
            val len = kotlin.math.abs(bx - ax) + kotlin.math.abs(by - ay)
            if (rest <= len || i == p.size - 2) {
                val t = if (len == 0) 0f else (rest / len).coerceIn(0f, 1f)
                out[0] = ax + (bx - ax) * t; out[1] = ay + (by - ay) * t
                return dirOf(bx - ax, by - ay)
            }
            rest -= len; i += 2
        }
        out[0] = p[p.size - 2].toFloat(); out[1] = p[p.size - 1].toFloat(); return Dir.DOWN
    }

    fun dirOf(dx: Int, dy: Int): Dir = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) (if (dx > 0) Dir.RIGHT else Dir.LEFT) else (if (dy > 0) Dir.DOWN else Dir.UP)
}
