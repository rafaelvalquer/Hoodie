package com.hoodie.app.pixel

import org.json.JSONObject

/** Regras explícitas para impedir a gravação de goldens sem revisão artística V3 completa. */
internal object NpcArtV3ApprovalGate {
    private val requiredSections = listOf(
        "matrices",
        "walk_all_species",
        "turn_sit_talk_sheets",
        "expressions_and_head_crops",
        "outfits",
        "props",
        "scale_legibility_075_to_100",
        "public_scenes",
        "day_night_contrast",
        "hoodie_bulldog_gate",
        "hoodie_cat_gate",
    )

    private val requiredCharacters = listOf(
        "bulldog_exec",
        "dog_worker",
        "rabbit_analyst",
        "mouse_commuter",
        "duck_sleepy",
        "raccoon_window",
        "cat_colleague",
    )

    private val requiredCharacterSections = listOf("idle", "walk", "talk", "sit")

    fun isApproved(manifest: JSONObject): Boolean {
        if (manifest.optString("version") != "npc-art-v3") return false
        if (manifest.optString("approval_state") != "APPROVED") return false
        if (manifest.optString("reviewed_by").isBlank() || manifest.optString("reviewed_at").isBlank()) return false
        if (requiredSections.any { manifest.optString(it) != "APPROVED" }) return false

        val characters = manifest.optJSONObject("characters") ?: return false
        if (requiredCharacters.any { !characters.has(it) }) return false
        return requiredCharacters.all { id ->
            val character = characters.optJSONObject(id) ?: return@all false
            requiredCharacterSections.all { character.optString(it) == "APPROVED" }
        }
    }
}
