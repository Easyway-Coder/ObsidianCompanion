package com.example.dwell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dwell.navigation.NavigationController
import com.example.dwell.screens.NoteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: NoteViewModel = viewModel()
            val dark = vm.darkMode
            val accent = vm.accent

            // Keep status/nav bar icon colours in sync with the in-app dark switch
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { dark }
                )
                onDispose { }
            }

            val baseScheme = if (dark) darkColorScheme() else lightColorScheme()
            val colorScheme = remember(dark, accent) {
                if (accent != null) {
                    val primary = Color(accent)
                    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
                    baseScheme.copy(
                        primary = primary,
                        onPrimary = onPrimary,
                        primaryContainer = primary.copy(alpha = if (dark) 0.25f else 0.15f),
                        onPrimaryContainer = if (dark) Color.White else Color.Black
                    )
                } else {
                    baseScheme
                }
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavigationController(vm = vm)
                }
            }
        }
    }
}
