package com.hoodie.app.pixel.npc.shopping

data class ShoppingNpcProfile(
    val browseWeight: Int = 22,
    val lookProductWeight: Int = 18,
    val pickProductWeight: Int = 12,
    val compareWeight: Int = 10,
    val checkListWeight: Int = 8,
    val checkPhoneWeight: Int = 6,
    val promotionWeight: Int = 7,
    val walkWeight: Int = 8,
    val idleWeight: Int = 7,
    val minPauseMs: Long = 2_000,
    val maxPauseMs: Long = 14_000,
)

object ShoppingNpcProfiles {
    val CALM_INDECISIVE = ShoppingNpcProfile()
}
