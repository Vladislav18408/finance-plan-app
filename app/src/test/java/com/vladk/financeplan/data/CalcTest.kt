package com.vladk.financeplan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalcTest {

    private val income = Income(salary = 2000.0, vouchers = 100.0)

    private fun category(id: String, kind: Kind, amount: Double = 0.0, percent: Double = 0.0, minusVouchers: Boolean = false) =
        Category(
            id = id, name = id, kind = kind,
            planType = if (percent > 0) PlanType.PERCENT else PlanType.FIXED,
            amount = amount, percent = percent, minusVouchers = minusVouchers,
        )

    private val data = AppData(
        income = income,
        categories = listOf(
            category("rent", Kind.NEEDS, amount = 300.0),
            category("food", Kind.NEEDS, percent = 14.0, minusVouchers = true),
            category("fun", Kind.WANTS, percent = 4.0),
            category("fund", Kind.SAVINGS, percent = 10.0),
            category("etf", Kind.INVESTMENTS, percent = 10.0),
        ),
        expenses = listOf(
            Expense("1", "rent", 300.0, "2026-11-01"),
            Expense("2", "food", 50.0, "2026-11-03"),
            Expense("3", "fun", 90.0, "2026-11-04"),
            Expense("4", "fund", 200.0, "2026-11-05"),
            Expense("5", "food", 70.0, "2026-10-20"),
        ),
    )

    @Test
    fun plannedAmounts() {
        assertEquals(300.0, Calc.planned(data.categories[0], income), 1e-9)
        // 14% of 2000 = 280, minus 100 in vouchers
        assertEquals(180.0, Calc.planned(data.categories[1], income), 1e-9)
        assertEquals(80.0, Calc.planned(data.categories[2], income), 1e-9)
        // Vouchers never push a plan below zero.
        assertEquals(0.0, Calc.planned(category("x", Kind.NEEDS, percent = 1.0, minusVouchers = true), income), 1e-9)
    }

    @Test
    fun monthSummaryCountsOnlyThatMonth() {
        val s = Calc.summary(data, YearMonth.of(2026, 11))
        assertEquals(440.0, s.actualSpending, 1e-9)   // 300 + 50 + 90; October's 70 is not included
        assertEquals(200.0, s.actualSaving, 1e-9)
        assertEquals(560.0, s.plannedSpending, 1e-9)  // 300 + 180 + 80
        assertEquals(400.0, s.plannedSaving, 1e-9)
        assertEquals(1040.0, Calc.unallocated(data, s), 1e-9)

        val status = s.lines.associate { it.category.id to it.status }
        assertEquals(Status.WITHIN_PLAN, status["rent"])
        assertEquals(Status.OVER_BUDGET, status["fun"])
        assertEquals(Status.GOAL_REACHED, status["fund"])
        assertEquals(Status.NO_DATA, status["etf"])
    }

    @Test
    fun savingsGoalIsReachedDespiteRounding() {
        // Three deposits of 33.33 + one of 0.01 add up to 100 only approximately in binary.
        assertEquals(Status.GOAL_REACHED, Calc.status(Kind.SAVINGS, 100.0, 33.33 + 33.33 + 33.33 + 0.01))
        assertEquals(Status.BELOW_GOAL, Calc.status(Kind.SAVINGS, 100.0, 99.0))
    }

    @Test
    fun rule503020UsesSalaryAsBase() {
        val rows = Calc.rule503020(data, Calc.summary(data, YearMonth.of(2026, 11)))
        assertEquals(listOf(50.0, 30.0, 20.0), rows.map { it.rulePercent })
        assertEquals(24.0, rows[0].plannedPercent, 1e-9)  // 480 / 2000
        assertEquals(4.0, rows[1].plannedPercent, 1e-9)
        assertEquals(20.0, rows[2].plannedPercent, 1e-9)  // savings + investments
    }

    @Test
    fun dailyAllowance() {
        val nov = YearMonth.of(2026, 11)
        // 12 Nov: 19 days left including today.
        val a = Calc.dailyAllowance(190.0, nov, LocalDate.of(2026, 11, 12))!!
        assertEquals(19, a.daysLeft)
        assertEquals(10.0, a.perDay, 1e-9)
        assertEquals(1, Calc.dailyAllowance(5.0, nov, LocalDate.of(2026, 11, 30))!!.daysLeft)
        assertNull("nothing left to spend", Calc.dailyAllowance(0.0, nov, LocalDate.of(2026, 11, 12)))
        assertNull("another month", Calc.dailyAllowance(100.0, nov, LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun goals() {
        assertEquals(0, Calc.monthsToGoal(0.0, 50.0))
        assertEquals(12, Calc.monthsToGoal(900.0, 80.0))
        assertNull(Calc.monthsToGoal(900.0, 0.0))
        assertEquals(0.25f, Calc.progress(300.0, 1200.0), 1e-6f)
        assertEquals(1f, Calc.progress(2000.0, 1200.0), 1e-6f)
        assertEquals(0f, Calc.progress(100.0, 0.0), 1e-6f)
    }

    @Test
    fun futureValueMatchesExcelFV() {
        // =FV(6%/12, 10*12, -200) = 32 775.87
        assertEquals(32775.87, Calc.futureValue(200.0, 6.0, 10), 0.01)
        assertEquals(24000.0, Calc.futureValue(200.0, 0.0, 10), 1e-9)
    }

    @Test
    fun parseAmountAcceptsWhatPeopleType() {
        assertEquals(12.5, Calc.parseAmount("12,50")!!, 0.0)
        assertEquals(12.5, Calc.parseAmount("12.50")!!, 0.0)
        assertEquals(1250.0, Calc.parseAmount("1 250")!!, 0.0)
        assertEquals(1250.0, Calc.parseAmount("1" + Char(0x00A0) + "250")!!, 0.0)
        assertEquals(0.5, Calc.parseAmount(",5")!!, 0.0)
        assertEquals(7.0, Calc.parseAmount("7.")!!, 0.0)
        // A receipt added up is stored as an exact number of cents.
        assertEquals(26.0, Calc.parseAmount("11.6+4.4+10")!!, 0.0)
        assertEquals(11.5, Calc.parseAmount("4,40 + 7,10")!!, 0.0)
    }

    @Test
    fun parseAmountRejectsEverythingElse() {
        listOf("", " ", "abc", "-5", "5+", "+5", "1.2.3", "1e5", "NaN", "Infinity", "0x10", "5-2").forEach {
            assertNull("\"$it\" should not be an amount", Calc.parseAmount(it))
        }
    }
}
