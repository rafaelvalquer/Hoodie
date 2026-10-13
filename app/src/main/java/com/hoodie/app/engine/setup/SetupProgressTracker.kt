package com.hoodie.app.engine.setup

import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.location.LocationPermissionManager
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.domain.setup.SetupProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Junta o que já é fato no aparelho (Casa cadastrada, localização o tempo todo) com o que a pessoa decidiu
 * (trabalho, notificações). Casa e localização são derivadas, então cadastrar depois atualiza tudo sozinho.
 */
@Singleton
class SetupProgressTracker @Inject constructor(
    private val settings: SettingsRepository,
    private val places: PlaceRepository,
    private val permissions: LocationPermissionManager,
) {
    val progress: Flow<SetupProgress> = combine(settings.settings, places.places, permissions.state) { s, saved, permission ->
        SetupProgress.of(
            onboardingDone = s.onboardingDone,
            home = saved.any { it.type == PlaceType.HOME },
            work = s.setupWorkDone,
            background = permission.canMonitorGeofences,
            notifications = s.setupNotificationsDone,
            demo = s.demoCompleted,
        )
    }

    /** Revalida a permissão (ao voltar das configurações do sistema). */
    fun refresh() { permissions.refresh() }
}