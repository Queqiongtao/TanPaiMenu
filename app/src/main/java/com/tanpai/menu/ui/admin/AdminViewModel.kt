package com.tanpai.menu.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.MenuSnapshot
import com.tanpai.menu.data.SettingsStore
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.domain.SellResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdminViewModel(
    private val repository: MenuRepository,
    settings: SettingsStore
) : ViewModel() {

    val deviceId: String = settings.deviceId

    val menu: StateFlow<MenuSnapshot> = repository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuSnapshot())

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

    fun deleteDish(id: Long) {
        viewModelScope.launch { repository.deleteDish(id) }
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
        fun factory(repository: MenuRepository, settings: SettingsStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AdminViewModel(repository, settings) as T
            }
    }
}
