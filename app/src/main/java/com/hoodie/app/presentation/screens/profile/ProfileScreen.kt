package com.hoodie.app.presentation.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.atZone
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.hoodie.HoodieSnapshot
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.NeedBar
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class ProfileState(val settings: AppSettings = AppSettings(), val snapshot: HoodieSnapshot? = null, val daysTogether: Long = 0)

@HiltViewModel
class ProfileViewModel @Inject constructor(hoodie: HoodieEngine, settings: SettingsRepository, clock: ClockProvider) : ViewModel() {
    private val snapshots = flow { while (true) { emit(hoodie.resolve()); delay(60_000) } }
    val state = combine(settings.settings, snapshots) { s, snap ->
        val days = if (s.installedAt > 0) ChronoUnit.DAYS.between(s.installedAt.atZone(clock.zone()).toLocalDate(), clock.today()) else 0
        ProfileState(s, snap, days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileState())
}

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpen: (String) -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val needs = state.snapshot?.liveNeeds
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.settings.catName.uppercase(), style = MaterialTheme.typography.headlineSmall)
        val expr = when {
            needs == null -> null
            needs.energy < 20 -> Expression.TIRED
            needs.mood > 80 -> Expression.HAPPY
            else -> null
        }
        AnimatedHoodie(AnimationId.IDLE, size = 180.dp, expression = expr)
        Text("${state.daysTogether + 1}º dia juntos", color = HoodieColors.Muted)
        if (needs != null) {
            PixelPanel(Modifier.fillMaxWidth()) {
                SectionLabel("Como ele está")
                NeedBar("Energia", "⚡", needs.energy, HoodieColors.Gold)
                NeedBar("Fome", "🍙", needs.hunger, HoodieColors.Coral)
                NeedBar("Humor", "💙", needs.mood, HoodieColors.Blue)
                NeedBar("Social", "💬", needs.social, HoodieColors.Mint)
                NeedBar("Foco", "🎯", needs.focus, HoodieColors.Hood)
                Text("\nNada aqui é punição: as necessidades só mudam o que ele escolhe fazer.", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        PixelButton("Memórias", { onOpen(Routes.MEMORIES) }, Modifier.fillMaxWidth())
        PixelButton("Voltar", onBack, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
    }
}
