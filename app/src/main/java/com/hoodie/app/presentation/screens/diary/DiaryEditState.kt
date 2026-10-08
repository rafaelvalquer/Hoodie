package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.domain.correction.DiaryCorrection

data class DiaryEditState(val request: DiaryCorrection?, val places: List<PlaceEntity> = emptyList(), val saving: Boolean = false, val error: String? = null)
