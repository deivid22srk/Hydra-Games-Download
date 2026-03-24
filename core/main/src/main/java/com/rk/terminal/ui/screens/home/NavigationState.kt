package com.rk.terminal.ui.screens.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object NavigationState {
    var selectedTab by mutableIntStateOf(0)
}
