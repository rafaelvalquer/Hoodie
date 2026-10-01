package com.hoodie.app.presentation.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.WorkMode
import com.hoodie.app.presentation.screens.places.PlacePickerContent
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.SpeechBubble
import com.hoodie.app.presentation.components.TimeField
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.DayOfWeek

@Composable
fun OnboardingScreen(vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var permissionTick by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++; vm.go(OnboardingStep.HOME)
    }
    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (!vm.hasLocation()) vm.go(OnboardingStep.HOME)
    }

    // Passos de endereço ocupam a tela toda (o mapa não pode ficar dentro de scroll).
    when (state.step) {
        OnboardingStep.HOME_ADDRESS -> {
            PlacePickerContent(
                PlaceType.HOME, null, onDone = vm::onHomeAddressSaved, onCancel = { vm.go(OnboardingStep.HOME) },
                modifier = Modifier.safeDrawingPadding(), allowTypeChange = false,
            )
            return
        }
        OnboardingStep.WORK_ADDRESS -> {
            PlacePickerContent(
                PlaceType.WORK, null, onDone = vm::onWorkAddressSaved, onCancel = { vm.go(OnboardingStep.WORK) },
                modifier = Modifier.safeDrawingPadding(), allowTypeChange = false,
            )
            return
        }
        else -> Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val anim = when (state.step) {
            OnboardingStep.WELCOME, OnboardingStep.DONE -> AnimationId.WAVE
            OnboardingStep.PERMISSION -> AnimationId.THINK_STAND
            OnboardingStep.HOME -> AnimationId.LOOK_AROUND
            OnboardingStep.WORK, OnboardingStep.SCHEDULE -> AnimationId.WORK_TYPING
            else -> AnimationId.IDLE
        }
        Text("HOODIE", style = MaterialTheme.typography.headlineSmall, color = HoodieColors.Hood)
        AnimatedHoodie(anim, size = 150.dp)

        when (state.step) {
            // Tratados acima, em tela cheia.
            OnboardingStep.HOME_ADDRESS, OnboardingStep.WORK_ADDRESS -> Unit
            OnboardingStep.WELCOME -> {
                SpeechBubble("Olá. Eu sou ${state.catName}.\nVamos descobrir como é o seu dia.")
                Text(
                    "Você vive a sua rotina. Eu vivo uma rotina paralela, reagindo a onde você está, ao horário e aos hábitos que você confirmar.",
                    color = HoodieColors.Muted, textAlign = TextAlign.Center,
                )
                PixelButton("Começar", { vm.go(OnboardingStep.NAME) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.NAME -> {
                SectionLabel("Passo 1 · Nome do gato")
                OutlinedTextField(state.catName, vm::setName, singleLine = true, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                PixelButton("Continuar", { vm.go(OnboardingStep.PERMISSION) }, Modifier.fillMaxWidth(), enabled = state.catName.isNotBlank())
            }
            OnboardingStep.PERMISSION -> {
                SectionLabel("Passo 2 · Localização")
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text("A localização permite ao ${state.catName} entender quando você está em casa, no trabalho ou em outros lugares importantes.")
                    Text("\n🔒 Seus locais ficam armazenados só no celular, cifrados.\n📡 Sem rastreamento contínuo: usamos regiões (geofences), não o seu trajeto.\n✈️ Funciona sem internet.", color = HoodieColors.Muted)
                }
                if (permissionTick >= 0 && vm.needsBackground()) {
                    PixelPanel(Modifier.fillMaxWidth(), color = HoodieColors.PanelLight) {
                        Text("Para perceber chegadas e saídas com o app fechado, escolha \"Permitir o tempo todo\" na próxima tela.")
                    }
                    PixelButton("Permitir o tempo todo", {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        else vm.go(OnboardingStep.HOME)
                    }, Modifier.fillMaxWidth())
                    PixelButton("Só com o app aberto", { vm.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                } else if (vm.hasLocation()) {
                    PixelButton("Continuar", { vm.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth())
                } else {
                    PixelButton("Permitir localização", {
                        foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, Modifier.fillMaxWidth())
                    PixelButton("Agora não", { vm.go(OnboardingStep.HOME) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                }
            }
            OnboardingStep.HOME -> {
                SectionLabel("Passo 3 · Casa")
                SpeechBubble("📍 Você está em casa agora?")
                if (state.busy) CircularProgressIndicator()
                state.message?.let { Text(it, color = HoodieColors.Coral, textAlign = TextAlign.Center) }
                if (vm.hasLocation()) {
                    PixelButton("Sim", vm::markHomeHere, Modifier.fillMaxWidth(), enabled = !state.busy)
                } else {
                    Text("Sem localização, o ${state.catName} segue a rotina provável. Dá para ativar depois.", color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
                PixelButton("Não, buscar pelo endereço", { vm.go(OnboardingStep.HOME_ADDRESS) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton("Definir depois", { vm.go(OnboardingStep.WORK) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            }
            OnboardingStep.WORK -> {
                SectionLabel("Passo 4 · Trabalho")
                SpeechBubble("Você trabalha fora de casa?")
                PixelButton("Sim, buscar o endereço", vm::pickWorkAddress, Modifier.fillMaxWidth())
                PixelButton("Sim, marco quando chegar lá", { vm.setWorkMode(WorkMode.OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Blue)
                PixelButton("Home office", { vm.setWorkMode(WorkMode.HOME_OFFICE) }, Modifier.fillMaxWidth(), color = HoodieColors.Hood)
                PixelButton("Não", { vm.setWorkMode(WorkMode.NONE) }, Modifier.fillMaxWidth(), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
                if (state.routine.workMode == WorkMode.OFFICE) {
                    Text("Quando chegar ao trabalho, toque em \"Salvar este local\" na tela inicial.", color = HoodieColors.Muted, textAlign = TextAlign.Center)
                }
            }
            OnboardingStep.SCHEDULE -> {
                SectionLabel("Passo 5 · Horários")
                val r = state.routine
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Entrada", r.startMinute, { m -> vm.updateRoutine { it.copy(startMinute = m) } }, Modifier.weight(1f))
                    TimeField("Saída", r.endMinute, { m -> vm.updateRoutine { it.copy(endMinute = m) } }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Almoço", r.lunchStartMinute, { m -> vm.updateRoutine { it.copy(lunchStartMinute = m) } }, Modifier.weight(1f))
                    TimeField("Volta", r.lunchEndMinute, { m -> vm.updateRoutine { it.copy(lunchEndMinute = m) } }, Modifier.weight(1f))
                }
                PixelButton("Continuar", { vm.go(OnboardingStep.DAYS) }, Modifier.fillMaxWidth())
            }
            OnboardingStep.DAYS -> {
                SectionLabel("Passo 6 · Dias")
                DayToggles(state.routine.days, vm::toggleDay)
                PixelButton("Continuar", { vm.go(OnboardingStep.DONE) }, Modifier.fillMaxWidth(), enabled = state.routine.days.isNotEmpty())
            }
            OnboardingStep.DONE -> {
                SpeechBubble("Prontinho! Agora é só viver o seu dia.\nEu vou junto.")
                PixelPanel(Modifier.fillMaxWidth()) {
                    Text("Configurado:", style = MaterialTheme.typography.titleMedium)
                    Text("🏠 Casa: " + if (state.homeSaved) "salva" else "definir depois", color = HoodieColors.Muted)
                    Text("🏢 Trabalho: " + state.routine.workMode.label + if (state.workSaved) " · local salvo" else "", color = HoodieColors.Muted)
                }
                PixelButton("Vamos lá!", vm::finish, Modifier.fillMaxWidth(), color = HoodieColors.Gold)
            }
        }
    }
}

@Composable
fun DayToggles(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    val labels = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEachIndexed { i, d ->
            val on = d in days
            Box(
                Modifier
                    .weight(1f)
                    .background(if (on) HoodieColors.Blue else HoodieColors.PanelLight)
                    .border(2.dp, HoodieColors.Outline)
                    .clickable { onToggle(d) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(labels[i] + if (on) "\n✓" else "\n ", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = if (on) HoodieColors.Outline else HoodieColors.Ink)
            }
        }
    }
}
