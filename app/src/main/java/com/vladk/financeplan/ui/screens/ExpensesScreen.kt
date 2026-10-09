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
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.KindDot
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.ValueRow
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
    val expenses = Calc.expensesIn(data, month)
        .filter { categoryFilter == null || it.categoryId == categoryFilter }
        .sortedWith(compareByDescending<Expense> { it.date })
    val byDay = expenses.groupBy { it.date }

    LazyColumn(modifier, contentPadding = FabSpace, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSwitcher(month, onMonthChange) }

        if (data.categories.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = categoryFilter == null,
                        onClick = { onFilterChange(null) },
                        label = { Text(s.allCategories) },
                    )
                }
                items(data.categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = categoryFilter == c.id,
                        onClick = { onFilterChange(if (categoryFilter == c.id) null else c.id) },
                        label = { Text(c.name) },
                        leadingIcon = { KindDot(c.kind, 8) },
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
                    Text(
                        fmt.day(LocalDate.parse(day)),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    list.forEach { e ->
                        val c = byId[e.categoryId]
                        ListRow(
                            title = c?.name ?: s.deletedCategory,
                            subtitle = e.note,
                            value = fmt.money(e.amount),
                            onClick = { onEdit(e) },
                            leading = c?.let { { KindDot(it.kind) } },
                        )
                    }
                }
            }
        }
    }
}
