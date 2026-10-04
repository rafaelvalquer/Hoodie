package com.hoodie.app.pixel.character.outfit

sealed interface OutfitStyle {
    data object Hoodie : OutfitStyle
    data object Suit : OutfitStyle
    data object Casual : OutfitStyle
    data object Student : OutfitStyle
    data object Sport : OutfitStyle
    data object Commuter : OutfitStyle
}
