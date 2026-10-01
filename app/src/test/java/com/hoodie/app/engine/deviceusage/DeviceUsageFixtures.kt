package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.deviceusage.AppMetadataResolver
import com.hoodie.app.core.deviceusage.RawUsageEvent
import com.hoodie.app.core.deviceusage.RawUsageEventType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms

const val YOUTUBE = "com.google.android.youtube"
const val SPOTIFY = "com.spotify.music"
const val WHATSAPP = "com.whatsapp"
const val TEAMS = "com.microsoft.teams"
const val LAUNCHER = "com.android.launcher3"
const val UNKNOWN_APP = "com.example.mystery_app"

/** Instante na segunda-feira de teste. */
fun mon(hour: Int, minute: Int = 0): Long = at(MONDAY, hour, minute).ms()

val DAY_START = mon(0)
val DAY_END = at(MONDAY + 1, 0).ms()

fun fg(pkg: String, t: Long) = RawUsageEvent(RawUsageEventType.APP_FOREGROUND, t, pkg)
fun bg(pkg: String, t: Long) = RawUsageEvent(RawUsageEventType.APP_BACKGROUND, t, pkg)
fun screenOn(t: Long) = RawUsageEvent(RawUsageEventType.SCREEN_INTERACTIVE, t)
fun screenOff(t: Long) = RawUsageEvent(RawUsageEventType.SCREEN_NON_INTERACTIVE, t)
fun unlock(t: Long) = RawUsageEvent(RawUsageEventType.KEYGUARD_HIDDEN, t)
fun lock(t: Long) = RawUsageEvent(RawUsageEventType.KEYGUARD_SHOWN, t)

/** Liga a tela, desbloqueia, usa [pkg] por [minutes] e apaga. */
fun use(pkg: String, start: Long, minutes: Long): List<RawUsageEvent> {
    val end = start + minutes * MINUTE_MS
    return listOf(screenOn(start), unlock(start + 1_000), fg(pkg, start + 2_000), bg(pkg, end), screenOff(end + 1_000), lock(end + 1_000))
}

class FakeAppMetadata(
    private val labels: Map<String, String> = mapOf(YOUTUBE to "YouTube", SPOTIFY to "Spotify", WHATSAPP to "WhatsApp", TEAMS to "Teams"),
    private val categories: Map<String, Int> = emptyMap(),
    private val ignored: Set<String> = setOf(LAUNCHER),
    private val uninstalled: Set<String> = emptySet(),
) : AppMetadataResolver {
    override fun label(packageName: String) = labels[packageName] ?: packageName.substringAfterLast('.')
    override fun systemCategory(packageName: String) = categories[packageName]
    override fun isInstalled(packageName: String) = packageName !in uninstalled
    override fun ignoredPackages() = ignored
}
