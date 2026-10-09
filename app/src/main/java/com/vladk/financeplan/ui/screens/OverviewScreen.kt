package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.CategoryLine
import com.vladk.financeplan.data.Kind
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.KindDot
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.PlanBar
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.StatusChip
import com.vladk.financeplan.ui.theme.badColor
import com.vladk.financeplan.ui.theme.goodColor
import com.vladk.financeplan.ui.theme.kindColor
import java.time.YearMonth

/** Bottom padding so the floating "Add expense" button never covers the last row. */
internal val FabSpace = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)

@Composable
fun OverviewScreen(
    data: AppData,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpenCategory: (String) -> Unit,
    onAddExpense: (String) -> Unit,
    onGoToBudget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val summary = Calc.summary(data, month)

    LazyColumn(modifier, contentPadding = FabSpace, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        if (data.categories.isEmpty()) {
            item {
                SectionCard(title = s.welcomeTitle) {
                    Text(s.welcomeText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToBudget) { Text(s.goToBudget) }
                }
            }
            return@LazyColumn
        }

        // Main card: how much of the planned spending is used up.
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(s.spentThisMonth, style = MaterialTheme.typography.labelLarge)
                    Text(
                        fmt.money(summary.actualSpending),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s.ofPlanned.format(fmt.money(summary.plannedSpending)), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    PlanBar(summary.plannedSpending, summary.actualSpending, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    val diff = summary.plannedSpending - summary.actualSpending
                    Text(
                        if (diff >= 0) s.left.format(fmt.money(diff)) else s.overBy.format(fmt.money(-diff)),
                        color = if (diff >= 0) MaterialTheme.colorScheme.onPrimaryContainer else badColor(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStat(
                    title = s.savedInvested,
                    value = fmt.money(summary.actualSaving),
                    sub = "${s.planned}: ${fmt.money(summary.plannedSaving)}",
                    modifier = Modifier.weight(1f),
                )
                val free = Calc.unallocated(data, summary)
                SmallStat(
                    title = s.unallocated,
                    value = fmt.money(free),
                    sub = fmt.pct(Calc.percentOfSalary(data, free)),
                    valueColor = if (free < -0.005) badColor() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Kind.entries.forEach { kind ->
            val lines = summary.lines.filter { it.category.kind == kind }
            if (lines.isNotEmpty()) item(key = kind.name) {
                SectionCard(
                    title = s.kind(kind),
                    action = {
                        Text(
                            "${fmt.moneyWhole(summary.actualByKind[kind] ?: 0.0)} / ${fmt.moneyWhole(summary.plannedByKind[kind] ?: 0.0)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = kindColor(kind),
                        )
                    },
                ) {
                    lines.forEach { line ->
                        CategoryProgress(line, onClick = { onOpenCategory(line.category.id) }, onAdd = { onAddExpense(line.category.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallStat(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryProgress(line: CategoryLine, onClick: () -> Unit, onAdd: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val kind = line.category.kind
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindDot(kind)
                Spacer(Modifier.width(8.dp))
                Text(
                    line.category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(line.status)
            }
            Spacer(Modifier.height(6.dp))
            PlanBar(line.planned, line.actual, kindColor(kind), overIsBad = !kind.isSaving)
            Spacer(Modifier.height(4.dp))
            Row {
                Text(
                    "${fmt.money(line.actual)} / ${fmt.money(line.planned)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val diff = line.planned - line.actual
                if (!kind.isSaving && line.actual > 0) {
                    Text(
                        if (diff >= 0) s.left.format(fmt.money(diff)) else s.overBy.format(fmt.money(-diff)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (diff >= 0) goodColor() else badColor(),
                    )
                }
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.AddCircleOutline, contentDescription = s.addExpense, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
