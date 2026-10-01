package com.hoodie.app.engine.hoodie

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.HoodieActivity.*
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.UserContextType

/**
 * Necessidades só direcionam comportamento. Nada é punitivo: valores ficam em
 * 0..100 e nunca disparam "game over".
 */
object NeedsEngine {

    /** Variação por hora: energia, fome, humor, social, foco. */
    private data class Rate(val energy: Int, val hunger: Int, val mood: Int, val social: Int, val focus: Int)

    private val awake = Rate(-5, +7, 0, -1, -2)

    private val rates: Map<HoodieActivity, Rate> = mapOf(
        SLEEPING to Rate(+14, +2, +2, 0, +8),
        WAKING_UP to Rate(+5, +6, 0, 0, 0),
        BREAKFAST to Rate(+10, -180, +20, 0, +10),
        EATING to Rate(+5, -180, +20, +5, 0),
        WORKING to Rate(-8, +7, -2, +1, -6),
        COFFEE to Rate(+120, +4, +30, 0, +90),
        RESTING to Rate(+15, +5, +6, 0, +10),
        GAMING to Rate(-6, +7, +15, +4, -5),
        READING to Rate(-3, +6, +8, 0, +6),
        TRAINING to Rate(-20, +12, +15, +2, +4),
        COOKING to Rate(-4, +6, +6, 0, 0),
        WALKING to Rate(-8, +8, +12, +5, +3),
        WATCHING_TV to Rate(-2, +6, +10, +2, -2),
        PHONE to Rate(-3, +6, +4, +20, -5),
        CLEANING to Rate(-8, +8, +6, 0, +2),
        COMMUTING to Rate(-6, +7, -1, +2, -1),
        IDLE to awake,
    )

    fun apply(needs: Needs, activity: HoodieActivity, context: UserContextType, minutes: Long): Needs {
        if (minutes <= 0) return needs
        val r = rates[activity] ?: awake
        val f = minutes / 60.0
        val socialBonus = if (context == UserContextType.LUNCH || context == UserContextType.VISITING) 6 else 0
        var mood = needs.mood + r.mood * f
        if (needs.hunger > 80) mood -= 4 * f
        return Needs(
            energy = clamp(needs.energy + r.energy * f),
            hunger = clamp(needs.hunger + r.hunger * f),
            mood = clamp(mood),
            social = clamp(needs.social + (r.social + socialBonus) * f),
            focus = clamp(needs.focus + r.focus * f),
        )
    }

    private fun clamp(v: Double): Int = v.toInt().coerceIn(0, 100)
}
