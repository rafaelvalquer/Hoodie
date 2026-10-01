package com.hoodie.app.pixel

import com.hoodie.app.pixel.renderer.PixelBuffer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Exporta buffers ampliados (nearest-neighbor) para build/pixel-preview, para
 * revisar sprites e cenas sem emulador. O classpath de teste do Android não tem
 * java.awt, então o PNG é escrito à mão.
 */
object PreviewExport {
    val dir: File = File("build/pixel-preview").apply { mkdirs() }

    fun save(name: String, buf: PixelBuffer, scale: Int = 4, background: Int = 0xFF2B2E4A.toInt()) =
        write(File(dir, "$name.png"), buf, scale, background)

    /** [background] null mantém a transparência (PNG RGBA). */
    fun write(file: File, buf: PixelBuffer, scale: Int, background: Int?) {
        file.parentFile?.mkdirs()
        val alpha = background == null
        val w = buf.width * scale; val h = buf.height * scale
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) {
            raw.write(0)
            for (x in 0 until w) {
                val c = buf[x / scale, y / scale].let { if (!alpha && it ushr 24 == 0) background!! else it }
                raw.write((c shr 16) and 0xFF); raw.write((c shr 8) and 0xFF); raw.write(c and 0xFF)
                if (alpha) raw.write((c ushr 24) and 0xFF)
            }
        }
        val deflater = Deflater(6).apply { setInput(raw.toByteArray()); finish() }
        val idat = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (!deflater.finished()) idat.write(chunk, 0, deflater.deflate(chunk))

        val out = DataOutputStream(file.outputStream())
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        val ihdr = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { writeInt(w); writeInt(h); writeByte(8); writeByte(if (alpha) 6 else 2); writeByte(0); writeByte(0); writeByte(0) }
        }
        writeChunk(out, "IHDR", ihdr.toByteArray())
        writeChunk(out, "IDAT", idat.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        out.close()
    }

    private fun writeChunk(out: DataOutputStream, type: String, data: ByteArray) {
        out.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes); out.write(data)
        val crc = CRC32().apply { update(typeBytes); update(data) }
        out.writeInt(crc.value.toInt())
    }

    /** Monta vários buffers lado a lado. */
    fun sheet(name: String, buffers: List<PixelBuffer>, columns: Int, scale: Int = 3) {
        if (buffers.isEmpty()) return
        val w = buffers.maxOf { it.width } + 2; val h = buffers.maxOf { it.height } + 2
        val rows = (buffers.size + columns - 1) / columns
        val out = PixelBuffer(w * columns, h * rows)
        out.fill(0xFF2B2E4A.toInt())
        buffers.forEachIndexed { i, b -> out.blit(b, (i % columns) * w + 1, (i / columns) * h + 1) }
        save(name, out, scale)
    }
}
