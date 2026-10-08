package com.hoodie.app.domain.home

import com.hoodie.app.engine.context.ContextEngine
import javax.inject.Inject

data class ConfirmCurrentContext(val expectedContextEventId: Long, val confirmedAt: Long)

class ConfirmCurrentContextUseCase @Inject constructor(private val context: ContextEngine) {
    suspend operator fun invoke(request: ConfirmCurrentContext): Boolean =
        context.confirmCurrentContext(request.expectedContextEventId, request.confirmedAt)
}
