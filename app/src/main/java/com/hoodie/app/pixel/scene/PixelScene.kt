package com.hoodie.app.pixel.scene

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.renderer.PixelBuffer

enum class SceneId(val label: String) {
    HOME("Casa"),
    OFFICE("Escritório"),
    STREET("Rua"),
    TRANSIT("Ônibus"),
    CAR("Carro"),
    RESTAURANT("Restaurante"),
    GYM("Academia"),
    UNKNOWN("Desconhecido"),
    GENERIC_INDOOR("Interior genérico"),
    GENERIC_OUTDOOR("Parque"),
}

/** Zonas/âncoras de cena (BED_ZONE, WORK_DESK...). */
enum class SpotId { BED, DESK, SOFA, KITCHEN, WINDOW, CENTER, DOOR, COFFEE, TABLE, TREADMILL, WEIGHTS, WATER, MAT, SEAT, WALK, PATH_A, PATH_B }

/** Âncora com a posição dos pés do Hoodie (x central, y do chão). */
data class Spot(val x: Int, val y: Int)

/** Dados variáveis que a cena precisa para se desenhar. */
data class SceneEnv(
    val period: DayPeriod,
    /** Minuto do dia real (relógio de parede da cena). */
    val clockMinute: Int,
    /** Variante estável (prato do almoço, decoração etc.). */
    val variant: Int = 0,
    val tvOn: Boolean = false,
    val screenOn: Boolean = true,
    /** Porta: 0 fechada … [DOOR_OPEN] aberta (DOOR_CLOSED → OPENING → OPEN → CLOSING). */
    val doorFrame: Int = 0,
    /** Estado de props sincronizado por eventos das animações. */
    val flags: Set<SceneFlag> = emptySet(),
) {
    companion object {
        const val DOOR_OPEN = 3
    }
}

/** Props que mudam conforme o Hoodie interage (caneca na mão, comida servida, cadeira ocupada…). */
enum class SceneFlag { MUG_IN_HAND, PHONE_IN_HAND, FOOD_SERVED, FOOD_DONE, CHAIR_OCCUPIED, DUMBBELL_TAKEN, IN_BED }

/** Objeto ordenado por Y: desenhado antes do Hoodie se [baseline] <= pés dele, depois caso contrário. */
class Prop(val baseline: Int, val draw: (PixelBuffer, SceneEnv, Long) -> Unit)

/** Fonte de luz para o overlay noturno: retângulos emissivos ou brilho circular. */
sealed interface Light {
    data class Emissive(val x0: Int, val y0: Int, val x1: Int, val y1: Int) : Light
    data class Glow(val cx: Int, val cy: Int, val radius: Int, val strength: Float = 0.75f) : Light
}

abstract class PixelScene(val id: SceneId) {
    val width = SCENE_W
    val height = SCENE_H

    abstract val spots: Map<SpotId, Spot>

    /** Spot padrão quando a atividade não pede um específico. */
    open val defaultSpot: SpotId = SpotId.CENTER

    /** Cenas de deslocamento: o gato anda "no lugar" e o cenário corre (paralaxe). */
    open val walkInPlace: Boolean = false

    /** Desenha a porta como prop animável (senão a porta, se houver, é estática). */
    open val hasAnimatedDoor: Boolean = false

    /** Cenas já escuras/estilizadas podem dispensar o overlay de período. */
    open val usesLighting: Boolean = true

    /** Camada estática (paredes, chão, janela). Cacheada por período. */
    abstract fun drawBackground(b: PixelBuffer, env: SceneEnv)

    /** Objetos com profundidade e microanimações. */
    abstract fun props(): List<Prop>

    /** Props criados uma vez e já ordenados por profundidade. */
    val sortedProps: List<Prop> by lazy { props().sortedBy { it.baseline } }

    open fun lights(env: SceneEnv): List<Light> = emptyList()

    fun spot(id: SpotId): Spot = spots[id] ?: spots.getValue(defaultSpot)

    companion object {
        const val SCENE_W = 240
        const val SCENE_H = 320
    }
}
