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

    private fun parse(e: Expense): LocalDate? = runCatching { LocalDate.parse(e.date) }.getOrNull()

    fun between(data: AppData, from: LocalDate, to: LocalDate): List<Expense> = data.expenses.filter { e ->
        val d = parse(e) ?: return@filter false
        !d.isBefore(from) && !d.isAfter(to)
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

    /** Planned needs + wants per month, as a weekly amount (a month is 52/12 ≈ 4.33 weeks). */
    fun weeklyGuide(data: AppData): Double =
        Calc.summary(data, YearMonth.now()).plannedSpending * 12.0 / 52.0

    fun week(data: AppData, start: LocalDate): WeekReport {
        val days = (0L..6L).map { start.plusDays(it) }
        val all = between(data, start, days.last())
        val spend = spending(data, all)
        val byDay = days.map { d -> spend.filter { it.date == d.toString() }.sumOf { it.amount } }
        val previous = spending(data, between(data, start.minusDays(7), start.minusDays(1))).sumOf { it.amount }
        return WeekReport(
            start = start,
            days = days,
            spentByDay = byDay,
            spent = byDay.sum(),
            saved = saving(data, all).sumOf { it.amount },
            previousSpent = previous,
            guide = weeklyGuide(data),
            expenses = all.sortedByDescending { it.date },
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
        val spend = spending(data, monthExpenses(data, month))
        var running = 0.0
        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            if (date.isAfter(today)) {
                null
            } else {
                running += spend.filter { it.date == date.toString() }.sumOf { it.amount }
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

    fun totalIncome(data: AppData) = data.income.salary + Calc.extrasTotal(data.income)
}
