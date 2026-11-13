package com.vladk.financeplan.data

import java.time.LocalDate
import java.time.YearMonth

/** Regular (monthly) payments: working out which ones are due and turning them into expenses. */
object Regular {

    /** A phone left unopened for years should not flood the list; one year of catching up is plenty. */
    private const val MAX_CATCH_UP_MONTHS = 12L

    /** The date a payment set for [day] falls on in [month]: the 31st becomes the 30th or 28th. */
    fun dateIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

    /** A regular payment made from an expense the user just recorded; it repeats from the next month on. */
    fun from(expense: Expense): Recurring? {
        val date = runCatching { LocalDate.parse(expense.date) }.getOrNull() ?: return null
        return Recurring(
            categoryId = expense.categoryId,
            amount = expense.amount,
            note = expense.note,
            day = date.dayOfMonth,
            lastAdded = YearMonth.from(date).toString(),
        )
    }

    /**
     * Adds every regular payment whose date has come (up to and including [today]) and has not
     * been added yet, including months missed while the app was not opened. Returns the data
     * unchanged when nothing is due. Each added expense gets an id made from the payment and the
     * month, so running this twice can never add the same payment twice.
     */
    fun addDue(data: AppData, today: LocalDate): AppData {
        val current = YearMonth.from(today)
        val categories = data.categories.map { it.id }.toSet()
        val existing = data.expenses.map { it.id }.toHashSet()
        val added = mutableListOf<Expense>()
        val updated = data.recurring.map { r ->
            if (r.categoryId !in categories) return@map r
            val last = runCatching { YearMonth.parse(r.lastAdded) }.getOrNull()
            // Never added before: start with the current month. Otherwise continue after the last one.
            var month = last?.plusMonths(1) ?: current
            if (month.isBefore(current.minusMonths(MAX_CATCH_UP_MONTHS))) month = current.minusMonths(MAX_CATCH_UP_MONTHS)
            var lastAdded = r.lastAdded
            while (!month.isAfter(current)) {
                val date = dateIn(month, r.day)
                if (date.isAfter(today)) break
                val id = "regular-${r.id}-$month"
                if (existing.add(id)) added += Expense(id, r.categoryId, r.amount, date.toString(), r.note)
                lastAdded = month.toString()
                month = month.plusMonths(1)
            }
            if (lastAdded == r.lastAdded) r else r.copy(lastAdded = lastAdded)
        }
        return if (updated == data.recurring) data else data.copy(expenses = data.expenses + added, recurring = updated)
    }

    /** What all regular payments add up to per month. */
    fun monthlyTotal(data: AppData): Double = data.recurring.sumOf { it.amount }
}
