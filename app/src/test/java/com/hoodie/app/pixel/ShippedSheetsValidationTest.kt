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

    /** O APK carrega uma cópia fiel do manifest; a release exige revisão manual de todos os grupos. */
    @Test
    fun `art-status - copia no APK e gate de revisao manual`() {
        val source = File("../assets-source/hoodie/art-status.json").readText()
        assertEquals("copie assets-source/hoodie/art-status.json para o APK", source.replace(CRLF, LF), File("src/main/assets/${SpriteSheetProvider.DIR}/art-status.json").readText().replace(CRLF, LF))
        val status = com.hoodie.app.pixel.sprite.ArtReviewStatus.parse(source)
        assertEquals(com.hoodie.app.pixel.sprite.ArtReviewStatus.GROUPS.keys, status.keys)
        assertTrue("todos os grupos têm arte final", status.values.all { it.final })
        val props = java.util.Properties().apply { File("../gradle.properties").inputStream().use(::load) }
        val release = !props.getProperty("HOODIE_VERSION_NAME").endsWith("-dev")
        val pending = com.hoodie.app.pixel.sprite.ArtReviewStatus.pendingReview(status)
        if (release) assertTrue("Release com arte sem revisão manual: $pending (V0.2 não pode sair só com bootstrap automático)", pending.isEmpty())
    }

    @Test
    fun `baseline do artista passa nos mesmos criterios`() {
        val baseline = File("../assets-source/hoodie/baseline")
        assertTrue("baseline ausente", baseline.listFiles { f -> f.name.endsWith(".json") }.orEmpty().isNotEmpty())
        validate(baseline)
    }

    private fun validate(dir: File) {
        val jsons = dir.listFiles { f -> f.name.endsWith(".json") && f.name != com.hoodie.app.pixel.sprite.ArtReviewStatus.FILE }.orEmpty()
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

            val frames = (0 until provider.frameCount(anim, d)).map { provider.frame(SpriteRequest(anim, d, it)) }
            // Pés: drift <= 1 px.
            val feetX = frames.map { it.anchors.feet.x }; val feetY = frames.map { it.anchors.feet.y }
            assertTrue("$anim/$facing pés derivam ${feetX.distinct()}/${feetY.distinct()}", feetX.max() - feetX.min() <= 1 && feetY.max() - feetY.min() <= 1)
            if (anim.loop) {
                // Silhueta: entre frames seguidos (e do último para o primeiro) cada borda anda no máximo 2 px.
                val boxes = frames.map { SpriteDebugRenderer.bbox(it)!! }
                (boxes + listOf(boxes.first())).zipWithNext().forEachIndexed { k, (a, b) ->
                    val jitter = jitterEdges(anim).maxOf { kotlin.math.abs(a[it] - b[it]) }
                    assertTrue("$anim/$facing jitter de silhueta $jitter px no frame $k→${k + 1}", jitter <= 2)
                }
                // Cabeça: em loops estáveis não passeia mais de 2 px.
                if (anim in STABLE_LOOPS) {
                    val hy = frames.map { it.anchors.head.y }; val hx = frames.map { it.anchors.head.x }
                    assertTrue("$anim/$facing cabeça deriva ${hy.distinct()}", hy.max() - hy.min() <= 2 && hx.max() - hx.min() <= 2)
                }
            }
        }
    }

    companion object {
        private const val CRLF = "\r\n"
        private const val LF = "\n"

        /** Loops em que o personagem está "parado": respiração e detalhes, não deslocamento. */
        val STABLE_LOOPS = setOf(
            com.hoodie.app.pixel.animation.AnimationId.IDLE, com.hoodie.app.pixel.animation.AnimationId.IDLE_SIT,
            com.hoodie.app.pixel.animation.AnimationId.SLEEP, com.hoodie.app.pixel.animation.AnimationId.WORK_TYPING,
            com.hoodie.app.pixel.animation.AnimationId.WORK_READ, com.hoodie.app.pixel.animation.AnimationId.WORK_MOUSE,
        )

        /**
         * Bordas do bbox [x0, y0, x1, y1] conferidas. Na locomoção pernas e braços abrem a
         * silhueta na horizontal de propósito: lá só o topo e o chão (bob) contam.
         */
        fun jitterEdges(anim: com.hoodie.app.pixel.animation.AnimationId) =
            if (anim.group == com.hoodie.app.pixel.animation.AnimGroup.LOCOMOTION) listOf(1, 3) else listOf(0, 1, 2, 3)
    }
}
