package com.hoodie.app.core.mobility

import com.hoodie.app.core.datastore.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mantém o Activity Recognition ligado só quando faz sentido: onboarding feito,
 * detecção ativada nos Ajustes e permissão concedida. Chamado na abertura do app,
 * no boot, ao voltar dos Ajustes e ao mudar a opção.
 */
@Singleton
class MobilityRegistration @Inject constructor(
    private val settings: SettingsRepository,
    private val permissions: ActivityRecognitionPermissions,
    private val registrar: ActivityRecognitionRegistrar,
) {
    /** true quando o reconhecimento ficou ativo. */
    suspend fun sync(): Boolean {
        val s = settings.current()
        val wanted = s.onboardingDone && s.mobility.detectionEnabled && permissions.state().granted
        return if (wanted) registrar.register() else { registrar.unregister(); false }
    }
}
