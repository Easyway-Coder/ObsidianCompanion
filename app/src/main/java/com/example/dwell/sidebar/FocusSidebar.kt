package com.example.dwell.sidebar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dwell.screens.FocusTimerViewModel
import com.example.dwell.screens.NoteViewModel

@Composable
fun FocusSidebar(
    focusVm: FocusTimerViewModel? = null,
    noteVm: NoteViewModel? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Focus Timer",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Column(modifier = Modifier.padding(16.dp)) {
            val statusText = when {
                focusVm?.sessionInfinite == true -> "Strict stopwatch active"
                focusVm?.isRunning == true -> "Focus session in progress"
                focusVm?.infiniteMode == true -> "Mode: Infinite stopwatch"
                else -> "Mode: Countdown timer"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (noteVm != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tasks synced with: ${noteVm.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}
