package com.vladk.financeplan.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import java.text.BreakIterator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vladk.financeplan.data.Calc
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.EmergencyFund
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.ExtraIncome
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.data.Income
import com.vladk.financeplan.data.Investing
import com.vladk.financeplan.data.Group
import com.vladk.financeplan.data.Kind
import com.vladk.financeplan.data.PlanType
import com.vladk.financeplan.data.Presets
import com.vladk.financeplan.data.emoji
import com.vladk.financeplan.data.label
import com.vladk.financeplan.ui.LocalFmt
import com.vladk.financeplan.ui.LocalStrings
import com.vladk.financeplan.ui.theme.badColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Dimmed layer over the whole app that holds an editor sheet. It is drawn inside the activity
 * rather than in a dialog window: only there does Android report the keyboard's size reliably,
 * which the sheet needs to keep its Save button above the keyboard. Taps on the dimmed part do
 * nothing (a half-typed form should not vanish by accident); Back and Cancel close it.
 */
@Composable
private fun Scrim(onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { } },
    ) { content() }
}

/**
 * Shared frame for every add/edit form: a sheet at the bottom of the screen with a title, the
 * scrollable form, and Cancel / Save buttons that stay visible above the keyboard.
 * Must be placed in a full-screen Box on top of the app's content (see FinanceApp).
 */
@Composable
fun EditorDialog(
    title: String,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = LocalStrings.current
    Scrim(onBack = onDismiss) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 12.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        if (onDelete != null) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Filled.Delete, contentDescription = s.delete, tint = badColor())
                            }
                        }
                    }
                    // The form scrolls; the buttons below it always stay on screen.
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(s.cancel) }
                        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text(s.save) }
                    }
                }
            }
        }
    }
}

/** The last whole emoji in [text] – flags and family emoji are several characters long. */
internal fun lastGrapheme(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(t)
    val end = breaks.last()
    val start = breaks.previous()
    return if (start == BreakIterator.DONE) t else t.substring(start, end)
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------- Expense

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseDialog(
    initial: Expense?,
    categories: List<Category>,
    recentCategoryIds: List<String>,
    defaultDate: LocalDate,
    preselectedCategoryId: String?,
    noteSuggestions: (categoryId: String) -> List<String>,
    onSave: (Expense, repeatMonthly: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    // TextFieldValue (not String) so the cursor can be moved to the end after the "+" button.
    var amountField by remember {
        val text = initial?.let { fmt.input(it.amount) } ?: ""
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val amount = amountField.text
    var categoryId by remember {
        mutableStateOf(initial?.categoryId ?: preselectedCategoryId ?: categories.singleOrNull()?.id)
    }
    var date by remember {
        mutableStateOf(initial?.let { runCatching { LocalDate.parse(it.date) }.getOrNull() } ?: defaultDate)
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var repeatMonthly by remember { mutableStateOf(false) }

    if (initial == null) LaunchedEffect(Unit) { focus.requestFocus() }

    EditorDialog(
        title = if (initial == null) s.newExpense else s.editExpense,
        onDismiss = onDismiss,
        // No "are you sure?" here: the caller offers Undo right after the delete.
        onDelete = initial?.let { { onDelete(); onDismiss() } },
        onSave = {
            val value = Calc.parseAmount(amount)
            amountError = if (value == null || value == 0.0) s.invalidAmount else null
            categoryError = categoryId == null
            if (amountError == null && categoryId != null) {
                onSave(
                    Expense(
                        id = initial?.id ?: com.vladk.financeplan.data.newId(),
                        categoryId = categoryId!!,
                        amount = value!!,
                        date = date.toString(),
                        note = note.trim(),
                    ),
                    repeatMonthly,
                )
                onDismiss()
            }
        },
    ) {
        OutlinedTextField(
            value = amountField,
            onValueChange = { amountField = it; amountError = null },
            label = { Text(s.amount) },
            placeholder = { Text(s.amountHint) },
            isError = amountError != null,
            supportingText = amountError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            // The phone's number pad has no "+", so offer it as a button for adding up a receipt.
            trailingIcon = {
                IconButton(onClick = {
                    val t = amount.trimEnd()
                    if (t.isNotEmpty() && !t.endsWith("+")) amountField = TextFieldValue("$t+", TextRange(t.length + 1))
                    focus.requestFocus()
                }) { Icon(Icons.Filled.Add, contentDescription = s.addAnotherAmount) }
            },
        )
        Calc.parseAmount(amount)?.takeIf { amount.contains('+') }?.let {
            Text("= ${fmt.money(it)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }

        FieldLabel(s.category)
        if (categories.isEmpty()) {
            EmptyText(s.needCategoryFirst)
        }
        if (categoryError) Text(s.pickCategory, color = badColor(), style = MaterialTheme.typography.bodySmall)
        CategoryPicker(categories, recentCategoryIds, categoryId) { categoryId = it; categoryError = false }

        FieldLabel(s.date)
        // Most expenses are from today or yesterday: one tap each, the calendar for anything else.
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = date == today, onClick = { date = today }, label = { Text(s.today) })
            FilterChip(selected = date == yesterday, onClick = { date = yesterday }, label = { Text(s.yesterday) })
            val other = date != today && date != yesterday
            FilterChip(
                selected = other,
                onClick = { showDatePicker = true },
                label = { Text(if (other) fmt.shortDate(date) else s.pickDate) },
                leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }

        TextInput(label = s.note, value = note, onValueChange = { note = it }, imeAction = ImeAction.Done)
        // Notes typed before for this category ("Lidl", "кафе" …): one tap instead of typing again.
        val suggestions = remember(categoryId) { categoryId?.let(noteSuggestions).orEmpty() }.filter { it != note.trim() }
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { text ->
                    SuggestionChip(
                        onClick = { note = text },
                        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        // Rent, subscriptions, instalments: record once, and the app adds it every month from now on.
        if (initial == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .toggleable(value = repeatMonthly, role = Role.Checkbox, onValueChange = { repeatMonthly = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = repeatMonthly, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(s.repeatMonthly, style = MaterialTheme.typography.bodyLarge)
                    if (repeatMonthly) {
                        Text(
                            "${s.regularEvery.format(date.dayOfMonth).replaceFirstChar { it.titlecase() }}. ${s.repeatMonthlyHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(s.cancel) } },
        ) { DatePicker(state = state) }
    }
}

/**
 * The categories used most recently (plus the chosen one) as one-tap chips; the full list, grouped,
 * opens underneath on request. With nothing recorded yet the full list starts open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    recentIds: List<String>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val s = LocalStrings.current
    val byId = categories.associateBy { it.id }
    val recent = recentIds.mapNotNull { byId[it] }.take(6)
    val quick = (recent + listOfNotNull(selectedId?.let { byId[it] })).distinctBy { it.id }
    var showAll by remember { mutableStateOf(recent.isEmpty()) }

    @Composable
    fun chips(list: List<Category>, collapseOnPick: Boolean = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { c ->
                FilterChip(
                    selected = c.id == selectedId,
                    onClick = {
                        onSelect(c.id)
                        if (collapseOnPick) showAll = false
                    },
                    label = { Text("${c.emoji()}  ${c.title()}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }

    @Composable
    fun header(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (quick.isNotEmpty()) {
        if (recent.isNotEmpty()) header(s.recentlyUsed)
        chips(quick)
    }
    if (categories.size > quick.size) {
        TextButton(onClick = { showAll = !showAll }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(s.allCategoriesToggle)
            Spacer(Modifier.width(4.dp))
            Icon(if (showAll) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
    }
    if (showAll) {
        Group.entries.forEach { g ->
            val list = categories.filter { it.group == g }
            if (list.isNotEmpty()) {
                header("${g.emoji}  ${s.group(g)}")
                chips(list, collapseOnPick = true)
            }
        }
    }
}

// ---------------------------------------------------------------- Category

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    initial: Category?,
    income: Income,
    expenseCount: Int,
    onSave: (Category) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var name by remember { mutableStateOf(initial?.label(s.code) ?: "") }
    var group by remember { mutableStateOf(initial?.group ?: Group.OTHER) }
    var icon by remember { mutableStateOf(initial?.icon ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: Group.OTHER.defaultKind) }
    var planType by remember { mutableStateOf(initial?.planType ?: PlanType.FIXED) }
    var amount by remember { mutableStateOf(initial?.let { fmt.input(it.amount) } ?: "") }
    var percent by remember { mutableStateOf(initial?.let { fmt.input(it.percent) } ?: "") }
    var minusVouchers by remember { mutableStateOf(initial?.minusVouchers ?: false) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var numberError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun build(): Category? {
        val a = if (amount.isBlank()) 0.0 else Calc.parseAmount(amount)
        val p = if (percent.isBlank()) 0.0 else Calc.parseAmount(percent)
        nameError = name.isBlank()
        numberError = (planType == PlanType.FIXED && a == null) || (planType == PlanType.PERCENT && p == null)
        if (nameError || numberError) return null
        // A predefined category that keeps its original name stays translatable (empty stored name).
        val presetName = initial?.presetId?.let { Presets.name(it, s.code) }
        return Category(
            id = initial?.id ?: com.vladk.financeplan.data.newId(),
            name = if (name.trim() == presetName) "" else name.trim(),
            kind = kind,
            planType = planType,
            amount = a ?: 0.0,
            percent = p ?: 0.0,
            minusVouchers = planType == PlanType.PERCENT && minusVouchers,
            note = note.trim(),
            group = group,
            icon = icon.trim(),
            presetId = initial?.presetId,
        )
    }

    EditorDialog(
        title = if (initial == null) s.newCategory else s.editCategory,
        onDismiss = onDismiss,
        onDelete = initial?.let { { confirmDelete = true } },
        onSave = { build()?.let { onSave(it); onDismiss() } },
    ) {
        TextInput(
            label = s.name,
            value = name,
            onValueChange = { name = it; nameError = false },
            error = if (nameError) s.nameRequired else null,
        )

        // The plan comes right after the name: setting the amount is what this form is opened for most.
        FieldLabel(s.planType)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            PlanType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = planType == t,
                    onClick = { planType = t; numberError = false },
                    shape = SegmentedButtonDefaults.itemShape(i, PlanType.entries.size),
                    // Transparent, so the unselected half takes the sheet's colour in dark mode too.
                    colors = SegmentedButtonDefaults.colors(inactiveContainerColor = Color.Transparent),
                ) { Text(if (t == PlanType.FIXED) s.planFixed else s.planPercent) }
            }
        }

        if (planType == PlanType.FIXED) {
            NumberField(
                label = s.amountPerMonth,
                value = amount,
                onValueChange = { amount = it; numberError = false },
                error = if (numberError) s.invalidAmount else null,
            )
        } else {
            NumberField(
                label = s.percentOfSalary,
                value = percent,
                onValueChange = { percent = it; numberError = false },
                error = if (numberError) s.invalidAmount else null,
                suffix = "%",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = minusVouchers, onCheckedChange = { minusVouchers = it })
                Text(s.minusVouchers, style = MaterialTheme.typography.bodyMedium)
            }
            val preview = Calc.planned(
                Category(name = "", kind = kind, planType = PlanType.PERCENT,
                    percent = Calc.parseAmount(percent) ?: 0.0, minusVouchers = minusVouchers),
                income,
            )
            Text("= ${fmt.money(preview)} ${s.perMonth}", fontWeight = FontWeight.SemiBold)
        }

        OutlinedTextField(
            value = icon,
            // One emoji only; typing another one replaces it.
            onValueChange = { icon = lastGrapheme(it) },
            label = { Text(s.icon) },
            placeholder = { Text(group.emoji) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        FieldLabel(s.group)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Group.entries.forEach { g ->
                FilterChip(
                    selected = g == group,
                    onClick = {
                        group = g
                        // For a new category, suggest the group's usual type.
                        if (initial == null) kind = g.defaultKind
                    },
                    label = { Text("${g.emoji}  ${s.group(g)}") },
                )
            }
        }

        FieldLabel(s.kind)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kind.entries.forEach { k ->
                FilterChip(
                    selected = k == kind,
                    onClick = { kind = k },
                    label = { Text(s.kind(k)) },
                    leadingIcon = { KindDot(k, 8) },
                )
            }
        }

        TextInput(label = s.note, value = note, onValueChange = { note = it }, singleLine = false)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = s.deleteConfirmTitle,
            text = initial.title() +
                if (expenseCount > 0) "\n\n${s.deleteCategoryWarning}\n${s.expensesInCategory.format(expenseCount)}" else "",
            confirmLabel = s.delete,
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------------------------------------------------------- Income

private class ExtraDraft(val id: String, name: String, amount: String) {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
}

@Composable
fun IncomeDialog(initial: Income, onSave: (Income) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var salary by remember { mutableStateOf(fmt.input(initial.salary)) }
    var vouchers by remember { mutableStateOf(fmt.input(initial.vouchers)) }
    var share by remember { mutableStateOf(fmt.input(initial.investSharePercent)) }
    val extras = remember {
        mutableStateListOf<ExtraDraft>().apply {
            initial.extras.forEach { add(ExtraDraft(it.id, it.name, fmt.input(it.amount))) }
        }
    }
    var error by remember { mutableStateOf(false) }

    fun num(t: String) = if (t.isBlank()) 0.0 else Calc.parseAmount(t)

    EditorDialog(
        title = s.editIncome,
        onDismiss = onDismiss,
        onSave = {
            val sal = num(salary)
            val vou = num(vouchers)
            val sh = num(share)
            val ex = extras.filter { it.name.isNotBlank() || it.amount.isNotBlank() }
                .map { d -> num(d.amount)?.let { ExtraIncome(d.id, d.name.trim().ifEmpty { "—" }, it) } }
            error = sal == null || vou == null || sh == null || sh > 100 || ex.any { it == null }
            if (!error) {
                onSave(Income(sal!!, vou!!, sh!!, ex.filterNotNull()))
                onDismiss()
            }
        },
    ) {
        NumberField(label = s.salary, value = salary, onValueChange = { salary = it; error = false }, hint = s.salaryHint)
        NumberField(label = s.vouchers, value = vouchers, onValueChange = { vouchers = it; error = false })

        Spacer(Modifier.height(4.dp))
        FieldLabel(s.extraIncomes)
        Text(s.extraIncomesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extras.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextInput(label = s.name, value = d.name, onValueChange = { d.name = it }, modifier = Modifier.weight(1.3f))
                Spacer(Modifier.width(8.dp))
                NumberField(label = s.amount, value = d.amount, onValueChange = { d.amount = it; error = false }, modifier = Modifier.weight(1f))
                IconButton(onClick = { extras.remove(d) }) { Icon(Icons.Filled.Close, contentDescription = s.delete) }
            }
        }
        TextButton(onClick = { extras.add(ExtraDraft(com.vladk.financeplan.data.newId(), "", "")) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(s.addExtraIncome)
        }
        if (extras.isNotEmpty()) {
            NumberField(label = s.investShare, value = share, onValueChange = { share = it; error = false }, suffix = "%")
        }
        if (error) Text(s.invalidAmount, color = badColor(), style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- Goals

@Composable
fun GoalDialog(initial: Goal?, onSave: (Goal) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(initial?.let { fmt.input(it.target) } ?: "") }
    var saved by remember { mutableStateOf(initial?.let { fmt.input(it.saved) } ?: "") }
    var monthly by remember { mutableStateOf(initial?.let { fmt.input(it.monthly) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var numberError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun num(t: String) = if (t.isBlank()) 0.0 else Calc.parseAmount(t)

    EditorDialog(
        title = if (initial == null) s.newGoal else s.editGoal,
        onDismiss = onDismiss,
        onDelete = initial?.let { { confirmDelete = true } },
        onSave = {
            val t = num(target); val sv = num(saved); val m = num(monthly)
            nameError = name.isBlank()
            numberError = t == null || sv == null || m == null
            if (!nameError && !numberError) {
                onSave(Goal(initial?.id ?: com.vladk.financeplan.data.newId(), name.trim(), t!!, sv!!, m!!, note.trim()))
                onDismiss()
            }
        },
    ) {
        TextInput(label = s.name, value = name, onValueChange = { name = it; nameError = false },
            error = if (nameError) s.nameRequired else null)
        NumberField(label = s.goalTarget, value = target, onValueChange = { target = it; numberError = false })
        NumberField(label = s.goalSaved, value = saved, onValueChange = { saved = it; numberError = false })
        NumberField(label = s.monthlyContribution, value = monthly, onValueChange = { monthly = it; numberError = false })
        if (numberError) Text(s.invalidAmount, color = badColor(), style = MaterialTheme.typography.bodySmall)
        TextInput(label = s.note, value = note, onValueChange = { note = it }, singleLine = false)
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = s.deleteConfirmTitle,
            text = initial?.name.orEmpty(),
            confirmLabel = s.delete,
            onConfirm = { onDelete(); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun EmergencyDialog(initial: EmergencyFund, onSave: (EmergencyFund) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var months by remember { mutableStateOf(initial.months.toString()) }
    var saved by remember { mutableStateOf(fmt.input(initial.saved)) }
    var monthly by remember { mutableStateOf(fmt.input(initial.monthly)) }
    var error by remember { mutableStateOf(false) }

    fun num(t: String) = if (t.isBlank()) 0.0 else Calc.parseAmount(t)

    EditorDialog(
        title = s.editEmergency,
        onDismiss = onDismiss,
        onSave = {
            val mo = months.trim().toIntOrNull()
            val sv = num(saved); val m = num(monthly)
            error = mo == null || mo < 0 || sv == null || m == null
            if (!error) {
                onSave(EmergencyFund(mo!!, sv!!, m!!))
                onDismiss()
            }
        },
    ) {
        NumberField(label = s.monthsToCover, value = months, onValueChange = { months = it; error = false })
        NumberField(label = s.alreadySaved, value = saved, onValueChange = { saved = it; error = false })
        NumberField(label = s.monthlyContribution, value = monthly, onValueChange = { monthly = it; error = false })
        if (error) Text(s.invalidAmount, color = badColor(), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun InvestingDialog(initial: Investing, onSave: (Investing) -> Unit, onDismiss: () -> Unit) {
    val s = LocalStrings.current
    val fmt = LocalFmt.current
    var age by remember { mutableStateOf(if (initial.age > 0) initial.age.toString() else "") }
    var rate by remember { mutableStateOf(fmt.input(initial.annualReturnPercent)) }
    var error by remember { mutableStateOf(false) }

    EditorDialog(
        title = s.editInvesting,
        onDismiss = onDismiss,
        onSave = {
            val a = if (age.isBlank()) 0 else age.trim().toIntOrNull()
            val r = if (rate.isBlank()) 0.0 else Calc.parseAmount(rate)
            error = a == null || a < 0 || r == null || r > 100
            if (!error) {
                onSave(Investing(a!!, r!!))
                onDismiss()
            }
        },
    ) {
        NumberField(label = s.ageToday, value = age, onValueChange = { age = it; error = false })
        NumberField(label = s.annualReturn, value = rate, onValueChange = { rate = it; error = false }, suffix = "%")
        if (error) Text(s.invalidAmount, color = badColor(), style = MaterialTheme.typography.bodySmall)
    }
}
