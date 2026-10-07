package com.hoodie.app.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.theme.HoodieColors

/** Falso nos fixtures de screenshot: o pulso animado deixaria a captura indeterminada. */
val LocalSkeletonPulse = staticCompositionLocalOf { true }

/** Bloco cinza-azulado que ocupa o lugar de um conteúdo que ainda está carregando. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier) {
    val alpha = if (LocalSkeletonPulse.current) {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.45f, targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton-alpha",
        ).value
    } else 0.65f
    Box(modifier.background(HoodieColors.PanelLight.copy(alpha = alpha)).border(2.dp, HoodieColors.Outline.copy(alpha = 0.5f)))
}

/** Painel de esqueleto: um "título" curto e [lines] linhas de texto. */
@Composable
fun SkeletonPanel(modifier: Modifier = Modifier, lines: Int = 3) {
    PixelPanel(modifier.fillMaxWidth()) {
        SkeletonBlock(Modifier.width(120.dp).height(14.dp))
        Spacer(Modifier.height(10.dp))
        repeat(lines) { i ->
            SkeletonBlock(Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f).height(12.dp))
            if (i < lines - 1) Spacer(Modifier.height(8.dp))
        }
    }
}

/** Para leitores de tela o esqueleto inteiro é uma única frase ("Carregando…"). */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, description: String = stringResource(R.string.state_loading), content: @Composable () -> Unit) {
    Box(modifier.semantics(mergeDescendants = true) { contentDescription = description }) { content() }
}

/** Esqueleto da Home: cabeçalho, cena e três painéis. */
@Composable
fun HomeLoadingSkeleton() {
    LoadingSkeleton(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(110.dp).height(24.dp))
                Spacer(Modifier.weight(1f))
                SkeletonBlock(Modifier.size(48.dp))
                Spacer(Modifier.width(8.dp))
                SkeletonBlock(Modifier.size(48.dp))
            }
            SkeletonBlock(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(220.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(16.dp).align(Alignment.CenterHorizontally))
                SkeletonPanel(lines = 2)
                SkeletonBlock(Modifier.fillMaxWidth().height(48.dp))
            }
        }
    }
}

/** Esqueleto do Diário: quatro tiles de resumo e um painel. */
@Composable
fun DiaryLoadingSkeleton() {
    LoadingSkeleton(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(Modifier.weight(1f).height(72.dp))
                    SkeletonBlock(Modifier.weight(1f).height(72.dp))
                }
            }
            SkeletonPanel(lines = 3)
        }
    }
}

/** Esqueleto de Ajustes: três botões e um painel. */
@Composable
fun SettingsLoadingSkeleton(modifier: Modifier = Modifier) {
    LoadingSkeleton(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(56.dp))
            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(48.dp)) }
            SkeletonPanel(lines = 2)
        }
    }
}

/**
 * Erro de carregamento: o Hoodie pensativo, a mensagem e o botão de nova tentativa.
 * [fillScreen] centraliza na tela inteira (Home); sem ele, cabe dentro de uma coluna.
 */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier, fillScreen: Boolean = false, retryLabel: String = stringResource(R.string.place_retry_load)) {
    Column(
        (if (fillScreen) modifier.fillMaxSize().statusBarsPadding() else modifier.fillMaxWidth()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, if (fillScreen) Alignment.CenterVertically else Alignment.Top),
    ) {
        AnimatedHoodie(AnimationId.THINK, size = 96.dp)
        Text(stringResource(R.string.state_error_title), style = MaterialTheme.typography.titleMedium, color = HoodieColors.Hood, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Coral, textAlign = TextAlign.Center)
        PixelButton(retryLabel, onRetry, Modifier.fillMaxWidth())
    }
}
