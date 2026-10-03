package com.hoodie.app.presentation.screens.phoneinsights

import com.hoodie.app.presentation.common.runSystemAction
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import com.hoodie.app.presentation.common.CollectUiEvents
import com.hoodie.app.presentation.common.appErrorText
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.pixel.phoneinsights.RetroFontStyles
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aba "Digital" do Diário. Não tem rolagem própria nem seletor de data: vive
 * dentro da coluna do Diário e segue a data escolhida lá.
 */
@Composable
fun PhoneInsightsScreen(date: LocalDate, modifier: Modifier = Modifier, vm: PhoneInsightsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zone = vm.zone
    LaunchedEffect(date) { vm.selectDate(date) }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openPermission = { runSystemAction({ cause -> android.util.Log.e("PhoneInsightsScreen", "Failed to open usage settings", cause); vm.permissionLaunchFailed() }) { context.startActivity(vm.permissionIntent()) } }
    CollectUiEvents(vm.events) { event ->
        when (event) {
            PhoneInsightsUiEvent.OpenUsageSettings -> openPermission()
            is PhoneInsightsUiEvent.ShowError -> scope.launch { snackbar.showSnackbar(context.appErrorText(event.error)) }
        }
    }

    PhoneInsightsContent(
        state = state,
        today = vm.today,
        zone = zone,
        modifier = modifier,
        snackbar = snackbar,
        actions = PhoneInsightsActions(
            enableAnalysis = { vm.enableAnalysis() },
            openPermission = openPermission,
            openAppDetails = { runSystemAction({ vm.permissionLaunchFailed() }) { context.startActivity(vm.appDetailsIntent()) } },
            refresh = { vm.refresh() },
            openApp = { vm.openApp(it) },
            closeApp = { vm.closeApp() },
            changeCategory = { packageName, category -> vm.changeCategory(packageName, category) },
        ),
    )
}

internal data class PhoneInsightsActions(
    val enableAnalysis: () -> Unit = {},
    val openPermission: () -> Unit = {},
    val openAppDetails: () -> Unit = {},
    val refresh: () -> Unit = {},
    val openApp: (com.hoodie.app.domain.phoneinsights.model.AppUsageEntry) -> Unit = {},
    val closeApp: () -> Unit = {},
    val changeCategory: (String, com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory?) -> Unit = { _, _ -> },
)

@Composable
internal fun PhoneInsightsContent(
    state: PhoneInsightsUiState,
    today: LocalDate,
    zone: ZoneId,
    modifier: Modifier = Modifier,
    actions: PhoneInsightsActions = PhoneInsightsActions(),
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val uiTextContext = LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SnackbarHost(snackbar)
        when (state.status) {
            PhoneInsightsStatus.DISABLED -> UsagePermissionScreen(
                state.permission,
                onActivate = { actions.enableAnalysis() },
                onOpenAppDetails = { actions.openAppDetails() },
            )
            PhoneInsightsStatus.NEEDS_PERMISSION -> UsagePermissionScreen(state.permission, onActivate = actions.openPermission,
                onOpenAppDetails = { actions.openAppDetails() })
            PhoneInsightsStatus.LOADING -> DigitalLoading()
            PhoneInsightsStatus.ERROR -> DigitalEmptyState(uiTextContext.getString(R.string.ui_extra_phone_insights_screen_1), state.error?.let { uiTextContext.appErrorText(it) }.orEmpty(), action = uiTextContext.getString(R.string.ui_extra_phone_insights_screen_2), onAction = actions.refresh)
            PhoneInsightsStatus.EMPTY -> DigitalEmptyState(
                uiTextContext.getString(R.string.ui_extra_phone_insights_screen_3),
                if (state.date == today) uiTextContext.getString(R.string.ui_extra_phone_insights_screen_4) else uiTextContext.getString(R.string.ui_extra_phone_insights_screen_5),
            )
            PhoneInsightsStatus.READY -> {
                val insights = state.insights ?: return@Column
                if (state.showPermissionBanner) {
                    Text(stringResource(R.string.ui_phone_insights_screen_1), style = RetroFontStyles.Small, color = HoodieColors.Coral)
                    PixelButton(stringResource(R.string.ui_phone_insights_screen_2), actions.openPermission, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
                HoodieDigitalCard(state.reaction, state.catName)
                ScreenTimeCard(insights, zone)
                TopAppsCard(insights.topApps, onApp = actions.openApp)
                UsageByContextCard(insights.usageByContext, insights.topApps, showApps = state.settings.showTopAppsByContext)
                CategoryUsageCard(insights.categoryUsage)
                DigitalTimelineCard(insights.appTimeline, insights.topApps, zone)
                Text(
                    stringResource(R.string.ui_phone_insights_screen_3) + if (insights.summary.isEstimated) uiTextContext.getString(R.string.ui_extra_phone_insights_screen_6) else "",
                    style = RetroFontStyles.Small, color = HoodieColors.Muted,
                )
            }
        }
    }

    state.selectedApp?.let { app ->
        AppDetailsBottomSheet(
            app, state.selectedAppByContext, zone,
            onCategory = { actions.changeCategory(app.packageName, it) },
            onDismiss = actions.closeApp,
        )
    }
}
