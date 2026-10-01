package com.hoodie.app.core.debug

import android.util.Log
import com.hoodie.app.BuildConfig
import com.hoodie.app.core.time.ClockProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observabilidade local, só em build debug: o app é offline e não tem analytics,
 * então o que aconteceu (geofence, troca de contexto, decisão do Hoodie, worker)
 * fica num buffer em memória exibido no Developer Lab e no logcat (tag "Hoodie").
 * Em release não guarda nada.
 */
@Singleton
class DebugEventLogger(private val clock: ClockProvider, private val enabled: Boolean) {

    @Inject constructor(clock: ClockProvider) : this(clock, BuildConfig.DEBUG)

    enum class Category { GEOFENCE, CONTEXT, HOODIE, WORKER, SYSTEM }

    data class Entry(val at: Long, val category: Category, val message: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun log(category: Category, message: String) {
        if (!enabled) return
        val entry = Entry(clock.nowMillis(), category, message)
        _entries.update { (it + entry).takeLast(CAPACITY) }
        runCatching { Log.d(TAG, "${category.name} $message") }
    }

    fun clear() = _entries.update { emptyList() }

    companion object {
        const val CAPACITY = 300
        private const val TAG = "Hoodie"
    }
}
