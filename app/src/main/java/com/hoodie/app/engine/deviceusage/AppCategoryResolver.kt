package com.hoodie.app.engine.deviceusage

import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory

/**
 * Categoria Hoodie de um app, nesta ordem:
 * 1. escolha manual do usuário
 * 2. mapeamento interno de apps conhecidos
 * 3. categoria declarada pelo app no Android (ApplicationInfo.category)
 * 4. OTHER
 */
class AppCategoryResolver(private val overrides: Map<String, HoodieAppCategory> = emptyMap()) {

    fun resolve(packageName: String, systemCategory: Int?): HoodieAppCategory =
        overrides[packageName]
            ?: known(packageName)
            ?: systemCategory?.let(::fromAndroid)
            ?: HoodieAppCategory.OTHER

    companion object {
        // Valores de ApplicationInfo.CATEGORY_* (mantidos como Int para o motor seguir puro/testável).
        const val ANDROID_GAME = 0
        const val ANDROID_AUDIO = 1
        const val ANDROID_VIDEO = 2
        const val ANDROID_IMAGE = 3
        const val ANDROID_SOCIAL = 4
        const val ANDROID_NEWS = 5
        const val ANDROID_MAPS = 6
        const val ANDROID_PRODUCTIVITY = 7
        const val ANDROID_ACCESSIBILITY = 8

        fun fromAndroid(category: Int): HoodieAppCategory? = when (category) {
            ANDROID_GAME -> HoodieAppCategory.GAMES
            ANDROID_AUDIO -> HoodieAppCategory.MUSIC
            ANDROID_VIDEO -> HoodieAppCategory.VIDEO
            ANDROID_IMAGE -> HoodieAppCategory.ENTERTAINMENT
            ANDROID_SOCIAL -> HoodieAppCategory.SOCIAL
            ANDROID_NEWS -> HoodieAppCategory.READING
            ANDROID_MAPS -> HoodieAppCategory.NAVIGATION
            ANDROID_PRODUCTIVITY -> HoodieAppCategory.WORK
            ANDROID_ACCESSIBILITY -> HoodieAppCategory.TOOLS
            else -> null
        }

        private val exact: Map<String, HoodieAppCategory> = mapOf(
            // Vídeo
            "com.google.android.youtube" to HoodieAppCategory.VIDEO,
            "com.google.android.apps.youtube.music" to HoodieAppCategory.MUSIC,
            "com.netflix.mediaclient" to HoodieAppCategory.VIDEO,
            "com.amazon.avod.thirdpartyclient" to HoodieAppCategory.VIDEO,
            "com.disney.disneyplus" to HoodieAppCategory.VIDEO,
            "com.hbo.hbonow" to HoodieAppCategory.VIDEO,
            "com.globo.globotv" to HoodieAppCategory.VIDEO,
            "tv.twitch.android.app" to HoodieAppCategory.VIDEO,
            "com.zhiliaoapp.musically" to HoodieAppCategory.VIDEO,
            "com.ss.android.ugc.trill" to HoodieAppCategory.VIDEO,
            "com.kwai.video" to HoodieAppCategory.VIDEO,
            // Música / áudio
            "com.spotify.music" to HoodieAppCategory.MUSIC,
            "deezer.android.app" to HoodieAppCategory.MUSIC,
            "com.apple.android.music" to HoodieAppCategory.MUSIC,
            "com.soundcloud.android" to HoodieAppCategory.MUSIC,
            "com.amazon.mp3" to HoodieAppCategory.MUSIC,
            // Social / mensagens
            "com.whatsapp" to HoodieAppCategory.SOCIAL,
            "com.whatsapp.w4b" to HoodieAppCategory.SOCIAL,
            "org.telegram.messenger" to HoodieAppCategory.SOCIAL,
            "com.instagram.android" to HoodieAppCategory.SOCIAL,
            "com.facebook.katana" to HoodieAppCategory.SOCIAL,
            "com.facebook.orca" to HoodieAppCategory.SOCIAL,
            "com.twitter.android" to HoodieAppCategory.SOCIAL,
            "com.snapchat.android" to HoodieAppCategory.SOCIAL,
            "com.reddit.frontpage" to HoodieAppCategory.SOCIAL,
            "com.discord" to HoodieAppCategory.SOCIAL,
            "org.thoughtcrime.securesms" to HoodieAppCategory.SOCIAL,
            "com.pinterest" to HoodieAppCategory.SOCIAL,
            "com.linkedin.android" to HoodieAppCategory.SOCIAL,
            "com.instagram.barcelona" to HoodieAppCategory.SOCIAL,
            "com.bsky.app" to HoodieAppCategory.SOCIAL,
            "com.google.android.apps.messaging" to HoodieAppCategory.SOCIAL,
            "com.samsung.android.messaging" to HoodieAppCategory.SOCIAL,
            // Trabalho
            "com.microsoft.teams" to HoodieAppCategory.WORK,
            "com.microsoft.office.outlook" to HoodieAppCategory.WORK,
            "com.microsoft.office.word" to HoodieAppCategory.WORK,
            "com.microsoft.office.excel" to HoodieAppCategory.WORK,
            "com.microsoft.office.officehubrow" to HoodieAppCategory.WORK,
            "com.Slack" to HoodieAppCategory.WORK,
            "us.zoom.videomeetings" to HoodieAppCategory.WORK,
            "com.google.android.apps.meetings" to HoodieAppCategory.WORK,
            "com.google.android.gm" to HoodieAppCategory.WORK,
            "com.google.android.apps.docs" to HoodieAppCategory.WORK,
            "com.google.android.apps.docs.editors.docs" to HoodieAppCategory.WORK,
            "com.google.android.apps.docs.editors.sheets" to HoodieAppCategory.WORK,
            "com.google.android.calendar" to HoodieAppCategory.WORK,
            "com.notion.id" to HoodieAppCategory.WORK,
            "com.atlassian.android.jira.core" to HoodieAppCategory.WORK,
            "com.trello" to HoodieAppCategory.WORK,
            // Navegação / mobilidade
            "com.google.android.apps.maps" to HoodieAppCategory.NAVIGATION,
            "com.waze" to HoodieAppCategory.NAVIGATION,
            "com.ubercab" to HoodieAppCategory.NAVIGATION,
            "com.taxis99" to HoodieAppCategory.NAVIGATION,
            "com.moovitapp.android" to HoodieAppCategory.NAVIGATION,
            "com.citymapper.app.release" to HoodieAppCategory.NAVIGATION,
            // Leitura / navegador
            "com.android.chrome" to HoodieAppCategory.READING,
            "org.mozilla.firefox" to HoodieAppCategory.READING,
            "com.brave.browser" to HoodieAppCategory.READING,
            "com.microsoft.emmx" to HoodieAppCategory.READING,
            "com.sec.android.app.sbrowser" to HoodieAppCategory.READING,
            "com.amazon.kindle" to HoodieAppCategory.READING,
            "com.google.android.apps.magazines" to HoodieAppCategory.READING,
            "com.medium.reader" to HoodieAppCategory.READING,
            "com.duolingo" to HoodieAppCategory.READING,
            // Compras
            "com.mercadolibre" to HoodieAppCategory.SHOPPING,
            "com.amazon.mShop.android.shopping" to HoodieAppCategory.SHOPPING,
            "com.shopee.br" to HoodieAppCategory.SHOPPING,
            "com.alibaba.aliexpresshd" to HoodieAppCategory.SHOPPING,
            "com.einnovation.temu" to HoodieAppCategory.SHOPPING,
            "br.com.brainweb.ifood" to HoodieAppCategory.SHOPPING,
            "com.magazineluiza.app" to HoodieAppCategory.SHOPPING,
            // Ferramentas
            "com.google.android.calculator" to HoodieAppCategory.TOOLS,
            "com.google.android.deskclock" to HoodieAppCategory.TOOLS,
            "com.android.settings" to HoodieAppCategory.TOOLS,
            "com.google.android.apps.photos" to HoodieAppCategory.TOOLS,
            "com.google.android.GoogleCamera" to HoodieAppCategory.TOOLS,
            "com.google.android.apps.nbu.files" to HoodieAppCategory.TOOLS,
            "com.google.android.dialer" to HoodieAppCategory.TOOLS,
            "com.google.android.googlequicksearchbox" to HoodieAppCategory.TOOLS,
            "com.nu.production" to HoodieAppCategory.TOOLS,
            "com.hoodie.app" to HoodieAppCategory.ENTERTAINMENT,
        )

        /** Famílias inteiras de pacotes (bancos, jogos de um estúdio...). */
        private val prefixes: List<Pair<String, HoodieAppCategory>> = listOf(
            "com.supercell." to HoodieAppCategory.GAMES,
            "com.king." to HoodieAppCategory.GAMES,
            "com.roblox." to HoodieAppCategory.GAMES,
            "com.mojang." to HoodieAppCategory.GAMES,
            "com.dts.freefire" to HoodieAppCategory.GAMES,
            "com.miHoYo." to HoodieAppCategory.GAMES,
            "com.microsoft.office." to HoodieAppCategory.WORK,
            "com.google.android.apps.docs" to HoodieAppCategory.WORK,
            "com.itau" to HoodieAppCategory.TOOLS,
            "com.bradesco" to HoodieAppCategory.TOOLS,
            "br.com.bb." to HoodieAppCategory.TOOLS,
            "br.com.intermedium" to HoodieAppCategory.TOOLS,
        )

        fun known(packageName: String): HoodieAppCategory? =
            exact[packageName] ?: prefixes.firstOrNull { packageName.startsWith(it.first) }?.second
    }
}
