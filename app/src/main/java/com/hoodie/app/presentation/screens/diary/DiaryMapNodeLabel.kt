package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.DiaryMapLayout
import com.hoodie.app.pixel.diary.DiaryMapPlaceNode
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Quando o nome de um prédio aparece. Em vez de todos os nomes sobrepostos:
 * Casa e Trabalho sempre; os outros só quando tocados ou quando o Hoodie está
 * neles no replay.
 */
object DiaryMapLabelPolicy {
    private val ALWAYS = setOf(PlaceType.HOME, PlaceType.WORK)

    fun visibleNodeIds(layout: DiaryMapLayout, activeNodeId: String?, selectedNodeId: String?): Set<String> =
        layout.nodes.filter { it.type in ALWAYS || it.id == activeNodeId || it.id == selectedNodeId }.map { it.id }.toSet()

    /** Ponto (lógico) onde a haste da etiqueta encosta: centro do topo do prédio. */
    fun anchor(node: DiaryMapPlaceNode): MapPoint {
        val (x, y, w, _) = node.footprint.pixels.toList()
        return MapPoint(x + w / 2f, y.toFloat())
    }

    /** Prédio do mapa em que o Hoodie está agora (visita ativa no replay). */
    fun activeNodeId(layout: DiaryMapLayout, activeVisitId: String?): String? =
        layout.visits.firstOrNull { it.id == activeVisitId }?.nodeId
}

/** Etiqueta pixel: painel pequeno com borda de 2 dp e uma haste até o prédio. */
@Composable
fun DiaryMapNodeLabel(text: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .border(2.dp, HoodieColors.Outline)
                .background(if (highlighted) HoodieColors.Gold else HoodieColors.Panel)
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (highlighted) HoodieColors.Outline else HoodieColors.Ink, maxLines = 1)
        }
        Box(Modifier.width(2.dp).height(5.dp).background(HoodieColors.Outline))
    }
}
