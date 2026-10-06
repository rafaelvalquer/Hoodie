package com.hoodie.app.pixel.npc.shopping

enum class ShoppingTripState { ENTERING, SHOPPING, READY_TO_CHECKOUT, CHECKOUT, LEAVING }

enum class ShoppingNpcIntent {
    ENTER_STORE, BROWSE_AISLE, LOOK_PRODUCT, PICK_PRODUCT, READ_LABEL, COMPARE_PRODUCTS,
    CHECK_LIST, CHECK_PHONE, LOOK_PROMOTION, PUT_IN_BASKET, WALK_TO_OTHER_AISLE,
    WALK_TO_CHECKOUT, WAIT_CHECKOUT, PAY, EXIT_STORE, IDLE,
}

enum class ShoppingNpcSpot {
    OFFSCREEN, DOOR, CENTER,
    AISLE_A_START, AISLE_A_MIDDLE, AISLE_A_END,
    AISLE_B_START, AISLE_B_MIDDLE, AISLE_B_END,
    PROMOTION_SIGN, CART_AREA, CHECKOUT_QUEUE, CHECKOUT_COUNTER,
}

data class ShoppingBasketState(val itemCount: Int, val maxItems: Int) {
    init { require(itemCount in 0..maxItems && maxItems > 0) }
    val isFull: Boolean get() = itemCount >= maxItems
}

data class ShoppingNpcState(
    val tripState: ShoppingTripState,
    val currentIntent: ShoppingNpcIntent,
    val currentSpot: ShoppingNpcSpot,
    val targetSpot: ShoppingNpcSpot?,
    val basket: ShoppingBasketState,
    val intentStartedAt: Long,
    val nextDecisionAt: Long,
    val recentIntents: List<ShoppingNpcIntent>,
    val decisionIndex: Long,
    val speechLine: String? = null,
    val speechOffsetMs: Long = 0,
    val speechCooldownUntil: Long = 0,
)

data class ShoppingNpcVisualState(
    val active: Boolean = false,
    val selectedShelf: ShoppingNpcSpot? = null,
    val productRemovedA: Boolean = false,
    val productRemovedB: Boolean = false,
    val checkoutActive: Boolean = false,
    val doorOpen: Boolean = false,
) {
    companion object { val EMPTY = ShoppingNpcVisualState() }
}
