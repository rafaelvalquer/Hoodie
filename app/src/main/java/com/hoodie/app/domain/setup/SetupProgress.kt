package com.hoodie.app.domain.setup

enum class SetupStage { NOT_STARTED, BASICS_COMPLETED, LOCATION_READY, ROUTINE_READY, FULLY_CONFIGURED }

/** Itens de configuração que podem ser feitos depois da entrada inicial, na ordem sugerida. */
enum class SetupItem { HOME, BACKGROUND_LOCATION, WORK, NOTIFICATIONS }

/**
 * Quanto do acompanhamento automático já está configurado. Independente de `onboardingDone`,
 * que só diz que a pessoa concluiu a entrada inicial.
 */
data class SetupProgress(
    val stage: SetupStage,
    val homeConfigured: Boolean,
    val workConfigured: Boolean,
    val backgroundLocationConfigured: Boolean,
    val notificationsConfigured: Boolean,
    val demoCompleted: Boolean,
) {
    /** Itens ainda abertos; vazio quando está tudo configurado. */
    val pending: List<SetupItem>
        get() = buildList {
            if (!homeConfigured) add(SetupItem.HOME)
            if (!backgroundLocationConfigured) add(SetupItem.BACKGROUND_LOCATION)
            if (!workConfigured) add(SetupItem.WORK)
            if (!notificationsConfigured) add(SetupItem.NOTIFICATIONS)
        }

    val next: SetupItem? get() = pending.firstOrNull()

    companion object {
        fun of(onboardingDone: Boolean, home: Boolean, work: Boolean, background: Boolean, notifications: Boolean, demo: Boolean): SetupProgress {
            val location = home && background
            val stage = when {
                !onboardingDone -> SetupStage.NOT_STARTED
                !location -> SetupStage.BASICS_COMPLETED
                !work -> SetupStage.LOCATION_READY
                !notifications -> SetupStage.ROUTINE_READY
                else -> SetupStage.FULLY_CONFIGURED
            }
            return SetupProgress(stage, home, work, background, notifications, demo)
        }
    }
}