package com.vladk.financeplan.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** One Monday–Sunday week. */
data class WeekReport(
    val start: LocalDate,
    val days: List<LocalDate>,
    /** Needs + wants spent per day, Monday first. */
    val spentByDay: List<Double>,
    val spent: Double,
    val saved: Double,
    val previousSpent: Double,
    /** Monthly planned spending turned into a weekly guide (× 12 / 52). */
    val guide: Double,
    val expenses: List<Expense>,
    val byCategory: List<Pair<Category?, Double>>,
) {
    val end: LocalDate get() = days.last()
}

data class MonthTotal(val month: YearMonth, val spent: Double, val saved: Double)

/** Report formulas. "Spending" = needs + wants; savings/investment deposits are counted separately. */
object Reports {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Expenses dated [from]..[to], both included. Stored dates are always ISO "yyyy-MM-dd"
     * (see [Backup.clean]), and those sort the same way as text, so no date parsing is needed.
     */
    fun between(data: AppData, from: LocalDate, to: LocalDate): List<Expense> {
        val a = from.toString()
        val b = to.toString()
        return data.expenses.filter { it.date >= a && it.date <= b }
    }

    private fun categories(data: AppData) = data.categories.associateBy { it.id }

    /** Expenses in a deleted/unknown category are treated as spending. */
    private fun isSaving(e: Expense, cats: Map<String, Category>) = cats[e.categoryId]?.kind?.isSaving == true

    fun spending(data: AppData, list: List<Expense>): List<Expense> {
        val cats = categories(data)
        return list.filterNot { isSaving(it, cats) }
    }

    fun saving(data: AppData, list: List<Expense>): List<Expense> {
        val cats = categories(data)
        return list.filter { isSaving(it, cats) }
    }

    /**
     * Newest first. Expenses of the same day keep the order they were added in, latest on top,
     * so the thing you just typed in is the first row.
     */
    fun newestFirst(list: List<Expense>): List<Expense> = list.asReversed().sortedByDescending { it.date }

    /** Planned needs + wants per month, as a weekly amount (a month is 52/12 ≈ 4.33 weeks). */
    fun weeklyGuide(data: AppData): Double =
        data.categories.filterNot { it.kind.isSaving }.sumOf { Calc.planned(it, data.income) } * 12.0 / 52.0

    fun week(data: AppData, start: LocalDate): WeekReport {
        val days = (0L..6L).map { start.plusDays(it) }
        val all = between(data, start, days.last())
        val spend = spending(data, all)
        val perDay = spend.groupBy { it.date }
        val byDay = days.map { d -> perDay[d.toString()]?.sumOf { it.amount } ?: 0.0 }
        val previous = spending(data, between(data, start.minusDays(7), start.minusDays(1))).sumOf { it.amount }
        return WeekReport(
            start = start,
            days = days,
            spentByDay = byDay,
            spent = byDay.sum(),
            saved = saving(data, all).sumOf { it.amount },
            previousSpent = previous,
            guide = weeklyGuide(data),
            expenses = newestFirst(all),
            byCategory = byCategory(data, spend),
        )
    }

    fun byCategory(data: AppData, list: List<Expense>): List<Pair<Category?, Double>> {
        val cats = categories(data)
        return list.groupBy { it.categoryId }
            .map { (id, l) -> cats[id] to l.sumOf { it.amount } }
            .sortedByDescending { it.second }
    }

    fun byGroup(data: AppData, list: List<Expense>): List<Pair<Group, Double>> {
        val cats = categories(data)
        return list.groupBy { cats[it.categoryId]?.group ?: Group.OTHER }
            .map { (g, l) -> g to l.sumOf { it.amount } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    fun monthExpenses(data: AppData, month: YearMonth): List<Expense> =
        between(data, month.atDay(1), month.atEndOfMonth())

    /**
     * Running total of spending for each day of [month]. Days after today are null for the
     * current month, so the line stops at today.
     */
    fun cumulative(data: AppData, month: YearMonth, today: LocalDate = LocalDate.now()): List<Double?> {
        val perDay = spending(data, monthExpenses(data, month)).groupBy { it.date }
        var running = 0.0
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            if (date.isAfter(today)) {
                null
            } else {
                running += perDay[date.toString()]?.sumOf { it.amount } ?: 0.0
                running
            }
        }
    }

    /** Spending and savings for the [count] months ending with [last]. */
    fun monthTotals(data: AppData, last: YearMonth, count: Int = 6): List<MonthTotal> =
        (count - 1 downTo 0).map { back ->
            val m = last.minusMonths(back.toLong())
            val list = monthExpenses(data, m)
            MonthTotal(m, spending(data, list).sumOf { it.amount }, saving(data, list).sumOf { it.amount })
        }

    /**
     * The months an "average per month" is taken over: from the first month with any data, and
     * without the month that is still in progress – a half-finished month would pull the average
     * down. While the current month is the only one with data, it is used as it is.
     */
    fun averagedMonths(totals: List<MonthTotal>, current: YearMonth = YearMonth.now()): List<MonthTotal> {
        val first = totals.indexOfFirst { it.spent > 0 || it.saved > 0 }
        val withData = if (first < 0) totals else totals.drop(first)
        return withData.filter { it.month < current }.ifEmpty { withData }
    }

    fun totalIncome(data: AppData) = data.income.salary + Calc.extrasTotal(data.income)
}
