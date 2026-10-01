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
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val CAT_NAME = stringPreferencesKey("cat_name")
        val INSTALLED_AT = longPreferencesKey("installed_at")
        val WAKE = intPreferencesKey("wake_minute")
        val SLEEP = intPreferencesKey("sleep_minute")
        val COMMUTE = stringPreferencesKey("commute_style")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val AUTONOMY_DAY = longPreferencesKey("last_autonomy_notify_day")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            catName = p[Keys.CAT_NAME] ?: "Hoodie",
            installedAt = p[Keys.INSTALLED_AT] ?: 0,
            sleep = SleepSchedule(p[Keys.WAKE] ?: SleepSchedule().wakeMinute, p[Keys.SLEEP] ?: SleepSchedule().sleepMinute),
            commuteStyle = p[Keys.COMMUTE]?.let { runCatching { CommuteStyle.valueOf(it) }.getOrNull() } ?: CommuteStyle.RANDOM,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            lastAutonomyNotifyDay = p[Keys.AUTONOMY_DAY] ?: -1,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun completeOnboarding(catName: String, now: Long) = context.dataStore.edit {
        it[Keys.ONBOARDING] = true
        it[Keys.CAT_NAME] = catName.ifBlank { "Hoodie" }
        if (it[Keys.INSTALLED_AT] == null) it[Keys.INSTALLED_AT] = now
    }

    suspend fun setCatName(name: String) = context.dataStore.edit { it[Keys.CAT_NAME] = name.ifBlank { "Hoodie" } }

    suspend fun setSleep(s: SleepSchedule) = context.dataStore.edit { it[Keys.WAKE] = s.wakeMinute; it[Keys.SLEEP] = s.sleepMinute }

    suspend fun setCommuteStyle(style: CommuteStyle) = context.dataStore.edit { it[Keys.COMMUTE] = style.name }

    suspend fun setNotifications(enabled: Boolean) = context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setAutonomyNotified(epochDay: Long) = context.dataStore.edit { it[Keys.AUTONOMY_DAY] = epochDay }

    suspend fun clear() = context.dataStore.edit { it.clear() }
}
