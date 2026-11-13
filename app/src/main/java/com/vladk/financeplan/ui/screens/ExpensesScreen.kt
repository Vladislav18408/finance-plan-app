package com.vladk.financeplan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Reports
import com.vladk.financeplan.data.emoji
import com.vladk.financeplan.data.label
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.components.CategoryAvatar
import com.vladk.financeplan.ui.components.EmptyText
import com.vladk.financeplan.ui.components.ListRow
import com.vladk.financeplan.ui.components.MonthSwitcher
import com.vladk.financeplan.ui.components.SectionCard
import com.vladk.financeplan.ui.components.ValueRow
import com.vladk.financeplan.ui.components.title
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
    val focus = LocalFocusManager.current
    val byId = remember(data.categories) { data.categories.associateBy { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    // Searching looks through every month; otherwise the list is the chosen month (and category).
    val monthExpenses = remember(data, month) { Calc.expensesIn(data, month) }
    val expenses = remember(data, month, categoryFilter, query, s.code) {
        val source = if (searching) {
            val q = query.trim().lowercase()
            // "12,5" should find 12.50 whichever decimal sign was typed.
            val number = q.replace(',', '.')
            data.expenses.filter { e ->
                e.note.lowercase().contains(q) ||
                    byId[e.categoryId]?.label(s.code)?.lowercase()?.contains(q) == true ||
                    (number.firstOrNull()?.isDigit() == true && "%.2f".format(java.util.Locale.ROOT, e.amount).startsWith(number))
            }
        } else {
            monthExpenses.filter { categoryFilter == null || it.categoryId == categoryFilter }
        }
        Reports.newestFirst(source)
    }
    val byDay = remember(expenses) { expenses.groupBy { it.date } }
    // Filter chips: categories used this month first, then the selected one if it has nothing yet.
    val used = monthExpenses.map { it.categoryId }.distinct().mapNotNull { byId[it] }
    val chips = (used + listOfNotNull(categoryFilter?.let { byId[it] })).distinctBy { it.id }

    LazyColumn(modifier, contentPadding = FabSpace, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!searching) item(key = "month") { MonthSwitcher(month, onMonthChange) }

        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(s.searchHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = ""; focus.clearFocus() }) {
                            Icon(Icons.Filled.Close, contentDescription = s.clearSearch)
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!searching && chips.isNotEmpty()) item(key = "chips") {
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

        item(key = "total") {
            SectionCard {
                val total = expenses.sumOf { it.amount }
                val saved = Reports.saving(data, expenses).sumOf { it.amount }
                when {
                    searching -> ValueRow(s.searchResults.format(expenses.size), fmt.money(total), strong = true)
                    // A month with deposits: show what was spent and what was put aside separately,
                    // so "spent" here is the same number as on the Overview.
                    categoryFilter == null && saved > 0 -> {
                        ValueRow(s.spent, fmt.money(total - saved), strong = true)
                        ValueRow(s.savedInvested, fmt.money(saved))
                    }
                    categoryFilter == null -> ValueRow(s.spent, fmt.money(total), strong = true)
                    else -> ValueRow(s.monthTotal, fmt.money(total), strong = true)
                }
            }
        }

        if (expenses.isEmpty()) {
            item(key = "empty") { EmptyText(if (searching) s.noSearchResults else s.noExpenses) }
        }

        byDay.forEach { (day, list) ->
            item(key = day) {
                SectionCard {
                    Row {
                        Text(
                            // Search results come from any year, so they show the full date.
                            if (searching) fmt.shortDate(day) else fmt.day(day),
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
