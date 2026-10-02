package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.error.AppError
import com.hoodie.app.pixel.animation.AnimationId

sealed interface HomeUiEvent {
    data class React(val animation: AnimationId) : HomeUiEvent
    data class ShowError(val error: AppError) : HomeUiEvent
}
