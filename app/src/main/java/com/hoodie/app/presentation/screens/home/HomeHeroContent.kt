package com.hoodie.app.presentation.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.SpeechBubble
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * A cena do Hoodie como protagonista: fala (em área reservada), cena animada, atividade, contexto e ações rápidas.
 * A cena segue sendo o [HoodieSceneView] de sempre (canvas 240×320), só que limitada à altura que sobra.
 * O relógio atualiza apenas os textos: a cena só recebe `visual`, então a animação não reinicia.
 */
@Composable
internal fun HomeHeroContent(
    state: HomeUiState, homeNow: HomeNowSnapshot?, zone: ZoneId, busy: Boolean,
    reactions: Flow<AnimationId>?, onScenePresented: (SceneId) -> Unit,
    onConfirm: (Long) -> Unit, onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val fontScale = LocalDensity.current.fontScale
    val sceneMax = remember(screenHeightDp, fontScale) { HomeHeroLayout.sceneMaxHeightDp(screenHeightDp, fontScale).dp }
    val hero = remember(state, homeNow) { HomePresentationMapper.map(state, homeNow) }
    Column(modifier.fillMaxWidth().testTag("home_hero"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // A fala fica sobreposta ao topo da cena e aparece/some por opacidade: não ocupa espaço nem empurra nada.
        val bubbleAlpha by animateFloatAsState(if (hero.dialogue != null) 1f else 0f, label = "dialogue")
        var lastDialogue by remember { mutableStateOf<String?>(null) }
        if (hero.dialogue != null) lastDialogue = hero.dialogue
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            HoodieSceneView(
                state.visual, Modifier.fillMaxWidth().heightIn(max = sceneMax).testTag("home_live_scene"),
                reactions = reactions, onScenePresented = onScenePresented,
            )
            // Mantém o último texto durante o fade-out, para não piscar vazio.
            lastDialogue?.let { SpeechBubble(it, Modifier.padding(top = 3.dp, start = 24.dp, end = 24.dp).alpha(bubbleAlpha).testTag("home_dialogue")) }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HoodieActivityStrip(hero.hoodieActivity, hero.activityStartedAt, state.now, zone, state.catName)
            UserContextCard(hero.context, state.now, zone) {
                HomeNowQuickActions(HomePresentationMapper.quickActions(hero.context), onConfirm = onConfirm, onCorrect = onCorrect, enabled = !busy)
            }
        }
    }
}