package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.json.JSONObject
import java.io.InputStream

/** Leitura de arquivos de asset (AssetManager no app, diretório nos testes). */
interface SpriteAssetSource {
    fun list(dir: String): List<String>
    fun open(path: String): InputStream
}

/** PNG → PixelBuffer (BitmapFactory no app). */
fun interface SpriteImageDecoder {
    fun decode(input: InputStream): PixelBuffer?
}

/** [explicitAnchors] = âncoras desenhadas pelo artista (marcador ou slice), não o valor padrão. */
data class SheetFrame(
    val image: PixelBuffer,
    val anchors: SpriteAnchors,
    val durationMs: Long,
    val explicitAnchors: Set<Anchor> = Anchor.entries.toSet(),
)

enum class SheetProblemSeverity { WARNING, ERROR }

/** ERROR = o clip (ou o arquivo) foi rejeitado e o procedural assume; WARNING = carregou, mas merece revisão. */
data class SheetProblem(val severity: SheetProblemSeverity, val clip: AnimationId?, val message: String) {
    override fun toString() = "${severity.name}: $message"
}

/** Resultado da carga: o que entrou e o que foi rejeitado (aparece no Pixel Lab). */
data class SheetLoadReport(val loaded: List<String>, val issues: List<SheetProblem>) {
    val errors: List<SheetProblem> get() = issues.filter { it.severity == SheetProblemSeverity.ERROR }
    val warnings: List<SheetProblem> get() = issues.filter { it.severity == SheetProblemSeverity.WARNING }
    /** Todas as mensagens (erros e avisos). */
    val problems: List<String> get() = issues.map { it.toString() }
}

/**
 * Lê exportações do Aseprite no formato JSON array:
 *
 *     aseprite -b hoodie_walk.aseprite --sheet hoodie_walk.png --data hoodie_walk.json \
 *              --format json-array --list-tags --list-slices
 *
 * Convenções:
 * - Tag = id da animação + vista: `walk_side`, `walk_front`, `walk_back`, `idle` (sem sufixo = frente).
 *   `walk_side` é desenhado olhando para a ESQUERDA; a direita é espelhada.
 * - Âncoras por frame vêm da camada `anchors` exportada à parte (`<nome>.anchors.png`):
 *   1 pixel por âncora com as cores de [AnchorMarkers]. Sem ela, valem slices com os
 *   nomes `feet`, `head`, `right_hand`, `left_hand`, `back` (pivot ou centro), e depois
 *   valores padrão.
 * - A duração de cada frame vem do próprio Aseprite.
 */
/**
 * Cores da camada `anchors` do Aseprite (1 pixel por âncora, por frame). A API Lua do
 * Aseprite não cria chaves de slice por frame, então a âncora animada é um pixel pintado.
 */
object AnchorMarkers {
    const val FEET = 0xFFFF00FF.toInt()        // magenta
    const val HEAD = 0xFFFFFF00.toInt()        // amarelo
    const val RIGHT_HAND = 0xFFFF0000.toInt()  // vermelho
    const val LEFT_HAND = 0xFF0000FF.toInt()   // azul
    const val BACK = 0xFF00FF00.toInt()        // verde
    const val SEAT_HIP = 0xFF00FFFF.toInt()    // ciano

    val BY_NAME = mapOf("feet" to FEET, "head" to HEAD, "right_hand" to RIGHT_HAND, "left_hand" to LEFT_HAND, "back" to BACK, "seat_hip" to SEAT_HIP)
}

object AsepriteSheetParser {

    private val ANCHOR_SLICES = setOf("feet", "head", "right_hand", "left_hand", "back", "seat_hip")

    fun tagKey(tag: String): Pair<AnimationId, Facing>? {
        val upper = tag.trim().uppercase()
        val facing = Facing.entries.firstOrNull { upper.endsWith("_${it.name}") }
        val idName = if (facing != null) upper.removeSuffix("_${facing.name}") else upper
        val id = AnimationId.entries.firstOrNull { it.name == idName } ?: return null
        return id to (facing ?: Facing.FRONT)
    }

    fun parse(
        json: String,
        sheet: PixelBuffer,
        problems: MutableList<String> = mutableListOf(),
        anchorsImage: PixelBuffer? = null,
    ): Map<Pair<AnimationId, Facing>, List<SheetFrame>> {
        val root = JSONObject(json)
        val framesJson = root.getJSONArray("frames")
        val meta = root.getJSONObject("meta")

        data class Raw(val x: Int, val y: Int, val w: Int, val h: Int, val duration: Long)
        val raw = (0 until framesJson.length()).map { i ->
            val f = framesJson.getJSONObject(i)
            val r = f.getJSONObject("frame")
            Raw(r.getInt("x"), r.getInt("y"), r.getInt("w"), r.getInt("h"), f.optLong("duration", 100).coerceAtLeast(1))
        }

        // Âncoras por frame (as chaves do slice valem a partir do frame indicado).
        val slices = meta.optJSONArray("slices")
        val anchorKeys = HashMap<String, List<Pair<Int, Point>>>()
        if (slices != null) for (i in 0 until slices.length()) {
            val s = slices.getJSONObject(i)
            val name = s.getString("name").lowercase()
            if (name !in ANCHOR_SLICES) continue
            val keys = s.getJSONArray("keys")
            anchorKeys[name] = (0 until keys.length()).map { k ->
                val key = keys.getJSONObject(k)
                val b = key.getJSONObject("bounds")
                val pivot = key.optJSONObject("pivot")
                val p = if (pivot != null) Point(b.getInt("x") + pivot.getInt("x"), b.getInt("y") + pivot.getInt("y"))
                else Point(b.getInt("x") + b.getInt("w") / 2, b.getInt("y") + b.getInt("h") / 2)
                key.getInt("frame") to p
            }.sortedBy { it.first }
        }

        /** Pixel marcador da camada `anchors` dentro do retângulo do frame. */
        fun markerAt(name: String, frame: Int): Point? {
            val img = anchorsImage ?: return null
            val color = AnchorMarkers.BY_NAME[name] ?: return null
            val rf = raw[frame]
            for (y in 0 until rf.h) for (x in 0 until rf.w) if (img[rf.x + x, rf.y + y] == color) return Point(x, y)
            return null
        }

        val explicit = mutableSetOf<String>()
        fun anchorAt(name: String, frame: Int, default: Point): Point {
            val p = markerAt(name, frame) ?: anchorKeys[name]?.lastOrNull { it.first <= frame }?.second
            if (p != null) explicit += name
            return p ?: default
        }
        val anchorByName = mapOf("feet" to Anchor.FEET, "head" to Anchor.HEAD, "right_hand" to Anchor.RIGHT_HAND, "left_hand" to Anchor.LEFT_HAND, "back" to Anchor.BACK, "seat_hip" to Anchor.SEAT_HIP)

        fun frameAt(i: Int): SheetFrame {
            val r = raw[i]
            val img = PixelBuffer(r.w, r.h)
            for (y in 0 until r.h) for (x in 0 until r.w) img.pixels[y * r.w + x] = sheet[r.x + x, r.y + y]
            val feetDefault = Point(r.w / 2, r.h - 1)
            explicit.clear()
            val anchors = SpriteAnchors(
                rightHand = anchorAt("right_hand", i, Point(r.w * 2 / 3, r.h * 3 / 4)),
                leftHand = anchorAt("left_hand", i, Point(r.w / 3, r.h * 3 / 4)),
                head = anchorAt("head", i, Point(r.w / 2, r.h / 8)),
                back = anchorAt("back", i, Point(r.w / 2, r.h * 2 / 3)),
                feet = anchorAt("feet", i, feetDefault),
                seatHip = markerAt("seat_hip", i) ?: anchorKeys["seat_hip"]?.lastOrNull { it.first <= i }?.second,
            )
            return SheetFrame(img, anchors, r.duration, explicit.mapNotNull { anchorByName[it] }.toSet())
        }

        val tags = meta.optJSONArray("frameTags")
        val out = LinkedHashMap<Pair<AnimationId, Facing>, List<SheetFrame>>()
        if (tags == null || tags.length() == 0) {
            problems += "sem frameTags (exporte com --list-tags)"
            return out
        }
        for (t in 0 until tags.length()) {
            val tag = tags.getJSONObject(t)
            val name = tag.getString("name")
            val key = tagKey(name)
            if (key == null) { problems += "tag desconhecida: $name"; continue }
            val from = tag.getInt("from"); val to = tag.getInt("to")
            if (from < 0 || to >= raw.size || from > to) { problems += "tag $name fora do intervalo"; continue }
            out[key] = (from..to).map(::frameAt)
        }
        return out
    }
}

/**
 * Animações finais desenhadas à mão. Tudo o que não estiver aqui cai no
 * procedural pelo [CompositeSpriteProvider].
 */
class SpriteSheetProvider(private val clips: Map<Pair<AnimationId, Facing>, List<SheetFrame>>) : SpriteProvider {
    override val name = "spritesheet"

    private val mirrored = HashMap<Pair<AnimationId, Int>, SheetFrame>()

    val available: Set<Pair<AnimationId, Facing>> get() = clips.keys

    override fun supports(animation: AnimationId, facing: Facing) = clips.containsKey(animation to facing)

    private fun framesFor(animation: AnimationId, direction: Direction): List<SheetFrame> {
        val view = viewFor(animation, direction)
        return clips[animation to view.facing] ?: emptyList()
    }

    override fun durations(animation: AnimationId, direction: Direction): LongArray =
        framesFor(animation, direction).map { it.durationMs }.toLongArray()

    override fun frame(request: SpriteRequest): SpriteFrame {
        val view = viewFor(request.animation, request.direction)
        val frames = framesFor(request.animation, request.direction)
        require(frames.isNotEmpty()) { "Sem frames para ${request.animation}/${view.facing}" }
        val idx = request.frameIndex.mod(frames.size)
        var f = frames[idx]
        if (view.flipX) f = synchronized(mirrored) {
            mirrored.getOrPut(request.animation to idx) {
                SheetFrame(PixelBuffer(f.image.width, f.image.height).also { it.blit(f.image, 0, 0, flipX = true) }, f.anchors.mirror(f.image.width), f.durationMs)
            }
        }
        // Eventos e itens seguem o clip procedural de mesmo índice (contrato de timing).
        val clipFrame = request.animation.clip.frames.getOrNull(idx)
        val item = clipFrame?.pose?.item?.takeIf { it in HAND_ITEMS }
        return SpriteFrame(f.image, f.anchors, f.durationMs, clipFrame?.events ?: emptySet(), itemOverlay = item, source = name)
    }

    companion object {
        const val DIR = "pixel/hoodie"
        private val HAND_ITEMS = setOf(Item.MUG, Item.PHONE, Item.BOTTLE, Item.FORK, Item.PAN, Item.BROOM, Item.DUMBBELL)

        /** Carrega todos os pares .json/.png de [dir]. Erros não quebram o app: viram relatório. */
        fun load(assets: SpriteAssetSource, decoder: SpriteImageDecoder, dir: String = DIR): Pair<SpriteSheetProvider, SheetLoadReport> {
            val clips = LinkedHashMap<Pair<AnimationId, Facing>, List<SheetFrame>>()
            val loaded = mutableListOf<String>(); val issues = mutableListOf<SheetProblem>()
            fun error(clip: AnimationId?, msg: String) { issues += SheetProblem(SheetProblemSeverity.ERROR, clip, msg) }
            val files = runCatching { assets.list(dir) }.getOrDefault(emptyList())
            for (json in files.filter { it.endsWith(".json") && !it.endsWith(".anchors.json") && it != ArtReviewStatus.FILE }.sorted()) {
                val base = json.removeSuffix(".json")
                runCatching {
                    val text = assets.open("$dir/$json").bufferedReader().use { it.readText() }
                    val image = assets.open("$dir/$base.png").use { decoder.decode(it) } ?: error("PNG inválido")
                    val anchors = if ("$base.anchors.png" in files) assets.open("$dir/$base.anchors.png").use { decoder.decode(it) } else null
                    val parserProblems = mutableListOf<String>()
                    val parsed = AsepriteSheetParser.parse(text, image, parserProblems, anchors)
                    parserProblems.forEach { error(null, "$base: $it") }
                    parsed.forEach { (key, frames) ->
                        val label = "$base ${key.first}/${key.second}"
                        // Eventos (pegar caneca, passo…) vêm do clip pelo índice: contagem diferente
                        // desalinha tudo. Sheet incompatível é REJEITADO → fica o procedural.
                        val expected = key.first.frames.size
                        if (frames.size != expected) {
                            error(key.first, "$label: ${frames.size} frames, o clip tem $expected — rejeitado (usa o procedural)")
                            return@forEach
                        }
                        val bad = frames.firstOrNull { it.image.width != HoodiePainter.WIDTH || it.image.height != HoodiePainter.HEIGHT }
                        if (bad != null) {
                            error(key.first, "$label: frame ${bad.image.width}×${bad.image.height} (esperado 48×72) — rejeitado")
                            return@forEach
                        }
                        // Âncora crítica faltando: o item/efeito "flutuaria" longe da mão → REJEITADO.
                        val missing = RequiredAnchors.missing(key.first, frames)
                        if (missing.isNotEmpty()) {
                            error(key.first, "$label: âncoras obrigatórias não desenhadas: ${missing.joinToString()} — rejeitado")
                            return@forEach
                        }
                        // Cores fora da paleta do personagem: carrega, mas avisa.
                        val extras = frames.flatMap { f -> f.image.pixels.filter { it ushr 24 != 0 }.toSet() }.toSet() - HoodiePalette.ALL.toSet()
                        if (extras.isNotEmpty()) issues += SheetProblem(SheetProblemSeverity.WARNING, key.first, "$label: paleta possui ${extras.size} cores extras")
                        clips[key] = frames
                        loaded += "${key.first.name.lowercase()}_${key.second.name.lowercase()}"
                    }
                }.onFailure { error(null, "$base: ${it.message}") }
            }
            return SpriteSheetProvider(clips) to SheetLoadReport(loaded, issues)
        }
    }
}
