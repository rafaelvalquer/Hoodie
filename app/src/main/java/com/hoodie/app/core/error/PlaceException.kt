package com.hoodie.app.core.error

sealed class PlaceException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotFound(val placeId: Long) : PlaceException("Place $placeId não encontrado")
    class SaveFailed(cause: Throwable) : PlaceException("Falha ao salvar lugar", cause)
}
