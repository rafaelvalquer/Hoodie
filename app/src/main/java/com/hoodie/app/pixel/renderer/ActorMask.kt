package com.hoodie.app.pixel.renderer

/**
 * Marca os pixels que um ator (Hoodie ou NPC) desenhou dentro da região das luzes emissivas, para que a
 * iluminação trate esses pixels como "quem está na frente do vidro" e não como o próprio vidro aceso.
 * Só trabalha na região das emissivas (cópia e comparação de poucos milhares de pixels por ator); fora dela,
 * ou em cenas sem luz emissiva, [track] apenas desenha.
 */
class ActorMask(private val width: Int, private val height: Int) {
    val mask = BooleanArray(width * height)
    var active = false
        private set
    private var dirty = false
    private var x0 = 0; private var y0 = 0; private var x1 = 0; private var y1 = 0
    private var snapshot = IntArray(0)

    /** [bounds] = (x0, y0, x1, y1) das luzes emissivas, ou null quando a cena não tem. */
    fun begin(bounds: IntArray?) {
        if (dirty) { java.util.Arrays.fill(mask, false); dirty = false }
        active = bounds != null
        if (bounds != null) { x0 = bounds[0]; y0 = bounds[1]; x1 = bounds[2]; y1 = bounds[3] }
    }

    inline fun track(buffer: PixelBuffer, draw: () -> Unit) {
        if (!active) { draw(); return }
        before(buffer)
        draw()
        after(buffer)
    }

    fun before(buffer: PixelBuffer) {
        val rw = x1 - x0 + 1; val rh = y1 - y0 + 1
        if (snapshot.size < rw * rh) snapshot = IntArray(rw * rh)
        for (y in y0..y1) System.arraycopy(buffer.pixels, y * width + x0, snapshot, (y - y0) * rw, rw)
    }

    fun after(buffer: PixelBuffer) {
        val rw = x1 - x0 + 1
        val px = buffer.pixels
        for (y in y0..y1) {
            val row = y * width; val sRow = (y - y0) * rw
            for (x in x0..x1) if (px[row + x] != snapshot[sRow + x - x0]) { mask[row + x] = true; dirty = true }
        }
    }
}