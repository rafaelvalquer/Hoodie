package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimGroup
import org.json.JSONObject

/**
 * Manifest de revisão artística (assets-source/hoodie/art-status.json, copiado para
 * o APK). `final` = o grupo tem arte final no runtime; `manualReview` = um humano
 * revisou no Aseprite. A release (versão sem "-dev") exige revisão manual de todos.
 */
data class ArtGroupStatus(val final: Boolean, val manualReview: Boolean, val pass: String?, val reviewedBy: String?)

object ArtReviewStatus {
    const val FILE = "art-status.json"

    /** Grupo do manifest → grupo de animações. */
    val GROUPS: Map<String, AnimGroup> = mapOf("walk" to AnimGroup.LOCOMOTION, "idle" to AnimGroup.IDLE, "sleep" to AnimGroup.SLEEP, "work" to AnimGroup.WORK)

    fun parse(json: String): Map<String, ArtGroupStatus> {
        val root = JSONObject(json)
        return root.keys().asSequence().associateWith { k ->
            val o = root.getJSONObject(k)
            ArtGroupStatus(
                final = o.optBoolean("final"),
                manualReview = o.optBoolean("manualReview"),
                pass = o.optString("pass").takeIf { it.isNotEmpty() && it != "null" },
                reviewedBy = if (o.isNull("reviewedBy")) null else o.optString("reviewedBy"),
            )
        }
    }

    /** Grupos que ainda bloqueiam a release. */
    fun pendingReview(status: Map<String, ArtGroupStatus>): List<String> =
        GROUPS.keys.filter { status[it]?.let { s -> s.final && s.manualReview } != true }

    fun reviewedPercent(status: Map<String, ArtGroupStatus>, group: AnimGroup): Int {
        val key = GROUPS.entries.firstOrNull { it.value == group }?.key ?: return 0
        return if (status[key]?.manualReview == true) 100 else 0
    }
}
