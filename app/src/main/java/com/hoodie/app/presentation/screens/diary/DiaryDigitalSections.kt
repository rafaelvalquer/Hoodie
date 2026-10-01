package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.presentation.screens.phoneinsights.ContextPhoneUsageSection
import com.hoodie.app.presentation.theme.HoodieColors

/** O Diário conta uma história só: a aba Digital é a mesma data vista pelo celular. */
enum class DiaryTab(val label: String) { GENERAL("🗺 Geral"), DIGITAL("📱 Digital") }

@Composable
internal fun DiaryTabs(selected: DiaryTab, onSelect: (DiaryTab) -> Unit) {
    Row(Modifier.fillMaxWidth().border(2.dp, HoodieColors.Outline)) {
        DiaryTab.entries.forEach { tab ->
            val on = tab == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) HoodieColors.Gold else HoodieColors.Panel)
                    .semantics { role = Role.Tab; this.selected = on }
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tab.label.uppercase(), style = MaterialTheme.typography.labelLarge, color = if (on) HoodieColors.Outline else HoodieColors.Muted)
            }
        }
    }
}

/** Toque num contexto do "Seu dia": como o celular foi usado lá. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContextPhoneSheet(context: UserContextType, phone: DailyPhoneInsights?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = HoodieColors.Panel) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${context.emoji} ${context.label}", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
            ContextPhoneUsageSection(phone, context)
        }
    }
}
