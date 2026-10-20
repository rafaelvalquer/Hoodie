package com.hoodie.app.pixel.diary.city

import com.hoodie.app.engine.diary.city.CityTown
import com.hoodie.app.engine.diary.city.CityTown.TS
import com.hoodie.app.pixel.renderer.PixelBuffer
import java.util.Random

/**
 * A vida da cidade, que não faz parte da Jornada do usuário: moradores andando e entrando em
 * lojas, trânsito com semáforo e fila, pombos que voam quando o Hoodie chega perto, patos,
 * borboletas, fumaça das chaminés e pássaros. Avança pelo relógio da tela (não pelo replay).
 */
internal class CityAmbient(seed: Long = 7L) {
    private val rnd = Random(seed)

    class Resident(val palette: CityCharacters.Palette, var tx: Int, var ty: Int, var speed: Float, val blink: Float) {
        var x = tx * TS + 8f; var y = ty * TS + 12f; var dir = CityTown.Dir.DOWN
        var path: IntArray? = null; var length = 0; var d = 0f; var wait = 0f; var hidden = 0f; var shop = false; var dest = intArrayOf(tx, ty)
        val walking get() = path != null && wait <= 0f
    }
    class Talker(val palette: CityCharacters.Palette, val x: Float, val y: Float, val dir: CityTown.Dir, val sitting: Boolean = false)
    class Vehicle(val lane: CityTown.Lane, var image: PixelBuffer, var length: Int, var vmax: Float, var s: Float) { var v = vmax * 0.8f }
    class Pigeon(val hx: Float, val hy: Float, var face: Int) { var x = hx; var y = hy; var t = 0f; var fly = 0f; var gone = 0f }
    class Smoke(var x: Float, var y: Float, val max: Float) { var life = 0f }

    val residents = ArrayList<Resident>()
    val talkers = listOf(
        Talker(CityCharacters.RESIDENTS[3], 51f * TS + 8, 32f * TS + 12, CityTown.Dir.RIGHT),
        Talker(CityCharacters.RESIDENTS[4], 52f * TS + 14, 32f * TS + 12, CityTown.Dir.LEFT),
        Talker(CityCharacters.RESIDENTS[5], 34f * TS + 4, 28f * TS + 10, CityTown.Dir.DOWN, sitting = true),
    )
    val vehicles = ArrayList<Vehicle>()
    val pigeons = listOf(30 to 27, 31 to 28, 40 to 27, 58 to 30, 59 to 30, 52 to 33, 25 to 26, 64 to 28)
        .mapIndexed { i, (tx, ty) -> Pigeon(tx * TS + 4f + (i % 3) * 3, ty * TS + 10f, if (i % 2 == 1) 1 else -1) }
    val smoke = ArrayList<Smoke>()
    var birdsX = -40f; var birdsY = 300f; var birdsWait = 6f
    private val shops = listOf("bakery", "market", "shops", "pharmacy", "restaurant", "office2", "office3", "house2", "house3", "house4").map(CityTown.slotById::getValue)

    init {
        listOf(10 to 12, 25 to 21, 36 to 21, 42 to 25, 30 to 35, 55 to 25, 64 to 21, 3 to 27, 52 to 31, 40 to 38, 60 to 35, 18 to 30, 48 to 16)
            .forEachIndexed { i, (x, y) -> residents += Resident(CityCharacters.RESIDENTS[i % CityCharacters.RESIDENTS.size], x, y, 20f + rnd.nextFloat() * 12f, rnd.nextFloat() * 4f).also { it.wait = rnd.nextFloat() * 3f } }
        CityTown.lanes.forEach { lane ->
            val n = if (lane.horizontal) 4 else 2; val span = kotlin.math.abs(lane.to - lane.from).toFloat()
            for (i in 0 until n) spawn(lane, lane.from + lane.dir * span * (i + rnd.nextFloat() * 0.5f) / n)
        }
    }

    private fun spawn(lane: CityTown.Lane, s: Float) {
        val k = rnd.nextInt(100)
        val (img, vmax) = if (lane.horizontal) {
            val base = if (k < 10) CityArt.bus else CityArt.sideCars[k % CityArt.sideCars.size]
            (if (lane.dir < 0) CityArt.flipped(base) else base) to (if (k < 10) 36f else 44f + rnd.nextFloat() * 16f)
        } else { val pair = CityArt.topCars[k % CityArt.topCars.size]; (if (lane.dir > 0) pair.first else pair.second) to 38f + rnd.nextFloat() * 12f }
        vehicles += Vehicle(lane, img, if (lane.horizontal) img.width else img.height, vmax, s)
    }

    private fun front(v: Vehicle) = if (v.lane.dir > 0) v.s + v.length else v.s
    private fun rear(v: Vehicle) = if (v.lane.dir > 0) v.s else v.s + v.length

    fun update(dt: Float, seconds: Float, hoodieX: Float, hoodieY: Float) {
        residents.forEach { updateResident(it, dt) }
        updateTraffic(dt, seconds)
        pigeons.forEach { p ->
            when {
                p.gone > 0 -> { p.gone -= dt; if (p.gone <= 0) { p.x = p.hx; p.y = p.hy; p.fly = 0f } }
                p.fly > 0 -> { p.fly += dt; p.x += 50 * p.face * dt; p.y -= 70 * dt; if (p.fly > 1.4f) p.gone = 8 + rnd.nextFloat() * 6 }
                kotlin.math.abs(p.x - hoodieX) < 22 && kotlin.math.abs(p.y - hoodieY) < 16 -> { p.fly = 0.01f; p.face = if (p.x < hoodieX) -1 else 1 }
                else -> { p.t += dt; if (p.t > 1.6f + (p.hx.toInt() % 7) * 0.2f) { p.t = 0f; p.x = (p.x + (rnd.nextFloat() - .5f) * 6).coerceIn(p.hx - 10, p.hx + 10); p.face = if (rnd.nextBoolean()) 1 else -1 } }
            }
        }
        birdsWait -= dt
        if (birdsWait <= 0) { birdsX += 46 * dt; if (birdsX > CityTown.WW + 60) { birdsX = -60f; birdsY = 80 + rnd.nextFloat() * (CityTown.WH - 200); birdsWait = 10 + rnd.nextFloat() * 8 } }
    }

    /** Fumaça das chaminés dos prédios desta cena. */
    fun updateSmoke(dt: Float, buildings: List<PaintedBuilding>) {
        buildings.forEach { b -> b.chimneys.forEach { (cx, cy) -> if (rnd.nextFloat() < dt * 2.2f) smoke += Smoke(b.slot.px + cx + (rnd.nextFloat() - .5f) * 2, b.slot.py + cy.toFloat(), 2.6f + rnd.nextFloat()) } }
        val it = smoke.iterator()
        while (it.hasNext()) { val s = it.next(); s.life += dt; s.y -= 9 * dt; s.x += 4 * dt; if (s.life > s.max) it.remove() }
    }

    private fun newPath(r: Resident) {
        repeat(6) {
            val target = if (rnd.nextFloat() < 0.3f) { r.shop = true; val b = shops[rnd.nextInt(shops.size)]; intArrayOf(b.doorX, b.frontY) }
                else { r.shop = false; CityTown.walkTiles[rnd.nextInt(CityTown.walkTiles.size)] }
            val p = CityTown.route(r.tx, r.ty, target[0], target[1])
            if (p != null && p.size >= 4) { r.path = p; r.length = CityTown.pathLength(p); r.d = 0f; r.dest = target; return }
        }
        r.wait = 2f
    }

    private val tmp = FloatArray(2)
    private fun updateResident(r: Resident, dt: Float) {
        if (r.hidden > 0) { r.hidden -= dt; if (r.hidden <= 0) r.dir = CityTown.Dir.DOWN; return }
        if (r.wait > 0) { r.wait -= dt; if (r.wait <= 0) newPath(r); return }
        val p = r.path ?: run { newPath(r); return }
        r.d += r.speed * dt
        r.dir = CityTown.pointAt(p, minOf(r.d, r.length.toFloat()), tmp); r.x = tmp[0]; r.y = tmp[1]
        if (r.d >= r.length) {
            r.tx = r.dest[0]; r.ty = r.dest[1]; r.path = null
            if (r.shop) { r.dir = CityTown.Dir.UP; r.hidden = 4 + rnd.nextFloat() * 7; r.wait = 0.4f } else r.wait = 1 + rnd.nextFloat() * 4
        }
    }

    private fun updateTraffic(dt: Float, seconds: Float) {
        CityTown.lanes.forEach { lane ->
            val list = vehicles.filter { it.lane === lane }.sortedByDescending { lane.dir * front(it) }
            val light = CityTown.light(lane.horizontal, seconds)
            list.forEachIndexed { i, v ->
                var limit = Float.MAX_VALUE
                list.getOrNull(i - 1)?.let { ahead -> limit = minOf(limit, lane.dir * (rear(ahead) - front(v)) - 6) }
                if (light != 'g') lane.stops.forEach { stop ->
                    val d = lane.dir * (stop - front(v))
                    if (d >= -1 && d < 70 && !(light == 'y' && d < 10)) limit = minOf(limit, d)
                }
                val target = if (limit < 1) 0f else minOf(v.vmax, limit * 1.6f)
                v.v = (v.v + (target - v.v).coerceIn(-90 * dt, 40 * dt)).coerceAtLeast(0f)
                v.s += lane.dir * v.v * dt
            }
            list.forEach { v ->
                if (lane.dir * (front(v) - lane.to) > 0) {
                    val last = list.last(); val start = lane.from - if (lane.dir > 0) v.length else 0
                    if (last === v || lane.dir * (rear(last) - (start + if (lane.dir > 0) v.length else 0)) > 30) { v.s = start.toFloat(); v.v = v.vmax * 0.7f }
                }
            }
        }
    }

    fun vehicleX(v: Vehicle) = if (v.lane.horizontal) v.s else (v.lane.pos - 1).toFloat()
    fun vehicleY(v: Vehicle) = if (v.lane.horizontal) v.lane.pos.toFloat() else v.s
}
