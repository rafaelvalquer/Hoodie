package com.hoodie.app.domain.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupProgressTest {
    private fun p(done: Boolean = true, home: Boolean = false, work: Boolean = false, bg: Boolean = false, notif: Boolean = false) =
        SetupProgress.of(done, home, work, bg, notif, demo = false)

    @Test fun `antes da entrada inicial nada comecou`() = assertEquals(SetupStage.NOT_STARTED, p(done = false).stage)

    @Test fun `entrada concluida sem localizacao fica nos basicos`() {
        assertEquals(SetupStage.BASICS_COMPLETED, p().stage)
        assertEquals(SetupStage.BASICS_COMPLETED, p(home = true).stage) // Casa sem monitoramento ainda nao e localizacao pronta
    }

    @Test fun `casa mais localizacao o tempo todo = localizacao pronta`() = assertEquals(SetupStage.LOCATION_READY, p(home = true, bg = true).stage)

    @Test fun `com trabalho decidido a rotina esta pronta`() = assertEquals(SetupStage.ROUTINE_READY, p(home = true, bg = true, work = true).stage)

    @Test fun `tudo configurado`() {
        val all = p(home = true, bg = true, work = true, notif = true)
        assertEquals(SetupStage.FULLY_CONFIGURED, all.stage)
        assertNull(all.next)
    }

    @Test fun `pendencias seguem a ordem sugerida`() {
        assertEquals(listOf(SetupItem.HOME, SetupItem.BACKGROUND_LOCATION, SetupItem.WORK, SetupItem.NOTIFICATIONS), p().pending)
        assertEquals(SetupItem.WORK, p(home = true, bg = true).next)
        assertEquals(listOf(SetupItem.NOTIFICATIONS), p(home = true, bg = true, work = true).pending)
    }
}