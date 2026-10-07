package com.hoodie.app.pixel.transport

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Test
import java.io.File

/**
 * Exporta as cenas V3 para revisão humana. Sempre em build/pixel-preview/transport-v3/;
 * com `-PtransportReview=true` também em docs/transport-art/review/. Só exporta, não aprova.
 */
class TransportSceneV3ExportTest {
    @Test fun exportCarReview() {
        val shots = DayPeriod.entries.map { p -> p to TransportSceneV3Review.render(MovementMode.CAR, p).image }
        val dirs = listOfNotNull(File(PreviewExport.dir, "transport-v3"),
            System.getProperty("transportReviewOutput")?.takeIf { System.getProperty("transportReview") == "true" }?.let(::File))
        dirs.forEach { dir ->
            shots.forEach { (p, img) -> PreviewExport.write(File(dir, "car-${p.name.lowercase()}.png"), img, 2, null) }
            val sheet = PixelBuffer(240 * 4 + 24, 320).apply { fill(0xFF2B2E4A.toInt()) }
            shots.forEachIndexed { i, (_, img) -> sheet.blit(img, i * 248, 0) }
            PreviewExport.write(File(dir, "car-periods.png"), sheet, 1, 0xFF2B2E4A.toInt())
        }
    }
}
