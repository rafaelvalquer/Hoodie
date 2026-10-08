package com.hoodie.app.domain.correction

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.mobility.MovementMode

enum class CorrectionTargetType { CONTEXT, CONTEXT_BOUNDARY, MOBILITY_SEGMENT }

data class DiaryCorrection(
    val targetType: CorrectionTargetType,
    val targetId: Long,
    val context: UserContextType?,
    val placeId: Long?,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: MovementMode? = null,
)
