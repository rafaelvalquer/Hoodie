package com.hoodie.app.presentation.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import com.hoodie.app.engine.interaction.InteractionRequest
import com.hoodie.app.engine.interaction.InteractionSound
import com.hoodie.app.engine.interaction.InteractionSoundPlan

/**
 * Toca os efeitos das interações no instante em que a reação começa de fato.
 *
 * - Volume moderado, no canal de mídia (o volume do celular controla).
 * - Celular no silencioso ou no vibrar: nenhum som.
 * - [stop] cancela o que estiver agendado ou tocando (reação cancelada, troca de cena, saída da Home).
 * - Som que ainda não terminou de carregar é pulado, nunca tocado atrasado.
 */
class HoodieInteractionSounds(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mutableSetOf<Int>()
    private val ids: Map<InteractionSound, Int>
    private val handler = Handler(Looper.getMainLooper())
    private val streams = mutableListOf<Int>()
    private val pending = mutableListOf<Runnable>()
    private var released = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
        ids = RESOURCES.mapValues { (_, res) -> pool.load(context, res, 1) }
    }

    fun play(request: InteractionRequest) {
        if (released || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        stop()
        InteractionSoundPlan.cues(request.response, request.requestId).forEach { cue ->
            val id = ids[cue.sound] ?: return@forEach
            val task = object : Runnable {
                override fun run() {
                    pending -= this
                    if (!released && id in loaded) {
                        val stream = pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
                        if (stream != 0) streams += stream
                    }
                }
            }
            pending += task
            handler.postDelayed(task, cue.offsetMs)
        }
    }

    fun stop() {
        pending.forEach(handler::removeCallbacks)
        pending.clear()
        streams.forEach(pool::stop)
        streams.clear()
    }

    fun release() {
        if (released) return
        stop()
        released = true
        pool.release()
    }

    companion object {
        private const val MAX_STREAMS = 2
        private const val VOLUME = 0.8f
        private val RESOURCES = mapOf(
            InteractionSound.TAP_1 to R.raw.hoodie_tap_1,
            InteractionSound.TAP_2 to R.raw.hoodie_tap_2,
            InteractionSound.TAP_3 to R.raw.hoodie_tap_3,
            InteractionSound.PET_HEAD_1 to R.raw.hoodie_pet_head_1,
            InteractionSound.PET_HEAD_2 to R.raw.hoodie_pet_head_2,
            InteractionSound.PURR to R.raw.hoodie_purr,
            InteractionSound.NUZZLE to R.raw.hoodie_nuzzle,
        )
    }
}

/** Tocador ligado à tela: criado só quando os sons estão ativos e liberado ao sair. */
@Composable
fun rememberHoodieInteractionSounds(enabled: Boolean): HoodieInteractionSounds? {
    val context = LocalContext.current
    val sounds = remember(enabled) { if (enabled) HoodieInteractionSounds(context.applicationContext) else null }
    DisposableEffect(sounds) { onDispose { sounds?.release() } }
    return sounds
}
