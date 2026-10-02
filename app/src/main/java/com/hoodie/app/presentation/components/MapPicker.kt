package com.hoodie.app.presentation.components

import android.graphics.Color as AColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hoodie.app.presentation.theme.HoodieColors
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

/**
 * Mapa OpenStreetMap para escolher um lugar. O pino fica fixo no centro: o
 * usuário arrasta o mapa e o centro vira a coordenada do lugar. O círculo
 * mostra o raio do geofence.
 *
 * [recenterKey] muda quando o ponto vem de fora (busca, "minha localização");
 * aí o mapa é movido para [latitude]/[longitude].
 *
 * Dentro do mapa: "◎ Minha localização" no canto inferior esquerdo (vira
 * "◌ Localizando..." com [loadingLocation]) e a atribuição do OpenStreetMap no
 * inferior direito — cantos opostos, nunca sobrepostos. O mapa não define a
 * própria altura: quem chama passa uma altura fixa (nada de weight).
 */
@Composable
fun MapPicker(
    latitude: Double,
    longitude: Double,
    radiusMeters: Float,
    recenterKey: Int,
    onCenterChanged: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    onMyLocation: (() -> Unit)? = null,
    loadingLocation: Boolean = false,
    onMapInteraction: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnChange by rememberUpdatedState(onCenterChanged)
    val currentRadius by rememberUpdatedState(radiusMeters)
    val currentOnInteraction by rememberUpdatedState(onMapInteraction)

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 4.0
            maxZoomLevel = 19.5
            controller.setZoom(17.0)
            controller.setCenter(GeoPoint(latitude, longitude))
        }
    }
    val circle = remember {
        Polygon(map).apply {
            fillPaint.color = AColor.argb(60, 134, 169, 232)
            outlinePaint.color = AColor.argb(220, 26, 28, 51)
            outlinePaint.strokeWidth = 4f
            map.overlays.add(this)
        }
    }

    fun refreshCircle() {
        val c = map.mapCenter
        circle.points = Polygon.pointsAsCircle(GeoPoint(c.latitude, c.longitude), currentRadius.toDouble())
        map.invalidate()
    }

    DisposableEffect(map) {
        val notify = Runnable { map.mapCenter.let { currentOnChange(it.latitude, it.longitude) } }
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                currentOnInteraction()
                refreshCircle()
                // Debounce: só avisa quando o usuário para de arrastar.
                map.removeCallbacks(notify); map.postDelayed(notify, 350)
                return false
            }
            override fun onZoom(event: ZoomEvent?): Boolean {
                currentOnInteraction()
                return false
            }
        }
        map.addMapListener(listener)
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.removeMapListener(listener)
            map.removeCallbacks(notify)
            map.onPause()
            map.onDetach()
        }
    }

    LaunchedEffect(recenterKey) {
        map.controller.animateTo(GeoPoint(latitude, longitude))
        refreshCircle()
    }
    LaunchedEffect(radiusMeters) { refreshCircle() }

    Box(modifier.border(2.dp, HoodieColors.Outline)) {
        AndroidView(factory = { map }, modifier = Modifier.matchParentSize())
        // A ponta do pino (base do desenho) fica exatamente no centro do mapa.
        PixelPin(Modifier.align(Alignment.Center).padding(bottom = 36.dp))
        if (onMyLocation != null) {
            MapOverlayButton(
                if (loadingLocation) "◌ Localizando..." else "◎ Minha localização",
                enabled = !loadingLocation,
                onClick = onMyLocation,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).testTag(MAP_MY_LOCATION_TAG),
            )
        }
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = HoodieColors.Outline,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color(0xCCFFFFFF)).padding(horizontal = 4.dp),
        )
    }
}

const val MAP_MY_LOCATION_TAG = "map_my_location"

/** Botão compacto sobre o mapa (borda pixel de 2 dp, fundo do painel). */
@Composable
fun MapOverlayButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) HoodieColors.Ink else HoodieColors.Muted,
        maxLines = 1,
        modifier = modifier
            .border(2.dp, HoodieColors.Outline)
            .background(HoodieColors.Panel)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Pino em pixel art; a ponta fica exatamente no centro do mapa. */
@Composable
private fun PixelPin(modifier: Modifier = Modifier) {
    // Grade 7×9; '#' contorno, 'o' preenchimento, '.' brilho.
    val rows = listOf(
        " ##### ",
        "#ooooo#",
        "#o.ooo#",
        "#ooooo#",
        "#ooooo#",
        " #ooo# ",
        "  #o#  ",
        "  #o#  ",
        "   #   ",
    )
    Canvas(modifier.size(28.dp, 36.dp)) {
        val px = size.width / 7f
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                val color = when (ch) {
                    '#' -> HoodieColors.Outline
                    'o' -> HoodieColors.Coral
                    '.' -> Color.White
                    else -> null
                }
                if (color != null) drawRect(color, Offset(x * px, y * px), Size(px + 0.5f, px + 0.5f))
            }
        }
    }
}
