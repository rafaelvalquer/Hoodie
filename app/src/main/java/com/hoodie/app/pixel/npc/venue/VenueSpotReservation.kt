package com.hoodie.app.pixel.npc.venue

/**
 * Reservas de pontos por intervalo de tempo. Um ponto de capacidade N nunca tem mais de N
 * ocupantes no mesmo instante. Os pontos do Hoodie ([hoodieSpots]: a cadeira dele, o balcão onde
 * ele é atendido…) nunca ficam livres para os NPCs — a cena é desenhada para que eles nem
 * precisem passar por ali.
 */
class VenueSpotReservation(private val hoodieSpots: Set<String> = emptySet()) {
    private class Hold(val spot: String, val holder: String, val from: Long, var until: Long)

    private val holds = ArrayList<Hold>()

    fun clear() = holds.clear()

    fun isHoodieSpot(spot: String) = spot in hoodieSpots

    fun isFree(spot: String, from: Long, until: Long, holder: String, capacity: Int = 1): Boolean {
        if (spot in hoodieSpots) return false
        var overlapping = 0
        for (h in holds) {
            if (h.spot != spot || h.holder == holder) continue
            if (h.from < until && from < h.until) overlapping++
        }
        return overlapping < capacity
    }

    fun reserve(spot: String, holder: String, from: Long, until: Long) {
        require(spot !in hoodieSpots) { "$holder tentou reservar o ponto do Hoodie $spot" }
        if (until > from) holds += Hold(spot, holder, from, until)
    }

    /** Encurta a reserva de [holder] em [spot] que ainda estaria ativa em [at] (saída antecipada). */
    fun releaseAt(spot: String, holder: String, at: Long) {
        holds.forEach { if (it.spot == spot && it.holder == holder && it.from <= at && it.until > at) it.until = at }
    }

    fun holdersAt(spot: String, t: Long): List<String> = holds.filter { it.spot == spot && t >= it.from && t < it.until }.map { it.holder }

    /** Descarta reservas que terminaram antes de [before]: o histórico não cresce sem limite. */
    fun prune(before: Long) {
        holds.removeAll { it.until < before }
    }

    internal val size: Int get() = holds.size
}
