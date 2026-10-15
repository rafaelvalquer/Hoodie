package com.hoodie.app.engine.reaction

object ReactionArbitrationPolicy {
    fun replacesPending(incoming: ReactionIntent, pending: ReactionIntent) = incoming.priority.rank > pending.priority.rank
    /** Priority never bypasses an essential clip or a safety/scene transition. */
    fun canDispatch(safe: Boolean, essential: Boolean, socialBusy: Boolean, intent: ReactionIntent) =
        safe && !essential && (!socialBusy || intent.source == ReactionSource.SOCIAL_EVENT)
}
