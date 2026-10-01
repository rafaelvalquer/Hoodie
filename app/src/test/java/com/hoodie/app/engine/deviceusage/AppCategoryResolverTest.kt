package com.hoodie.app.engine.deviceusage

import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoryResolverTest {
    private val resolver = AppCategoryResolver()

    @Test
    fun `apps conhecidos do plano`() {
        assertEquals(HoodieAppCategory.VIDEO, resolver.resolve(YOUTUBE, null))
        assertEquals(HoodieAppCategory.MUSIC, resolver.resolve(SPOTIFY, null))
        assertEquals(HoodieAppCategory.SOCIAL, resolver.resolve(WHATSAPP, null))
        assertEquals(HoodieAppCategory.READING, resolver.resolve("com.android.chrome", null))
        assertEquals(HoodieAppCategory.WORK, resolver.resolve(TEAMS, null))
        assertEquals(HoodieAppCategory.NAVIGATION, resolver.resolve("com.google.android.apps.maps", null))
    }

    @Test
    fun `prefixo de familia de pacotes`() {
        assertEquals(HoodieAppCategory.GAMES, resolver.resolve("com.supercell.clashroyale", null))
    }

    @Test
    fun `override do usuario vence o mapeamento interno`() {
        val r = AppCategoryResolver(mapOf("com.android.chrome" to HoodieAppCategory.WORK))
        assertEquals(HoodieAppCategory.WORK, r.resolve("com.android.chrome", null))
    }

    @Test
    fun `mapeamento interno vence a categoria do Android`() {
        assertEquals(HoodieAppCategory.VIDEO, resolver.resolve(YOUTUBE, AppCategoryResolver.ANDROID_SOCIAL))
    }

    @Test
    fun `categoria do Android para apps desconhecidos`() {
        assertEquals(HoodieAppCategory.GAMES, resolver.resolve(UNKNOWN_APP, AppCategoryResolver.ANDROID_GAME))
        assertEquals(HoodieAppCategory.NAVIGATION, resolver.resolve(UNKNOWN_APP, AppCategoryResolver.ANDROID_MAPS))
        assertEquals(HoodieAppCategory.WORK, resolver.resolve(UNKNOWN_APP, AppCategoryResolver.ANDROID_PRODUCTIVITY))
    }

    @Test
    fun `sem nada vira OTHER`() {
        assertEquals(HoodieAppCategory.OTHER, resolver.resolve(UNKNOWN_APP, null))
        assertEquals(HoodieAppCategory.OTHER, resolver.resolve(UNKNOWN_APP, 99))
    }

    @Test
    fun `parse tolera valor salvo invalido`() {
        assertEquals(HoodieAppCategory.OTHER, HoodieAppCategory.parse("NAO_EXISTE"))
        assertEquals(HoodieAppCategory.MUSIC, HoodieAppCategory.parse("MUSIC"))
    }
}
