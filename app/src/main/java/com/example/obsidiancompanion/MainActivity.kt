package com.example.obsidiancompanion

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
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
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ---------- Data ----------

// lineIndex = which line of the .md file this task lives on
data class Task(
    val title: String,
    val done: Boolean,
    val lineIndex: Int,
)

// ---------- State + file logic ----------

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver = app.contentResolver
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Matches lines like "- [ ] Buy milk" or "  * [x] Done thing"
    // group 1 = the mark (" ", "x" or "X"), group 2 = the task text
    private val taskRegex = Regex("""^\s*[-*+]\s+\[([ xX])]\s+(.*)""")

    private var noteUri: Uri? = prefs.getString("note_uri", null)?.toUri()
    private val ioLock = Mutex() // one file operation at a time

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    private val _status = MutableStateFlow(
        noteUri?.let { nameOf(it) } ?: "No note selected"
    )
    val status: StateFlow<String> = _status

    // Called when the user picks a file in the system picker
    fun onNotePicked(uri: Uri) {
        try {
            // Keep access to this file after the app is closed
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            _status.value = "Couldn't keep access to that file: ${e.message}"
            return
        }
        prefs.edit { putString("note_uri", uri.toString()) }
        noteUri = uri
        refresh()
    }

    // Re-read the file (also called every time the app comes to the foreground)
    fun refresh() {
        val uri = noteUri ?: return
        viewModelScope.launch {
            ioLock.withLock {
                try {
                    publish(readLines(uri))
                    _status.value = nameOf(uri)
                } catch (e: Exception) {
                    _status.value = "Couldn't read note: ${e.message}"
                }
            }
        }
    }

    fun toggle(task: Task) {
        val uri = noteUri ?: return
        viewModelScope.launch {
            ioLock.withLock {
                try {
                    // 1. Read the file fresh, in case Obsidian changed it meanwhile
                    val current = readLines(uri)
                    val line = current.getOrNull(task.lineIndex)
                    val match = line?.let { taskRegex.find(it) }

                    // 2. If that line is no longer the task we expect, refresh instead of writing
                    if (line == null || match == null || (match.groupValues[2].trim() != task.title)) {
                        publish(current)
                        _status.value = "Note changed, refreshed"
                        return@withLock
                    }

                    // 3. Change only the one character inside [ ]
                    val newMark = if (match.groupValues[1] == " ") "x" else " "
                    val updated = current.toMutableList()
                    updated[task.lineIndex] = line.replaceRange(match.groups[1]!!.range, newMark)

                    // 4. Write the whole file back, then update the screen
                    writeLines(uri, updated)
                    publish(updated)
                } catch (e: Exception) {
                    _status.value = "Couldn't save: ${e.message}"
                }
            }
        }
    }

    // ----- helpers -----

    private fun publish(lines: List<String>) {
        _tasks.value = lines.mapIndexedNotNull { index, line ->
            taskRegex.find(line)?.let { m ->
                Task(
                    title = m.groupValues[2].trim(),
                    done = m.groupValues[1].isNotBlank(),
                    lineIndex = index,
                )
            }
        }
    }

    private suspend fun readLines(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw java.io.IOException("Cannot open file for reading")
        text.split("\n")
    }

    private suspend fun writeLines(
        uri: Uri,
        lines: List<String>
    ) = withContext(Dispatchers.IO) {

        val stream = resolver.openOutputStream(uri, "wt")
            ?: throw java.io.IOException("Cannot open file for writing")

        stream.bufferedWriter(Charsets.UTF_8).use {
            it.write(lines.joinToString("\n"))
        }
    }

    private fun nameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast('/') ?: "Note"
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
    val status by vm.status.collectAsState()

    // System file picker; "*/*" because some devices label .md files oddly
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { vm.onNotePicked(it) }
    }

    // Re-read the note whenever the app comes to the foreground
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refresh()
    }

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

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { picker.launch(arrayOf("*/*")) }) {
            Text("Choose note")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Text("No tasks found. Add lines like \"- [ ] Something\" to your note.")
        }

        tasks.forEach { task ->
            TaskRow(
                title = task.title,
                checked = task.done,
                onCheckedChange = { vm.toggle(task) },
            )
        }
    }
}

@Composable
fun TaskRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
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