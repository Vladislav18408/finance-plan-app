package com.vladk.financeplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.ui.components.CategoryDialog
import com.vladk.financeplan.ui.components.EmergencyDialog
import com.vladk.financeplan.ui.components.ExpenseDialog
import com.vladk.financeplan.ui.components.GoalDialog
import com.vladk.financeplan.ui.components.IncomeDialog
import com.vladk.financeplan.ui.components.InvestingDialog
import com.vladk.financeplan.ui.screens.BudgetScreen
import com.vladk.financeplan.ui.screens.ExpensesScreen
import com.vladk.financeplan.ui.screens.GoalsScreen
import com.vladk.financeplan.ui.screens.OverviewScreen
import com.vladk.financeplan.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class Tab { OVERVIEW, EXPENSES, BUDGET, GOALS }

/** Which add/edit dialog is open. Only one at a time. */
sealed interface Editor {
    data class ExpenseEdit(val expense: Expense?, val preselectedCategoryId: String? = null) : Editor
    data class CategoryEdit(val category: Category?) : Editor
    data object IncomeEdit : Editor
    data class GoalEdit(val goal: Goal?) : Editor
    data object EmergencyEdit : Editor
    data object InvestingEdit : Editor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceApp(vm: FinanceViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val strings = stringsFor(data.settings.language)
    val fmt = remember(strings, data.settings.currency) { Fmt(strings, data.settings.currency) }

    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Editor?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != Tab.OVERVIEW) { tab = Tab.OVERVIEW }

    CompositionLocalProvider(LocalStrings provides strings, LocalFmt provides fmt) {
        val s = strings
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (showSettings) s.settings else when (tab) {
                                Tab.OVERVIEW -> s.tabOverview
                                Tab.EXPENSES -> s.tabExpenses
                                Tab.BUDGET -> s.tabBudget
                                Tab.GOALS -> s.tabGoals
                            },
                        )
                    },
                    navigationIcon = {
                        if (showSettings) {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                            }
                        }
                    },
                    actions = {
                        if (!showSettings) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = s.settings)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            bottomBar = {
                if (!showSettings) {
                    NavigationBar {
                        NavItem(Tab.OVERVIEW, tab, Icons.Filled.Home, s.tabOverview) { tab = it }
                        NavItem(Tab.EXPENSES, tab, Icons.AutoMirrored.Filled.ReceiptLong, s.tabExpenses) { tab = it }
                        NavItem(Tab.BUDGET, tab, Icons.Filled.AccountBalanceWallet, s.tabBudget) { tab = it }
                        NavItem(Tab.GOALS, tab, Icons.Filled.Flag, s.tabGoals) { tab = it }
                    }
                }
            },
            floatingActionButton = {
                if (!showSettings && (tab == Tab.OVERVIEW || tab == Tab.EXPENSES)) {
                    ExtendedFloatingActionButton(
                        onClick = { editor = Editor.ExpenseEdit(null, categoryFilter.takeIf { tab == Tab.EXPENSES }) },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(s.addExpense) },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            val modifier = Modifier.padding(padding)
            if (showSettings) {
                SettingsScreen(data, vm, notify, modifier)
            } else when (tab) {
                Tab.OVERVIEW -> OverviewScreen(
                    data = data,
                    month = month,
                    onMonthChange = { monthText = it.toString() },
                    onOpenCategory = { id -> categoryFilter = id; tab = Tab.EXPENSES },
                    onAddExpense = { id -> editor = Editor.ExpenseEdit(null, id) },
                    onGoToBudget = { tab = Tab.BUDGET },
                    modifier = modifier,
                )
                Tab.EXPENSES -> ExpensesScreen(
                    data = data,
                    month = month,
                    onMonthChange = { monthText = it.toString() },
                    categoryFilter = categoryFilter,
                    onFilterChange = { categoryFilter = it },
                    onEdit = { editor = Editor.ExpenseEdit(it) },
                    modifier = modifier,
                )
                Tab.BUDGET -> BudgetScreen(
                    data = data,
                    month = month,
                    onEditIncome = { editor = Editor.IncomeEdit },
                    onEditCategory = { editor = Editor.CategoryEdit(it) },
                    modifier = modifier,
                )
                Tab.GOALS -> GoalsScreen(
                    data = data,
                    month = month,
                    onEditEmergency = { editor = Editor.EmergencyEdit },
                    onEditGoal = { editor = Editor.GoalEdit(it) },
                    onEditInvesting = { editor = Editor.InvestingEdit },
                    modifier = modifier,
                )
            }
        }

        val close = { editor = null }
        when (val e = editor) {
            is Editor.ExpenseEdit -> ExpenseDialog(
                initial = e.expense,
                categories = data.categories,
                defaultDate = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1),
                preselectedCategoryId = e.preselectedCategoryId,
                onSave = vm::saveExpense,
                onDelete = { e.expense?.let { vm.deleteExpense(it.id) } },
                onDismiss = close,
            )
            is Editor.CategoryEdit -> CategoryDialog(
                initial = e.category,
                income = data.income,
                onSave = vm::saveCategory,
                onDelete = {
                    e.category?.let {
                        vm.deleteCategory(it.id)
                        if (categoryFilter == it.id) categoryFilter = null
                    }
                },
                onDismiss = close,
            )
            Editor.IncomeEdit -> IncomeDialog(data.income, vm::saveIncome, close)
            is Editor.GoalEdit -> GoalDialog(
                initial = e.goal,
                onSave = vm::saveGoal,
                onDelete = { e.goal?.let { vm.deleteGoal(it.id) } },
                onDismiss = close,
            )
            Editor.EmergencyEdit -> EmergencyDialog(data.emergency, vm::saveEmergency, close)
            Editor.InvestingEdit -> InvestingDialog(data.investing, vm::saveInvesting, close)
            null -> Unit
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    item: Tab,
    current: Tab,
    icon: ImageVector,
    label: String,
    onSelect: (Tab) -> Unit,
) {
    NavigationBarItem(
        selected = item == current,
        onClick = { onSelect(item) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
