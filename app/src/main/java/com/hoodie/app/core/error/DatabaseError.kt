package com.hoodie.app.core.error

sealed interface DatabaseError : AppError {
    data object Unavailable : DatabaseError
    data object ReadFailed : DatabaseError
    data object WriteFailed : DatabaseError
}
