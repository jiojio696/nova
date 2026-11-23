package com.example

import android.app.Application
import com.example.data.database.NovaDatabase
import com.example.data.repository.HistoryRepository
import com.example.data.repository.NovaApiRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.NovaSessionController
import com.example.domain.scanner.OcrEngine
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class NovaContainer(application: Application) {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val database: NovaDatabase = NovaDatabase.getInstance(application)
    val historyRepository: HistoryRepository = HistoryRepository(database.scanHistoryDao(), moshi)
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val apiRepository: NovaApiRepository = NovaApiRepository(moshi)
    val ocrEngine: OcrEngine = OcrEngine()

    val sessionController: NovaSessionController = NovaSessionController(
        ocrEngine = ocrEngine,
        apiRepository = apiRepository,
        historyRepository = historyRepository,
        settingsRepository = settingsRepository
    )
}

class NovaApplication : Application() {
    lateinit var container: NovaContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = NovaContainer(this)
    }
}
