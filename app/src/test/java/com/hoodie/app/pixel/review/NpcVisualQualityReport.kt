package com.hoodie.app.pixel.review

data class NpcVisualQualityReport(
    val character: String,
    val pose: String,
    val clipped: Boolean,
    /** Cores efetivamente renderizadas no checkpoint, incluindo detalhes faciais compartilhados. */
    val paletteSize: Int,
    /** Tamanho da paleta declarada para a identidade NPC. */
    val declaredPaletteSize: Int,
    val renderedPaletteSize: Int,
    val outsidePaletteColors: Int,
    val outlineRatio: Float,
    val furOutfitContrast: Float,
    val height: Int,
    val width: Int,
    val handPropDistance: Int?,
    val footContactValid: Boolean,
    val anchorValid: Boolean,
    val hardIssues: List<String>,
    val softWarnings: List<String>,
) {
    val score: String get() = if (hardIssues.isEmpty() && softWarnings.isEmpty()) "OK" else if (hardIssues.isNotEmpty()) "FAIL" else "WARNING"

    fun json(): String = """{"character":"$character","pose":"$pose","score":"$score","clipped":$clipped,"paletteSize":$paletteSize,"declaredPaletteSize":$declaredPaletteSize,"renderedPaletteSize":$renderedPaletteSize,"outsidePaletteColors":$outsidePaletteColors,"outlineRatio":${"%.4f".format(java.util.Locale.ROOT, outlineRatio)},"furOutfitContrast":${"%.3f".format(java.util.Locale.ROOT, furOutfitContrast)},"width":$width,"height":$height,"handPropDistance":${handPropDistance ?: "null"},"footContactValid":$footContactValid,"anchorValid":$anchorValid,"hardIssues":[${hardIssues.joinToString { "\"$it\"" }}],"softWarnings":[${softWarnings.joinToString { "\"$it\"" }}]}"""
}
