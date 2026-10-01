package com.hoodie.app.engine

import com.hoodie.app.core.model.Place
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.WorkMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

/** 2026-10-05 é uma segunda-feira. */
fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
    LocalDateTime.of(2026, 10, day, hour, minute).atZone(ZONE)

fun ZonedDateTime.ms(): Long = toInstant().toEpochMilli()

val MONDAY = 5
val SATURDAY = 10
val SUNDAY = 11

val officeRoutine = Routine(workMode = WorkMode.OFFICE)

fun place(id: Long, type: PlaceType, visits: Int = 0) =
    Place(id, type.label, type, -23.55, -46.63, 150f, visits, 0, null)

val HOME_PLACE = place(1, PlaceType.HOME)
val WORK_PLACE = place(2, PlaceType.WORK)
val GYM_PLACE = place(3, PlaceType.GYM, visits = 1)
