package com.hoodie.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.SleepSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hoodie_settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val catName: String = "Hoodie",
    val installedAt: Long = 0,
    val sleep: SleepSchedule = SleepSchedule(),
    val commuteStyle: CommuteStyle = CommuteStyle.RANDOM,
    val notificationsEnabled: Boolean = true,
    /** Último dia (epochDay) em que avisamos uma atividade autônoma — no máximo 1/dia. */
    val lastAutonomyNotifyDay: Long = -1,
    val lastGeofenceRegisterDay: Long = -1,
    val digital: DigitalSettings = DigitalSettings(),
)

/**
 * Ajustes do Diário Digital. A análise só roda com a permissão "Acesso ao uso"
 * concedida E [analysisEnabled]; os outros controlam o que aparece e o que é salvo.
 */
data class DigitalSettings(
    val analysisEnabled: Boolean = false,
    val analysisRequested: Boolean = false,
    val showInDiary: Boolean = true,
    val saveHistory: Boolean = true,
    val showTopAppsByContext: Boolean = true,
)

@Singleton
class SettingsRepository(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.dataStore)

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val CAT_NAME = stringPreferencesKey("cat_name")
        val INSTALLED_AT = longPreferencesKey("installed_at")
        val WAKE = intPreferencesKey("wake_minute")
        val SLEEP = intPreferencesKey("sleep_minute")
        val COMMUTE = stringPreferencesKey("commute_style")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val AUTONOMY_DAY = longPreferencesKey("last_autonomy_notify_day")
        val LAST_GEOFENCE_REGISTER_DAY = longPreferencesKey("last_geofence_register_day")
        val DIGITAL_ENABLED = booleanPreferencesKey("digital_analysis_enabled")
        val DIGITAL_REQUESTED = booleanPreferencesKey("digital_analysis_requested")
        val DIGITAL_IN_DIARY = booleanPreferencesKey("digital_show_in_diary")
        val DIGITAL_SAVE = booleanPreferencesKey("digital_save_history")
        val DIGITAL_BY_CONTEXT = booleanPreferencesKey("digital_top_apps_by_context")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            catName = p[Keys.CAT_NAME] ?: "Hoodie",
            installedAt = p[Keys.INSTALLED_AT] ?: 0,
            sleep = SleepSchedule(p[Keys.WAKE] ?: SleepSchedule().wakeMinute, p[Keys.SLEEP] ?: SleepSchedule().sleepMinute),
            commuteStyle = p[Keys.COMMUTE]?.let { runCatching { CommuteStyle.valueOf(it) }.getOrNull() } ?: CommuteStyle.RANDOM,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            lastAutonomyNotifyDay = p[Keys.AUTONOMY_DAY] ?: -1,
            lastGeofenceRegisterDay = p[Keys.LAST_GEOFENCE_REGISTER_DAY] ?: -1,
            digital = DigitalSettings(
                analysisEnabled = p[Keys.DIGITAL_ENABLED] ?: false,
                analysisRequested = p[Keys.DIGITAL_REQUESTED] ?: false,
                showInDiary = p[Keys.DIGITAL_IN_DIARY] ?: true,
                saveHistory = p[Keys.DIGITAL_SAVE] ?: true,
                showTopAppsByContext = p[Keys.DIGITAL_BY_CONTEXT] ?: true,
            ),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun completeOnboarding(catName: String, now: Long) = store.edit {
        it[Keys.ONBOARDING] = true
        it[Keys.CAT_NAME] = catName.ifBlank { "Hoodie" }
        if (it[Keys.INSTALLED_AT] == null) it[Keys.INSTALLED_AT] = now
    }

    suspend fun setCatName(name: String) = store.edit { it[Keys.CAT_NAME] = name.ifBlank { "Hoodie" } }

    suspend fun setSleep(s: SleepSchedule) = store.edit { it[Keys.WAKE] = s.wakeMinute; it[Keys.SLEEP] = s.sleepMinute }

    suspend fun setCommuteStyle(style: CommuteStyle) = store.edit { it[Keys.COMMUTE] = style.name }

    suspend fun setNotifications(enabled: Boolean) = store.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setAutonomyNotified(epochDay: Long) = store.edit { it[Keys.AUTONOMY_DAY] = epochDay }
    suspend fun setLastGeofenceRegisterDay(epochDay: Long) = store.edit { it[Keys.LAST_GEOFENCE_REGISTER_DAY] = epochDay }

    suspend fun setDigital(d: DigitalSettings) = store.edit {
        it[Keys.DIGITAL_ENABLED] = d.analysisEnabled
        it[Keys.DIGITAL_REQUESTED] = d.analysisRequested
        it[Keys.DIGITAL_IN_DIARY] = d.showInDiary
        it[Keys.DIGITAL_SAVE] = d.saveHistory
        it[Keys.DIGITAL_BY_CONTEXT] = d.showTopAppsByContext
    }

    suspend fun clear() = store.edit { it.clear() }
}
