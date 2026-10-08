package com.hoodie.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.engine.routine.RoutineLearningCoordinator
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RoutineLearningWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters,
    private val learner: Lazy<RoutineLearningCoordinator>, private val gate: DatabaseGate) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!com.hoodie.app.core.config.HoodieConfig.LEARNED_ROUTINE) return Result.success()
        if (!gate.isReady()) return Result.retry()
        return try { learner.get().recompute(); Result.success() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
    companion object { const val NAME = "routine_learning" }
}
