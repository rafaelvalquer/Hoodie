package com.hoodie.app.presentation.screens.onboarding

sealed interface OnboardingUiEvent {
    data object OpenAppSettings : OnboardingUiEvent
}
