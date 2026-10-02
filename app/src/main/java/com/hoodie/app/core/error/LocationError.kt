package com.hoodie.app.core.error

sealed interface LocationError : AppError {
    data object Unavailable : LocationError
    data object PermissionDenied : LocationError
    data object Disabled : LocationError
}
