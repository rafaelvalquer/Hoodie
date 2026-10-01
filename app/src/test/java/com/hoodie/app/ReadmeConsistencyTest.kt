package com.hoodie.app

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** O README é consequência do código: os números citados precisam bater com o que existe. */
class ReadmeConsistencyTest {

    private val readme = File("../README.md").readText()

    private fun assertMentions(count: Int, noun: String) {
        assertTrue("README deve citar \"$count $noun\" (atualize o README)", readme.contains("$count $noun"))
    }

    @Test
    fun `numero de animacoes`() = assertMentions(AnimationId.entries.size, "animações")

    @Test
    fun `numero de cenas`() = assertMentions(SceneId.entries.size, "cenas")

    /** @Database não fica no runtime: conta as tabelas do schema exportado mais recente. */
    @Test
    fun `numero de tabelas`() {
        val latest = File("schemas/com.hoodie.app.core.database.HoodieDatabase").listFiles()!!
            .maxBy { it.nameWithoutExtension.toInt() }
        val tables = JSONObject(latest.readText()).getJSONObject("database").getJSONArray("entities").length()
        assertMentions(tables, "tabelas")
    }
}
