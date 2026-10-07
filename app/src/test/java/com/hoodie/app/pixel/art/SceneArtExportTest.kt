package com.hoodie.app.pixel.art

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pipeline dos cenários de transporte V3 (docs/transport-art-bible.md §6–8):
 *
 * - `-PsceneBootstrap=car|all` → gera o rascunho em `assets-source/scenes/transport/<cena>.aseprite`
 *   (sobrescreve: use só para recomeçar uma cena);
 * - `-PexportSceneArt=true` → valida a fonte e grava a cópia normalizada em
 *   `src/main/resources/pixel/scenes/transport/` + PNGs por camada em build/pixel-preview/scene-art;
 * - sempre → a cópia do app é igual à fonte normalizada e passa na validação.
 */
class SceneArtExportTest {
    private val sourceDir = File("../assets-source/scenes/transport")
    private val runtimeDir = File("src/main/resources/${SceneArtStore.DIR}")

    private val required = mapOf(
        "car" to setOf("seat_feet", "steering", "door_feet", "wheel_0", "wheel_1"),
        "train" to setOf("seat_hip", "npc_seat_0"),
        "metro" to setOf("seat_hip", "npc_seat_0"),
        "bus" to setOf("seat_hip", "npc_seat_0"),
    )

    @Test fun bootstrapAndExport() {
        val bootstrap = System.getProperty("sceneBootstrap").orEmpty()
        if (bootstrap.isNotEmpty()) {
            SceneBootstrapStudio.SCENES.filterKeys { bootstrap == "all" || it == bootstrap }.forEach { (name, build) ->
                AsepriteFile.write(File(sourceDir, "$name.aseprite"), build())
                println("Rascunho gravado: ${File(sourceDir, "$name.aseprite").absolutePath}")
            }
        }
        if (System.getProperty("exportSceneArt") == "true") {
            sourceFiles().forEach { src ->
                val doc = AsepriteFile.read(src)
                val report = SceneArtCompiler.validate(doc, required[src.nameWithoutExtension].orEmpty())
                assertTrue("${src.name}: ${report.errors}", report.ok)
                File(runtimeDir, src.name).apply { parentFile.mkdirs() }.writeBytes(SceneArtCompiler.normalize(doc))
                val art = SceneArt.from(doc)
                DayPeriod.entries.forEach { p ->
                    SceneArt.LAYERS.forEach { l -> art.layer(l, p)?.let { PreviewExport.write(File(PreviewExport.dir, "scene-art/${src.nameWithoutExtension}/${p.name.lowercase()}_$l.png"), it, 2, null) } }
                }
                println("${src.name}: ${report.colors} cores, slots ${report.slots.keys}")
            }
        }
    }

    @Test fun runtimeCopiesMatchTheirSourcesAndValidate() {
        sourceFiles().forEach { src ->
            val doc = AsepriteFile.read(src)
            val report = SceneArtCompiler.validate(doc, required[src.nameWithoutExtension].orEmpty())
            assertTrue("${src.name}: ${report.errors}", report.ok)
            val runtime = File(runtimeDir, src.name)
            assertTrue("Falta ${runtime.path}: rode com -PexportSceneArt=true", runtime.exists())
            assertArrayEquals("${src.name} mudou: rode com -PexportSceneArt=true", SceneArtCompiler.normalize(doc), runtime.readBytes())
        }
    }

    private fun sourceFiles() = sourceDir.listFiles { f -> f.extension == "aseprite" }.orEmpty().sortedBy { it.name }
}
