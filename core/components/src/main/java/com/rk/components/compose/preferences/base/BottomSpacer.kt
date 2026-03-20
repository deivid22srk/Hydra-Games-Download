package com.rk.components.compose.preferences.base

/*
 * Copyright 2021, Lawnchair.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Creates a spacer that respects the [navigation bars][WindowInsets.Companion.navigationBars] and
 * [IME][WindowInsets.Companion.ime].
 */
@Composable
fun BottomSpacer(modifier: Modifier = Modifier) {
    Spacer(
        modifier =
            modifier
                .windowInsetsBottomHeight(WindowInsets.navigationBars.union(WindowInsets.ime))
                .fillMaxWidth()
    )
}
