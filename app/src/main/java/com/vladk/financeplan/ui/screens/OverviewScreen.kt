package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.CategoryLine
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Group
import com.vladk.financeplan.data.Reports
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.CategoryAvatar
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.Legend
import com.vladk.financeplan.ui.components.LegendItem
import com.vladk.financeplan.ui.components.LineChart
import com.vladk.financeplan.ui.components.LineSeries
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.PlanBar
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.StatusChip
import com.vladk.financeplan.ui.components.title
import com.vladk.financeplan.ui.theme.badColor
import com.vladk.financeplan.ui.theme.chartMuted
import com.vladk.financeplan.ui.theme.goodColor
import com.vladk.financeplan.ui.theme.groupColor
import com.vladk.financeplan.ui.theme.seriesColor
import java.time.LocalDate
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
    onEditExpense: (Expense) -> Unit,
    onGoToBudget: () -> Unit,
    onOpenWeekly: () -> Unit,
    onSeeAllExpenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val summary = Calc.summary(data, month)

    LazyColumn(modifier, contentPadding = FabSpace, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        if (data.income.salary == 0.0) {
            item {
                SectionCard(title = s.welcomeTitle) {
                    Text(s.welcomeText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onGoToBudget) { Text(s.goToBudget) }
                }
            }
        }

        // Hero: how much of the planned spending is used up.
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(s.spentThisMonth, style = MaterialTheme.typography.labelLarge)
                    Text(fmt.money(summary.actualSpending), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
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
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStat(
                    title = s.savedInvested,
                    value = fmt.money(summary.actualSaving),
                    sub = "${s.planned}: ${fmt.money(summary.plannedSaving)}",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                val free = Calc.unallocated(data, summary)
                SmallStat(
                    title = s.unallocated,
                    value = fmt.money(free),
                    sub = fmt.pct(Calc.percentOfSalary(data, free)),
                    valueColor = if (free < -0.005) badColor() else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // This week (Monday–Sunday) – opens the weekly report.
        item {
            val week = Reports.week(data, Reports.weekStart(LocalDate.now()))
            SectionCard(
                title = "${s.thisWeek} · ${fmt.weekRange(week.start)}",
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpenWeekly),
                action = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.openWeeklyReport) },
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt.money(week.spent), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    if (week.guide > 0) {
                        Text(
                            "/ ${fmt.money(week.guide)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                if (week.guide > 0) {
                    Spacer(Modifier.height(8.dp))
                    PlanBar(week.guide, week.spent, seriesColor(0))
                    Spacer(Modifier.height(4.dp))
                    val left = week.guide - week.spent
                    Row {
                        Text(
                            s.weeklyGuide,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (left >= 0) s.left.format(fmt.money(left)) else s.overBy.format(fmt.money(-left)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (left >= 0) goodColor() else badColor(),
                        )
                    }
                }
            }
        }

        // Spending through the month vs. last month and the plan.
        item { MonthLineCard(data, month) }

        // Budget by group (only categories with a plan or with spending).
        val active = summary.lines.filter { it.planned > 0 || it.actual > 0 }
        if (active.isEmpty() && data.income.salary > 0) {
            item { SectionCard { EmptyText(s.setAmountsHint) } }
        }
        Group.entries.forEach { g ->
            val lines = active.filter { it.category.group == g }
            if (lines.isNotEmpty()) item(key = g.name) {
                SectionCard(
                    title = "${g.emoji}  ${s.group(g)}",
                    action = {
                        Text(
                            "${fmt.moneyWhole(lines.sumOf { it.actual })} / ${fmt.moneyWhole(lines.sumOf { it.planned })}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    lines.forEach { line ->
                        CategoryProgress(line, onClick = { onOpenCategory(line.category.id) }, onAdd = { onAddExpense(line.category.id) })
                    }
                }
            }
        }

        // Recent expenses of the month.
        val recent = Calc.expensesIn(data, month).sortedByDescending { it.date }.take(5)
        if (recent.isNotEmpty()) item {
            val byId = data.categories.associateBy { it.id }
            SectionCard(title = s.recentExpenses, action = { TextButton(onClick = onSeeAllExpenses) { Text(s.seeAll) } }) {
                recent.forEach { e ->
                    val c = byId[e.categoryId]
                    ListRow(
                        title = c.title(),
                        subtitle = listOf(fmt.dayMonth(LocalDate.parse(e.date)), e.note).filter { it.isNotBlank() }.joinToString(" · "),
                        value = fmt.money(e.amount),
                        onClick = { onEditExpense(e) },
                        leading = { CategoryAvatar(c) },
                    )
                }
            }
        }
    }
}

/** Cumulative spending this month (blue) against last month (grey) and the plan (dashed). */
@Composable
internal fun MonthLineCard(data: AppData, month: YearMonth) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var selected by remember(month) { mutableStateOf<Int?>(null) }
    val current = Reports.cumulative(data, month)
    val previousMonth = month.minusMonths(1)
    val previous = Reports.cumulative(data, previousMonth)
    val planned = Calc.summary(data, month).plannedSpending
    val days = month.lengthOfMonth()
    val planLine = (1..days).map { planned * it / days }
    val thisColor = seriesColor(0)
    val lastColor = chartMuted()
    val planColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = s.spendingThroughMonth) {
        // Read-out for the crosshair (or for today / the last day).
        val day = selected ?: (current.indexOfLast { it != null }.takeIf { it >= 0 } ?: (days - 1))
        val date = month.atDay(day + 1)
        Text(
            "${s.upTo} ${fmt.dayMonth(date)}: ${current.getOrNull(day)?.let { fmt.money(it) } ?: "—"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            buildString {
                previous.getOrNull(day)?.let { append("${s.lastMonth}: ${fmt.money(it)}") }
                if (planned > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("${s.planned}: ${fmt.money(planLine[day])}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LineChart(
            series = buildList {
                if (planned > 0) add(LineSeries(s.planned, planLine, planColor, dashed = true))
                add(LineSeries(s.lastMonth, previous, lastColor))
                add(LineSeries(s.thisMonth, current, thisColor))
            },
            xLabel = { (it + 1).toString() },
            axisFormat = fmt::number,
            selected = selected,
            onSelect = { selected = it },
        )
        Spacer(Modifier.height(8.dp))
        Legend(
            buildList {
                add(LegendItem(s.thisMonth, thisColor, line = true))
                add(LegendItem(s.lastMonth, lastColor, line = true))
                if (planned > 0) add(LegendItem(s.planned, planColor, line = true, dashed = true))
            },
        )
    }
}

@Composable
internal fun SmallStat(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
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
        CategoryAvatar(line.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Name and amounts on top, bar in the middle, status and what is left underneath.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.category.title(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                    "${fmt.moneyWhole(line.actual)} / ${fmt.moneyWhole(line.planned)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(6.dp))
            PlanBar(line.planned, line.actual, groupColor(line.category.group), overIsBad = !kind.isSaving)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(line.status)
                Spacer(Modifier.weight(1f))
                val diff = line.planned - line.actual
                if (!kind.isSaving && line.actual > 0 && line.planned > 0) {
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
