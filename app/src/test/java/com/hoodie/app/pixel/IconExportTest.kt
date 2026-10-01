package com.hoodie.app.pixel

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePose
import org.junit.Test
import java.io.File

/**
 * Gera o foreground do ícone adaptativo a partir do próprio sprite do Hoodie,
 * em escala inteira por densidade (o ícone é pixel art de verdade, não um blur).
 * Rode com -PexportIcons=true quando o personagem mudar.
 */
class IconExportTest {
    @Test
    fun exportLauncherForeground() {
        if (System.getProperty("exportIcons") != "true") return
        val head = HoodiePainter.sprite(HoodiePose(headOnly = true))
        // Grade lógica de 54×54 (= 108dp em mdpi /2). Cabeça centralizada na zona segura.
        val grid = PixelBuffer(54, 54)
        val crop = PixelBuffer(48, 37)
        for (y in 0 until 37) for (x in 0 until 48) crop.set(x, y, head[x, y])
        grid.blit(crop, 3, 9)
        val res = File("src/main/res")
        mapOf("mdpi" to 2, "hdpi" to 3, "xhdpi" to 4, "xxhdpi" to 6, "xxxhdpi" to 8).forEach { (density, scale) ->
            PreviewExport.write(File(res, "mipmap-$density/ic_launcher_foreground.png"), grid, scale, background = null)
        }
    }
}
