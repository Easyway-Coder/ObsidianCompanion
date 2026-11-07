package com.example.obsidiancompanion

import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

    private val _darkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val darkMode: StateFlow<Boolean> = _darkMode

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean("dark_mode", enabled) }
        _darkMode.value = enabled
    }

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
                    val match = matchFor(current, task)

                    // 2. If that line is no longer the task we expect, refresh instead of writing
                    if (match == null) {
                        publish(current)
                        _status.value = "Note changed, refreshed"
                        return@withLock
                    }

                    // 3. Change only the one character inside [ ]
                    val newMark = if (match.groupValues[1] == " ") "x" else " "
                    val updated = current.toMutableList()
                    updated[task.lineIndex] =
                        current[task.lineIndex].replaceRange(match.groups[1]!!.range, newMark)

                    // 4. Write the whole file back, then update the screen
                    writeLines(uri, updated)
                    publish(updated)
                } catch (e: Exception) {
                    _status.value = "Couldn't save: ${e.message}"
                }
            }
        }
    }

    // Adds "- [ ] <title>" at the end of the note.
    // Returns false if nothing was started (empty text, or no note chosen yet).
    fun addTask(title: String): Boolean {
        val clean = title.trim().replace("\n", " ")
        if (clean.isEmpty()) return false
        val uri = noteUri
        if (uri == null) {
            _status.value = "Choose a note first"
            return false
        }
        viewModelScope.launch {
            ioLock.withLock {
                try {
                    val current = readLines(uri)

                    // Keep the file's trailing newline (if it has one) after the new line
                    val endsWithNewline = current.lastOrNull() == ""
                    val body = if (endsWithNewline) current.dropLast(1) else current
                    val updated = buildList {
                        addAll(body)
                        add("- [ ] $clean")
                        if (endsWithNewline) add("")
                    }

                    writeLines(uri, updated)
                    publish(updated)
                } catch (e: Exception) {
                    _status.value = "Couldn't save: ${e.message}"
                }
            }
        }
        return true
    }

    // Deletes the task's line from the note (only that one line)
    fun remove(task: Task) {
        val uri = noteUri ?: return
        viewModelScope.launch {
            ioLock.withLock {
                try {
                    val current = readLines(uri)

                    // Same safety check as toggle: only delete if the line is still what we expect
                    if (matchFor(current, task) == null) {
                        publish(current)
                        _status.value = "Note changed, refreshed"
                        return@withLock
                    }

                    val updated = current.toMutableList()
                    updated.removeAt(task.lineIndex)

                    writeLines(uri, updated)
                    publish(updated)
                } catch (e: Exception) {
                    _status.value = "Couldn't save: ${e.message}"
                }
            }
        }
    }

    // ----- helpers -----

    // Returns the regex match only if the line at task.lineIndex is still this exact task
    private fun matchFor(lines: List<String>, task: Task): MatchResult? =
        lines.getOrNull(task.lineIndex)
            ?.let { taskRegex.find(it) }
            ?.takeIf { it.groupValues[2].trim() == task.title }

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
            val vm: TaskViewModel = viewModel()
            val darkMode by vm.darkMode.collectAsState()

            MaterialTheme(
                colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TaskChecklist(vm = vm)
                }
            }
        }
    }
}

@Composable
fun TaskChecklist(vm: TaskViewModel = viewModel()) {
    val tasks by vm.tasks.collectAsState()
    val status by vm.status.collectAsState()
    val darkMode by vm.darkMode.collectAsState()

    var newTitle by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }

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

    // Confirmation before deleting, since this edits your real note
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete task?") },
            text = { Text("\"${task.title}\" will be removed from your note.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(task)
                    pendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Obsidian Companion",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dark",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Switch(
                    checked = darkMode,
                    onCheckedChange = { vm.setDarkMode(it) }
                )
            }
        }

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

        // Add-task row
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("New task") }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { if (vm.addTask(newTitle)) newTitle = "" },
                enabled = newTitle.isNotBlank()
            ) {
                Text("Add")
            }
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
                onDelete = { pendingDelete = task },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance ->
            distance * 0.6f
        },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                false
            } else {
                true
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,

        // The background revealed behind the task
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF53711))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete task",
                    tint = Color(0xFFFFFFFF)
                )
            }
        },

        // The task itself
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
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
        }
    )
}