package com.hoodie.app.engine.backup

import androidx.datastore.preferences.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit portable allowlist. Device permission grants, scheduling and backup dates are excluded. */
object BackupSettingsMapper {
    val strings = setOf("cat_name", "commute_style", "mobility_preferred_mode", "diary_map_mode", "notification_mode")
    val booleans = setOf("onboarding_done", "notifications_enabled", "interaction_sounds_enabled", "digital_analysis_enabled", "digital_analysis_requested", "digital_show_in_diary", "digital_save_history", "digital_top_apps_by_context", "mobility_detect", "mobility_learn", "mobility_confirm_new_places", "setup_progress_v2", "setup_work_done", "demo_completed")
    val longs = setOf("installed_at")
    val ints = setOf("wake_minute", "sleep_minute")
    val sets = setOf("mobility_approved_patterns", "mobility_declined_patterns", "routine_automatic_events")
    val excluded = setOf("last_autonomy_notify_day", "last_geofence_register_day", "setup_notifications_done", "onboarding_draft_step", "onboarding_draft_name", "last_backup_at")
    val included get() = strings + booleans + longs + ints + sets

    fun export(p: Preferences): JSONObject = JSONObject().apply {
        p.asMap().forEach { (key, value) ->
            if (key.name in included) put(key.name, if (value is Set<*>) JSONArray(value.sortedBy { it.toString() }) else value)
        }
    }

    fun restore(j: JSONObject): Preferences = mutablePreferencesOf().apply {
        requireBackup(j.keys().asSequence().all { it in included })
        j.keys().forEach { name ->
            val v = j.get(name)
            when (name) {
                in strings -> {
                    requireBackup(v is String && v.length <= 256)
                    val text = v as String
                    when (name) {
                        "cat_name" -> requireBackup(text.isNotBlank())
                        "commute_style" -> requireBackup(com.hoodie.app.core.model.CommuteStyle.entries.any { it.name == text })
                        "mobility_preferred_mode" -> requireBackup(com.hoodie.app.core.mobility.MovementMode.entries.any { it.name == text })
                        "notification_mode" -> requireBackup(com.hoodie.app.domain.notification.NotificationMode.entries.any { it.name == text })
                        "diary_map_mode" -> requireBackup(text in setOf("JOURNEY", "CLOCK"))
                    }
                    this[stringPreferencesKey(name)] = text
                }
                in booleans -> { requireBackup(v is Boolean); this[booleanPreferencesKey(name)] = v as Boolean }
                in ints -> { requireBackup(v is Int && v in 0..1439); this[intPreferencesKey(name)] = v as Int }
                in longs -> { requireBackup((v is Long || v is Int) && (v as Number).toLong() in 0..4133980800000L); this[longPreferencesKey(name)] = (v as Number).toLong() }
                in sets -> {
                    requireBackup(v is JSONArray && v.length() <= 10000)
                    val a = v as JSONArray
                    val values = (0 until a.length()).map { a.get(it).also { value -> requireBackup(value is String && value.length <= 256) } as String }.toSet()
                    if (name == "routine_automatic_events") requireBackup(values.all { value -> com.hoodie.app.domain.routine.RoutineEventType.entries.any { it.name == value } })
                    this[stringSetPreferencesKey(name)] = values
                }
            }
        }
        this[booleanPreferencesKey("digital_analysis_requested")] = j.optBoolean("digital_analysis_requested") || j.optBoolean("digital_analysis_enabled")
        this[booleanPreferencesKey("digital_analysis_enabled")] = false
        this[booleanPreferencesKey("setup_notifications_done")] = false
        this[longPreferencesKey("last_geofence_register_day")] = -1
        // Restored history is available immediately; permissions are always re-evaluated on this device.
        this[booleanPreferencesKey("onboarding_done")] = true
    }
}
