package com.example.dwell.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dwell.components.AccentColorPickerDialog
import com.example.dwell.components.ColorDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(vm: NoteViewModel, onBack: () -> Unit) {
    var pickingAccent by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    if (pickingAccent) {
        AccentColorPickerDialog(
            current = vm.accent,
            darkMode = vm.darkMode,
            onPick = {
                vm.updateAccent(it)
                pickingAccent = false
            },
            onDismiss = { pickingAccent = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PreferenceRow(label = "Accent Color", onClick = { pickingAccent = true }) {
                ColorDot(MaterialTheme.colorScheme.primary, size = 28)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceRow(label = "Dark Mode", onClick = { vm.setDarkMode(!vm.darkMode) }) {
                Switch(checked = vm.darkMode, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
