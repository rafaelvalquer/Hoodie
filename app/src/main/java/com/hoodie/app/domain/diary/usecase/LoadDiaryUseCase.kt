package com.hoodie.app.domain.diary.usecase

import com.hoodie.app.data.repository.DiaryRepository
import com.hoodie.app.domain.diary.model.DailyDiary
import java.time.LocalDate
import javax.inject.Inject

class LoadDiaryUseCase @Inject constructor(private val repository: DiaryRepository) {
    suspend operator fun invoke(date: LocalDate): DailyDiary = repository.loadDiary(date)
}
