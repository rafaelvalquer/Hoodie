package com.hoodie.app.presentation.screens.phoneinsights

import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.ContextAppUsage
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.engine.deviceusage.DigitalReaction
import com.hoodie.app.engine.deviceusage.HoodieDigitalReactions
import java.time.LocalDate
import com.hoodie.app.BuildConfig

data class UsagePermissionUiState(
    val permission: UsagePermissionState,
    val showRestrictedHelp: Boolean = BuildConfig.DEBUG && permission == UsagePermissionState.DENIED,
)

enum class PhoneInsightsStatus {
    /** Usuário desligou a análise em Ajustes e não há histórico salvo. */
    DISABLED,
    /** Falta liberar "Acesso ao uso" (e não há histórico salvo para mostrar). */
    NEEDS_PERMISSION,
    LOADING,
    ERROR,
    /** Permissão ok, mas nada registrado neste dia. */
    EMPTY,
    READY,
}

sealed interface PhoneInsightsUiEvent {
    data object OpenUsageSettings : PhoneInsightsUiEvent
}

data class PhoneInsightsUiState(
    val date: LocalDate,
    val permission: UsagePermissionState = UsagePermissionState.DENIED,
    val settings: DigitalSettings = DigitalSettings(),
    val insights: DailyPhoneInsights? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val catName: String = "Hoodie",
    val selectedApp: AppUsageEntry? = null,
) {
    val permissionUi: UsagePermissionUiState get() = UsagePermissionUiState(permission)
    val status: PhoneInsightsStatus get() = statusOf(permission, settings, insights, isLoading, error)

    val reaction: DigitalReaction get() = HoodieDigitalReactions.react(insights, catName)

    /** Onde o app escolhido foi usado (para o bottom sheet). */
    val selectedAppByContext: List<ContextAppUsage>
        get() = selectedApp?.let { app ->
            insights?.usageByContext.orEmpty().mapNotNull { ctx -> ctx.apps.firstOrNull { it.packageName == app.packageName } }
        }.orEmpty()

    /** Há dados salvos, mas a permissão caiu: mostra o histórico com um aviso. */
    val showPermissionBanner: Boolean
        get() = insights != null && settings.analysisEnabled && permission != UsagePermissionState.GRANTED

    companion object {
        fun statusOf(
            permission: UsagePermissionState,
            settings: DigitalSettings,
            insights: DailyPhoneInsights?,
            isLoading: Boolean,
            error: String?,
        ): PhoneInsightsStatus = when {
            insights != null && !insights.isEmpty -> PhoneInsightsStatus.READY
            isLoading -> PhoneInsightsStatus.LOADING
            !settings.analysisEnabled -> PhoneInsightsStatus.DISABLED
            permission != UsagePermissionState.GRANTED -> PhoneInsightsStatus.NEEDS_PERMISSION
            error != null -> PhoneInsightsStatus.ERROR
            else -> PhoneInsightsStatus.EMPTY
        }
    }
}
