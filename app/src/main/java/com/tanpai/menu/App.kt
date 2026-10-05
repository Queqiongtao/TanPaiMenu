package com.tanpai.menu

import android.app.Application
import android.content.Context
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.PinManager
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.TpDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val database: TpDatabase = TpDatabase.build(context)
    val menuRepository: MenuRepository = MenuRepository(database)
    val settingsStore: SettingsStore = SettingsStore(context)
    val pinManager: PinManager = PinManager(settingsStore)
}

class App : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch { container.menuRepository.seedIfEmpty() }
    }
}
