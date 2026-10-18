package com.hoodie.app.presentation.screens.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.engine.backup.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class BackupUiState(
    val state: BackupState = BackupState.IDLE,
    val error: BackupError? = null,
    val passwordForExport: Boolean? = null,
    val preview: BackupPreview? = null,
) {
    val busy get() = state in setOf(BackupState.PREPARING, BackupState.READING, BackupState.ENCRYPTING, BackupState.WRITING, BackupState.VERIFYING, BackupState.DECRYPTING, BackupState.VALIDATING, BackupState.RESTORING)
}

@HiltViewModel class BackupViewModel @Inject constructor(
    private val coordinator: BackupCoordinator,
    settings: SettingsRepository,
    private val reset: com.hoodie.app.core.security.LocalDataReset,
) : ViewModel() {
    private val mutable = MutableStateFlow(BackupUiState())
    val ui = mutable.asStateFlow()
    val lastBackup = settings.lastBackupAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private var password: CharArray? = null
    private var includeDigital = true
    private var source: Uri? = null
    private var task: Job? = null
    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (coordinator.hasInterruptedExport()) mutable.update { it.copy(error = BackupError.INCOMPLETE_DOCUMENT) }
        }
    }

    fun requestExport() { mutable.update { it.copy(passwordForExport = true, error = null) } }
    fun fileSelected(uri: Uri?) {
        if (uri != null) { source = uri; mutable.update { it.copy(passwordForExport = false, error = null) } }
    }
    fun dismissPassword() { password?.fill('\u0000'); password = null; mutable.update { it.copy(passwordForExport = null) } }
    fun exportPassword(value: CharArray, digital: Boolean) {
        password?.fill('\u0000'); password = value; includeDigital = digital
        mutable.update { it.copy(passwordForExport = null) }
    }
    fun destinationSelected(uri: Uri?) {
        val secret = password.also { password = null }
        if (secret == null) {
            if (uri != null) runOperation {
                if (!coordinator.discardIncompleteDocument(uri)) throw BackupException(BackupError.INCOMPLETE_DOCUMENT)
                progress(BackupState.CANCELLED)
            }
            return
        }
        if (uri == null) { secret.fill('\u0000'); mutable.update { it.copy(state = BackupState.CANCELLED) }; return }
        var started = false
        runOperation { started = true; coordinator.export(uri, secret, includeDigital, ::progress) }
        task?.invokeOnCompletion {
            secret.fill('\u0000')
            if (!started) CoroutineScope(Dispatchers.IO).launch { runCatching { coordinator.discardIncompleteDocument(uri) } }
        }
    }
    fun importPassword(value: CharArray) {
        val uri = source ?: run { value.fill('\u0000'); return }
        mutable.update { it.copy(passwordForExport = null) }
        runOperation {
            mutable.value.preview?.let { coordinator.discard(it) }
            mutable.update { it.copy(preview = null) }
            val result = coordinator.preview(uri, value, ::progress)
            mutable.update { it.copy(preview = result, state = BackupState.PREVIEW_READY) }
        }
        task?.invokeOnCompletion { value.fill('\u0000') }
    }
    fun restoreConfirmed() {
        val preview = mutable.value.preview ?: return
        runOperation {
            progress(BackupState.RESTORING)
            coordinator.restore(preview)
        }
    }
    fun cancel() {
        if (mutable.value.state == BackupState.RESTORING) return
        task?.cancel()
        dismissPassword()
        val old = mutable.value.preview
        mutable.update { it.copy(preview = null, passwordForExport = null) }
        if (old != null) viewModelScope.launch(Dispatchers.IO) { coordinator.discard(old) }
    }
    fun restart() = reset.restart()
    private fun progress(state: BackupState) { mutable.update { it.copy(state = state) } }
    private fun runOperation(block: suspend () -> Unit) {
        if (task?.isActive == true) return
        mutable.update { it.copy(error = null, state = BackupState.PREPARING) }
        task = viewModelScope.launch {
            try { block() }
            catch (_: CancellationException) { mutable.update { if (it.state == BackupState.SUCCESS) it else it.copy(state = BackupState.CANCELLED) } }
            catch (e: Exception) {
                val error = BackupError.from(e)
                mutable.update { it.copy(state = BackupState.FAILED, error = error) }
            }
        }
    }
    override fun onCleared() {
        password?.fill('\u0000')
        val old = mutable.value.preview
        if (old != null && mutable.value.state != BackupState.RESTORING)
            CoroutineScope(Dispatchers.IO).launch { runCatching { coordinator.discard(old) } }
    }
}
