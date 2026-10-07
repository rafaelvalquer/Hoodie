package com.hoodie.app.pixel.art

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Point

/**
 * Cena de transporte em camadas, vinda de um `.aseprite` (docs/transport-art-bible.md §6–7).
 *
 * - Camadas fixas vêm do frame 0; `bg_*` e `emissive` podem variar por período (frames com as tags
 *   `morning`, `day`, `evening`, `night`; sem tag, o frame 0 vale para todos).
 * - `masks`: pixels opacos = onde o fundo (`bg_*`) aparece. Camada vazia = fundo em toda a cena.
 * - `slots`: 1 pixel por âncora, identificado pela cor ([SceneSlots]).
 */
class SceneArt(
    val width: Int,
    val height: Int,
    private val fixed: Map<String, PixelBuffer>,
    private val byPeriod: Map<DayPeriod, Map<String, PixelBuffer>>,
    val slots: Map<String, Point>,
    /** Máscara do fundo (null = fundo em toda a cena). */
    val mask: BooleanArray?,
) {
    fun layer(name: String, period: DayPeriod): PixelBuffer? = byPeriod[period]?.get(name) ?: fixed[name]

    fun slot(name: String): Point? = slots[name]

    fun slotsWithPrefix(prefix: String): Map<String, Point> = slots.filterKeys { it.startsWith(prefix) }.toSortedMap()

    companion object {
        /** Ordem de composição, de baixo para cima. `actors` é onde o renderer insere personagens. */
        val LAYERS = listOf("bg_far", "bg_mid", "bg_near", "vehicle_back", "actors", "vehicle_front", "foreground", "emissive", "masks", "slots")
        val PERIODIC = setOf("bg_far", "bg_mid", "bg_near", "emissive")
        val PERIOD_TAGS = mapOf("morning" to DayPeriod.MORNING, "day" to DayPeriod.DAY, "evening" to DayPeriod.EVENING, "night" to DayPeriod.NIGHT)

        /** Monta a cena a partir do documento já validado por [SceneArtCompiler]. */
        fun from(doc: AsepriteFile.Document): SceneArt {
            fun layerImage(name: String, frame: Int): PixelBuffer? {
                val index = doc.layers.indexOfFirst { it.name == name }
                if (index < 0) return null
                val cel = doc.frames.getOrNull(frame)?.cels?.firstOrNull { it.layer == index } ?: return null
                return PixelBuffer(doc.width, doc.height).also { it.blit(cel.image, cel.x, cel.y) }
            }
            val fixed = LAYERS.filter { it !in setOf("actors", "masks", "slots") }.mapNotNull { n -> layerImage(n, 0)?.let { n to it } }.toMap()
            val byPeriod = PERIOD_TAGS.mapNotNull { (tag, period) ->
                val t = doc.tags.firstOrNull { it.name == tag } ?: return@mapNotNull null
                period to PERIODIC.mapNotNull { n -> layerImage(n, t.from)?.let { n to it } }.toMap()
            }.toMap()
            val slots = layerImage("slots", 0)?.let { SceneSlots.read(it) }.orEmpty()
            val mask = layerImage("masks", 0)?.let { m -> BooleanArray(m.pixels.size) { m.pixels[it] ushr 24 != 0 } }?.takeIf { it.any { v -> v } }
            return SceneArt(doc.width, doc.height, fixed, byPeriod, slots, mask)
        }
    }
}

/**
 * Cenas compiladas, lidas de `src/main/resources/pixel/scenes/transport/<cena>.aseprite` pelo
 * classloader (igual no APK e nos testes da JVM). Ausente ou inválida → null (a cena antiga assume).
 */
object SceneArtStore {
    const val DIR = "pixel/scenes/transport"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<SceneArt>>()

    fun get(name: String): SceneArt? = cache.getOrPut(name) {
        java.util.Optional.ofNullable(runCatching {
            SceneArtStore::class.java.getResourceAsStream("/$DIR/$name.aseprite")?.use { SceneArt.from(AsepriteFile.decode(it.readBytes())) }
        }.getOrNull())
    }.orElse(null)
}

/** Cores da camada `slots` (bíblia §7). As do Hoodie repetem as cores das âncoras do sprite. */
object SceneSlots {
    const val SEAT_FEET = 0xFFFF00FF.toInt()   // pés do Hoodie sentado (cor da âncora feet)
    const val SEAT_HIP = 0xFF00FFFF.toInt()    // quadril do Hoodie sentado de frente
    const val STEERING = 0xFFFF0000.toInt()    // mão direita no volante
    const val POLE_GRIP = 0xFF0000FF.toInt()   // mão na barra
    const val DOOR_FEET = 0xFFFFFF00.toInt()   // onde o Hoodie para ao entrar/sair
    /** `wheel_N`: centro da roda N (0x40FF40 + N). */
    const val WHEEL_BASE = 0xFF40FF40.toInt()
    /** `npc_seat_N`: quadril do NPC sentado N (0xFF8000 + N). */
    const val NPC_SEAT_BASE = 0xFFFF8000.toInt()
    /** `npc_pole_N`: mão do NPC em pé N (0x80FF00 + N). */
    const val NPC_POLE_BASE = 0xFF80FF00.toInt()

    private val named = mapOf(SEAT_FEET to "seat_feet", SEAT_HIP to "seat_hip", STEERING to "steering", POLE_GRIP to "pole_grip", DOOR_FEET to "door_feet")

    fun nameOf(color: Int): String? = named[color] ?: when (color and 0xFFFFFF00.toInt()) {
        WHEEL_BASE and 0xFFFFFF00.toInt() -> "wheel_${color and 0x3F}"
        NPC_SEAT_BASE and 0xFFFFFF00.toInt() -> "npc_seat_${color and 0x7F}"
        NPC_POLE_BASE and 0xFFFFFF00.toInt() -> "npc_pole_${color and 0xFF}"
        else -> null
    }

    fun colorOf(name: String): Int = named.entries.firstOrNull { it.value == name }?.key ?: when {
        name.startsWith("wheel_") -> WHEEL_BASE + name.removePrefix("wheel_").toInt()
        name.startsWith("npc_seat_") -> NPC_SEAT_BASE + name.removePrefix("npc_seat_").toInt()
        name.startsWith("npc_pole_") -> NPC_POLE_BASE + name.removePrefix("npc_pole_").toInt()
        else -> error("slot desconhecido: $name")
    }

    fun read(layer: PixelBuffer): Map<String, Point> = buildMap {
        for (y in 0 until layer.height) for (x in 0 until layer.width) {
            val c = layer[x, y]
            if (c ushr 24 == 0) continue
            nameOf(c)?.let { put(it, Point(x, y)) }
        }
    }
}

/**
 * Validação + normalização da fonte `.aseprite` (bíblia §3, §6–7). A cópia normalizada vai para
 * `src/main/resources/pixel/scenes/transport/` e é lida igual no app e nos testes da JVM.
 */
object SceneArtCompiler {
    const val MAX_SCENE_COLORS = 24

    data class Report(val errors: List<String>, val colors: Int, val slots: Map<String, Point>) {
        val ok get() = errors.isEmpty()
    }

    fun validate(doc: AsepriteFile.Document, requiredSlots: Set<String>): Report {
        val errors = mutableListOf<String>()
        val names = doc.layers.filter { !it.reference }.map { it.name }
        val unknown = names - SceneArt.LAYERS.toSet()
        if (unknown.isNotEmpty()) errors += "camadas desconhecidas: $unknown"
        val order = names.filter { it in SceneArt.LAYERS }
        if (order != SceneArt.LAYERS.filter { it in order }) errors += "ordem das camadas difere de ${SceneArt.LAYERS}"
        listOf("bg_far", "vehicle_back", "vehicle_front", "slots").forEach { if (it !in names) errors += "falta a camada $it" }
        val art = SceneArt.from(doc)
        val missing = requiredSlots - art.slots.keys
        if (missing.isNotEmpty()) errors += "faltam slots: $missing"
        val colors = sceneColors(doc)
        if (colors.size > MAX_SCENE_COLORS) errors += "${colors.size} cores (máximo $MAX_SCENE_COLORS por período)"
        val actorsIndex = doc.layers.indexOfFirst { it.name == "actors" }
        if (actorsIndex >= 0 && doc.frames.any { f -> f.cels.any { it.layer == actorsIndex && it.image.pixels.any { p -> p ushr 24 != 0 } } }) {
            errors += "a camada actors precisa ficar vazia"
        }
        return Report(errors, colors.size, art.slots)
    }

    /**
     * Cópia de runtime: sem camadas de referência (rascunhos do artista), com as camadas do contrato
     * na ordem certa. Bytes determinísticos (o teste de paridade compara com a cópia do app).
     */
    fun normalize(doc: AsepriteFile.Document): ByteArray {
        val keep = doc.layers.withIndex().filter { (_, l) -> !l.reference && l.name in SceneArt.LAYERS }
        val remap = keep.mapIndexed { newIndex, (old, _) -> old to newIndex }.toMap()
        val frames = doc.frames.map { f -> f.copy(cels = f.cels.mapNotNull { c -> remap[c.layer]?.let { c.copy(layer = it) } }) }
        return AsepriteFile.encode(doc.copy(layers = keep.map { it.value }, frames = frames))
    }

    /** Maior número de cores opacas usadas num mesmo período (sem slots/masks). */
    fun sceneColors(doc: AsepriteFile.Document): Set<Int> {
        val art = SceneArt.from(doc)
        return com.hoodie.app.core.time.DayPeriod.entries.map { p ->
            buildSet {
                listOf("bg_far", "bg_mid", "bg_near", "vehicle_back", "vehicle_front", "foreground", "emissive").forEach { n ->
                    art.layer(n, p)?.pixels?.forEach { c -> if (c ushr 24 == 0xFF) add(c) }
                }
            }
        }.maxBy { it.size }
    }
}
