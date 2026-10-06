package com.hoodie.app.pixel.npc.restaurant

enum class RestaurantNpcIntent {
    EAT,
    DRINK,
    CHECK_PHONE,
    LOOK_AROUND,
    LOOK_WINDOW,
    READ_MENU,
    WAIT_FOOD,
    TALK,
    REACT_TO_HOODIE,
    IDLE,
    ENTER,
    LEAVE,
}

enum class RestaurantMealState { WAITING, SERVED, EATING, FINISHED }
enum class RestaurantFoodAmount { FULL, PARTIAL, LOW, EMPTY }
enum class RestaurantDrinkAmount { FULL, HALF, EMPTY }

data class RestaurantTableState(
    val mealState: RestaurantMealState,
    val menuOpen: Boolean,
    val foodAmount: RestaurantFoodAmount,
    val drinkAmount: RestaurantDrinkAmount,
)

data class RestaurantNpcState(
    val npcId: String,
    val currentIntent: RestaurantNpcIntent,
    val mealState: RestaurantMealState,
    val currentSpot: RestaurantNpcSpot,
    val targetSpot: RestaurantNpcSpot?,
    val intentStartedAt: Long,
    val nextDecisionAt: Long,
    val recentIntents: List<RestaurantNpcIntent>,
    val decisionIndex: Long,
    val eatingElapsedMs: Long,
    val eatingTargetMs: Long,
    val drinksTaken: Int,
    val menuRead: Boolean,
    val cooldowns: Map<RestaurantNpcIntent, Long>,
    val speechLine: String? = null,
)
