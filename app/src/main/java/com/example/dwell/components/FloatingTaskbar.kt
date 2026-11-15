package com.example.dwell.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dwell.navigation.AppTab

@Composable
fun FloatingTaskbar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                TaskbarItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TaskbarItem(
    tab: AppTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val yOffset by animateDpAsState(
        targetValue = if (selected) (-5).dp else 0.dp,
        label = "tabElevationOffset"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val itemColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .height(56.dp)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .offset(y = yOffset),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            // Render an identical copy of the selected item (icon + label) behind it as the shadow
            TaskbarItemContent(
                tab = tab,
                showLabel = true,
                color = Color.Black.copy(alpha = 0.6f),
                contentDescription = null,
                modifier = Modifier
                    .offset(y = 5.dp)
                    .blur(radius = 4.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
        }

        TaskbarItemContent(
            tab = tab,
            showLabel = selected,
            color = itemColor,
            contentDescription = tab.label
        )
    }
}

@Composable
private fun TaskbarItemContent(
    tab: AppTab,
    showLabel: Boolean,
    color: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.label,
                color = color,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
        }
    }
}
