package com.corner.catvodcore.enum

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ConfigType {
    SITE
}

enum class Menu(val icon: ImageVector){
    HOME(Icons.Outlined.Home),
    SETTING(Icons.Outlined.Settings),
    SEARCH(Icons.Outlined.Search),
    HISTORY(Icons.Outlined.History)
}