package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.renderer.PixelBuffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Leitura e escrita do formato aberto .aseprite (RGBA, sem tilemaps), conforme
 * https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md.
 * Só o subconjunto que o Hoodie usa: camadas normais, cels comprimidos (zlib),
 * tags, paleta e perfil de cor sRGB.
 */
object AsepriteFile {

    const val LAYER_VISIBLE = 1
    const val LAYER_EDITABLE = 2
    const val LAYER_REFERENCE = 64

    data class Layer(val name: String, val flags: Int = LAYER_VISIBLE or LAYER_EDITABLE, val opacity: Int = 255) {
        val visible get() = flags and LAYER_VISIBLE != 0
        val reference get() = flags and LAYER_REFERENCE != 0
    }

    /** Imagem inteira da camada no frame; o escritor recorta para a área usada. */
    data class Cel(val layer: Int, val x: Int, val y: Int, val image: PixelBuffer)

    data class Frame(val durationMs: Int, val cels: List<Cel>)

    data class Tag(val name: String, val from: Int, val to: Int)

    data class Document(
        val width: Int,
        val height: Int,
        val layers: List<Layer>,
        val frames: List<Frame>,
        val tags: List<Tag>,
        val palette: List<Int>,
    ) {
        /** Composição das camadas visíveis (exceto as de referência e as ignoradas), como o `aseprite -b` exporta. */
        fun flatten(frame: Int, ignore: Set<String> = emptySet()): PixelBuffer {
            val out = PixelBuffer(width, height)
            frames[frame].cels.sortedBy { it.layer }.forEach { cel ->
                val l = layers[cel.layer]
                if (!l.visible || l.reference || l.name in ignore) return@forEach
                out.blit(cel.image, cel.x, cel.y)
            }
            return out
        }
    }

    // ───────────── Escrita ─────────────

    fun write(file: File, doc: Document) {
        file.parentFile?.mkdirs()
        file.writeBytes(encode(doc))
    }

    fun encode(doc: Document): ByteArray {
        val frames = doc.frames.mapIndexed { i, f ->
            val chunks = mutableListOf<ByteArray>()
            if (i == 0) {
                chunks += chunk(0x2007) { u16(1); u16(0); u32(0); zeros(8) } // sRGB
                chunks += paletteChunk(doc.palette)
                doc.layers.forEach { l ->
                    chunks += chunk(0x2004) {
                        u16(l.flags); u16(0); u16(0); u16(0); u16(0); u16(0); u8(l.opacity); zeros(3); string(l.name)
                    }
                }
                if (doc.tags.isNotEmpty()) chunks += chunk(0x2018) {
                    u16(doc.tags.size); zeros(8)
                    doc.tags.forEach { t -> u16(t.from); u16(t.to); u8(0); u16(0); zeros(6); zeros(3); u8(0); string(t.name) }
                }
            }
            f.cels.forEach { cel -> cropped(cel)?.let { chunks += celChunk(it) } }
            val body = ByteArrayOutputStream().apply { chunks.forEach(::write) }.toByteArray()
            le(16) {
                u32(16 + body.size); u16(0xF1FA); u16(minOf(chunks.size, 0xFFFF)); u16(f.durationMs); zeros(2); u32(chunks.size)
            } + body
        }
        val total = 128 + frames.sumOf { it.size }
        val header = le(128) {
            u32(total); u16(0xA5E0); u16(doc.frames.size); u16(doc.width); u16(doc.height); u16(32)
            u32(1) // opacidade de camada válida
            u16(100); u32(0); u32(0); u8(0); zeros(3); u16(doc.palette.size.coerceAtMost(256)); u8(1); u8(1)
            u16(0); u16(0); u16(16); u16(16); zeros(84)
        }
        return ByteArrayOutputStream().apply { write(header); frames.forEach(::write) }.toByteArray()
    }

    private fun paletteChunk(colors: List<Int>) = chunk(0x2019) {
        u32(colors.size); u32(0); u32(colors.size - 1); zeros(8)
        colors.forEach { c -> u16(0); u8((c shr 16) and 0xFF); u8((c shr 8) and 0xFF); u8(c and 0xFF); u8((c ushr 24) and 0xFF) }
    }

    private fun cropped(cel: Cel): Cel? {
        val img = cel.image
        var x0 = img.width; var y0 = img.height; var x1 = -1; var y1 = -1
        for (y in 0 until img.height) for (x in 0 until img.width) if (img[x, y] ushr 24 != 0) {
            if (x < x0) x0 = x; if (y < y0) y0 = y; if (x > x1) x1 = x; if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        val out = PixelBuffer(x1 - x0 + 1, y1 - y0 + 1)
        for (y in y0..y1) for (x in x0..x1) out.pixels[(y - y0) * out.width + (x - x0)] = img[x, y]
        return Cel(cel.layer, cel.x + x0, cel.y + y0, out)
    }

    private fun celChunk(cel: Cel): ByteArray {
        val raw = ByteArray(cel.image.width * cel.image.height * 4)
        cel.image.pixels.forEachIndexed { i, c ->
            raw[i * 4] = (c shr 16).toByte(); raw[i * 4 + 1] = (c shr 8).toByte(); raw[i * 4 + 2] = c.toByte(); raw[i * 4 + 3] = (c ushr 24).toByte()
        }
        val z = deflate(raw)
        return chunk(0x2005) {
            u16(cel.layer); u16(cel.x); u16(cel.y); u8(255); u16(2); u16(0); zeros(5); u16(cel.image.width); u16(cel.image.height); bytes(z)
        }
    }

    private fun deflate(raw: ByteArray): ByteArray {
        val d = Deflater(9).apply { setInput(raw); finish() }
        val out = ByteArrayOutputStream(); val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private class Le(size: Int) {
        val b: ByteBuffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun u8(v: Int) { b.put(v.toByte()) }
        fun u16(v: Int) { b.putShort(v.toShort()) }
        fun u32(v: Int) { b.putInt(v) }
        fun zeros(n: Int) = repeat(n) { u8(0) }
        fun bytes(a: ByteArray) { b.put(a) }
        fun string(s: String) { val a = s.toByteArray(Charsets.UTF_8); u16(a.size); bytes(a) }
    }

    private fun le(size: Int, block: Le.() -> Unit): ByteArray = Le(size).apply(block).b.array()

    /** Chunk com tamanho calculado (escreve num buffer folgado e corta). */
    private fun chunk(type: Int, block: Le.() -> Unit): ByteArray {
        val body = Le(1 shl 20).apply(block)
        val data = body.b.array().copyOf(body.b.position())
        return le(6 + data.size) { u32(6 + data.size); u16(type); bytes(data) }
    }

    // ───────────── Leitura ─────────────

    fun read(file: File): Document = decode(file.readBytes())

    fun decode(bytes: ByteArray): Document {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u8() = b.get().toInt() and 0xFF
        fun u16() = b.short.toInt() and 0xFFFF
        fun s16() = b.short.toInt()
        fun u32() = b.int
        fun string(): String { val n = u16(); val a = ByteArray(n); b.get(a); return String(a, Charsets.UTF_8) }

        u32()
        require(u16() == 0xA5E0) { "não é um arquivo .aseprite" }
        val frameCount = u16(); val w = u16(); val h = u16()
        require(u16() == 32) { "só RGBA é suportado" }
        b.position(128)
        val layers = mutableListOf<Layer>(); val tags = mutableListOf<Tag>(); val palette = mutableListOf<Int>()
        val frames = mutableListOf<Frame>()
        repeat(frameCount) {
            val start = b.position()
            val size = u32()
            require(u16() == 0xF1FA) { "frame corrompido" }
            val old = u16(); val duration = u16(); b.position(b.position() + 2); val new = u32()
            val chunks = if (new != 0) new else old
            val cels = mutableListOf<Cel>()
            repeat(chunks) {
                val cStart = b.position()
                val cSize = u32(); val type = u16()
                when (type) {
                    0x2004 -> {
                        val flags = u16(); u16(); u16(); u16(); u16(); u16(); val opacity = u8(); b.position(b.position() + 3)
                        layers += Layer(string(), flags, opacity)
                    }
                    0x2005 -> {
                        val layer = u16(); val x = s16(); val y = s16(); u8(); val celType = u16(); s16(); b.position(b.position() + 5)
                        // Cel ligado (o Aseprite cria ao duplicar frames): reaproveita a imagem do frame indicado.
                        if (celType == 1) {
                            val linked = u16()
                            frames.getOrNull(linked)?.cels?.firstOrNull { it.layer == layer }?.let { cels += it.copy(x = x, y = y) }
                            b.position(cStart + cSize)
                            return@repeat
                        }
                        require(celType == 0 || celType == 2) { "cel tipo $celType não suportado" }
                        val cw = u16(); val ch = u16()
                        val raw = ByteArray(cw * ch * 4)
                        if (celType == 0) b.get(raw) else {
                            val z = ByteArray(cStart + cSize - b.position()); b.get(z)
                            Inflater().apply { setInput(z); var off = 0; while (off < raw.size && !finished()) off += inflate(raw, off, raw.size - off); end() }
                        }
                        val img = PixelBuffer(cw, ch)
                        for (i in 0 until cw * ch) {
                            img.pixels[i] = ((raw[i * 4 + 3].toInt() and 0xFF) shl 24) or ((raw[i * 4].toInt() and 0xFF) shl 16) or
                                ((raw[i * 4 + 1].toInt() and 0xFF) shl 8) or (raw[i * 4 + 2].toInt() and 0xFF)
                        }
                        cels += Cel(layer, x, y, img)
                    }
                    0x2018 -> {
                        val n = u16(); b.position(b.position() + 8)
                        repeat(n) { val from = u16(); val to = u16(); b.position(b.position() + 1 + 2 + 6 + 3 + 1); tags += Tag(string(), from, to) }
                    }
                    0x2019 -> {
                        val n = u32(); u32(); u32(); b.position(b.position() + 8)
                        repeat(n) {
                            val flags = u16(); val r = u8(); val g = u8(); val bl = u8(); val a = u8()
                            if (flags and 1 != 0) string()
                            palette += (a shl 24) or (r shl 16) or (g shl 8) or bl
                        }
                    }
                }
                b.position(cStart + cSize)
            }
            frames += Frame(duration, cels)
            b.position(start + size)
        }
        return Document(w, h, layers, frames, tags, palette)
    }
}
