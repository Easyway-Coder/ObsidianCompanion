package com.example.dwell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarTask(val id: String, val title: String, val done: Boolean = false)

/**
 * Plan tab: compact, evenly spaced month calendar at the top with the selected day's task list below.
 * Plug your vault in via [tasksFor] (e.g. parse that day's daily note) and [onToggle].
 */
@Composable
fun PlanScreen(
    tasksFor: (LocalDate) -> List<CalendarTask> = { emptyList() },
    onToggle: (LocalDate, CalendarTask) -> Unit = { _, _ -> },
    onAddTask: ((LocalDate) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Default selection = today; saved across rotation as epoch day
    var selectedEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var monthEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val month = YearMonth.from(LocalDate.ofEpochDay(monthEpoch))
    val tasks = tasksFor(selected)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp) // clears the sidebar button
        ) {
            // ---- Top section: calendar ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                CalendarHeader(
                    month = month,
                    onPrev = { monthEpoch = month.minusMonths(1).atDay(1).toEpochDay() },
                    onNext = { monthEpoch = month.plusMonths(1).atDay(1).toEpochDay() }
                )
                Spacer(Modifier.height(8.dp))
                MonthGrid(
                    month = month,
                    selected = selected,
                    onSelect = { day ->
                        selectedEpoch = day.toEpochDay()
                        monthEpoch = day.withDayOfMonth(1).toEpochDay() // tapping a faded day jumps to its month
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ---- Bottom section: selected day's tasks ----
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                TaskList(
                    date = selected,
                    tasks = tasks,
                    onToggle = { onToggle(selected, it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (onAddTask != null) {
            FloatingActionButton(
                onClick = { onAddTask(selected) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        }
    }
}

@Composable
private fun CalendarHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${month.year}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    // Sunday-first: java.time Sunday = 7, so % 7 gives 0 offset
    val offset = month.atDay(1).dayOfWeek.value % 7
    val start = month.atDay(1).minusDays(offset.toLong())
    val rowCount = (offset + month.lengthOfMonth() + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(rowCount) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(7) { col ->
                        val date = start.plusDays((row * 7 + col).toLong())
                        val inMonth = YearMonth.from(date) == month
                        val isSelected = date == selected

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                        date == today -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onBackground
                                    },
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected || date == today) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskList(
    date: LocalDate,
    tasks: List<CalendarTask>,
    onToggle: (CalendarTask) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${date.dayOfMonth} " +
                        date.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (tasks.isNotEmpty()) {
                Text(
                    text = "${tasks.size} ${if (tasks.size == 1) "task" else "tasks"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        }

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No tasks for this day",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggle(task) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = task.title,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                            fontSize = 16.sp,
                            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { onToggle(task) }
                        )
                    }
                }
            }
        }
    }
}