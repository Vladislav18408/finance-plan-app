package com.vladk.financeplan.data

import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

enum class Status { NO_DATA, WITHIN_PLAN, OVER_BUDGET, GOAL_REACHED, BELOW_GOAL }

data class CategoryLine(
    val category: Category,
    val planned: Double,
    val actual: Double,
    val status: Status,
)

data class MonthSummary(
    val lines: List<CategoryLine>,
    val plannedByKind: Map<Kind, Double>,
    val actualByKind: Map<Kind, Double>,
) {
    private fun sum(map: Map<Kind, Double>, pick: (Kind) -> Boolean) =
        map.filterKeys(pick).values.sum()

    /** Needs + wants: the money that is actually spent. */
    val plannedSpending get() = sum(plannedByKind) { !it.isSaving }
    val actualSpending get() = sum(actualByKind) { !it.isSaving }

    /** Savings + investments: the money that is put aside. */
    val plannedSaving get() = sum(plannedByKind) { it.isSaving }
    val actualSaving get() = sum(actualByKind) { it.isSaving }

    val plannedTotal get() = plannedByKind.values.sum()
}

data class RuleRow(val label: Kind, val rulePercent: Double, val plannedPercent: Double, val actualPercent: Double)

data class ProjectionRow(val years: Int, val age: Int?, val contributed: Double, val value: Double) {
    val growth get() = value - contributed
}

/** All the spreadsheet formulas, as plain functions. */
object Calc {

    fun planned(c: Category, income: Income): Double = when (c.planType) {
        PlanType.FIXED -> c.amount
        PlanType.PERCENT -> {
            val v = c.percent / 100.0 * income.salary
            if (c.minusVouchers) max(0.0, v - income.vouchers) else v
        }
    }

    fun expensesIn(data: AppData, month: YearMonth): List<Expense> {
        val prefix = month.toString() // "2026-10"
        return data.expenses.filter { it.date.startsWith(prefix) }
    }

    fun status(kind: Kind, planned: Double, actual: Double): Status = when {
        actual == 0.0 -> Status.NO_DATA
        kind.isSaving -> if (actual >= planned) Status.GOAL_REACHED else Status.BELOW_GOAL
        else -> if (actual <= planned + 0.005) Status.WITHIN_PLAN else Status.OVER_BUDGET
    }

    fun summary(data: AppData, month: YearMonth): MonthSummary {
        val actualByCat = expensesIn(data, month).groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
        val lines = data.categories.map { c ->
            val p = planned(c, data.income)
            val a = actualByCat[c.id] ?: 0.0
            CategoryLine(c, p, a, status(c.kind, p, a))
        }
        return MonthSummary(
            lines = lines,
            plannedByKind = Kind.entries.associateWith { k -> lines.filter { it.category.kind == k }.sumOf { it.planned } },
            actualByKind = Kind.entries.associateWith { k -> lines.filter { it.category.kind == k }.sumOf { it.actual } },
        )
    }

    fun unallocated(data: AppData, s: MonthSummary) = data.income.salary - s.plannedTotal

    fun percentOfSalary(data: AppData, value: Double) =
        if (data.income.salary == 0.0) 0.0 else value / data.income.salary * 100.0

    fun rule503020(data: AppData, s: MonthSummary): List<RuleRow> {
        fun pct(map: Map<Kind, Double>, pick: (Kind) -> Boolean) =
            percentOfSalary(data, map.filterKeys(pick).values.sum())
        return listOf(
            RuleRow(Kind.NEEDS, 50.0, pct(s.plannedByKind) { it == Kind.NEEDS }, pct(s.actualByKind) { it == Kind.NEEDS }),
            RuleRow(Kind.WANTS, 30.0, pct(s.plannedByKind) { it == Kind.WANTS }, pct(s.actualByKind) { it == Kind.WANTS }),
            RuleRow(Kind.SAVINGS, 20.0, pct(s.plannedByKind) { it.isSaving }, pct(s.actualByKind) { it.isSaving }),
        )
    }

    // ---- Extra incomes (100% saved/invested) ----
    fun extrasTotal(i: Income) = i.extras.sumOf { it.amount }
    fun extrasToInvest(i: Income) = extrasTotal(i) * i.investSharePercent / 100.0
    fun extrasToSave(i: Income) = extrasTotal(i) - extrasToInvest(i)

    fun monthlyInvesting(data: AppData, s: MonthSummary) =
        (s.plannedByKind[Kind.INVESTMENTS] ?: 0.0) + extrasToInvest(data.income)

    fun monthlySaving(data: AppData, s: MonthSummary) =
        (s.plannedByKind[Kind.SAVINGS] ?: 0.0) + extrasToSave(data.income)

    /** (investments + savings) / (salary + extra incomes) */
    fun savingsRate(data: AppData, s: MonthSummary): Double {
        val all = data.income.salary + extrasTotal(data.income)
        return if (all == 0.0) 0.0 else (monthlyInvesting(data, s) + monthlySaving(data, s)) / all * 100.0
    }

    // ---- Goals ----
    fun emergencyTarget(data: AppData, s: MonthSummary) =
        data.emergency.months * (s.plannedByKind[Kind.NEEDS] ?: 0.0)

    /** Months until [remaining] is covered by [monthly]; null when it can never be reached. */
    fun monthsToGoal(remaining: Double, monthly: Double): Int? = when {
        remaining <= 0.0 -> 0
        monthly <= 0.0 -> null
        else -> ceil(remaining / monthly).toInt()
    }

    fun progress(saved: Double, target: Double) =
        if (target <= 0.0) 0f else (saved / target).coerceIn(0.0, 1.0).toFloat()

    // ---- Investing projection, same as Excel FV(rate/12, years*12, -monthly) ----
    fun futureValue(monthly: Double, annualPercent: Double, years: Int): Double {
        val r = annualPercent / 100.0 / 12.0
        val n = years * 12
        return if (r == 0.0) monthly * n else monthly * ((1 + r).pow(n) - 1) / r
    }

    fun projection(data: AppData, monthly: Double): List<ProjectionRow> =
        listOf(5, 10, 20, 30, 40).map { y ->
            ProjectionRow(
                years = y,
                age = data.investing.age.takeIf { it > 0 }?.plus(y),
                contributed = monthly * 12 * y,
                value = futureValue(monthly, data.investing.annualReturnPercent, y),
            )
        }

    /**
     * Reads an amount the way people type it on a phone: "12,50", "1 250" or a sum like "11.6+4.4+10".
     * Returns null when the text is not a valid non-negative amount.
     */
    fun parseAmount(text: String): Double? {
        val clean = text.replace(" ", "").replace(' ', ' ').replace(" ", "").replace(',', '.')
        if (clean.isEmpty()) return null
        val parts = clean.split('+')
        var total = 0.0
        for (p in parts) {
            if (p.isEmpty()) return null
            total += p.toDoubleOrNull()?.takeIf { it >= 0 } ?: return null
        }
        return total
    }
}
