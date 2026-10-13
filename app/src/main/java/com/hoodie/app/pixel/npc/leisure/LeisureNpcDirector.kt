package com.hoodie.app.pixel.npc.leisure

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcBehaviorSequence
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.npc.NpcPath
import com.hoodie.app.pixel.npc.NpcPathPoint
import com.hoodie.app.pixel.npc.NpcSpeechProfile
import com.hoodie.app.pixel.npc.NpcStep
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.sprite.Facing

/**
 * Pequenas histórias ambientais no Passeio: viajantes cruzam a cena numa só direção,
 * param para observar, fotografar e se alongar, e saem antes de reiniciar o percurso.
 * Uma terceira personagem ocupa um dos lados do banco com uma rotina própria.
 *
 * Não há Random por frame: mesma variante, seed e tempo geram a mesma cena.
 * Usa NpcPath/NpcBehaviorSequence e o painter universal dos outros ambientes.
 */
object LeisureNpcDirector {
    fun plan(env: SceneEnv): List<AmbientNpcSlot> {
        // As variantes 0 e 3 compartilham cenário e também devem compartilhar NPCs.
        val variant = env.variant.mod(3)
        val seed = env.daySeed * 37 + variant * 17
        val drift = (env.daySeed.mod(3) - 1) * 4
        val quiet = env.period == DayPeriod.NIGHT

        val rabbit = NpcCharacterRegistry.RABBIT_WALKER
        val cat = NpcCharacterRegistry.CAT_COLLEAGUE
        val reader = NpcCharacterRegistry.MOUSE_COMMUTER

        val explorer = AmbientNpcSlot(
            definition = definition(rabbit, NpcAnimation.WALK),
            x = -36, floorY = 266, baseline = 266, seed = seed + 11,
            depth = NpcDepth.SCENE,
            path = NpcPath(
                points = listOf(
                    NpcPathPoint(-36, 264, holdMs = 600),
                    NpcPathPoint(34 + drift, 264, NpcAnimation.LOOK_WINDOW, 3_400),
                    NpcPathPoint(72 + drift, 268, NpcAnimation.STRETCH, 2_500),
                    NpcPathPoint(120 + drift, 273, NpcAnimation.STAND_PHONE, 2_600),
                    NpcPathPoint(171 + drift, 270, NpcAnimation.LOOK, 3_000),
                    NpcPathPoint(208 + drift, 266, if (quiet) NpcAnimation.LOOK_WINDOW else NpcAnimation.STAND_PHOTO, 2_700),
                    NpcPathPoint(277, 266),
                ),
                millisPerPixel = 34,
            ),
        )

        val sightseer = AmbientNpcSlot(
            definition = definition(
                cat, NpcAnimation.WALK,
                speech = NpcSpeechProfile(
                    listOf("Que vista!", "Dia bonito.", "Olha as flores."),
                    cycleMs = 55_000, visibleMs = 1_500,
                ),
            ),
            x = 277, floorY = 281, baseline = 281, seed = seed + 53,
            depth = NpcDepth.SCENE,
            path = NpcPath(
                points = listOf(
                    NpcPathPoint(277, 283, holdMs = 1_600),
                    NpcPathPoint(212 - drift, 281, NpcAnimation.LOOK, 2_300),
                    NpcPathPoint(183 - drift, 279, NpcAnimation.STAND_PHOTO, 3_800),
                    NpcPathPoint(136 - drift, 281, NpcAnimation.STAND_PHONE, 2_500),
                    NpcPathPoint(81 - drift, 281, NpcAnimation.STRETCH, 2_400),
                    NpcPathPoint(30 - drift, 282, NpcAnimation.TALK, 1_800),
                    NpcPathPoint(-37, 283),
                ),
                millisPerPixel = 38,
            ),
        )

        // O banco é estreito: ocupa o canto esquerdo para deixar o centro ao Hoodie.
        val benchReader = AmbientNpcSlot(
            definition = definition(
                reader, NpcAnimation.SIT_LOOK,
                sequence = NpcBehaviorSequence.of(
                    NpcStep(NpcAnimation.SIT_LOOK, 5_500, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.READ_DOCUMENT, 7_200, seated = true, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.SIT_DRINK, 3_600, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.SIT_PHONE, 4_300, facing = Facing.FRONT),
                    NpcStep(NpcAnimation.SIT_LOOK, 2_800, facing = Facing.FRONT),
                ),
            ),
            x = 98, floorY = 223, baseline = 220, seed = seed + 97,
            depth = NpcDepth.SCENE,
        )
        // À noite, o parque fica mais tranquilo e o banco permanece vazio.
        return if (quiet) listOf(explorer, sightseer) else listOf(explorer, sightseer, benchReader)
    }

    private fun definition(
        style: CharacterStyle,
        default: NpcAnimation,
        sequence: NpcBehaviorSequence? = null,
        speech: NpcSpeechProfile? = null,
    ): AmbientNpcDefinition = AmbientNpcDefinition(
        id = style.id,
        characterStyle = style,
        behaviorProfile = NpcBehaviorProfile(
            animation = default,
            motion = SpeciesMotionProfiles.forCharacter(style),
            sequence = sequence,
            // Evita que reações espontâneas interrompam a ação selecionada no ponto.
            reactions = false,
        ),
        speechProfile = speech,
    )
}
