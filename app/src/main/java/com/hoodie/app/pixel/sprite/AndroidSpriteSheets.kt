package com.hoodie.app.pixel.sprite

import android.content.Context
import android.graphics.BitmapFactory
import com.hoodie.app.pixel.renderer.PixelBuffer
import java.io.InputStream

/** Ponte Android: assets do APK + BitmapFactory sem escala nem pré-multiplicação. */
object AndroidSpriteSheets {

    @Volatile
    var lastReport: SheetLoadReport = SheetLoadReport(emptyList(), emptyList<SheetProblem>())
        private set

    /** Clips/vistas servidos por arte final (para a cobertura no Pixel Lab). */
    @Volatile
    var available: Set<Pair<com.hoodie.app.pixel.animation.AnimationId, Facing>> = emptySet()
        private set

    /** Manifest de revisão artística (vazio se ausente). */
    @Volatile
    var artStatus: Map<String, ArtGroupStatus> = emptyMap()
        private set

    fun install(context: Context) {
        val assets = object : SpriteAssetSource {
            override fun list(dir: String): List<String> = context.assets.list(dir)?.toList().orEmpty()
            override fun open(path: String): InputStream = context.assets.open(path)
        }
        val decoder = SpriteImageDecoder { input ->
            val opts = BitmapFactory.Options().apply { inScaled = false; inPremultiplied = false }
            BitmapFactory.decodeStream(input, null, opts)?.let { bmp ->
                PixelBuffer(bmp.width, bmp.height).also { bmp.getPixels(it.pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height); bmp.recycle() }
            }
        }
        val (sheets, report) = SpriteSheetProvider.load(assets, decoder)
        lastReport = report
        available = sheets.available
        artStatus = runCatching { assets.open("${SpriteSheetProvider.DIR}/${ArtReviewStatus.FILE}").bufferedReader().use { ArtReviewStatus.parse(it.readText()) } }.getOrDefault(emptyMap())
        HoodieSprites.provider = if (sheets.available.isEmpty()) ProceduralSpriteProvider else CompositeSpriteProvider(sheets)
    }
}
