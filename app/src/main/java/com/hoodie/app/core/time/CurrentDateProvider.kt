package com.hoodie.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * "Hoje" como fluxo: emite de novo quando o relógio vira o dia (meia-noite,
 * mudança de fuso ou de hora). Use com flatMapLatest para nunca ficar
 * observando o dia anterior.
 */
fun currentDateFlow(clock: ClockProvider, tickMs: Long = DATE_TICK_MS): Flow<LocalDate> = flow {
    while (true) {
        emit(clock.today())
        delay(tickMs)
    }
}.distinctUntilChanged()

const val DATE_TICK_MS = 30_000L
