package com.hoodie.app.domain.phoneinsights.model

import com.hoodie.app.core.model.UserContextType
import java.time.LocalDate

/**
 * Categorias próprias do Hoodie (não as da Play Store): curtas, estáveis e com
 * uma cor/emoji cada na UI. A ordem é a de exibição nos seletores.
 */
enum class HoodieAppCategory(val emoji: String, val label: String) {
    SOCIAL("💬", "Social"),
    WORK("💼", "Trabalho"),
    ENTERTAINMENT("🎭", "Diversão"),
    VIDEO("🎬", "Vídeo"),
    MUSIC("🎵", "Música"),
    NAVIGATION("🧭", "Navegação"),
    READING("📖", "Leitura"),
    SHOPPING("🛍", "Compras"),
    GAMES("🎮", "Games"),
    TOOLS("🔧", "Ferramentas"),
    OTHER("📦", "Outros");

    companion object {
        fun parse(name: String?): HoodieAppCategory = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** De onde vem o ícone: o Hoodie só emoldura o ícone instalado, nunca redesenha a marca. */
sealed interface AppIconSource {
    data class Installed(val packageName: String) : AppIconSource
    data object Generic : AppIconSource
}

data class DailyPhoneSummary(
    val date: LocalDate,
    val screenTimeMs: Long,
    val sessionCount: Int,
    val unlockCount: Int,
    val firstUseAt: Long?,
    val lastUseAt: Long?,
    val longestSessionMs: Long,
    /** true quando o aparelho não expõe eventos de tela/desbloqueio e os números foram inferidos do uso de apps. */
    val isEstimated: Boolean = false,
) {
    companion object {
        fun empty(date: LocalDate) = DailyPhoneSummary(date, 0, 0, 0, null, null, 0)
    }
}

data class AppUsageEntry(
    val packageName: String,
    val appLabel: String,
    val appCategory: HoodieAppCategory,
    val foregroundMs: Long,
    val sessionCount: Int,
    val firstUsedAt: Long?,
    val lastUsedAt: Long?,
    val iconSource: AppIconSource,
)

data class AppSession(
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMs: Long get() = endedAt - startedAt
}

/** Nome do plano V0.2 para a sessão de um app: pacote + horários (nada de conteúdo). */
typealias PhoneAppSession = AppSession

/** Uso de um app dentro de uma visita. */
data class VisitAppUsage(val packageName: String, val appLabel: String, val category: HoodieAppCategory, val foregroundMs: Long)

/** Uso do celular durante uma visita (interseção das sessões com a visita). */
data class VisitPhoneUsage(val foregroundMs: Long, val apps: List<VisitAppUsage>) {
    val isEmpty: Boolean get() = foregroundMs <= 0
}

data class ScreenSession(
    val startedAt: Long,
    val endedAt: Long,
) {
    val durationMs: Long get() = endedAt - startedAt
}

data class ContextAppUsage(
    val context: UserContextType,
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
    val sessionCount: Int,
)

data class ContextUsageSummary(
    val context: UserContextType,
    val foregroundMs: Long,
    val sessionCount: Int,
    /** Ordenado por tempo, maior primeiro. */
    val apps: List<ContextAppUsage>,
)

data class CategoryUsageSummary(
    val category: HoodieAppCategory,
    val foregroundMs: Long,
    val appCount: Int,
)

/** Um bloco de uso relevante na linha do tempo digital (sessões próximas do mesmo app já fundidas). */
data class PhoneTimelineItem(
    val packageName: String,
    val appLabel: String,
    val category: HoodieAppCategory,
    val startedAt: Long,
    val endedAt: Long,
    val context: UserContextType?,
) {
    val durationMs: Long get() = endedAt - startedAt
}

data class DailyPhoneInsights(
    val summary: DailyPhoneSummary,
    val topApps: List<AppUsageEntry>,
    val usageByContext: List<ContextUsageSummary>,
    val appTimeline: List<PhoneTimelineItem>,
    val categoryUsage: List<CategoryUsageSummary>,
    /** Tempo de tela por hora do dia (24 posições). Vazio quando o dia veio só do histórico salvo. */
    val hourlyScreenMs: List<Long> = emptyList(),
    /** Total de apps diferentes usados (topApps pode estar cortado). */
    val appCount: Int = topApps.size,
    /** Sessões por app do dia (pacote + horários), para o replay e o uso por visita. */
    val appSessions: List<AppSession> = emptyList(),
) {
    private val labels by lazy { (topApps.map { it.packageName to it.appLabel } + appTimeline.map { it.packageName to it.appLabel }).toMap() }
    private val categories by lazy { (topApps.map { it.packageName to it.appCategory } + appTimeline.map { it.packageName to it.category }).toMap() }

    /** Nome do app; sem registro, um nome derivado do pacote (com.spotify.music → Spotify). */
    fun labelOf(packageName: String): String = labels[packageName] ?: fallbackLabel(packageName)
    fun categoryOf(packageName: String): HoodieAppCategory = categories[packageName] ?: HoodieAppCategory.OTHER

    val isEmpty: Boolean get() = summary.screenTimeMs == 0L && topApps.isEmpty()

    /** Detalhe de um contexto (ex.: "Uso do celular no trabalho"). */
    fun usageIn(context: UserContextType): ContextUsageSummary? = usageByContext.firstOrNull { it.context == context }

    companion object {
        private val GENERIC = setOf("com", "br", "org", "net", "android", "app", "apps", "mobile", "client", "lite", "google")

        fun fallbackLabel(packageName: String): String {
            val part = packageName.split('.').filter { it.isNotEmpty() && it.lowercase() !in GENERIC }.let { it.getOrNull(0) ?: packageName.substringAfterLast('.') }
            return part.replaceFirstChar { it.uppercase() }
        }
    }
}
