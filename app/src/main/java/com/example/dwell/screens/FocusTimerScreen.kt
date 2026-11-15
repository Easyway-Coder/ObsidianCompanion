package com.example.dwell.screens

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dwell.components.DeleteActionIcon
import com.example.dwell.components.DurationPickerDialog
import com.example.dwell.components.MarkdownBlock
import com.example.dwell.components.MarkdownText
import com.example.dwell.components.TimerActionIcon
import com.example.dwell.components.formatTime
import com.example.dwell.components.parseMarkdownBlocks
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ============================================================================
// ViewModel
//
// The session is stored as an absolute END TIMESTAMP (wall clock) in
// SharedPreferences, so the timer keeps "running" while the app is in the
// background, and is restored if the process is killed.
//
// startSession() / endSession() are public on purpose: the NFC tags can call
// them later (desk tag -> startSession(block = true), far-away tag ->
// endSession()) without touching any UI code.
// ============================================================================

class FocusTimerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)

    /** True in debug builds only; enables the emergency exit for strict sessions. */
    val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Chosen focus length in seconds (default 15:00). */
    var durationSeconds by mutableIntStateOf(
        prefs.getInt(KEY_DURATION, prefs.getInt(KEY_OLD_DURATION_MIN, 15) * 60)
    )
        private set
    var blockApps by mutableStateOf(prefs.getBoolean(KEY_BLOCK, false))
        private set

    /** Selected mode: false = countdown timer, true = infinite (strict stopwatch). */
    var infiniteMode by mutableStateOf(prefs.getBoolean(KEY_INFINITE_MODE, false))
        private set

    var isRunning by mutableStateOf(false)
        private set

    /** True while the running session is a strict stopwatch. */
    var sessionInfinite by mutableStateOf(false)
        private set
    var elapsedMs by mutableLongStateOf(0L)
        private set

    var remainingMs by mutableLongStateOf(durationSeconds * 1000L)
        private set
    var totalMs by mutableLongStateOf(durationSeconds * 1000L)
        private set

    /** Set when a countdown ran to zero; cleared on the next start. */
    var justFinished by mutableStateOf(false)
        private set

    private var tickJob: Job? = null

    init {
        // Restore a session that was running when the app/process went away
        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        val endAt = prefs.getLong(KEY_END_AT, 0L)
        val total = prefs.getLong(KEY_TOTAL, 0L)
        val now = System.currentTimeMillis()
        when {
            startedAt > 0L -> { // strict stopwatch: survives until the NFC tag ends it
                sessionInfinite = true
                isRunning = true
                elapsedMs = (now - startedAt).coerceAtLeast(0L)
                startStopwatch(startedAt)
            }
            endAt > now -> {
                totalMs = total
                remainingMs = endAt - now
                isRunning = true
                startTicker(endAt)
            }
            endAt > 0L -> { // finished while we were away
                clearSession()
                justFinished = true
            }
        }
    }

    fun setDuration(seconds: Int) {
        if (isRunning) return
        durationSeconds = seconds.coerceIn(1, MAX_SECONDS)
        remainingMs = durationSeconds * 1000L
        totalMs = remainingMs
        justFinished = false
        prefs.edit { putInt(KEY_DURATION, durationSeconds) }
    }

    fun updateBlockApps(enabled: Boolean) {
        if (isRunning) return
        blockApps = enabled
        prefs.edit { putBoolean(KEY_BLOCK, enabled) }
    }

    fun updateInfiniteMode(enabled: Boolean) {
        if (isRunning) return
        infiniteMode = enabled
        justFinished = false
        prefs.edit { putBoolean(KEY_INFINITE_MODE, enabled) }
    }

    // Public on purpose: the NFC desk tag calls startSession(block = true, infinite = true)
    fun startSession(
        seconds: Int = durationSeconds,
        block: Boolean = blockApps,
        infinite: Boolean = infiniteMode
    ) {
        if (isRunning) return
        justFinished = false
        if (infinite) {
            val startedAt = System.currentTimeMillis()
            prefs.edit { putLong(KEY_STARTED_AT, startedAt) }
            sessionInfinite = true
            elapsedMs = 0L
            isRunning = true
            applyBlocking(block)
            startStopwatch(startedAt)
        } else {
            val total = seconds.coerceIn(1, MAX_SECONDS) * 1000L
            val endAt = System.currentTimeMillis() + total
            prefs.edit {
                putLong(KEY_END_AT, endAt)
                putLong(KEY_TOTAL, total)
            }
            totalMs = total
            remainingMs = total
            isRunning = true
            applyBlocking(block)
            startTicker(endAt)
        }
    }

    // Strict: a stopwatch session ignores every end request except the one from the NFC tag
    // (the far-away tag calls endSession(byNfc = true)).
    fun endSession(byNfc: Boolean = false) {
        if (sessionInfinite && !byNfc) return
        tickJob?.cancel()
        clearSession()
        applyBlocking(false)
        isRunning = false
        sessionInfinite = false
        elapsedMs = 0L
        remainingMs = durationSeconds * 1000L
        totalMs = remainingMs
    }

    private fun startTicker(endAt: Long) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val left = endAt - System.currentTimeMillis()
                if (left <= 0L) {
                    finish()
                    return@launch
                }
                remainingMs = left
                delay(250)
            }
        }
    }

    private fun startStopwatch(startedAt: Long) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                delay(250)
            }
        }
    }

    private fun finish() {
        clearSession()
        applyBlocking(false)
        isRunning = false
        justFinished = true
        remainingMs = 0L
    }

    private fun clearSession() {
        prefs.edit {
            remove(KEY_END_AT)
            remove(KEY_TOTAL)
            remove(KEY_STARTED_AT)
        }
    }

    // ------------------------------------------------------------------
    // HOOK: distracting-app blocking. Intentionally empty for now.
    // ------------------------------------------------------------------
    @Suppress("UNUSED_PARAMETER")
    private fun applyBlocking(enabled: Boolean) {
        // TODO: start/stop app blocking
    }

    private companion object {
        const val MAX_SECONDS = 24 * 3600 - 1
        const val KEY_DURATION = "duration_sec"
        const val KEY_OLD_DURATION_MIN = "duration_min" // from the first version
        const val KEY_BLOCK = "block_apps"
        const val KEY_INFINITE_MODE = "infinite_mode"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_END_AT = "end_at"
        const val KEY_TOTAL = "total_ms"
    }
}

// ============================================================================
// Screen
// ============================================================================

private val QUOTES = listOf(
    "Start where you are. Use what you have. Do what you can.",
    "The secret of getting ahead is getting started.",
    "Be productive, not just busy.",
    "One focused hour beats a distracted day.",
    "Discipline is choosing what you want most over what you want now.",
    "Small steps, taken daily, move mountains.",
    "Make it work today. Make it better tomorrow.",
    "Where attention goes, progress follows."
)

@Composable
fun FocusTimerScreen(vm: FocusTimerViewModel, noteVm: NoteViewModel) {
    val running = vm.isRunning

    // Keep the screen awake while a session is running
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var goalsOpen by rememberSaveable { mutableStateOf(true) }
    var completedOpen by rememberSaveable { mutableStateOf(true) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingLine by rememberSaveable { mutableStateOf<Int?>(null) }

    // Tasks come straight from the note, so ticking/adding/deleting here syncs with Obsidian
    val tasks = remember(noteVm.text) {
        parseMarkdownBlocks(noteVm.text).filterIsInstance<MarkdownBlock.TaskItem>()
    }
    val goals = tasks.filter { !it.checked }
    val completed = tasks.filter { it.checked }
    val editing = tasks.firstOrNull { it.lineIndex == editingLine }

    if (showPicker) {
        DurationPickerDialog(
            initialSeconds = vm.durationSeconds,
            onDismiss = { showPicker = false },
            onConfirm = {
                vm.setDuration(it)
                showPicker = false
            }
        )
    }

    if (showAddDialog) {
        TaskDialog(
            title = "New task",
            initial = "",
            confirmText = "Add",
            onDismiss = { showAddDialog = false }
        ) { if (noteVm.addTask(it)) showAddDialog = false }
    }

    editing?.let { task ->
        TaskDialog(
            title = "Edit task",
            initial = task.text,
            confirmText = "Save",
            onDismiss = { editingLine = null }
        ) { if (noteVm.editTask(task.lineIndex, it)) editingLine = null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- Overflow menu (the sidebar toggle is overlaid at the left by MainActivity) ----
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Infinite mode") },
                        enabled = !running,
                        trailingIcon = {
                            Checkbox(checked = vm.infiniteMode, onCheckedChange = null, enabled = !running)
                        },
                        onClick = { vm.updateInfiniteMode(!vm.infiniteMode) }
                    )
                    DropdownMenuItem(
                        text = { Text("Block distracting apps") },
                        enabled = !running,
                        trailingIcon = {
                            Checkbox(checked = vm.blockApps, onCheckedChange = null, enabled = !running)
                        },
                        onClick = { vm.updateBlockApps(!vm.blockApps) }
                    )
                    // Emergency exit for testing strict mode before the NFC tags exist (debug builds only)
                    if (vm.debuggable && vm.sessionInfinite) {
                        DropdownMenuItem(
                            text = { Text("End session (debug)") },
                            onClick = {
                                menuOpen = false
                                vm.endSession(byNfc = true)
                            }
                        )
                    }
                }
            }
        }

        // ---- Ring + time (tap the time to set a custom duration) ----
        val progress = when {
            vm.infiniteMode || vm.sessionInfinite ->
                (vm.elapsedMs % 60_000L) / 60_000f   // one lap per minute
            vm.totalMs > 0 -> vm.remainingMs / vm.totalMs.toFloat()
            else -> 0f
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = !running && !vm.infiniteMode,
                    role = Role.Button,
                    onClickLabel = "Set focus time",
                    onClick = { showPicker = true }
                )
        ) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (vm.sessionInfinite || vm.infiniteMode) formatTime(vm.elapsedMs, countUp = true)
                    else formatTime(vm.remainingMs),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Light
                )
                val caption = when {
                    vm.sessionInfinite && vm.blockApps -> "Locked in · apps blocked"
                    vm.sessionInfinite -> "Locked in"
                    running && vm.blockApps -> "Focusing · apps blocked"
                    running -> "Focusing"
                    vm.infiniteMode -> "Infinite mode"
                    vm.justFinished -> "Session complete"
                    else -> ""
                }
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Quote: types itself out when the tab opens ----
        TypewriterQuote(
            active = running,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 8.dp)
        )

        // ---- Goals / Completed ----
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            item(key = "goals_header") {
                SectionHeader(
                    title = "Goals",
                    count = goals.size,
                    open = goalsOpen,
                    onClick = { goalsOpen = !goalsOpen },
                    action = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add task")
                        }
                    }
                )
            }
            if (goalsOpen) {
                if (goals.isEmpty()) {
                    item(key = "goals_empty") {
                        Text(
                            text = "Nothing to do yet. Tap + to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                } else {
                    items(goals, key = { "g_${it.lineIndex}_${it.text}" }) { task ->
                        FocusTaskRow(
                            task = task,
                            darkMode = noteVm.darkMode,
                            onToggle = { noteVm.toggleTask(task.lineIndex) },
                            onEdit = { editingLine = task.lineIndex },
                            onDelete = { noteVm.removeTask(task.lineIndex, task.text) }
                        )
                    }
                }
            }
            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    SectionHeader(
                        title = "Completed",
                        count = completed.size,
                        open = completedOpen,
                        onClick = { completedOpen = !completedOpen }
                    )
                }
                if (completedOpen) {
                    items(completed, key = { "c_${it.lineIndex}_${it.text}" }) { task ->
                        FocusTaskRow(
                            task = task,
                            darkMode = noteVm.darkMode,
                            onToggle = { noteVm.toggleTask(task.lineIndex) },
                            onEdit = { editingLine = task.lineIndex },
                            onDelete = { noteVm.removeTask(task.lineIndex, task.text) }
                        )
                    }
                }
            }
        }

        // ---- Play / Stop ----
        FilledIconButton(
            onClick = { if (running) vm.endSession() else vm.startSession() },
            enabled = !vm.sessionInfinite,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = when {
                    vm.sessionInfinite -> Icons.Default.Lock
                    running -> Icons.Default.Stop
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    vm.sessionInfinite -> "Locked until the end tag is scanned"
                    running -> "End session"
                    else -> "Start focus"
                },
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

// Characters appear one by one after a short delay. The not-yet-typed part is
// laid out but transparent, so the text never re-wraps or jumps while it types.
@Composable
private fun TypewriterQuote(active: Boolean, modifier: Modifier = Modifier) {
    var full by remember { mutableStateOf("") }
    var shown by remember { mutableIntStateOf(0) }

    LaunchedEffect(active) {
        if (!active) {
            shown = 0
            return@LaunchedEffect
        }
        full = "${QUOTES.random()} ✨"
        shown = 0
        delay(500)
        while (shown < full.length) {
            shown++
            delay(40)
        }
    }

    val text = buildAnnotatedString {
        append(full.take(shown))
        withStyle(SpanStyle(color = Color.Transparent)) { append(full.drop(shown)) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontStyle = FontStyle.Italic,
                lineHeight = 26.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    open: Boolean,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    val rotation by animateFloatAsState(if (open) 0f else -90f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = if (open) "Collapse $title" else "Expand $title",
            modifier = Modifier.rotate(rotation)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}

// Task row is an opaque foreground overlay; swiping it left slides the row to
// uncover the stationary action buttons sitting underneath on the right.
@Composable
private fun FocusTaskRow(
    task: MarkdownBlock.TaskItem,
    darkMode: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val buttonWidth = 46.dp
    val buttonHeight = 40.dp
    val totalActionsWidth = buttonWidth * 2
    val maxRevealPx = with(density) { totalActionsWidth.toPx() }

    var revealed by rememberSaveable(task.lineIndex, task.text) { mutableStateOf(false) }
    var dragOffsetPx by remember(task.lineIndex, task.text) {
        mutableFloatStateOf(if (revealed) -maxRevealPx else 0f)
    }

    LaunchedEffect(revealed, maxRevealPx) {
        dragOffsetPx = if (revealed) -maxRevealPx else 0f
    }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = dragOffsetPx,
        label = "taskSwipeOffset"
    )

    // White icon tint in dark theme, black icon tint in light theme
    val iconTint = if (darkMode) Color.White else Color.Black
    val timerBgColor = Color(0xFF3B82F6)
    val deleteBgColor = Color(0xFFD94343)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        // ---- Underneath layer: stationary action buttons at the right edge ----
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(totalActionsWidth)
                .height(buttonHeight)
                .clip(RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left action button (Timer / Clock icon - unassigned for now)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(timerBgColor)
                    .clickable(enabled = animatedOffsetPx < -1f) { /* unassigned */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TimerActionIcon,
                    contentDescription = "Timer action",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Right action button (Delete icon - deletes the task)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(deleteBgColor)
                    .clickable(enabled = animatedOffsetPx < -1f) {
                        revealed = false
                        dragOffsetPx = 0f
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DeleteActionIcon,
                    contentDescription = "Delete task",
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ---- Foreground overlay: opaque task row that slides left to reveal the icons underneath ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(maxRevealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            revealed = dragOffsetPx < -maxRevealPx * 0.35f
                            dragOffsetPx = if (revealed) -maxRevealPx else 0f
                        },
                        onDragCancel = {
                            dragOffsetPx = if (revealed) -maxRevealPx else 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(-maxRevealPx, 0f)
                    }
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.checked,
                onCheckedChange = {
                    if (revealed) {
                        revealed = false
                        dragOffsetPx = 0f
                    } else {
                        onToggle()
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        onClickLabel = "Edit task",
                        onClick = {
                            if (revealed) {
                                revealed = false
                                dragOffsetPx = 0f
                            } else {
                                onEdit()
                            }
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                MarkdownText(
                    text = task.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (task.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.checked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// One dialog for both adding and editing
@Composable
private fun TaskDialog(
    title: String,
    initial: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Task") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onConfirm(value) }),
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
