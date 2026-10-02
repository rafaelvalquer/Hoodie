package com.hoodie.app.presentation.screens.diary

import java.time.LocalDate

sealed interface DiaryUiEvent {
    data class ShowPlaceDetails(val date: LocalDate, val nodeId: String) : DiaryUiEvent
}
