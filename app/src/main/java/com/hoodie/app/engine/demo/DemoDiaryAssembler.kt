package com.hoodie.app.engine.demo

import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.engine.diary.DiaryAssembler
import com.hoodie.app.engine.diary.JourneyMapAssembler

data class DemoDiaryView(val diary: DailyDiary, val journey: JourneyMapData)

/** Reaproveita os montadores reais do Diário com dados em memória; somente leitura. */
object DemoDiaryAssembler {
    fun assemble(day: DemoDay): DemoDiaryView {
        val diary = DiaryAssembler.build(day.date, day.contexts, day.timeline, emptyList(), day.places, day.dayStart, day.dayEnd, day.now)
        return DemoDiaryView(diary, JourneyMapAssembler.build(diary, day.now))
    }
}