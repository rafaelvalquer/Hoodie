package com.hoodie.app.core.time

/** Período do dia real, usado por cenário e iluminação. */
enum class DayPeriod(val label: String) {
    MORNING("Manhã"),
    DAY("Dia"),
    EVENING("Entardecer"),
    NIGHT("Noite");

    companion object {
        fun of(hour: Int): DayPeriod = when (hour) {
            in 6..8 -> MORNING
            in 9..17 -> DAY
            in 18..20 -> EVENING
            else -> NIGHT
        }
    }
}
