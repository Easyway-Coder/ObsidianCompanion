package com.example.dwell.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    FOCUS_TIMER("Focus Timer", Icons.Outlined.Timer),
    NOTES("Notes", Icons.Outlined.Edit),
    PLAN("Plan", Icons.Outlined.CalendarMonth),
    STATS("Stats", Icons.Outlined.BarChart),
}
