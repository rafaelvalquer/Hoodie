package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteAssetSource
import com.hoodie.app.pixel.sprite.SpriteImageDecoder
import com.hoodie.app.pixel.sprite.SpriteRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater

/**
 * Exporta clips procedurais no MESMO formato do Aseprite (json-array + frameTags +
 * slices). Serve para provar o pipeline de ponta a ponta e para gerar o
 * "baseline" que o artista abre no Aseprite (File → Import Sprite Sheet).
 */
object SheetBaker {

    /** [anchors] = camada `anchors` do Aseprite exportada à parte (1 pixel colorido por âncora). */
    data class Baked(val image: PixelBuffer, val json: String, val anchors: PixelBuffer? = null)

    fun bake(clips: List<Pair<AnimationId, Facing>>): Baked {
        val frames = clips.flatMap { (anim, facing) ->
            val dir = when (facing) { Facing.FRONT -> Direction.FRONT; Facing.BACK -> Direction.BACK; Facing.SIDE -> Direction.LEFT }
            anim.frames.indices.map { i -> Triple(anim, facing, ProceduralSpriteProvider.frame(SpriteRequest(anim, dir, i))) }
        }
        val w = HoodiePainter.WIDTH; val h = HoodiePainter.HEIGHT
        val sheet = PixelBuffer(w * frames.size, h)
        val anchorLayer = PixelBuffer(w * frames.size, h)
        val framesJson = JSONArray()
        val tags = JSONArray()
        val anchorKeys = mapOf("feet" to JSONArray(), "head" to JSONArray(), "right_hand" to JSONArray(), "left_hand" to JSONArray(), "back" to JSONArray())
        var start = 0
        clips.forEach { (anim, facing) ->
            val count = anim.frames.size
            tags.put(JSONObject().put("name", "${anim.name.lowercase()}_${facing.name.lowercase()}").put("from", start).put("to", start + count - 1).put("direction", "forward"))
            start += count
        }
        frames.forEachIndexed { i, (anim, _, f) ->
            sheet.blit(f.image, i * w, 0)
            framesJson.put(
                JSONObject()
                    .put("filename", "${anim.name.lowercase()} $i.aseprite")
                    .put("frame", JSONObject().put("x", i * w).put("y", 0).put("w", w).put("h", h))
                    .put("duration", f.durationMs),
            )
            val a = f.anchors
            mapOf(
                com.hoodie.app.pixel.sprite.AnchorMarkers.FEET to a.feet, com.hoodie.app.pixel.sprite.AnchorMarkers.HEAD to a.head,
                com.hoodie.app.pixel.sprite.AnchorMarkers.RIGHT_HAND to a.rightHand, com.hoodie.app.pixel.sprite.AnchorMarkers.LEFT_HAND to a.leftHand,
                com.hoodie.app.pixel.sprite.AnchorMarkers.BACK to a.back,
            ).forEach { (color, p) -> anchorLayer.set(i * w + p.x, p.y, color) }
            mapOf("feet" to a.feet, "head" to a.head, "right_hand" to a.rightHand, "left_hand" to a.leftHand, "back" to a.back).forEach { (name, p) ->
                anchorKeys.getValue(name).put(
                    JSONObject().put("frame", i)
                        .put("bounds", JSONObject().put("x", p.x).put("y", p.y).put("w", 1).put("h", 1))
                        .put("pivot", JSONObject().put("x", 0).put("y", 0)),
                )
            }
        }
        val slices = JSONArray()
        anchorKeys.forEach { (name, keys) -> slices.put(JSONObject().put("name", name).put("color", "#0000ffff").put("keys", keys)) }
        val meta = JSONObject()
            .put("app", "https://www.aseprite.org/").put("format", "RGBA8888")
            .put("size", JSONObject().put("w", sheet.width).put("h", sheet.height))
            .put("frameTags", tags).put("slices", slices)
        return Baked(sheet, JSONObject().put("frames", framesJson).put("meta", meta).toString(2), anchorLayer)
    }

    fun write(dir: File, name: String, baked: Baked) {
        dir.mkdirs()
        PreviewExport.write(File(dir, "$name.png"), baked.image, 1, background = null)
        File(dir, "$name.json").writeText(baked.json)
        baked.anchors?.let { PreviewExport.write(File(dir, "$name.anchors.png"), it, 1, background = null) }
    }

    fun assetSource(root: File) = object : SpriteAssetSource {
        override fun list(dir: String): List<String> = File(root, dir).list()?.toList().orEmpty()
        override fun open(path: String): InputStream = File(root, path).inputStream()
    }

    /** Decodificador PNG mínimo (RGBA/RGB 8 bits, filtros 0–4) — suficiente para os PNGs exportados. */
    val decoder = SpriteImageDecoder { input -> decodePng(input) }

    fun decodePng(input: InputStream): PixelBuffer? {
        val data = DataInputStream(input.buffered())
        val sig = ByteArray(8); data.readFully(sig)
        var w = 0; var h = 0; var colorType = 0
        val idat = ByteArrayOutputStream()
        while (true) {
            val len = data.readInt()
            val type = ByteArray(4).also { data.readFully(it) }.toString(Charsets.US_ASCII)
            val chunk = ByteArray(len).also { data.readFully(it) }
            data.readInt() // CRC
            when (type) {
                "IHDR" -> {
                    val d = DataInputStream(chunk.inputStream())
                    w = d.readInt(); h = d.readInt(); d.readByte(); colorType = d.readByte().toInt()
                }
                "IDAT" -> idat.write(chunk)
                "IEND" -> break
            }
        }
        val bpp = if (colorType == 6) 4 else 3
        val inflater = Inflater().apply { setInput(idat.toByteArray()) }
        val raw = ByteArray((w * bpp + 1) * h)
        var off = 0
        while (off < raw.size && !inflater.finished()) off += inflater.inflate(raw, off, raw.size - off)
        val out = PixelBuffer(w, h)
        val stride = w * bpp
        val prev = ByteArray(stride); val cur = ByteArray(stride)
        for (y in 0 until h) {
            val filter = raw[y * (stride + 1)].toInt()
            System.arraycopy(raw, y * (stride + 1) + 1, cur, 0, stride)
            for (i in 0 until stride) {
                val a = if (i >= bpp) cur[i - bpp].toInt() and 0xFF else 0
                val b = prev[i].toInt() and 0xFF
                val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                val x = cur[i].toInt() and 0xFF
                cur[i] = when (filter) {
                    1 -> x + a; 2 -> x + b; 3 -> x + (a + b) / 2
                    4 -> { val p = a + b - c; val pa = kotlin.math.abs(p - a); val pb = kotlin.math.abs(p - b); val pc = kotlin.math.abs(p - c); x + if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c }
                    else -> x
                }.toByte()
            }
            for (px in 0 until w) {
                val r = cur[px * bpp].toInt() and 0xFF; val g = cur[px * bpp + 1].toInt() and 0xFF; val bl = cur[px * bpp + 2].toInt() and 0xFF
                val al = if (bpp == 4) cur[px * bpp + 3].toInt() and 0xFF else 255
                out.pixels[y * w + px] = (al shl 24) or (r shl 16) or (g shl 8) or bl
            }
            System.arraycopy(cur, 0, prev, 0, stride)
        }
        return out
    }
}
