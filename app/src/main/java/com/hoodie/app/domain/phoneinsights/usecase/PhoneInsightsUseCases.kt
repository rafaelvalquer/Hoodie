package com.hoodie.app.domain.phoneinsights.usecase

import com.hoodie.app.data.repository.DeviceUsageRepository
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import java.time.LocalDate
import javax.inject.Inject

class LoadPhoneInsightsUseCase @Inject constructor(private val repository: DeviceUsageRepository) {
    suspend operator fun invoke(date: LocalDate): DailyPhoneInsights? = repository.insightsFor(date)
}

class SetAppCategoryUseCase @Inject constructor(private val repository: DeviceUsageRepository) {
    /** null volta para a categoria automática. */
    suspend operator fun invoke(packageName: String, category: HoodieAppCategory?) = repository.setCategoryOverride(packageName, category)
}

class ClearDigitalHistoryUseCase @Inject constructor(private val repository: DeviceUsageRepository) {
    suspend operator fun invoke() = repository.clearHistory()
}
