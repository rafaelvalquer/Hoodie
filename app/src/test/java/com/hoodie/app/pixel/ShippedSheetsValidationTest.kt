package com.hoodie.app.pixel

import com.hoodie.app.pixel.debug.SpriteDebugRenderer
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.RequiredShippedAnimations
import com.hoodie.app.pixel.sprite.SpriteAssetSource
import com.hoodie.app.pixel.sprite.SpriteRequest
import com.hoodie.app.pixel.sprite.SpriteSheetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * Critério de aceite da arte desenhada à mão: o que for colocado em
 * app/src/main/assets/pixel/hoodie passa pelos mesmos critérios do procedural.
 * Sem arquivos, o teste passa (tudo procedural).
 */
class ShippedSheetsValidationTest {

    /** Lê uma pasta qualquer como se fosse assets/pixel/hoodie. */
    private fun folder(dir: File) = object : SpriteAssetSource {
        override fun list(dir2: String): List<String> = dir.list()?.toList().orEmpty()
        override fun open(path: String): InputStream = File(dir, path.substringAfterLast('/')).inputStream()
    }

    @Test
    fun `sprite sheets entregues no app respeitam o padrao visual`() {
        validate(File("src/main/assets/${SpriteSheetProvider.DIR}"))
    }

    /** Release (versionName sem "-dev") precisa da arte final dos clips obrigatórios. */
    @Test
    fun `clips obrigatorios existem como arte final na release`() {
        val props = java.util.Properties().apply { File("../gradle.properties").inputStream().use(::load) }
        val release = !props.getProperty("HOODIE_VERSION_NAME").endsWith("-dev")
        val (provider, _) = SpriteSheetProvider.load(folder(File("src/main/assets/${SpriteSheetProvider.DIR}")), SheetBaker.decoder)
        val missing = RequiredShippedAnimations.missing(provider.available)
        if (release) assertTrue("Arte final obrigatória ausente no APK: $missing", missing.isEmpty())
    }

    @Test
    fun `baseline do artista passa nos mesmos criterios`() {
        val baseline = File("../assets-source/hoodie/baseline")
        assertTrue("baseline ausente", baseline.listFiles { f -> f.name.endsWith(".json") }.orEmpty().isNotEmpty())
        validate(baseline)
    }

    private fun validate(dir: File) {
        val jsons = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        jsons.forEach { json -> assertTrue("${json.name} sem PNG", File(dir, json.name.removeSuffix(".json") + ".png").exists()) }
        val (provider, report) = SpriteSheetProvider.load(folder(dir), SheetBaker.decoder)
        assertTrue("Erros de importação em ${dir.name}: ${report.errors}", report.errors.isEmpty())
        if (jsons.isNotEmpty()) assertTrue("nenhuma animação reconhecida em ${jsons.map { it.name }}", provider.available.isNotEmpty())

        provider.available.forEach { (anim, facing) ->
            val d = when (facing) { Facing.FRONT -> Direction.FRONT; Facing.BACK -> Direction.BACK; Facing.SIDE -> Direction.LEFT }
            val colors = mutableSetOf<Int>()
            val bottoms = mutableSetOf<Int>()
            for (i in 0 until provider.frameCount(anim, d)) {
                val f = provider.frame(SpriteRequest(anim, d, i))
                val tag = "$anim/$facing/$i"
                assertEquals("$tag largura", HoodiePainter.WIDTH, f.image.width)
                assertEquals("$tag altura", HoodiePainter.HEIGHT, f.image.height)
                assertTrue("$tag duração", f.durationMs > 0)
                // Pés no chão e no centro: senão o gato "flutua" ou desliza ao trocar de frame.
                assertEquals("$tag pés (y)", HoodiePainter.FEET.y, f.anchors.feet.y)
                assertTrue("$tag pés (x) fora do centro", f.anchors.feet.x in 22..26)
                // Sem anti-aliasing: pixels totalmente opacos ou transparentes.
                f.image.pixels.forEach { px ->
                    val a = px ushr 24
                    assertTrue("$tag pixel semitransparente (anti-aliasing?)", a == 0 || a == 255)
                    if (a == 255) colors += px
                }
                SpriteDebugRenderer.bbox(f)?.let { bottoms += it[3] }
            }
            // Paleta reduzida: as 17 cores do personagem + no máximo 7 extras (≤ 24).
            val extras = colors - HoodiePalette.ALL.toSet()
            assertTrue("$anim/$facing usa ${extras.size} cores fora da paleta", extras.size <= 7)
            // Pulo (lift) e deitar (só a cabeça) mudam a silhueta de propósito.
            if (anim.frames.none { it.pose.lift > 0 || it.pose.headOnly }) assertTrue("$anim/$facing chão variando: $bottoms", bottoms.size <= 2)
        }
    }
}
