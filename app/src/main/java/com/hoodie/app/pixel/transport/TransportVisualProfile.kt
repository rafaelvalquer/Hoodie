package com.hoodie.app.pixel.transport

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.mobility.MobilityPhase
import com.hoodie.app.core.mobility.MobilityVisualSnapshot
import com.hoodie.app.core.mobility.ModeCertainty
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.diary.journey.JourneyVehicle
import com.hoodie.app.pixel.scene.SceneId

enum class TransportRouteStyle { WALK, CAR, BUS, TRAIN, METRO, BICYCLE, OTHER, GENERIC_TRANSIT }
enum class TransportVibration { NONE, LOW, MEDIUM }
enum class TransportLighting { OPEN_AIR, DAYLIGHT_INTERIOR, TUNNEL }

data class TransportAnimationSet(
    val enter: List<AnimationId> = emptyList(),
    val primary: List<AnimationId>,
    val exit: List<AnimationId> = emptyList(),
)

data class TransportAmbientProfile(
    val parallax: Boolean,
    val outsideSpeed: Float,
    val vibration: TransportVibration,
    val lighting: TransportLighting,
)

data class TransportJourneyProfile(val vehicle: JourneyVehicle, val routeStyle: TransportRouteStyle)

data class TransportVisualProfile(
    val mode: MovementMode?,
    val scene: SceneId,
    val animationSet: TransportAnimationSet,
    val ambient: TransportAmbientProfile,
    val journey: TransportJourneyProfile,
)

data class TransportVisualResolution(
    val profile: TransportVisualProfile,
    val certainty: ModeCertainty,
    val source: MobilitySource?,
    val reason: String,
)

/** Single mapping from recognized movement to Home scene, animation, ambience and Journey style. */
object TransportVisualRegistry {
    private val neutral = TransportVisualProfile(
        null, SceneId.GENERIC_OUTDOOR, TransportAnimationSet(primary = listOf(AnimationId.IDLE)),
        TransportAmbientProfile(true, .35f, TransportVibration.NONE, TransportLighting.OPEN_AIR),
        TransportJourneyProfile(JourneyVehicle.GENERIC_TRANSIT, TransportRouteStyle.GENERIC_TRANSIT),
    )
    private val walk = TransportVisualProfile(
        null, SceneId.STREET, TransportAnimationSet(primary = listOf(AnimationId.WALK)),
        TransportAmbientProfile(true, .7f, TransportVibration.NONE, TransportLighting.OPEN_AIR),
        TransportJourneyProfile(JourneyVehicle.ON_FOOT, TransportRouteStyle.WALK),
    )
    private val busPreference = walk.copy(
        scene = SceneId.BUS,
        animationSet = TransportAnimationSet(
            listOf(AnimationId.BUS_ENTER),
            listOf(AnimationId.BUS_SIT, AnimationId.BUS_LOOK_WINDOW, AnimationId.BUS_PHONE, AnimationId.BUS_BUMP),
            listOf(AnimationId.BUS_STAND, AnimationId.BUS_EXIT),
        ),
        ambient = TransportAmbientProfile(true, .55f, TransportVibration.MEDIUM, TransportLighting.DAYLIGHT_INTERIOR),
        journey = TransportJourneyProfile(JourneyVehicle.BUS, TransportRouteStyle.BUS),
    )

    fun profileFor(mode: MovementMode?, fallback: CommuteStyle, variant: Int = 0): TransportVisualProfile = when (mode) {
        MovementMode.WALKING -> walk.copy(mode = mode)
        MovementMode.RUNNING -> walk.copy(mode = mode, animationSet = TransportAnimationSet(primary = listOf(AnimationId.RUN)))
        MovementMode.BICYCLE -> profile(
            MovementMode.BICYCLE, SceneId.BICYCLE, AnimationId.BIKE_START,
            listOf(AnimationId.BIKE_PEDAL, AnimationId.BIKE_COAST, AnimationId.BIKE_LOOK), listOf(AnimationId.BIKE_BRAKE, AnimationId.BIKE_STOP),
            TransportAmbientProfile(true, .65f, TransportVibration.LOW, TransportLighting.OPEN_AIR),
            JourneyVehicle.BICYCLE, TransportRouteStyle.BICYCLE,
        )
        MovementMode.CAR -> profile(
            MovementMode.CAR, SceneId.CAR, AnimationId.CAR_ENTER,
            listOf(AnimationId.CAR_IDLE, AnimationId.CAR_LOOK_WINDOW, AnimationId.CAR_LOOK_FRONT, AnimationId.CAR_BUMP), listOf(AnimationId.CAR_EXIT),
            TransportAmbientProfile(true, .8f, TransportVibration.LOW, TransportLighting.DAYLIGHT_INTERIOR),
            JourneyVehicle.CAR, TransportRouteStyle.CAR,
        )
        MovementMode.BUS -> busPreference.copy(mode = mode)
        MovementMode.TRAIN -> profile(
            mode, SceneId.TRAIN, AnimationId.TRAIN_ENTER,
            listOf(AnimationId.TRAIN_SIT, AnimationId.TRAIN_WINDOW, AnimationId.TRAIN_PHONE, AnimationId.TRAIN_BRAKE), listOf(AnimationId.TRAIN_STAND, AnimationId.TRAIN_EXIT),
            TransportAmbientProfile(true, .9f, TransportVibration.LOW, TransportLighting.DAYLIGHT_INTERIOR),
            JourneyVehicle.TRAIN, TransportRouteStyle.TRAIN,
        )
        MovementMode.METRO -> profile(
            mode, SceneId.METRO, AnimationId.METRO_ENTER,
            listOf(AnimationId.METRO_SIT, AnimationId.METRO_HANDLE, AnimationId.METRO_LOOK_WINDOW, AnimationId.METRO_BRAKE), listOf(AnimationId.METRO_STAND, AnimationId.METRO_EXIT),
            TransportAmbientProfile(true, 1f, TransportVibration.MEDIUM, TransportLighting.TUNNEL),
            JourneyVehicle.METRO, TransportRouteStyle.METRO,
        )
        MovementMode.OTHER -> profile(
            mode, SceneId.GENERIC_RIDE, AnimationId.OTHER_RIDE_START,
            listOf(AnimationId.OTHER_RIDE, AnimationId.OTHER_RIDE_LOOK), listOf(AnimationId.OTHER_RIDE_STOP),
            TransportAmbientProfile(true, .6f, TransportVibration.LOW, TransportLighting.OPEN_AIR),
            JourneyVehicle.OTHER, TransportRouteStyle.OTHER,
        )
        MovementMode.PUBLIC_TRANSPORT, MovementMode.VEHICLE_UNKNOWN -> genericTransit(mode)
        MovementMode.NONE, null -> when (fallback) {
            CommuteStyle.WALK -> walk
            CommuteStyle.BUS -> busPreference
            CommuteStyle.RANDOM -> if (variant % 2 == 0) walk else busPreference
        }
    }

    /** Resolves only from current mobility evidence; absence of a mode never means walking. */
    fun resolve(
        snapshot: MobilityVisualSnapshot,
        preferredMode: MovementMode?,
        now: Long,
        variant: Int = 0,
    ): TransportVisualResolution {
        val open = snapshot.sessionId != null && snapshot.phase in setOf(MobilityPhase.CANDIDATE, MobilityPhase.ACTIVE, MobilityPhase.ARRIVING)
        val candidateRecent = snapshot.observedAt?.let { now >= it && now - it <= CANDIDATE_EVIDENCE_MAX_AGE_MS } == true
        val vehicleRecent = snapshot.lastVehicleAt?.let { now >= it && now - it <= CANDIDATE_EVIDENCE_MAX_AGE_MS } == true
        val vehicleStillObserved = snapshot.vehicleExitAt == null || snapshot.lastVehicleAt == null || snapshot.vehicleExitAt < snapshot.lastVehicleAt

        if (open && snapshot.mode?.isVehicle == true && snapshot.phase != MobilityPhase.CANDIDATE) {
            val mode = snapshot.mode
            val profile = profileFor(mode, CommuteStyle.WALK, variant)
            return TransportVisualResolution(profile, snapshot.certainty, snapshot.source,
                "Sessão ativa com modo $mode")
        }
        if (open && snapshot.observedMovement == com.hoodie.app.core.mobility.DetectedMovement.IN_VEHICLE && vehicleRecent && vehicleStillObserved) {
            val selected = preferredMode?.takeIf { it.isVehicle }
            val mode = selected ?: MovementMode.VEHICLE_UNKNOWN
            val certainty = if (selected != null) ModeCertainty.PREFERRED else ModeCertainty.PROVISIONAL
            val source = if (selected != null) MobilitySource.PREFERENCE else MobilitySource.ACTIVITY_RECOGNITION
            return TransportVisualResolution(profileFor(mode, CommuteStyle.WALK, variant), certainty, source,
                if (selected != null) "Veículo detectado; preferência aplicada: $selected" else "IN_VEHICLE detectado; tipo ainda desconhecido")
        }
        if (open && candidateRecent) {
            when (snapshot.observedMovement) {
                com.hoodie.app.core.mobility.DetectedMovement.WALKING -> return TransportVisualResolution(
                    profileFor(MovementMode.WALKING, CommuteStyle.WALK, variant), ModeCertainty.PROVISIONAL,
                    MobilitySource.ACTIVITY_RECOGNITION, "Caminhada detectada provisoriamente")
                com.hoodie.app.core.mobility.DetectedMovement.RUNNING -> return TransportVisualResolution(
                    profileFor(MovementMode.RUNNING, CommuteStyle.WALK, variant), ModeCertainty.PROVISIONAL,
                    MobilitySource.ACTIVITY_RECOGNITION, "Corrida detectada provisoriamente")
                com.hoodie.app.core.mobility.DetectedMovement.ON_BICYCLE -> return TransportVisualResolution(
                    profileFor(MovementMode.BICYCLE, CommuteStyle.WALK, variant), ModeCertainty.PROVISIONAL,
                    MobilitySource.ACTIVITY_RECOGNITION, "Bicicleta detectada provisoriamente")
                else -> Unit
            }
        }
        return TransportVisualResolution(neutral, ModeCertainty.UNKNOWN, null,
            if (!open) "Nenhuma sessão de deslocamento ativa" else "Sem evidência recente suficiente para escolher um transporte")
    }

    private fun genericTransit(mode: MovementMode?) = profile(
        mode, SceneId.TRANSIT, AnimationId.TRANSIT_ENTER,
        listOf(AnimationId.TRANSIT_SIT, AnimationId.TRANSIT_LOOK_WINDOW, AnimationId.TRANSIT_BUMP), listOf(AnimationId.TRANSIT_EXIT),
        TransportAmbientProfile(true, .7f, TransportVibration.LOW, TransportLighting.DAYLIGHT_INTERIOR),
        JourneyVehicle.GENERIC_TRANSIT, TransportRouteStyle.GENERIC_TRANSIT,
    )

    private const val CANDIDATE_EVIDENCE_MAX_AGE_MS = 5 * 60_000L

    private fun profile(
        mode: MovementMode?, scene: SceneId, enter: AnimationId, primary: List<AnimationId>, exit: List<AnimationId>,
        ambient: TransportAmbientProfile, vehicle: JourneyVehicle, route: TransportRouteStyle,
    ) = TransportVisualProfile(
        mode, scene, TransportAnimationSet(listOf(enter), primary, exit), ambient,
        TransportJourneyProfile(vehicle, route),
    )
}
