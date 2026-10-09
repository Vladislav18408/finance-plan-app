package com.vladk.financeplan.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vladk.financeplan.data.AppData
import com.vladk.financeplan.data.Category
import com.vladk.financeplan.data.EmergencyFund
import com.vladk.financeplan.data.Expense
import com.vladk.financeplan.data.Goal
import com.vladk.financeplan.data.Income
import com.vladk.financeplan.data.Investing
import com.vladk.financeplan.data.Repository
import kotlinx.coroutines.flow.StateFlow

/** The single place where the UI changes data. Each function is one user action. */
class FinanceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)
    val data: StateFlow<AppData> = repo.data

    /** Adds the item, or replaces the existing one with the same id. */
    private fun <T> List<T>.upsert(item: T, id: (T) -> String): List<T> {
        val i = indexOfFirst { id(it) == id(item) }
        return if (i < 0) this + item else toMutableList().also { it[i] = item }
    }

    fun saveExpense(e: Expense) = repo.update { it.copy(expenses = it.expenses.upsert(e) { x -> x.id }) }
    fun deleteExpense(id: String) = repo.update { d -> d.copy(expenses = d.expenses.filterNot { it.id == id }) }

    fun saveCategory(c: Category) = repo.update { it.copy(categories = it.categories.upsert(c) { x -> x.id }) }

    /** Deleting a category also deletes its expenses, so totals never point at nothing. */
    fun deleteCategory(id: String) = repo.update { d ->
        d.copy(
            categories = d.categories.filterNot { it.id == id },
            expenses = d.expenses.filterNot { it.categoryId == id },
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

    /** Returns false when the text is not a valid backup; nothing changes in that case. */
    fun importJson(text: String): Boolean = try {
        repo.replaceAll(repo.parseBackup(text))
        true
    } catch (e: Exception) {
        false
    }

    /** Wipes everything except the language and currency choice. */
    fun deleteAll() = repo.update { AppData(settings = it.settings) }
}
