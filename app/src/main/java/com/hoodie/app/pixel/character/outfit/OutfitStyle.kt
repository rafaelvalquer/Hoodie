package com.hoodie.app.pixel.character.outfit

sealed interface OutfitStyle {
    data object Hoodie : OutfitStyle
    data object Suit : OutfitStyle
    data object Casual : OutfitStyle
    data object Student : OutfitStyle
    data object Sport : OutfitStyle
    data object Commuter : OutfitStyle
}

/** Pintor de cada roupa (um arquivo por roupa em `outfit/`). */
val OutfitStyle.painter: OutfitPainter
    get() = when (this) {
        OutfitStyle.Suit -> SuitOutfitPainter
        OutfitStyle.Casual -> CasualOutfitPainter
        OutfitStyle.Student -> StudentOutfitPainter
        OutfitStyle.Sport -> SportOutfitPainter
        OutfitStyle.Commuter -> CommuterOutfitPainter
        OutfitStyle.Hoodie -> HoodieOutfitPainter
    }

/** Acessório nas costas: alças na frente, volume atrás. */
sealed interface BackAccessory {
    data object None : BackAccessory
    data object Backpack : BackAccessory
    data object ShoulderBag : BackAccessory
}
