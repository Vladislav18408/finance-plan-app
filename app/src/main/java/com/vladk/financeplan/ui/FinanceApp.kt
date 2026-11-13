package com.vladk.financeplan.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.data.Recurring
import com.vladk.financeplan.data.label
import com.vladk.financeplan.ui.components.ConfirmDialog
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
import com.vladk.financeplan.ui.screens.ReportTab
import com.vladk.financeplan.ui.screens.ReportsScreen
import com.vladk.financeplan.data.Reports
import androidx.compose.material.icons.filled.BarChart
import com.vladk.financeplan.ui.screens.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class Tab { OVERVIEW, EXPENSES, REPORTS, BUDGET, GOALS }

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
fun FinanceApp(vm: FinanceViewModel, addExpenseRequests: Int = 0) {
    val data by vm.data.collectAsStateWithLifecycle()
    val strings = stringsFor(data.settings.language)
    val fmt = remember(strings, data.settings.currency) { Fmt(strings, data.settings.currency) }

    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var reportTab by rememberSaveable { mutableStateOf(ReportTab.WEEK) }
    var weekText by rememberSaveable { mutableStateOf(Reports.weekStart(LocalDate.now()).toString()) }
    val weekStart = LocalDate.parse(weekText)
    var editor by remember { mutableStateOf<Editor?>(null) }
    var stopRecurring by remember { mutableStateOf<Recurring?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    // Tell the user when the data could not be written (e.g. storage full) instead of losing it silently.
    val saveFailed by vm.saveFailed.collectAsStateWithLifecycle()
    LaunchedEffect(saveFailed) {
        if (saveFailed) snackbar.showSnackbar(strings.saveFailed, withDismissAction = true, duration = SnackbarDuration.Long)
    }

    // Opened from the reminder notification or the launcher shortcut: go straight to the new-expense form.
    LaunchedEffect(addExpenseRequests) {
        if (addExpenseRequests > 0) {
            showSettings = false
            tab = Tab.OVERVIEW
            monthText = YearMonth.now().toString()
            editor = Editor.ExpenseEdit(null)
        }
    }

    // The app often stays in memory for days. When it comes back on a new day, the month and week
    // on screen follow the calendar (unless the user was looking at an older one on purpose) …
    var today by rememberSaveable(stateSaver = Saver({ it.toString() }, { LocalDate.parse(it) })) { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        val now = LocalDate.now()
        if (now != today) {
            // Compared as text: these are the saved states, read fresh each time the app resumes.
            if (monthText == YearMonth.from(today).toString()) monthText = YearMonth.from(now).toString()
            if (weekText == Reports.weekStart(today).toString()) weekText = Reports.weekStart(now).toString()
            today = now
        }
        onPauseOrDispose { }
    }
    // … and regular payments whose date has come are added.
    LaunchedEffect(today) {
        val added = vm.addDueRegular(today)
        if (added > 0) snackbar.showSnackbar(strings.regularAdded.format(added))
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != Tab.OVERVIEW) { tab = Tab.OVERVIEW }

    CompositionLocalProvider(LocalStrings provides strings, LocalFmt provides fmt) {
        val s = strings
        // The add/edit forms are a layer on top of the screen, so both sit in one full-size Box.
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                if (showSettings) s.settings else when (tab) {
                                    Tab.OVERVIEW -> s.tabOverview
                                    Tab.EXPENSES -> s.tabExpenses
                                    Tab.REPORTS -> s.tabReports
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
                            NavItem(Tab.REPORTS, tab, Icons.Filled.BarChart, s.tabReports) { tab = it }
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
                        onEditExpense = { editor = Editor.ExpenseEdit(it) },
                        onGoToBudget = { tab = Tab.BUDGET },
                        onOpenWeekly = {
                            reportTab = ReportTab.WEEK
                            weekText = Reports.weekStart(LocalDate.now()).toString()
                            tab = Tab.REPORTS
                        },
                        onSeeAllExpenses = { categoryFilter = null; tab = Tab.EXPENSES },
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
                    Tab.REPORTS -> ReportsScreen(
                        data = data,
                        month = month,
                        onMonthChange = { monthText = it.toString() },
                        tab = reportTab,
                        onTabChange = { reportTab = it },
                        weekStart = weekStart,
                        onWeekChange = { weekText = it.toString() },
                        onEditExpense = { editor = Editor.ExpenseEdit(it) },
                        modifier = modifier,
                    )
                    Tab.BUDGET -> BudgetScreen(
                        data = data,
                        month = month,
                        onEditIncome = { editor = Editor.IncomeEdit },
                        onEditCategory = { editor = Editor.CategoryEdit(it) },
                        onAddPresets = vm::addMissingPresets,
                        onStopRecurring = { stopRecurring = it },
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

            stopRecurring?.let { r ->
                ConfirmDialog(
                    title = s.stopRepeating,
                    text = listOf(
                        data.categories.firstOrNull { it.id == r.categoryId }?.label(s.code).orEmpty(),
                        fmt.money(r.amount),
                        s.regularEvery.format(r.day),
                    ).filter { it.isNotBlank() }.joinToString(" · ") + "\n\n" + s.stopRepeatingText,
                    confirmLabel = s.stopRepeating,
                    onConfirm = { vm.deleteRecurring(r.id) },
                    onDismiss = { stopRecurring = null },
                )
            }

            val close = { editor = null }
            when (val e = editor) {
                is Editor.ExpenseEdit -> ExpenseDialog(
                    initial = e.expense,
                    categories = data.categories,
                    recentCategoryIds = remember(data.expenses) {
                        Reports.newestFirst(data.expenses).map { it.categoryId }.distinct()
                    },
                    defaultDate = if (month == YearMonth.now()) LocalDate.now() else month.atDay(1),
                    preselectedCategoryId = e.preselectedCategoryId,
                    noteSuggestions = { categoryId -> noteSuggestions(data.expenses, categoryId) },
                    onSave = vm::saveExpense,
                    onDelete = {
                        e.expense?.let { deleted ->
                            vm.deleteExpense(deleted.id)
                            // A slip of the finger is undone with one tap instead of a confirmation every time.
                            scope.launch {
                                snackbar.currentSnackbarData?.dismiss()
                                val result = snackbar.showSnackbar(s.expenseDeleted, actionLabel = s.undo, duration = SnackbarDuration.Long)
                                if (result == SnackbarResult.ActionPerformed) vm.saveExpense(deleted)
                            }
                        }
                    },
                    onDismiss = close,
                )
                is Editor.CategoryEdit -> CategoryDialog(
                    initial = e.category,
                    income = data.income,
                    expenseCount = e.category?.let { c -> data.expenses.count { it.categoryId == c.id } } ?: 0,
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
}

/** The notes used most often for a category ("Lidl", "кафе" …), newest habits first – offered as one-tap chips. */
internal fun noteSuggestions(expenses: List<Expense>, categoryId: String, limit: Int = 4): List<String> =
    expenses.asReversed().asSequence()
        .filter { it.categoryId == categoryId && it.note.isNotBlank() }
        .take(200)
        .groupingBy { it.note.trim() }
        .eachCount()
        .entries.sortedByDescending { it.value }
        .take(limit)
        .map { it.key }

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
        label = {
            // Five tabs share the width, so with a large system font a label would wrap mid-word
            // ("Expense / s"). The label grows with the font setting only up to +15% and stays on one line.
            val fontScale = LocalDensity.current.fontScale
            val size = MaterialTheme.typography.labelMedium.fontSize
            Text(
                label,
                maxLines = 1,
                softWrap = false,
                fontSize = if (fontScale > 1.15f) size * (1.15f / fontScale) else size,
            )
        },
    )
}
