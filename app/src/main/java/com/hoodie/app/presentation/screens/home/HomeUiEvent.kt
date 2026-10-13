package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.error.AppError
import com.hoodie.app.pixel.animation.AnimationId

sealed interface HomeUiEvent {
    data class React(val animation: AnimationId) : HomeUiEvent
    data class ShowError(val error: AppError) : HomeUiEvent
    data class ShowMessage(val message: String) : HomeUiEvent
    /** Correção aplicada que pode ser desfeita (Snackbar com "Desfazer" por ~15 s). */
    data class OfferUndo(val operationId: String, val message: String) : HomeUiEvent
}
