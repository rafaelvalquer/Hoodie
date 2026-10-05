package com.hoodie.app.pixel.npc

/**
 * Reações raras e determinísticas: o tempo é dividido em janelas de [WINDOW_MS]; em
 * ~1 de cada [RARITY] janelas (escolhidas por hash de seed + janela) o NPC parado
 * reage ao Hoodie por [NpcPoseLibrary.REACTION_MS]. Nada é sorteado por quadro.
 */
object NpcReactions {
    const val WINDOW_MS = 20_000L
    const val RARITY = 9

    fun at(seed: Int, timeMs: Long): Pair<NpcReaction, Long>? {
        val shifted = timeMs + seed * 7_919L
        val window = Math.floorDiv(shifted, WINDOW_MS)
        val local = Math.floorMod(shifted, WINDOW_MS)
        // Começa no meio da janela para não coincidir com a troca de passo.
        val start = 8_000L
        if (local !in start until start + NpcPoseLibrary.REACTION_MS) return null
        val h = hash(seed, window)
        if (h % RARITY != 0) return null
        return NpcReaction.entries[(h / RARITY) % NpcReaction.entries.size] to (local - start)
    }

    private fun hash(seed: Int, window: Long): Int {
        var x = seed.toLong() * 0x9E3779B97F4A7C15uL.toLong() + window * 0xBF58476D1CE4E5B9uL.toLong()
        x = x xor (x ushr 31)
        x *= 0x94D049BB133111EBuL.toLong()
        x = x xor (x ushr 29)
        return (x and 0x7FFFFFFF).toInt()
    }
}
