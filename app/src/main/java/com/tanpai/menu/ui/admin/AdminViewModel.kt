package com.tanpai.menu.ui.admin

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.MenuSnapshot
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.SellResult
import com.tanpai.menu.imaging.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: MenuRepository,
    private val settings: SettingsStore,
    private val imageStore: ImageStore
) : ViewModel() {

    val deviceId: String = settings.deviceId

    var storeName by mutableStateOf(settings.storeName)
        private set

    var autostartEnabled by mutableStateOf(settings.autostartEnabled)
        private set

    val menu: StateFlow<MenuSnapshot> = repository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuSnapshot())

    fun updateStoreName(value: String) {
        storeName = value
        settings.storeName = value
    }

    fun updateAutostart(value: Boolean) {
        autostartEnabled = value
        settings.autostartEnabled = value
    }

    fun createCaptureUri(): Uri = imageStore.newCaptureUri()

    suspend fun importImage(uri: Uri): String? = imageStore.importFromUri(uri)

    fun deleteImage(imagePath: String?) = imageStore.delete(imagePath)

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
        fun factory(
            repository: MenuRepository,
            settings: SettingsStore,
            imageStore: ImageStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AdminViewModel(repository, settings, imageStore) as T
        }
    }
}
