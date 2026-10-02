package com.hoodie.app.core.error

sealed interface PlaceError : AppError {
    data class NotFound(val placeId: Long) : PlaceError
    data object SaveFailed : PlaceError
}
