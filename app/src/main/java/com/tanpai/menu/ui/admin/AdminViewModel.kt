package com.tanpai.menu.ui.admin

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanpai.menu.backup.BackupManager
import com.tanpai.menu.backup.BackupResult
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.MenuSnapshot
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.SellResult
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.logging.TpLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: MenuRepository,
    private val settings: SettingsStore,
    private val imageStore: ImageStore,
    private val backupManager: BackupManager
) : ViewModel() {

    val deviceId: String = settings.deviceId

    var storeName by mutableStateOf(settings.displayStoreName)
        private set

    var autostartEnabled by mutableStateOf(settings.autostartEnabled)
        private set

    var showStockHint by mutableStateOf(settings.showStockHint)
        private set

    var cardAlphaPercent by mutableStateOf(settings.cardAlphaPercent)
        private set

    var backgroundImagePath by mutableStateOf(settings.backgroundImagePath)
        private set

    var backgroundScrimPercent by mutableStateOf(settings.backgroundScrimPercent)
        private set

    val menu: StateFlow<MenuSnapshot> = repository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuSnapshot())

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    private val _resetDone = MutableStateFlow(false)
    val resetDone: StateFlow<Boolean> = _resetDone.asStateFlow()

    fun updateStoreName(value: String) {
        storeName = value
        settings.storeName = value
    }

    fun updateAutostart(value: Boolean) {
        autostartEnabled = value
        settings.autostartEnabled = value
    }

    fun updateShowStockHint(value: Boolean) {
        showStockHint = value
        settings.showStockHint = value
    }

    fun updateCardAlphaPercent(value: Int) {
        cardAlphaPercent = value
        settings.cardAlphaPercent = value
    }

    fun updateBackgroundScrimPercent(value: Int) {
        backgroundScrimPercent = value
        settings.backgroundScrimPercent = value
    }

    /** 导入并保存展示页背景图，成功后删除旧图；返回是否导入成功。 */
    suspend fun applyBackgroundImage(uri: Uri): Boolean {
        val path = imageStore.importFromUri(
            uri,
            maxEdge = ImageStore.BACKGROUND_MAX_EDGE,
            jpegQuality = ImageStore.BACKGROUND_JPEG_QUALITY
        ) ?: return false
        val previous = backgroundImagePath
        backgroundImagePath = path
        settings.backgroundImagePath = path
        if (previous != null && previous != path) imageStore.delete(previous)
        TpLog.i(TAG, "已更新展示页背景图")
        return true
    }

    /** 清除展示页背景图并删除对应文件。 */
    fun clearBackgroundImage() {
        val previous = backgroundImagePath
        backgroundImagePath = null
        settings.backgroundImagePath = null
        imageStore.delete(previous)
        TpLog.i(TAG, "已清除展示页背景图")
    }

    /** 清空菜单与图片并写回默认菜单，用于「恢复默认菜单」。 */
    fun resetToDefaultMenu() {
        viewModelScope.launch {
            runCatching {
                repository.resetToSeedMenu()
                imageStore.deleteOrphans(setOfNotNull(backgroundImagePath))
            }.onSuccess {
                _resetDone.value = true
                TpLog.i(TAG, "已恢复默认菜单")
            }.onFailure {
                TpLog.e(TAG, "恢复默认菜单失败", it)
            }
        }
    }

    fun clearResetDone() {
        _resetDone.value = false
    }

    fun createCaptureUri(): Uri = imageStore.newCaptureUri()

    suspend fun importImage(uri: Uri): String? = imageStore.importFromUri(uri)

    fun deleteImage(imagePath: String?) = imageStore.delete(imagePath)

    fun exportBackup(directory: Uri) {
        viewModelScope.launch { _backupResult.value = backupManager.exportTo(directory) }
    }

    fun importBackup(directory: Uri) {
        viewModelScope.launch { _backupResult.value = backupManager.importFrom(directory) }
    }

    fun clearBackupResult() {
        _backupResult.value = null
    }

    fun createCategory(name: String, sortOrder: Int) {
        viewModelScope.launch { repository.createCategory(name, sortOrder) }
    }

    fun updateCategory(id: Long, name: String, sortOrder: Int) {
        viewModelScope.launch { repository.updateCategory(id, name, sortOrder) }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch { repository.deleteCategory(id) }
    }

    fun createDish(dish: Dish) {
        viewModelScope.launch { repository.createDish(dish) }
    }

    fun updateDish(dish: Dish) {
        viewModelScope.launch { repository.updateDish(dish) }
    }

    fun deleteDish(dish: Dish) {
        viewModelScope.launch {
            repository.deleteDish(dish.id)
            imageStore.delete(dish.imagePath)
        }
    }

    fun setSoldOut(dish: Dish, soldOut: Boolean) {
        viewModelScope.launch { repository.updateDish(dish.copy(soldOut = soldOut)) }
    }

    fun sell(dishId: Long, onResult: (SellResult) -> Unit) {
        viewModelScope.launch { onResult(repository.sell(dishId, deviceId)) }
    }

    fun unsell(dishId: Long, onResult: (SellResult) -> Unit) {
        viewModelScope.launch { onResult(repository.unsell(dishId, deviceId)) }
    }

    companion object {
        private const val TAG = "admin"

        fun factory(
            repository: MenuRepository,
            settings: SettingsStore,
            imageStore: ImageStore,
            backupManager: BackupManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminViewModel(repository, settings, imageStore, backupManager) as T
        }
    }
}
