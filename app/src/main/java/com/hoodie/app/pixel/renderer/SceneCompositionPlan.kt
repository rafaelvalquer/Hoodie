package com.hoodie.app.pixel.renderer

import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.events.StoryObjectHolder
import com.hoodie.app.pixel.npc.events.StoryObjectState
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.Prop
import com.hoodie.app.pixel.scene.SceneEnv

/** Reusable entries in original prop/NPC/floor-object order, stably sorted by depth.
 * No per-frame mapped pairs, draw closures, filtered lists or sorted copies. */
internal class SceneCompositionPlan {
    class Entry {
        var prop: Prop? = null
        var npc: AmbientNpcSlot? = null
        var floorObject: StoryObjectState? = null
        var baseline = 0
        fun clear() { prop = null; npc = null; floorObject = null }
    }
    private val pool = ArrayList<Entry>()
    val entries = ArrayList<Entry>()
    internal var entriesCreated = 0
        private set

    fun update(scene: PixelScene, slots: List<AmbientNpcSlot>, env: SceneEnv, timeMs: Long) {
        entries.clear()
        fun next(): Entry {
            val index = entries.size
            val entry = if (index < pool.size) pool[index] else Entry().also { pool.add(it); entriesCreated++ }
            entry.clear(); entries.add(entry)
            return entry
        }
        for (i in scene.sortedProps.indices) {
            val prop = scene.sortedProps[i]
            next().also { it.prop = prop; it.baseline = prop.baseline }
        }
        for (i in slots.indices) {
            val npc = slots[i]
            next().also { it.npc = npc; it.baseline = sceneNpcDepth(npc, timeMs) }
        }
        val objects = env.sceneStory?.objects
        if (objects != null) for (i in objects.indices) {
            val obj = objects[i]
            if (obj.holder == StoryObjectHolder.ON_FLOOR && obj.x != null && obj.y != null) {
                next().also { it.floorObject = obj; it.baseline = obj.y }
            }
        }
        // Clear unused pool refs so a smaller next scene cannot retain an old NPC session.
        for (i in entries.size until pool.size) pool[i].clear()
        for (i in 1 until entries.size) {
            val entry = entries[i]
            var j = i - 1
            while (j >= 0 && entries[j].baseline > entry.baseline) {
                entries[j + 1] = entries[j]; j--
            }
            entries[j + 1] = entry
        }
    }

    fun clear() { entries.clear(); pool.forEach { it.clear() } }
}

internal fun sceneNpcDepth(slot: AmbientNpcSlot, timeMs: Long): Int =
    slot.story?.movementAt(slot.definition.id, timeMs)?.floorY
        ?: slot.leisureActor?.frameStateAt(timeMs)?.floorY ?: slot.venueActor?.frameStateAt(timeMs)?.floorY ?: slot.baseline
