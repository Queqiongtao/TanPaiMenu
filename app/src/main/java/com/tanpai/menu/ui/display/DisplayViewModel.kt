package com.tanpai.menu.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.MenuSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DisplayViewModel(repository: MenuRepository) : ViewModel() {

    val menu: StateFlow<MenuSnapshot> = repository.observeMenu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuSnapshot())

    companion object {
        fun factory(repository: MenuRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DisplayViewModel(repository) as T
            }
    }
}
