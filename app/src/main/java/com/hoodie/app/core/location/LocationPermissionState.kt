package com.hoodie.app.core.location

/**
 * O que o Hoodie consegue fazer com a localização agora. Geofences exigem posição
 * precisa **e** "Permitir o tempo todo" (Android 10+); só isso é [BACKGROUND].
 */
enum class LocationPermissionState {
    /** Nenhuma permissão de localização. */
    NONE,

    /** Usuário escolheu "aproximada" (Android 12+): sem geofence. */
    APPROXIMATE_ONLY,

    /** Precisa, só durante o uso: cadastra lugares, mas não percebe chegadas com o app fechado. */
    FOREGROUND,

    /** Precisa + o tempo todo: geofences funcionam com o app fechado. */
    BACKGROUND,

    /** Permissão concedida, mas a localização do sistema está desligada. */
    LOCATION_DISABLED;

    val canMonitorGeofences: Boolean get() = this == BACKGROUND
    val canReadPosition: Boolean get() = this == APPROXIMATE_ONLY || this == FOREGROUND || this == BACKGROUND
    val needsBackgroundStep: Boolean get() = this == FOREGROUND || this == APPROXIMATE_ONLY

    /** Compatibilidade com a UI antiga (banner da Home). */
    fun toStatus(): LocationStatus = when (this) {
        NONE -> LocationStatus.NO_PERMISSION
        LOCATION_DISABLED -> LocationStatus.DISABLED
        APPROXIMATE_ONLY, FOREGROUND -> LocationStatus.NO_BACKGROUND
        BACKGROUND -> LocationStatus.OK
    }

    companion object {
        /** Regra pura (testável): permissões + estado do sistema → estado. */
        fun resolve(fine: Boolean, coarse: Boolean, background: Boolean, enabled: Boolean, sdk: Int): LocationPermissionState = when {
            !fine && !coarse -> NONE
            !enabled -> LOCATION_DISABLED
            !fine -> APPROXIMATE_ONLY
            // Antes do Android 10 não existe permissão separada de segundo plano.
            sdk < 29 || background -> BACKGROUND
            else -> FOREGROUND
        }
    }
}
