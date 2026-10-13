package com.hoodie.app.engine.personality

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.personality.PersonalityTrait
import org.json.JSONArray

/**
 * Fala de personalidade declarada em assets/metadata/personality-dialogues.json:
 *
 *     { "id": "break_coffee_01", "trigger": "WORK_BREAK", "trait": "COFFEE_AFFINITY", "minTrait": 70,
 *       "activity": "COFFEE", "text": "Pausa boa é pausa com café.", "priority": 3 }
 *
 * [trait]/[minTrait] só liberam a fala se o traço do perfil for alto o bastante. Campos nulos não filtram.
 */
data class PersonalityDialogueRule(
    val id: String,
    val trigger: PersonalityTrigger,
    val text: String,
    val trait: PersonalityTrait? = null,
    val minTrait: Int = 0,
    val context: UserContextType? = null,
    val activity: HoodieActivity? = null,
    val hourFrom: Int? = null,
    val hourTo: Int? = null,
    val priority: Int = 1,
)

object PersonalityDialogueCatalog {
    const val ASSET = "metadata/personality-dialogues.json"
    /** Falas curtas: cabem no balão sem quebrar em muitas linhas. */
    const val MAX_TEXT_LENGTH = 48

    fun parse(json: String): List<PersonalityDialogueRule> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            fun intOrNull(k: String) = if (o.has(k)) o.getInt(k) else null
            PersonalityDialogueRule(
                id = o.getString("id"),
                trigger = PersonalityTrigger.valueOf(o.getString("trigger")),
                text = o.getString("text"),
                trait = o.optString("trait").takeIf { it.isNotEmpty() }?.let { PersonalityTrait.valueOf(it) },
                minTrait = o.optInt("minTrait", 0),
                context = o.optString("context").takeIf { it.isNotEmpty() }?.let { UserContextType.valueOf(it) },
                activity = o.optString("activity").takeIf { it.isNotEmpty() }?.let { HoodieActivity.valueOf(it) },
                hourFrom = intOrNull("hourFrom"),
                hourTo = intOrNull("hourTo"),
                priority = o.optInt("priority", 1),
            )
        }
    }
}
