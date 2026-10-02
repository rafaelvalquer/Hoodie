package com.hoodie.app.core.error

/** Domain failures carry data, never localized presentation text. */
sealed interface AppError

fun Exception.appErrorOr(fallback: AppError): AppError =
    if (this is PlaceException) error else fallback
