package com.vladk.financeplan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Backup
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.EmergencyFund
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.data.Income
import com.vladk.financeplan.data.Investing
import com.vladk.financeplan.data.Presets
import com.vladk.financeplan.data.Regular
import com.vladk.financeplan.data.Reminder
import com.vladk.financeplan.data.Repository
import com.vladk.financeplan.reminder.Reminders
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** The single place where the UI changes data. Each function is one user action. */
class FinanceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    val data: StateFlow<AppData> = repo.data

    /** True while the data could not be written to the phone's storage. */
    val saveFailed: StateFlow<Boolean> = repo.saveFailed

    /** Adds the item, or replaces the existing one with the same id. */
    private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val i = indexOfFirst { id(it) == id(item) }
        return if (i < 0) this + item else toMutableList().also { it[i] = item }
    }

    init {
        // Alarms are lost when the phone restarts or the app is updated; setting it again on every start is cheap.
        Reminders.schedule(app, data.value.settings.reminder)
    }

    /** Saves the expense; with [repeatMonthly] it also becomes a regular payment from next month on. */
    fun saveExpense(e: Expense, repeatMonthly: Boolean = false) = repo.update { d ->
        d.copy(
            expenses = d.expenses.upsert(e) { x -> x.id },
            recurring = d.recurring + listOfNotNull(Regular.from(e).takeIf { repeatMonthly }),
        )
    }

    fun deleteExpense(id: String) = repo.update { d -> d.copy(expenses = d.expenses.filterNot { it.id == id }) }

    /** Stops a regular payment. The expenses it already added stay. */
    fun deleteRecurring(id: String) = repo.update { d -> d.copy(recurring = d.recurring.filterNot { it.id == id }) }

    /**
     * Adds the regular payments that have come due since the app was last open.
     * Returns how many expenses were added.
     */
    fun addDueRegular(today: LocalDate = LocalDate.now()): Int {
        val before = data.value.expenses.size
        repo.update { Regular.addDue(it, today) }
        return data.value.expenses.size - before
    }

    fun setReminder(reminder: Reminder) {
        repo.update { it.copy(settings = it.settings.copy(reminder = reminder)) }
        Reminders.schedule(getApplication(), reminder)
    }

    fun saveCategory(c: Category) = repo.update { it.copy(categories = it.categories.upsert(c) { x -> x.id }) }

    /** Deleting a category also deletes its expenses and regular payments, so totals never point at nothing. */
    fun deleteCategory(id: String) = repo.update { d ->
        d.copy(
            categories = d.categories.filterNot { it.id == id },
            expenses = d.expenses.filterNot { it.categoryId == id },
            recurring = d.recurring.filterNot { it.categoryId == id },
        )
    }

    fun saveIncome(i: Income) = repo.update { it.copy(income = i) }

    fun saveGoal(g: Goal) = repo.update { it.copy(goals = it.goals.upsert(g) { x -> x.id }) }
    fun deleteGoal(id: String) = repo.update { d -> d.copy(goals = d.goals.filterNot { it.id == id }) }

    fun saveEmergency(e: EmergencyFund) = repo.update { it.copy(emergency = e) }
    fun saveInvesting(i: Investing) = repo.update { it.copy(investing = i) }

    fun setLanguage(code: String) = repo.update { it.copy(settings = it.settings.copy(language = code)) }
    fun setCurrency(symbol: String) = repo.update { it.copy(settings = it.settings.copy(currency = symbol)) }

    fun exportJson(): String = repo.exportJson()

    /**
     * Replaces everything with the backup in [text]. Returns how many unreadable entries were
     * left out, or null when the text is not a backup at all; nothing changes in that case.
     */
    fun importJson(text: String): Int? = try {
        val backup = Backup.parse(text)
        repo.update { Repository.withPresets(backup.data) }
        Reminders.schedule(getApplication(), backup.data.settings.reminder)
        backup.skipped
    } catch (e: Exception) {
        null
    }

    /** All expenses as a CSV table for Excel / Google Sheets, in the app's current language. */
    fun exportCsv(s: Strings): String = Backup.expensesCsv(
        data = data.value,
        language = s.code,
        header = listOf(s.date, s.category, s.group, s.kind, s.amount, s.note),
        kindName = s::kind,
        groupName = s::group,
        unknownCategory = s.deletedCategory,
    )

    /** Adds the predefined categories the user does not have (e.g. after deleting some). */
    fun addMissingPresets() = repo.update { d ->
        d.copy(
            categories = d.categories + Presets.missing(d.categories).map(Presets::toCategory),
            presetsVersion = Presets.VERSION,
        )
    }

    /** Wipes everything except the language and currency choice; the predefined categories come back. */
    fun deleteAll() = repo.update {
        AppData(settings = it.settings, categories = Presets.defaults(), presetsVersion = Presets.VERSION)
    }
}
