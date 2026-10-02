package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.AnchorMarkers
import org.json.JSONArray
import org.json.JSONObject

/**
 * `.aseprite` (fonte da verdade da arte) → assets de runtime, no mesmo formato de
 *
 *     aseprite -b hoodie_walk.aseprite --ignore-layer "baseline (referencia)" --ignore-layer anchors \
 *              --sheet hoodie_walk.png --data hoodie_walk.json --format json-array --list-tags --list-slices
 *
 * mais `<nome>.anchors.png` (só a camada `anchors`). Camadas escondidas e de
 * referência não entram, como no Aseprite. As âncoras também viram slices por frame.
 */
object AsepriteSourceCompiler {
    const val ANCHORS_LAYER = "anchors"
    const val BASELINE_LAYER = "baseline (referencia)"

    data class Compiled(val image: PixelBuffer, val json: String, val anchors: PixelBuffer)

    fun compile(doc: AsepriteFile.Document): Compiled {
        val w = doc.width; val h = doc.height
        val count = doc.frames.size
        val sheet = PixelBuffer(w * count, h)
        val anchorSheet = PixelBuffer(w * count, h)
        val anchorIndex = doc.layers.indexOfFirst { it.name == ANCHORS_LAYER }
        val framesJson = JSONArray()
        val keys = LinkedHashMap<String, JSONArray>().apply { AnchorMarkers.BY_NAME.keys.forEach { put(it, JSONArray()) } }

        for (i in 0 until count) {
            sheet.blit(doc.flatten(i, ignore = setOf(ANCHORS_LAYER, BASELINE_LAYER)), i * w, 0)
            val markers = PixelBuffer(w, h)
            doc.frames[i].cels.filter { it.layer == anchorIndex }.forEach { markers.blit(it.image, it.x, it.y) }
            anchorSheet.blit(markers, i * w, 0)
            AnchorMarkers.BY_NAME.forEach { (name, color) ->
                val at = (0 until w * h).firstOrNull { markers.pixels[it] == color } ?: return@forEach
                keys.getValue(name).put(
                    JSONObject().put("frame", i)
                        .put("bounds", JSONObject().put("x", at % w).put("y", at / w).put("w", 1).put("h", 1))
                        .put("pivot", JSONObject().put("x", 0).put("y", 0)),
                )
            }
            framesJson.put(
                JSONObject()
                    .put("filename", "frame $i.aseprite")
                    .put("frame", JSONObject().put("x", i * w).put("y", 0).put("w", w).put("h", h))
                    .put("duration", doc.frames[i].durationMs),
            )
        }
        val tags = JSONArray()
        doc.tags.forEach { t -> tags.put(JSONObject().put("name", t.name).put("from", t.from).put("to", t.to).put("direction", "forward")) }
        val slices = JSONArray()
        keys.forEach { (name, k) -> if (k.length() > 0) slices.put(JSONObject().put("name", name).put("color", "#0000ffff").put("keys", k)) }
        val meta = JSONObject()
            .put("app", "https://www.aseprite.org/").put("format", "RGBA8888")
            .put("size", JSONObject().put("w", sheet.width).put("h", sheet.height))
            .put("frameTags", tags).put("slices", slices)
        return Compiled(sheet, JSONObject().put("frames", framesJson).put("meta", meta).toString(2), anchorSheet)
    }
}
