package com.hoodie.app.core.debug

import com.hoodie.app.BuildConfig
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Debug-only view of the scene selected by Home and the scene actually presented by its renderer. */
data class HomeVisualSnapshot(
    val expected: SceneId? = null,
    val selected: SceneId? = null,
    val presented: SceneId? = null,
    val selectedAt: Long? = null,
    val presentedAt: Long? = null,
    val homeVisible: Boolean = false,
    val mode: String? = null,
    val certainty: String? = null,
    val reason: String? = null,
)

@Singleton
class HomeVisualDiagnostics @Inject constructor() {
    private val _snapshot = MutableStateFlow(HomeVisualSnapshot())
    val snapshot: StateFlow<HomeVisualSnapshot> = _snapshot.asStateFlow()

    fun selected(visual: VisualState, now: Long) {
        if (!BuildConfig.DEBUG) return
        _snapshot.value = _snapshot.value.copy(
            expected = visual.mobilityVisual?.profile?.scene ?: visual.scene,
            selected = visual.scene,
            selectedAt = now,
            mode = visual.mobilityVisual?.profile?.mode?.name,
            certainty = visual.mobilityVisual?.certainty?.name,
            reason = visual.mobilityVisual?.reason ?: "cena de contexto",
        )
    }

    fun presented(scene: SceneId, now: Long) {
        if (!BuildConfig.DEBUG) return
        if (_snapshot.value.presented != scene) {
            _snapshot.value = _snapshot.value.copy(presented = scene, presentedAt = now)
        }
    }

    fun visibility(visible: Boolean) {
        if (!BuildConfig.DEBUG) return
        _snapshot.value = _snapshot.value.copy(homeVisible = visible)
    }
}
