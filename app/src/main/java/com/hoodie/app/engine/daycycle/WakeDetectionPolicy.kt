package com.hoodie.app.engine.daycycle

/** Política pura: limiares compartilhados por detector e builders de evidência. */
object WakeDetectionPolicy {
    const val PHONE_SCORE = 70
    const val SCREEN_SCORE = 70
    const val MOBILITY_SCORE = 100
    const val HOME_EXIT_SCORE = 90
    const val CONTEXT_SCORE = 65
    const val HOODIE_SCORE = 20
}
