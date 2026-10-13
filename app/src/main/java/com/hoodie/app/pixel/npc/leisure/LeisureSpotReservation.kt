package com.hoodie.app.pixel.npc.leisure

/**
 * Reservas de lugares do Passeio por intervalo de tempo. Um lugar com capacidade 1 nunca tem
 * dois ocupantes no mesmo instante. O banco tem um único assento e o Hoodie tem prioridade:
 * enquanto ele está indo ao banco ou sentado nele ([hoodieClaims]), nenhum visitante senta, e
 * quem já estava sentado levanta e sai (a sessão corta o plano no início da reserva do Hoodie).
 */
class LeisureSpotReservation {
    private class Hold(val spot: LeisureNpcSpot, val holder: String, val from: Long, var until: Long)

    /** Intervalo em que o Hoodie quer o banco; [end] null = ainda quer. */
    class HoodieClaim(val start: Long, var end: Long?) {
        fun covers(t: Long) = t >= start && (end?.let { t < it } ?: true)
    }

    private val holds = ArrayList<Hold>()
    val hoodieClaims = ArrayList<HoodieClaim>()

    fun clearNpcHolds() = holds.clear()

    fun isFree(spot: LeisureNpcSpot, from: Long, until: Long, holder: String, capacity: Int = 1): Boolean {
        if (spot == LeisureNpcSpot.BENCH_SEAT && hoodieClaimsBench(from, until)) return false
        var overlapping = 0
        for (h in holds) {
            if (h.spot != spot || h.holder == holder) continue
            if (h.from < until && from < h.until) overlapping++
        }
        return overlapping < capacity
    }

    fun reserve(spot: LeisureNpcSpot, holder: String, from: Long, until: Long) {
        if (until > from) holds += Hold(spot, holder, from, until)
    }

    /** Encurta a reserva de [holder] em [spot] que ainda estaria ativa em [at] (saída antecipada). */
    fun releaseAt(spot: LeisureNpcSpot, holder: String, at: Long) {
        holds.forEach { if (it.spot == spot && it.holder == holder && it.from <= at && it.until > at) it.until = at }
    }

    fun holderAt(spot: LeisureNpcSpot, t: Long): String? = holds.firstOrNull { it.spot == spot && t >= it.from && t < it.until }?.holder

    fun hoodieClaimAt(t: Long): Boolean = hoodieClaims.any { it.covers(t) }

    /** O Hoodie quer o banco em algum momento de [from, until)? */
    fun hoodieClaimsBench(from: Long, until: Long): Boolean =
        hoodieClaims.any { c -> c.start < until && (c.end?.let { from < it } ?: true) }

    /** Primeiro instante em (from, until) em que começa uma reserva do Hoodie (null = nenhum). */
    fun firstHoodieClaimStartIn(from: Long, until: Long): Long? =
        hoodieClaims.map { it.start }.filter { it in (from + 1) until until }.minOrNull()

    /** Descarta reservas que terminaram antes de [before]: o histórico não cresce sem limite. */
    fun prune(before: Long) {
        holds.removeAll { it.until < before }
    }
}
