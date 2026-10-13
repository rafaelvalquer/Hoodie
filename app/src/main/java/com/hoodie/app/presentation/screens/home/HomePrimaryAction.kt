package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.model.PlaceType

/** Por que o acompanhamento automático não está funcionando como deveria. */
enum class MonitoringIssue { NO_PERMISSION, NO_BACKGROUND, DISABLED, PROBABLE_ROUTINE }

/** A única pendência que a Home destaca por vez. */
sealed interface HomePrimaryAction {
    data class PendingQuestion(val questionId: Long) : HomePrimaryAction
    data class MonitoringWarning(val reason: MonitoringIssue) : HomePrimaryAction
    data class SuggestPlace(val type: PlaceType) : HomePrimaryAction
}