package com.hoodie.app.presentation.screens.pixellab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.effects.TapEffectStyle
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.SceneThumbnail
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.components.TapEffectLayer
import com.hoodie.app.presentation.components.TapEffectPreview
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Pixel Lab · Toque: experimentar os efeitos de clique. Toque na cena para ver; a escolha também
 * vale no app inteiro até fechar o app ("Nenhum" desliga).
 */
@Composable
internal fun TapEffectsLab() {
    val styles = TapEffectStyle.entries
    var slow by remember { mutableStateOf(false) }
    var scene by remember { mutableStateOf(0) }
    val scenes = listOf(SceneId.HOME, SceneId.OFFICE, SceneId.LEISURE)
    val selected = TapEffectPreview.style
    androidx.compose.runtime.DisposableEffect(Unit) {
        TapEffectPreview.labOpen = true
        // Abre já com um efeito para o primeiro toque mostrar algo.
        if (TapEffectPreview.style == null) TapEffectPreview.style = TapEffectStyle.SPARKLE
        onDispose { TapEffectPreview.labOpen = false }
    }
    SectionLabel("Efeito")
    ChipRow(styles.map { it.label } + "Nenhum", selected?.let { styles.indexOf(it) } ?: styles.size, { i ->
        TapEffectPreview.style = styles.getOrNull(i)
    })
    ChipRow(listOf("Casa", "Escritório", "Passeio"), scene, { scene = it })
    ChipRow(listOf(if (slow) "☑ Câmera lenta (4×)" else "☐ Câmera lenta (4×)"), null, { slow = !slow })
    Text(
        "Toque em qualquer lugar da cena. A escolha vale também no resto do app até fechá-lo, para testar na Home.",
        color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall,
    )
    TapEffectLayer(selected, Modifier.fillMaxWidth().height(420.dp).testTag("pixel-lab-tap"), speed = if (slow) 0.25f else 1f) {
        Box(Modifier.fillMaxSize().background(HoodieColors.PanelLight), contentAlignment = Alignment.Center) {
            SceneThumbnail(scenes[scene], DayPeriod.DAY, Modifier.fillMaxSize().padding(4.dp))
        }
    }
}
