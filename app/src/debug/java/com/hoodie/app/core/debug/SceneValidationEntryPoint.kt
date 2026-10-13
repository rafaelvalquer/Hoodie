package com.hoodie.app.core.debug

import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.engine.context.ContextEngine
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Instrumentation accesses the real app graph, without opening another database. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SceneValidationEntryPoint {
    fun settings(): SettingsRepository
    fun places(): PlaceRepository
    fun database(): HoodieDatabase
    fun contextEngine(): ContextEngine
    fun homeVisuals(): HomeVisualDiagnostics
}
