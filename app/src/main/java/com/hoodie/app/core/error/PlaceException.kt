package com.hoodie.app.core.error

sealed class PlaceException(val error: PlaceError, message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val placeId: Long) : PlaceException(PlaceError.NotFound(placeId), "Place missing: $placeId")
    class SaveFailed(cause: Throwable) : PlaceException(PlaceError.SaveFailed, "Place write failed", cause)
}
