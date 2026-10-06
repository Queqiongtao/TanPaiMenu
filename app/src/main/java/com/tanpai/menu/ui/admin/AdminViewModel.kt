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
import com.tanpai.menu.ui.theme.BackgroundStyles
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
    /**
     * 设置页当前选中的内置样式；null 表示正在使用自定义背景图（此时不高亮任何样式卡）。
     * 未设置过背景时回退默认样式「喜庆红」，与实际展示页一致。
     */
    var backgroundStyleId by mutableStateOf(
        if (settings.backgroundImagePath == null) {
            settings.backgroundStyleId ?: BackgroundStyles.DEFAULT_ID
        } else {
            null
        }
    )
        private set
    var wechatQrImagePath by mutableStateOf(settings.wechatQrImagePath)
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

    /**
     * 导入并保存展示页背景图；成功后删除旧图并清空内置样式（两者互斥），返回是否导入成功。
     */
    suspend fun applyBackgroundImage(uri: Uri): Boolean {
        val path = imageStore.importFromUri(
            uri,
            maxEdge = ImageStore.BACKGROUND_MAX_EDGE,
            jpegQuality = ImageStore.BACKGROUND_JPEG_QUALITY
        ) ?: return false
        val previous = backgroundImagePath
        backgroundImagePath = path
        settings.backgroundImagePath = path
        backgroundStyleId = null
        settings.backgroundStyleId = null
        if (previous != null && previous != path) imageStore.delete(previous)
        TpLog.i(TAG, "已更新展示页背景图")
        return true
    }

    /** 选用内置背景样式：写入样式 id，并清除自定义背景图与其文件（两者互斥）。 */
    fun applyBackgroundStyle(id: String) {
        val previous = backgroundImagePath
        backgroundStyleId = id
        settings.backgroundStyleId = id
        backgroundImagePath = null
        settings.backgroundImagePath = null
        imageStore.delete(previous)
        TpLog.i(TAG, "已选用内置背景样式 $id")
    }

    /** 恢复默认背景：清除自定义背景图并回到默认样式（喜庆红），同时删除图片文件。 */
    fun clearBackground() {
        val previous = backgroundImagePath
        backgroundStyleId = BackgroundStyles.DEFAULT_ID
        settings.backgroundStyleId = BackgroundStyles.DEFAULT_ID
        backgroundImagePath = null
        settings.backgroundImagePath = null
        imageStore.delete(previous)
        TpLog.i(TAG, "已恢复默认背景")
    }

    /** 导入并保存展示页微信二维码（无损 PNG），成功后删除旧图；返回是否导入成功。 */
    suspend fun applyWechatQr(uri: Uri): Boolean {
        val path = imageStore.importLosslessFromUri(uri) ?: return false
        val previous = wechatQrImagePath
        wechatQrImagePath = path
        settings.wechatQrImagePath = path
        if (previous != null && previous != path) imageStore.delete(previous)
        TpLog.i(TAG, "已更新微信二维码")
        return true
    }

    /** 清除展示页微信二维码并删除对应文件。 */
    fun clearWechatQr() {
        val previous = wechatQrImagePath
        wechatQrImagePath = null
        settings.wechatQrImagePath = null
        imageStore.delete(previous)
        TpLog.i(TAG, "已清除微信二维码")
    }

    /** 清空菜单与图片并写回默认菜单，用于「恢复默认菜单」。 */
    fun resetToDefaultMenu() {
        viewModelScope.launch {
            runCatching {
                repository.resetToSeedMenu()
                imageStore.deleteOrphans(setOfNotNull(backgroundImagePath, wechatQrImagePath))
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
