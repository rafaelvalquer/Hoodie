package com.hoodie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * Versionamento coerente: gradle.properties é a fonte única; o BuildConfig gerado,
 * o README e as regras de nomenclatura precisam concordar com ela.
 */
class VersionConsistencyTest {

    private val props = Properties().apply { File("../gradle.properties").inputStream().use(::load) }
    private val code = props.getProperty("HOODIE_VERSION_CODE")?.toInt()
    private val name = props.getProperty("HOODIE_VERSION_NAME")

    @Test
    fun `gradle properties e a fonte unica da versao do build`() {
        assertTrue("HOODIE_VERSION_CODE ausente", code != null)
        assertTrue("HOODIE_VERSION_NAME ausente", name != null)
        assertEquals(code, BuildConfig.VERSION_CODE)
        assertEquals(name, BuildConfig.VERSION_NAME)
    }

    @Test
    fun `versionCode avancou e o nome segue semver`() {
        assertTrue("versionCode deve ser > 1 (recebe +1 a cada build distribuído)", code!! > 1)
        assertTrue("versionName fora do padrão x.y.z[-dev]: $name", Regex("""\d+\.\d+\.\d+(-dev)?""").matches(name!!))
    }

    @Test
    fun `a partir da V0_2 o nome nao carrega mais mvp`() {
        val (major, minor) = name!!.split('.').map { it.takeWhile(Char::isDigit).toInt() }
        if (major > 0 || minor >= 2) assertFalse("versionName ainda contém 'mvp': $name", name.contains("mvp", ignoreCase = true))
    }

    @Test
    fun `versao documentada no README bate com o build`() {
        val readme = File("../README.md").readText()
        val documented = Regex("""\*\*Versão: ([^*]+)\*\*""").find(readme)?.groupValues?.get(1)
        assertEquals("README deve declarar a versão do build", name, documented)
    }
}
