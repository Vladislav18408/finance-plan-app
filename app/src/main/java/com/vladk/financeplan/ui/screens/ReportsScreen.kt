package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Kind
import com.vladk.financeplan.data.Reports
import com.vladk.financeplan.data.emoji
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.BarChart
import com.vladk.financeplan.ui.components.BarRow
import com.vladk.financeplan.ui.components.CategoryAvatar
import com.vladk.financeplan.ui.components.DonutChart
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.Legend
import com.vladk.financeplan.ui.components.LegendItem
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.PlanBar
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.Segment
import com.vladk.financeplan.ui.components.Slice
import com.vladk.financeplan.ui.components.StackedBar
import com.vladk.financeplan.ui.components.ValueRow
import com.vladk.financeplan.ui.components.title
import com.vladk.financeplan.ui.theme.badColor
import com.vladk.financeplan.ui.theme.goodColor
import com.vladk.financeplan.ui.theme.groupColor
import com.vladk.financeplan.ui.theme.kindColor
import com.vladk.financeplan.ui.theme.seriesColor
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

enum class ReportTab { WEEK, MONTH, TRENDS }

private val ReportPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    data: AppData,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    tab: ReportTab,
    onTabChange: (ReportTab) -> Unit,
    weekStart: LocalDate,
    onWeekChange: (LocalDate) -> Unit,
    onEditExpense: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            ReportTab.entries.forEach { t ->
                Tab(
                    selected = t == tab,
                    onClick = { onTabChange(t) },
                    text = {
                        Text(
                            when (t) {
                                ReportTab.WEEK -> s.reportWeek
                                ReportTab.MONTH -> s.reportMonth
                                ReportTab.TRENDS -> s.reportTrends
                            },
                        )
                    },
                )
            }
        }
        when (tab) {
            ReportTab.WEEK -> WeeklyReport(data, weekStart, onWeekChange, onEditExpense)
            ReportTab.MONTH -> MonthlyReport(data, month, onMonthChange)
            ReportTab.TRENDS -> TrendsReport(data, month, onMonthChange)
        }
    }
}

// ------------------------------------------------------------------ Week (Monday – Sunday)

@Composable
private fun WeekSwitcher(start: LocalDate, onChange: (LocalDate) -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        IconButton(onClick = { onChange(start.minusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = s.previousWeek)
        }
        Text(
            fmt.weekRange(start),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onChange(Reports.weekStart(LocalDate.now())) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        IconButton(onClick = { onChange(start.plusWeeks(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = s.nextWeek)
        }
    }
}

@Composable
private fun WeeklyReport(
    data: AppData,
    weekStart: LocalDate,
    onWeekChange: (LocalDate) -> Unit,
    onEditExpense: (Expense) -> Unit,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val r = Reports.week(data, weekStart)
    val today = LocalDate.now()
    val todayIndex = r.days.indexOf(today).takeIf { it >= 0 }
    var selectedDay by remember(weekStart) { mutableStateOf<Int?>(null) }
    val byId = data.categories.associateBy { it.id }

    LazyColumn(contentPadding = ReportPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { WeekSwitcher(weekStart, onWeekChange) }

        // Headline: spent this week, against the weekly guide and the week before.
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(s.spentThisWeek, style = MaterialTheme.typography.labelLarge)
                    Text(fmt.money(r.spent), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    if (r.guide > 0) {
                        Text("${s.weeklyGuide}: ${fmt.money(r.guide)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        PlanBar(r.guide, r.spent, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        val left = r.guide - r.spent
                        Text(
                            if (left >= 0) s.left.format(fmt.money(left)) else s.overBy.format(fmt.money(-left)),
                            color = if (left >= 0) MaterialTheme.colorScheme.onPrimaryContainer else badColor(),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    WeekComparison(r.spent, r.previousSpent)
                }
            }
        }

        item {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val elapsed = when {
                    today.isBefore(r.start) -> 1
                    today.isAfter(r.end) -> 7
                    else -> (todayIndex ?: 6) + 1
                }
                SmallStat(
                    s.dailyAverage, fmt.money(r.spent / elapsed),
                    if (r.guide > 0) "${s.guidePerDay}: ${fmt.money(r.guide / 7)}" else "",
                    Modifier.weight(1f).fillMaxHeight(),
                )
                SmallStat(s.savedThisWeek, fmt.money(r.saved), "", Modifier.weight(1f).fillMaxHeight())
            }
        }

        // Daily bars, Monday to Sunday.
        item {
            SectionCard(title = s.spent) {
                BarChart(
                    values = r.spentByDay,
                    labels = r.days.map(fmt::weekdayShort),
                    color = seriesColor(0),
                    format = fmt::money,
                    axisFormat = fmt::number,
                    selected = selectedDay,
                    onSelect = { selectedDay = it },
                    highlight = todayIndex,
                    reference = if (r.guide > 0) r.guide / 7 else null,
                    referenceLabel = s.guidePerDay,
                )
                Text(s.tapBarHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Where the money went.
        item {
            SectionCard(title = s.byCategory) {
                if (r.byCategory.isEmpty()) EmptyText(s.noSpendingWeek)
                val max = r.byCategory.maxOfOrNull { it.second } ?: 0.0
                r.byCategory.forEach { (c, v) ->
                    BarRow(
                        emoji = c?.emoji() ?: "❔",
                        title = c.title(),
                        value = fmt.money(v),
                        fraction = if (max > 0) (v / max).toFloat() else 0f,
                        color = groupColor(c?.group ?: com.vladk.financeplan.data.Group.OTHER),
                        trailingNote = if (r.spent > 0) fmt.pct(v / r.spent * 100) else null,
                    )
                }
            }
        }

        // Expenses of the selected day (or the whole week).
        item {
            val list = selectedDay?.let { d -> r.expenses.filter { it.date == r.days[d].toString() } } ?: r.expenses
            SectionCard(title = selectedDay?.let { fmt.day(r.days[it]) } ?: s.allDays) {
                if (list.isEmpty()) EmptyText(s.noSpendingWeek)
                list.forEach { e ->
                    val c = byId[e.categoryId]
                    ListRow(
                        title = c.title(),
                        subtitle = listOf(fmt.weekdayShort(LocalDate.parse(e.date)) + " " + fmt.dayMonth(LocalDate.parse(e.date)), e.note)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        value = fmt.money(e.amount),
                        onClick = { onEditExpense(e) },
                        leading = { CategoryAvatar(c) },
                    )
                }
            }
        }
    }
}

/** "▲ 12,00 € more than the week before" – icon + text, never colour alone. */
@Composable
private fun WeekComparison(spent: Double, previous: Double) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val diff = spent - previous
    val (icon, text, color) = when {
        abs(diff) < 0.005 -> Triple(Icons.AutoMirrored.Filled.TrendingFlat, s.sameAsLastWeek, MaterialTheme.colorScheme.onPrimaryContainer)
        diff > 0 -> Triple(Icons.AutoMirrored.Filled.TrendingUp, s.moreThanLastWeek.format(fmt.money(diff)), badColor())
        else -> Triple(Icons.AutoMirrored.Filled.TrendingDown, s.lessThanLastWeek.format(fmt.money(-diff)), goodColor())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ------------------------------------------------------------------ Month

@Composable
private fun MonthlyReport(data: AppData, month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val all = Reports.monthExpenses(data, month)
    val spend = Reports.spending(data, all)
    val spent = spend.sumOf { it.amount }
    val saved = Reports.saving(data, all).sumOf { it.amount }
    val groups = Reports.byGroup(data, spend)
    var selected by remember(month) { mutableStateOf<Int?>(null) }

    LazyColumn(contentPadding = ReportPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        // Spending by group: donut + rows that double as the legend.
        item {
            SectionCard(title = s.byGroup) {
                if (groups.isEmpty()) {
                    EmptyText(s.noSpendingMonth)
                } else {
                    val sel = selected?.let { groups.getOrNull(it) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(
                            slices = groups.map { (g, v) -> Slice(s.group(g), v, groupColor(g)) },
                            centerLabel = sel?.let { "${it.first.emoji} ${s.group(it.first)}" } ?: s.spent,
                            centerValue = fmt.money(sel?.second ?: spent),
                            selected = selected,
                            onSelect = { selected = it },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    groups.forEachIndexed { i, (g, v) ->
                        BarRow(
                            emoji = g.emoji,
                            title = s.group(g),
                            value = fmt.money(v),
                            fraction = (v / spent).toFloat(),
                            color = groupColor(g),
                            trailingNote = fmt.pct(v / spent * 100),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selected = if (selected == i) null else i },
                        )
                    }
                }
            }
        }

        // Cash flow: where the income went.
        item {
            val income = Reports.totalIncome(data)
            val left = income - spent - saved
            val spentColor = seriesColor(1)
            val savedColor = kindColor(Kind.SAVINGS)
            val leftColor = MaterialTheme.colorScheme.outlineVariant
            SectionCard(title = s.cashFlow) {
                ValueRow(s.income, fmt.money(income), strong = true)
                Spacer(Modifier.height(6.dp))
                StackedBar(
                    listOf(
                        Segment(s.spent, spent, spentColor),
                        Segment(s.saved, saved, savedColor),
                        Segment(s.remaining, left.coerceAtLeast(0.0), leftColor),
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Legend(listOf(LegendItem(s.spent, spentColor), LegendItem(s.saved, savedColor), LegendItem(s.remaining, leftColor)))
                Spacer(Modifier.height(4.dp))
                ValueRow(s.spent, fmt.money(spent))
                ValueRow(s.saved, fmt.money(saved))
                ValueRow(s.remaining, fmt.money(left), valueColor = if (left < 0) badColor() else MaterialTheme.colorScheme.onSurface)
            }
        }

        item { MonthLineCard(data, month) }

        // Biggest categories of the month.
        item {
            val cats = Reports.byCategory(data, spend).take(10)
            SectionCard(title = s.byCategory) {
                if (cats.isEmpty()) EmptyText(s.noSpendingMonth)
                val max = cats.maxOfOrNull { it.second } ?: 0.0
                cats.forEach { (c, v) ->
                    BarRow(
                        emoji = c?.emoji() ?: "❔",
                        title = c.title(),
                        value = fmt.money(v),
                        fraction = if (max > 0) (v / max).toFloat() else 0f,
                        color = groupColor(c?.group ?: com.vladk.financeplan.data.Group.OTHER),
                        trailingNote = if (spent > 0) fmt.pct(v / spent * 100) else null,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Trends (6 months)

@Composable
private fun TrendsReport(data: AppData, month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val totals = Reports.monthTotals(data, month, 6)
    // Averages start at the first month that has any data, so empty months before the app was used don't pull them down.
    val counted = totals.indexOfFirst { it.spent > 0 || it.saved > 0 }.let { if (it < 0) totals else totals.drop(it) }
    val planned = Calc.summary(data, month).plannedSpending
    var selSpent by remember(month) { mutableStateOf<Int?>(null) }
    var selSaved by remember(month) { mutableStateOf<Int?>(null) }

    LazyColumn(contentPadding = ReportPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        item {
            SectionCard(title = s.spendingByMonth) {
                BarChart(
                    values = totals.map { it.spent },
                    labels = totals.map { fmt.monthShort(it.month) },
                    color = seriesColor(0),
                    format = fmt::money,
                    axisFormat = fmt::number,
                    selected = selSpent,
                    onSelect = { selSpent = it },
                    highlight = totals.lastIndex,
                    reference = planned.takeIf { it > 0 },
                    referenceLabel = s.planned,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(s.averagePerMonth, fmt.money(counted.sumOf { it.spent } / counted.size), strong = true)
            }
        }

        item {
            SectionCard(title = s.savedByMonth) {
                BarChart(
                    values = totals.map { it.saved },
                    labels = totals.map { fmt.monthShort(it.month) },
                    color = kindColor(Kind.SAVINGS),
                    format = fmt::money,
                    axisFormat = fmt::number,
                    selected = selSaved,
                    onSelect = { selSaved = it },
                    highlight = totals.lastIndex,
                )
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ValueRow(s.averagePerMonth, fmt.money(counted.sumOf { it.saved } / counted.size), strong = true)
            }
        }

        // Average per month for each category over the same six months.
        item {
            val from = counted.first().month.atDay(1)
            val to = totals.last().month.atEndOfMonth()
            val spend = Reports.spending(data, Reports.between(data, from, to))
            val cats = Reports.byCategory(data, spend).take(10)
            SectionCard(title = "${s.byCategory} · ${s.averagePerMonth}") {
                if (cats.isEmpty()) EmptyText(s.noSpendingMonth)
                val max = cats.maxOfOrNull { it.second } ?: 0.0
                cats.forEach { (c, v) ->
                    BarRow(
                        emoji = c?.emoji() ?: "❔",
                        title = c.title(),
                        value = "${fmt.money(v / counted.size)} ${s.perMonth}",
                        fraction = if (max > 0) (v / max).toFloat() else 0f,
                        color = groupColor(c?.group ?: com.vladk.financeplan.data.Group.OTHER),
                    )
                }
            }
        }
    }
}
