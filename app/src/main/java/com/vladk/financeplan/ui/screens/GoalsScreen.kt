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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.PlanBar
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.ValueRow
import com.vladk.financeplan.ui.theme.goodColor
import java.time.YearMonth

@Composable
fun GoalsScreen(
    data: AppData,
    month: YearMonth,
    onEditEmergency: () -> Unit,
    onEditGoal: (Goal?) -> Unit,
    onEditInvesting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val summary = Calc.summary(data, month)

    fun monthsText(remaining: Double, monthly: Double): String =
        when (val m = Calc.monthsToGoal(remaining, monthly)) {
            null -> s.notReachable
            0 -> s.reached
            else -> "$m ${s.months}"
        }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Emergency fund: N months of planned needs
        item {
            val e = data.emergency
            val target = Calc.emergencyTarget(data, summary)
            SectionCard(title = s.emergencyFund, action = { EditButton(onEditEmergency, s.editEmergency) }) {
                ValueRow(s.monthsToCover, e.months.toString())
                ValueRow(s.monthlyNeeds, fmt.money(summary.plannedByKind[com.vladk.financeplan.data.Kind.NEEDS] ?: 0.0))
                ValueRow(s.target, fmt.money(target), strong = true)
                ValueRow(s.alreadySaved, fmt.money(e.saved))
                Spacer(Modifier.height(6.dp))
                PlanBar(target, e.saved, goodColor(), overIsBad = false)
                Text(
                    fmt.pct(Calc.progress(e.saved, target) * 100.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ValueRow(s.monthlyContribution, fmt.money(e.monthly))
                ValueRow(s.monthsLeft, monthsText(target - e.saved, e.monthly))
            }
        }

        // 2. Savings goals
        item {
            SectionCard(title = s.savingsGoals) {
                if (data.goals.isEmpty()) EmptyText(s.noGoals)
                data.goals.forEach { g -> GoalRow(g, monthsText(g.target - g.saved, g.monthly)) { onEditGoal(g) } }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { onEditGoal(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(s.addGoal)
                }
            }
        }

        // 3. Investing projection
        item {
            val inv = data.investing
            val monthly = Calc.monthlyInvesting(data, summary)
            SectionCard(title = s.investing, action = { EditButton(onEditInvesting, s.editInvesting) }) {
                ValueRow(s.monthlyInvestment, fmt.money(monthly), strong = true)
                Text(s.monthlyInvestmentHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (inv.age > 0) ValueRow(s.ageToday, inv.age.toString())
                ValueRow(s.annualReturn, fmt.pct(inv.annualReturnPercent))
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TableRow(listOf(s.years, s.contributed, s.value, s.growth), header = true)
                Calc.projection(data, monthly).forEach { r ->
                    TableRow(
                        listOf(
                            if (r.age != null) "${r.years} (${r.age})" else r.years.toString(),
                            fmt.moneyWhole(r.contributed),
                            fmt.moneyWhole(r.value),
                            fmt.moneyWhole(r.growth),
                        ),
                    )
                }
                Text(
                    s.projectionNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GoalRow(goal: Goal, monthsLeft: String, onClick: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Row {
            Text(goal.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${fmt.money(goal.saved)} / ${fmt.money(goal.target)}", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        PlanBar(goal.target, goal.saved, goodColor(), overIsBad = false)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(
                "${fmt.money(goal.monthly)} ${s.perMonth}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("${s.monthsLeft}: $monthsLeft", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (goal.note.isNotBlank()) {
            Text(goal.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
