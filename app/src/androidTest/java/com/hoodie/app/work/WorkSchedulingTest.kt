package com.hoodie.app.work

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.hoodie.app.worker.ReconcileWorker
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A HoodieApp agenda a reconciliação periódica ao iniciar; o BootReceiver faz o
 * mesmo depois de um reboot (Jornada 6). Só leitura: não altera o agendamento.
 */
@RunWith(AndroidJUnit4::class)
class WorkSchedulingTest {

    @Test
    fun reconciliacaoPeriodicaEstaAgendada() {
        val wm = WorkManager.getInstance(ApplicationProvider.getApplicationContext())
        // O enqueue da HoodieApp.onCreate é assíncrono: espera até 5 s.
        var infos = wm.getWorkInfosForUniqueWork(ReconcileWorker.NAME).get()
        var waited = 0
        while (infos.isEmpty() && waited < 5_000) {
            Thread.sleep(250)
            waited += 250
            infos = wm.getWorkInfosForUniqueWork(ReconcileWorker.NAME).get()
        }
        assertTrue("ReconcileWorker deve existir", infos.isNotEmpty())
        assertTrue("estados: ${infos.map { it.state }}", infos.any { !it.state.isFinished })
    }
}
