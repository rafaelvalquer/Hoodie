package com.hoodie.app.core.deviceusage

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Nome, categoria do sistema e ícone de um app instalado (fake nos testes). */
interface AppMetadataResolver {
    /** Nome visível; o próprio pacote quando o app não está mais instalado. */
    fun label(packageName: String): String

    /** ApplicationInfo.category (API 26+), ou null quando indefinida. */
    fun systemCategory(packageName: String): Int?

    fun isInstalled(packageName: String): Boolean

    /**
     * Pacotes que não contam como "uso de app": launchers (a tela inicial) e a
     * interface do sistema. Eles ainda encerram a sessão do app anterior.
     */
    fun ignoredPackages(): Set<String>
}

@Singleton
class AndroidAppMetadataResolver @Inject constructor(@ApplicationContext private val context: Context) : AppMetadataResolver {
    private val pm: PackageManager get() = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    @Volatile private var ignored: Set<String>? = null

    override fun label(packageName: String): String = labels.getOrPut(packageName) {
        info(packageName)?.let { pm.getApplicationLabel(it).toString() }?.takeIf { it.isNotBlank() } ?: prettify(packageName)
    }

    override fun systemCategory(packageName: String): Int? =
        info(packageName)?.category?.takeIf { it != ApplicationInfo.CATEGORY_UNDEFINED }

    override fun isInstalled(packageName: String): Boolean = info(packageName) != null

    override fun ignoredPackages(): Set<String> = ignored ?: run {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(home, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(home, 0)
            }
        }.getOrDefault(emptyList()).mapNotNull { it.activityInfo?.packageName }
        (launchers + ALWAYS_IGNORED).toSet().also { ignored = it }
    }

    /** Ícone instalado (ou null → a UI usa o ícone genérico em pixel art). */
    fun icon(packageName: String): Drawable? = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    private fun info(packageName: String): ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
    }.getOrNull()

    companion object {
        val ALWAYS_IGNORED = setOf("com.android.systemui", "android")

        /** "com.example.my_app" → "My app" para apps desinstalados. */
        fun prettify(packageName: String): String =
            packageName.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
