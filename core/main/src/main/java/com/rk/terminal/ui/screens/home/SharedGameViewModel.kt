package com.rk.terminal.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SharedGameViewModel : ViewModel() {
    var selectedGameTitle by mutableStateOf("")
    var selectedGameUris by mutableStateOf<List<String>>(emptyList())
    var selectedGameObjectId by mutableStateOf<String?>(null)
    var selectedGameShop by mutableStateOf<String?>(null)
    var selectedGameCover by mutableStateOf<String?>(null)

    fun setGame(title: String, uris: List<String>) {
        selectedGameTitle = title
        selectedGameUris = uris
    }
}
