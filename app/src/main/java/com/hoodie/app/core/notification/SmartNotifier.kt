package com.hoodie.app.core.notification

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.domain.notification.NotificationMode
import com.hoodie.app.engine.notification.NotificationDeliveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * O [Notifier] que as engines enxergam. Com `SMART_NOTIFICATIONS_V2` desligada repassa tudo ao [HoodieNotifier];
 * ligada, perguntas passam pela política de entrega e chegadas conhecidas deixam de gerar alerta.
 * O coordenador é Lazy: com o banco indisponível (worker cedo demais) nada que dependa dele é criado.
 */
@Singleton
class SmartNotifier @Inject constructor(
    private val delegate: HoodieNotifier,
    private val coordinator: dagger.Lazy<NotificationDeliveryCoordinator>,
    private val settings: SettingsRepository,
) : Notifier {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val smart get() = HoodieConfig.SMART_NOTIFICATIONS_V2

    override suspend fun event(text: String) {
        if (smart && settings.current().notificationMode == NotificationMode.SILENT) return
        delegate.event(text)
    }

    /** Chegada conhecida com confiança alta: o Hoodie já mostra isso na Home; sem alerta desnecessário. */
    override suspend fun knownArrival(text: String) {
        if (!smart) delegate.event(text)
    }

    override suspend fun askYesNo(questionId: Long, text: String) {
        if (smart) coordinator.get().submitQuestion(questionId, text, yesNo = true) else delegate.askYesNo(questionId, text)
    }

    override suspend fun askInApp(questionId: Long, text: String) {
        if (smart) coordinator.get().submitQuestion(questionId, text, yesNo = false) else delegate.askInApp(questionId, text)
    }

    override fun cancelQuestion(questionId: Long) {
        delegate.cancelQuestion(questionId)
        if (smart) scope.launch { runCatching { coordinator.get().cancelForQuestion(questionId) } }
    }

    override fun cancelAll() = delegate.cancelAll()
}