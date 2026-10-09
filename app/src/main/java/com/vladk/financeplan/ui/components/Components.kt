package com.vladk.financeplan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.Kind
import com.vladk.financeplan.data.Status
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.theme.badColor
import com.vladk.financeplan.ui.theme.goodColor
import com.vladk.financeplan.ui.theme.kindColor
import java.time.YearMonth

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null || action != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    action?.invoke()
                }
            }
            content()
        }
    }
}

@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.previousMonth)
        }
        Text(
            LocalFmt.current.month(month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(YearMonth.now()) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.nextMonth)
        }
    }
}

@Composable
fun KindDot(kind: Kind, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(kindColor(kind)),
    )
}

/** Progress of [actual] against [planned]; turns red when spending goes over plan. */
@Composable
fun PlanBar(planned: Double, actual: Double, color: Color, overIsBad: Boolean = true) {
    val fraction = if (planned <= 0.0) (if (actual > 0) 1f else 0f) else (actual / planned).toFloat().coerceIn(0f, 1f)
    val over = overIsBad && actual > planned + 0.005
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = if (over) badColor() else color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun StatusChip(status: Status) {
    val s = LocalStrings.current
    val color = when (status) {
        Status.WITHIN_PLAN, Status.GOAL_REACHED -> goodColor()
        Status.OVER_BUDGET -> badColor()
        Status.BELOW_GOAL -> MaterialTheme.colorScheme.tertiary
        Status.NO_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            s.status(status),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun ValueRow(label: String, value: String, strong: Boolean = false, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
        )
    }
}

/** A tappable list row: title, optional subtitle, and a value on the right. */
@Composable
fun ListRow(
    title: String,
    subtitle: String?,
    value: String,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    suffix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = trailing,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun TextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = badColor()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

@Composable
fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
