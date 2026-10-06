package com.hoodie.app.pixel.npc.restaurant

data class RestaurantIntentDuration(val minimumMs: Long, val maximumMs: Long) {
    init { require(minimumMs > 0 && maximumMs >= minimumMs) }
}

data class RestaurantNpcProfile(
    val weightsByMealState: Map<RestaurantMealState, Map<RestaurantNpcIntent, Int>>,
    val durations: Map<RestaurantNpcIntent, RestaurantIntentDuration>,
    val eatingTargetMs: RestaurantIntentDuration,
    val talkCooldownMs: RestaurantIntentDuration,
    val phoneCooldownMs: RestaurantIntentDuration,
    val drinkCooldownMs: RestaurantIntentDuration,
) {
    fun duration(intent: RestaurantNpcIntent): RestaurantIntentDuration =
        durations.getValue(intent)

    companion object {
        val CALM_GUEST = RestaurantNpcProfile(
            weightsByMealState = mapOf(
                RestaurantMealState.WAITING to mapOf(
                    RestaurantNpcIntent.WAIT_FOOD to 30,
                    RestaurantNpcIntent.IDLE to 18,
                    RestaurantNpcIntent.CHECK_PHONE to 12,
                    RestaurantNpcIntent.LOOK_AROUND to 10,
                    RestaurantNpcIntent.LOOK_WINDOW to 7,
                    RestaurantNpcIntent.READ_MENU to 15,
                    RestaurantNpcIntent.DRINK to 4,
                    RestaurantNpcIntent.TALK to 4,
                ),
                RestaurantMealState.SERVED to mapOf(
                    RestaurantNpcIntent.EAT to 30,
                    RestaurantNpcIntent.IDLE to 18,
                    RestaurantNpcIntent.DRINK to 12,
                    RestaurantNpcIntent.CHECK_PHONE to 12,
                    RestaurantNpcIntent.LOOK_AROUND to 10,
                    RestaurantNpcIntent.LOOK_WINDOW to 7,
                    RestaurantNpcIntent.TALK to 4,
                ),
                RestaurantMealState.EATING to mapOf(
                    RestaurantNpcIntent.EAT to 30,
                    RestaurantNpcIntent.IDLE to 18,
                    RestaurantNpcIntent.DRINK to 12,
                    RestaurantNpcIntent.CHECK_PHONE to 12,
                    RestaurantNpcIntent.LOOK_AROUND to 10,
                    RestaurantNpcIntent.LOOK_WINDOW to 7,
                    RestaurantNpcIntent.TALK to 4,
                ),
                RestaurantMealState.FINISHED to mapOf(
                    RestaurantNpcIntent.IDLE to 20,
                    RestaurantNpcIntent.DRINK to 16,
                    RestaurantNpcIntent.CHECK_PHONE to 14,
                    RestaurantNpcIntent.LOOK_AROUND to 15,
                    RestaurantNpcIntent.LOOK_WINDOW to 12,
                    RestaurantNpcIntent.TALK to 5,
                    RestaurantNpcIntent.LEAVE to 18,
                ),
            ),
            durations = mapOf(
                RestaurantNpcIntent.EAT to RestaurantIntentDuration(6_000, 15_000),
                RestaurantNpcIntent.IDLE to RestaurantIntentDuration(4_000, 12_000),
                RestaurantNpcIntent.DRINK to RestaurantIntentDuration(3_000, 6_000),
                RestaurantNpcIntent.CHECK_PHONE to RestaurantIntentDuration(3_000, 8_000),
                RestaurantNpcIntent.LOOK_AROUND to RestaurantIntentDuration(2_000, 6_000),
                RestaurantNpcIntent.LOOK_WINDOW to RestaurantIntentDuration(3_000, 8_000),
                RestaurantNpcIntent.READ_MENU to RestaurantIntentDuration(4_000, 9_000),
                RestaurantNpcIntent.WAIT_FOOD to RestaurantIntentDuration(10_000, 22_000),
                RestaurantNpcIntent.TALK to RestaurantIntentDuration(1_500, 3_000),
                RestaurantNpcIntent.REACT_TO_HOODIE to RestaurantIntentDuration(1_200, 2_000),
                RestaurantNpcIntent.LEAVE to RestaurantIntentDuration(1_500, 3_000),
                RestaurantNpcIntent.ENTER to RestaurantIntentDuration(1_500, 3_000),
            ),
            eatingTargetMs = RestaurantIntentDuration(120_000, 165_000),
            talkCooldownMs = RestaurantIntentDuration(45_000, 120_000),
            phoneCooldownMs = RestaurantIntentDuration(20_000, 45_000),
            drinkCooldownMs = RestaurantIntentDuration(15_000, 40_000),
        )
    }
}
