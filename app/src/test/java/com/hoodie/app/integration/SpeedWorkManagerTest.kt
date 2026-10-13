package com.hoodie.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.testing.SynchronousExecutor
import com.hoodie.app.core.database.*
import com.hoodie.app.worker.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpeedWorkManagerTest {
    @Test fun keepPolicyPreventsParallelReadsAndCancellationTargetsEvidenceAndSession() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                object : CoroutineWorker(appContext, workerParameters) {
                    override suspend fun doWork(): Result { awaitCancellation() }
                }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder()
            .setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).setWorkerFactory(factory).build())
        val scheduler = WorkScheduler(context)
        val wm = WorkManager.getInstance(context)
        try {
            scheduler.enqueue(10, 20)
            scheduler.enqueue(10, 20)
            val infos = wm.getWorkInfosForUniqueWork(TransportSpeedSampleWorker.evidenceName(20)).get()
            assertEquals(1, infos.count { !it.state.isFinished })
            assertTrue(infos.single().tags.contains(TransportSpeedSampleWorker.sessionTag(10)))
            scheduler.cancelEvidence(20)
            wm.getWorkInfosForUniqueWork(TransportSpeedSampleWorker.evidenceName(20)).get().forEach { assertTrue(it.state.isFinished) }
            scheduler.enqueue(10, 21)
            scheduler.cancelSession(10)
            assertTrue(wm.getWorkInfosByTag(TransportSpeedSampleWorker.sessionTag(10)).get().all { it.state.isFinished })
        } finally { wm.cancelAllWork().result.get() }
    }

    @Test fun databaseUnavailableRetriesWithoutInstantiatingSampler() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gate = DatabaseGate { object : SecureDatabaseBootstrap {
            override fun prepare(): DatabaseBootstrapResult = DatabaseBootstrapResult.Failed(DatabaseSecurityState.KeyUnrecoverable)
        } }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                TransportSpeedSampleWorker(appContext, workerParameters, gate, dagger.Lazy { error("must remain lazy while unavailable") })
        }
        val worker = TestListenableWorkerBuilder<TransportSpeedSampleWorker>(context).setWorkerFactory(factory).build()
        assertEquals(ListenableWorker.Result.retry(), worker.doWork())
    }
}
