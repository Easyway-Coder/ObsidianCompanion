package com.example.obsidiancompanion
// Keep your existing `package ...` line as the very first line of the file.

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// ---------- Data ----------

data class Task(
    val title: String,
    val done: Boolean = false
)

// ---------- State holder ----------
// Later, your .md file reading/writing logic will live here.

class TaskViewModel : ViewModel() {

    private val _tasks = MutableStateFlow(
        listOf(
            Task("Task 1"),
            Task("Task 2"),
            Task("Task 3")
        )
    )
    val tasks: StateFlow<List<Task>> = _tasks

    fun toggle(index: Int) {
        _tasks.update { list ->
            list.mapIndexed { i, task ->
                if (i == index) task.copy(done = !task.done) else task
            }
        }
    }
}

// ---------- UI ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TaskChecklist()
                }
            }
        }
    }
}

@Composable
fun TaskChecklist(vm: TaskViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Obsidian Companion",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        tasks.forEachIndexed { index, task ->
            TaskRow(
                title = task.title,
                checked = task.done,
                onCheckedChange = { vm.toggle(index) }
            )
        }
    }
}

@Composable
fun TaskRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The whole row handles the tap, so the checkbox itself is display-only.
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )

        Text(
            text = title,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}