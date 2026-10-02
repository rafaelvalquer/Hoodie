package com.hoodie.app.presentation.screens.phoneinsights

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
    val zone = ZoneId.systemDefault()
    LaunchedEffect(date) { vm.selectDate(date) }
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val openPermission = { runCatching { context.startActivity(vm.permissionIntent()) }.onFailure { vm.permissionLaunchFailed() }; Unit }
    CollectUiEvents(vm.events) { event ->
        when (event) {
            PhoneInsightsUiEvent.OpenUsageSettings -> openPermission()
            is PhoneInsightsUiEvent.ShowError -> scope.launch { snackbar.showSnackbar(context.appErrorText(event.error)) }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SnackbarHost(snackbar)
        when (state.status) {
            PhoneInsightsStatus.DISABLED -> UsagePermissionScreen(
                state.permission,
                onActivate = { vm.enableAnalysis() },
                onOpenAppDetails = { context.startActivity(vm.appDetailsIntent()) },
            )
            PhoneInsightsStatus.NEEDS_PERMISSION -> UsagePermissionScreen(state.permission, onActivate = openPermission,
                onOpenAppDetails = { context.startActivity(vm.appDetailsIntent()) })
            PhoneInsightsStatus.LOADING -> DigitalLoading()
            PhoneInsightsStatus.ERROR -> DigitalEmptyState("Ops!", state.error?.let { context.appErrorText(it) }.orEmpty(), action = "Tentar de novo", onAction = vm::refresh)
            PhoneInsightsStatus.EMPTY -> DigitalEmptyState(
                "Nada por aqui",
                if (date == LocalDate.now(zone)) "Ainda não há uso do celular registrado hoje." else "O Android não guardou uso do celular para este dia.",
            )
            PhoneInsightsStatus.READY -> {
                val insights = state.insights ?: return@Column
                if (state.showPermissionBanner) {
                    Text(stringResource(R.string.ui_phone_insights_screen_1), style = RetroFontStyles.Small, color = HoodieColors.Coral)
                    PixelButton(stringResource(R.string.ui_phone_insights_screen_2), openPermission, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
                HoodieDigitalCard(state.reaction, state.catName)
                ScreenTimeCard(insights, zone)
                TopAppsCard(insights.topApps, onApp = vm::openApp)
                UsageByContextCard(insights.usageByContext, insights.topApps, showApps = state.settings.showTopAppsByContext)
                CategoryUsageCard(insights.categoryUsage)
                DigitalTimelineCard(insights.appTimeline, insights.topApps, zone)
                Text(
                    stringResource(R.string.ui_phone_insights_screen_3) + if (insights.summary.isEstimated) " ≈ = estimado: este aparelho não informa desbloqueios exatos." else "",
                    style = RetroFontStyles.Small, color = HoodieColors.Muted,
                )
            }
        }
    }

    state.selectedApp?.let { app ->
        AppDetailsBottomSheet(
            app, state.selectedAppByContext, zone,
            onCategory = { vm.changeCategory(app.packageName, it) },
            onDismiss = vm::closeApp,
        )
    }
}
