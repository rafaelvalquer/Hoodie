package com.hoodie.app.presentation.screens.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.time.atZone
import com.hoodie.app.data.repository.HistoryRepository
import com.hoodie.app.engine.memory.Milestone
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class MemoriesViewModel @Inject constructor(history: HistoryRepository) : ViewModel() {
    val memories = history.memories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun MemoriesScreen(onBack: () -> Unit, vm: MemoriesViewModel = hiltViewModel()) {
    val memories by vm.memories.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val unlocked = memories.map { it.key }.toSet()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("MEMÓRIAS", style = MaterialTheme.typography.headlineSmall)
        memories.forEach { m ->
            PixelPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.emoji, style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        Text(m.unlockedAt.atZone(zone).format(fmt), color = HoodieColors.Muted)
                    }
                }
            }
        }
        // Marcos ainda bloqueados aparecem como mistério.
        Milestone.entries.filter { it.name !in unlocked }.forEach {
            PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.Night) {
                Text("🔒 ???", color = HoodieColors.Muted)
            }
        }
        PixelButton("Voltar", onBack, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
    }
}
