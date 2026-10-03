package com.hoodie.app.core.error

sealed interface SystemSettingsError : AppError {
    data object Unavailable : SystemSettingsError
}
