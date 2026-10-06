package com.hoodie.app.domain.daycycle

enum class WakeReason {
    PHONE_SUSTAINED,
    MOBILITY_CONFIRMED,
    HOME_EXIT,
    CONTEXT_CHANGE,
    SCREEN_ACTIVITY,
    HOODIE_WAKE,
    SCHEDULE_FALLBACK,
}

enum class WakeConfidence { HIGH, MEDIUM, LOW }
