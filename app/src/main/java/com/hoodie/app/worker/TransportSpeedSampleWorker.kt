package com.hoodie.app.worker
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hoodie.app.core.database.DatabaseGate
import com.hoodie.app.engine.mobility.TransportSpeedSampler
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
@HiltWorker
class TransportSpeedSampleWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val gate: DatabaseGate, private val sampler: Lazy<TransportSpeedSampler>,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!gate.isReady()) return Result.retry()
        val session = inputData.getLong(SESSION, -1)
        val evidence = inputData.getLong(EVIDENCE, -1)
        if (session < 0 || evidence < 0) return Result.failure()
        try { sampler.get().sample(session, evidence, id.toString()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: com.hoodie.app.core.database.DatabaseUnavailableException) { return Result.retry() }
        catch (_: Exception) { return Result.success() }
        return Result.success()
    }
    companion object {
        const val SESSION = "sessionId"
        const val EVIDENCE = "evidenceId"
        const val TAG = "transport_speed"
        fun sessionTag(id: Long) = "$TAG/session/$id"
        fun evidenceName(id: Long) = "$TAG/evidence/$id"
    }
}
