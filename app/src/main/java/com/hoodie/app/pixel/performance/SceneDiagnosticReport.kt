package com.hoodie.app.pixel.performance

/** Export is explicit at the caller. This formatter has no IO or Android dependencies. */
object SceneDiagnosticReport {
    const val HEADER = "scene,stage,frames,presentations,fps,renderP50Ms,renderP95Ms,renderP99Ms,intervalP95Ms,planningP95Ms,stateP95Ms,npcP95Ms,objectsP95Ms,compositionP95Ms,lightingP95Ms,postP95Ms,bitmapP95Ms,activeNpcs,visibleNpcs,sessionsCreated,timelineRebuilds,snapshotsBuilt,heapBytes,heapDeltaBytes,processAllocatedBytes,processGcCount,processGcMs,renderThread,medianFps,sessionsDisposed"
    fun row(stage: String, s: SceneDiagnosticSnapshot): String {
        require(stage.matches(Regex("[a-zA-Z0-9_-]+")))
        return listOf(s.scene.name, stage, s.frames, s.presentations, s.fps, s.renderP50Ms, s.renderP95Ms,
            s.renderP99Ms, s.intervalP95Ms, *s.stageP95Ms.drop(1).toTypedArray(), s.bitmapP95Ms,
            s.activeNpcs, s.visibleNpcs, s.sessionsCreated, s.timelineRebuilds, s.snapshotsBuilt,
            s.heapBytes, s.heapDeltaBytes, s.processAllocatedBytes, s.processGcCount, s.processGcTimeMs,
            s.renderThread.replace(",", " ").replace("\n", " "), s.medianFps, s.sessionsDisposed).joinToString(",") { it?.toString() ?: "" }
    }
}
