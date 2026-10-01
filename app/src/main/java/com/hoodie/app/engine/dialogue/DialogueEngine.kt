package com.hoodie.app.engine.dialogue

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import org.json.JSONArray
import kotlin.random.Random

/**
 * Regra de fala declarada em assets/metadata/dialogues.json. Campos nulos não filtram.
 *
 *     { "id": "work_long_day_01", "context": "WORK", "minContextMinutes": 600, "text": "Hoje o expediente está longo." }
 */
data class DialogueRule(
    val id: String,
    val text: String,
    val context: UserContextType? = null,
    val activity: HoodieActivity? = null,
    val minContextMinutes: Int? = null,
    val hourFrom: Int? = null,
    val hourTo: Int? = null,
    val weekend: Boolean? = null,
    val overtime: Boolean? = null,
    val maxEnergy: Int? = null,
    val arrivedEarly: Boolean? = null,
)

data class DialogueInput(
    val context: UserContextType,
    val activity: HoodieActivity,
    val contextMinutes: Long,
    val hour: Int,
    val weekend: Boolean,
    val overtime: Boolean,
    val energy: Int,
    val arrivedEarly: Boolean,
)

class DialogueEngine(private val rules: List<DialogueRule>) {

    fun matches(input: DialogueInput): List<DialogueRule> = rules.filter { r ->
        (r.context == null || r.context == input.context) &&
            (r.activity == null || r.activity == input.activity) &&
            (r.minContextMinutes == null || input.contextMinutes >= r.minContextMinutes) &&
            (r.hourFrom == null || input.hour >= r.hourFrom) &&
            (r.hourTo == null || input.hour < r.hourTo) &&
            (r.weekend == null || r.weekend == input.weekend) &&
            (r.overtime == null || r.overtime == input.overtime) &&
            (r.maxEnergy == null || input.energy <= r.maxEnergy) &&
            (r.arrivedEarly == null || r.arrivedEarly == input.arrivedEarly)
    }

    /** Regras mais específicas (mais condições) têm prioridade sobre falas genéricas. */
    fun pick(input: DialogueInput, random: Random): String? {
        val candidates = matches(input)
        if (candidates.isEmpty()) return null
        val best = candidates.maxOf { specificity(it) }
        val top = candidates.filter { specificity(it) == best }
        return top[random.nextInt(top.size)].text
    }

    /** Condições situacionais (dia longo, hora extra, cansaço, chegou cedo) valem mais que contexto/atividade. */
    private fun specificity(r: DialogueRule): Int =
        listOf(r.context, r.activity, r.hourFrom, r.hourTo, r.weekend).count { it != null } +
            2 * listOf(r.minContextMinutes, r.overtime, r.maxEnergy, r.arrivedEarly).count { it != null }

    companion object {
        fun parse(json: String): List<DialogueRule> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun intOrNull(k: String) = if (o.has(k)) o.getInt(k) else null
                fun boolOrNull(k: String) = if (o.has(k)) o.getBoolean(k) else null
                DialogueRule(
                    id = o.getString("id"),
                    text = o.getString("text"),
                    context = o.optString("context").takeIf { it.isNotEmpty() }?.let { UserContextType.valueOf(it) },
                    activity = o.optString("activity").takeIf { it.isNotEmpty() }?.let { HoodieActivity.valueOf(it) },
                    minContextMinutes = intOrNull("minContextMinutes"),
                    hourFrom = intOrNull("hourFrom"),
                    hourTo = intOrNull("hourTo"),
                    weekend = boolOrNull("weekend"),
                    overtime = boolOrNull("overtime"),
                    maxEnergy = intOrNull("maxEnergy"),
                    arrivedEarly = boolOrNull("arrivedEarly"),
                )
            }
        }
    }
}
