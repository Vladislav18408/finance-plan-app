package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.emoji
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.CategoryAvatar
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.ValueRow
import com.vladk.financeplan.ui.components.title
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExpensesScreen(
    data: AppData,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    categoryFilter: String?,
    onFilterChange: (String?) -> Unit,
    onEdit: (Expense) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    val byId = data.categories.associateBy { it.id }
    val monthExpenses = Calc.expensesIn(data, month)
    val expenses = monthExpenses
        .filter { categoryFilter == null || it.categoryId == categoryFilter }
        .sortedWith(compareByDescending<Expense> { it.date })
    val byDay = expenses.groupBy { it.date }
    // Filter chips: categories used this month first, then the selected one if it has nothing yet.
    val used = monthExpenses.map { it.categoryId }.distinct().mapNotNull { byId[it] }
    val chips = (used + listOfNotNull(categoryFilter?.let { byId[it] })).distinctBy { it.id }

    LazyColumn(modifier, contentPadding = FabSpace, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        if (chips.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { onFilterChange(null) },
                        label = { Text(s.allCategories) },
                    )
                }
                items(chips, key = { it.id }) { c ->
                    FilterChip(
                        selected = categoryFilter == c.id,
                        onClick = { onFilterChange(if (categoryFilter == c.id) null else c.id) },
                        label = { Text("${c.emoji()}  ${c.title()}") },
                    )
                }
            }
        }

        item {
            SectionCard {
                ValueRow(s.monthTotal, fmt.money(expenses.sumOf { it.amount }), strong = true)
            }
        }

        if (expenses.isEmpty()) {
            item { EmptyText(s.noExpenses) }
        }

        byDay.forEach { (day, list) ->
            item(key = day) {
                SectionCard {
                    androidx.compose.foundation.layout.Row {
                        Text(
                            fmt.day(LocalDate.parse(day)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                        )
                        Text(
                            fmt.money(list.sumOf { it.amount }),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    list.forEach { e ->
                        val c = byId[e.categoryId]
                        ListRow(
                            title = c.title(),
                            subtitle = e.note,
                            value = fmt.money(e.amount),
                            onClick = { onEdit(e) },
                            leading = { CategoryAvatar(c) },
                        )
                    }
                }
            }
        }
    }
}
