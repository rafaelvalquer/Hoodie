package com.hoodie.app.core.error

sealed interface UsageAccessError : AppError {
    data object PermissionDenied : UsageAccessError
    data object Unavailable : UsageAccessError
    data object ReadFailed : UsageAccessError
}
