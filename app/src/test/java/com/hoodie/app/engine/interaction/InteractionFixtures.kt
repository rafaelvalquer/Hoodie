package com.hoodie.app.engine.interaction

import java.io.File

internal fun interactionRules() = HoodieInteractionCatalog.parse(File("src/main/assets/metadata/interactive_dialogues.json").readText())
