package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.Kind
import com.vladk.financeplan.data.PlanType
import com.vladk.financeplan.data.Group
import com.vladk.financeplan.data.Presets
import com.vladk.financeplan.ui.components.CategoryAvatar
import com.vladk.financeplan.ui.components.Legend
import com.vladk.financeplan.ui.components.LegendItem
import com.vladk.financeplan.ui.components.Segment
import com.vladk.financeplan.ui.components.StackedBar
import com.vladk.financeplan.ui.components.title
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.KindDot
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.PlanBar
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.ValueRow
import com.vladk.financeplan.ui.theme.badColor
import com.vladk.financeplan.ui.theme.goodColor
import com.vladk.financeplan.ui.theme.kindColor
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun BudgetScreen(
    data: AppData,
    month: YearMonth,
    onEditIncome: () -> Unit,
    onEditCategory: (Category?) -> Unit,
    onAddPresets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val summary = Calc.summary(data, month)
    val income = data.income

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Income
        item {
            SectionCard(title = s.income, action = { EditButton(onEditIncome) }) {
                ValueRow(s.salary, fmt.money(income.salary), strong = true)
                if (income.vouchers > 0) ValueRow(s.vouchers, fmt.money(income.vouchers))
                income.extras.forEach { ValueRow(it.name, fmt.money(it.amount)) }
                if (income.salary == 0.0) {
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onEditIncome) { Text(s.enterIncome) }
                }
            }
        }

        // 2. How the salary is split (only meaningful once a salary is entered)
        if (income.salary > 0) item {
            val free = Calc.unallocated(data, summary)
            val plannedPct = Calc.percentOfSalary(data, summary.plannedTotal)
            SectionCard(title = s.planOfSalary) {
                ValueRow(s.plannedTotal, "${fmt.money(summary.plannedTotal)} · ${fmt.pct(plannedPct)}", strong = true)
                Spacer(Modifier.height(6.dp))
                PlanBar(income.salary, summary.plannedTotal, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                ValueRow(
                    s.unallocated,
                    "${fmt.money(free)} · ${fmt.pct(Calc.percentOfSalary(data, free))}",
                    valueColor = if (free < -0.005) badColor() else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when {
                        free < -0.005 -> s.planOver
                        abs(free) < 0.01 -> s.fullyAllocated
                        else -> "${s.planFits} · ${s.givePurpose}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (free < -0.005) badColor() else goodColor(),
                )
            }
        }

        // 3. 50/30/20: the planned split as one bar, then the comparison table
        if (income.salary > 0) item {
            SectionCard(title = s.ruleTitle) {
                StackedBar(
                    Kind.entries.map { k -> Segment(s.kind(k), summary.plannedByKind[k] ?: 0.0, kindColor(k)) } +
                        Segment(s.unallocated, Calc.unallocated(data, summary).coerceAtLeast(0.0), MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(8.dp))
                Legend(
                    Kind.entries.map { k -> LegendItem(s.kind(k), kindColor(k)) } +
                        LegendItem(s.unallocated, MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(Modifier.height(8.dp))
                TableRow(listOf("", s.ruleCol, s.planned, s.actual), header = true)
                Calc.rule503020(data, summary).forEach { r ->
                    TableRow(
                        listOf(s.kind(r.label), fmt.pct(r.rulePercent), fmt.pct(r.plannedPercent), fmt.pct(r.actualPercent)),
                    )
                }
            }
        }

        // 5. Extra incomes: 100% saved and invested
        if (income.extras.isNotEmpty()) item {
            SectionCard(title = s.extrasTitle) {
                ValueRow(s.extraIncomes, fmt.money(Calc.extrasTotal(income)), strong = true)
                ValueRow("  ${s.toInvest} (${fmt.pct(income.investSharePercent)})", fmt.money(Calc.extrasToInvest(income)))
                ValueRow("  ${s.toSave}", fmt.money(Calc.extrasToSave(income)))
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(s.investPerMonth, fmt.money(Calc.monthlyInvesting(data, summary)))
                ValueRow(s.savePerMonth, fmt.money(Calc.monthlySaving(data, summary)))
                ValueRow(s.savingsRate, fmt.pct(Calc.savingsRate(data, summary)), strong = true)
            }
        }

        // 5. Categories, one card per group. Tap a row to set its amount.
        item {
            Text(
                s.categories,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        if (data.categories.isEmpty()) item { EmptyText(s.noCategories) }
        Group.entries.forEach { g ->
            val lines = summary.lines.filter { it.category.group == g }
            if (lines.isNotEmpty()) item(key = "group-${g.name}") {
                SectionCard(
                    title = "${g.emoji}  ${s.group(g)}",
                    action = {
                        Text(fmt.money(lines.sumOf { it.planned }), style = MaterialTheme.typography.labelLarge)
                    },
                ) {
                    lines.forEach { line ->
                        val c = line.category
                        val planText = when {
                            line.planned == 0.0 && c.amount == 0.0 && c.percent == 0.0 -> s.notPlanned
                            c.planType == PlanType.FIXED -> s.planFixed
                            else -> "${fmt.pct(c.percent)} ${s.percentOfSalary.removePrefix("% ")}"
                        }
                        ListRow(
                            title = c.title(),
                            subtitle = "${s.kind(c.kind)} · $planText" + if (c.note.isNotBlank()) " · ${c.note}" else "",
                            value = if (line.planned > 0) fmt.money(line.planned) else "—",
                            onClick = { onEditCategory(c) },
                            leading = { CategoryAvatar(c) },
                            valueColor = if (line.planned > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column {
                FilledTonalButton(onClick = { onEditCategory(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.addCategory)
                }
                val missing = Presets.missing(data.categories).size
                if (missing > 0) {
                    TextButton(onClick = onAddPresets, modifier = Modifier.fillMaxWidth()) {
                        Text(s.addSuggested.format(missing))
                    }
                }
            }
        }
    }
}

@Composable
internal fun EditButton(onClick: () -> Unit, label: String = LocalStrings.current.editIncome) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Edit, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A simple table row: first column wide and left-aligned, the rest right-aligned. */
@Composable
internal fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                modifier = Modifier.weight(if (i == 0) 1.4f else 1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (i == 0 && !header) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}
