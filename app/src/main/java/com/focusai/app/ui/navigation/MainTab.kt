package com.focusai.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.focusai.app.R

enum class MainTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    Focus("focus", R.string.tab_focus, Icons.Outlined.Home),
    Stats("stats", R.string.tab_stats, Icons.Outlined.BarChart),
    Settings("settings", R.string.tab_settings, Icons.Outlined.Settings),
    About("about", R.string.tab_about, Icons.Outlined.Info)
}
