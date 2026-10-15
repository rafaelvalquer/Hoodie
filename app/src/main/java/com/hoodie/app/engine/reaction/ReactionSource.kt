package com.hoodie.app.engine.reaction

import com.hoodie.app.engine.personality.ReactionPriority

enum class ReactionSource(val priority: ReactionPriority) {
    SAFETY_TRANSITION(ReactionPriority.SAFETY_TRANSITION),
    ESSENTIAL_ANIMATION(ReactionPriority.ESSENTIAL_ANIMATION),
    USER_INTERACTION(ReactionPriority.USER_INTERACTION),
    SOCIAL_EVENT(ReactionPriority.SOCIAL_EVENT),
    PERSONALITY_REACTION(ReactionPriority.PERSONALITY_REACTION),
    AMBIENT_SPEECH(ReactionPriority.AMBIENT_SPEECH),
}
