package com.vladk.financeplan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ReportsTest {

    private val data = AppData(
        income = Income(salary = 2600.0),
        categories = listOf(
            Category(id = "food", name = "Food", kind = Kind.NEEDS, amount = 400.0, group = Group.FOOD),
            Category(id = "fun", name = "Fun", kind = Kind.WANTS, amount = 120.0, group = Group.LIFESTYLE),
            Category(id = "fund", name = "Fund", kind = Kind.SAVINGS, amount = 200.0, group = Group.SAVINGS),
        ),
        expenses = listOf(
            Expense("a", "food", 20.0, "2026-11-02"),  // Monday of the week before
            Expense("b", "food", 30.0, "2026-11-09"),  // Monday
            Expense("c", "fun", 15.0, "2026-11-09"),
            Expense("d", "fund", 200.0, "2026-11-10"), // a deposit, not spending
            Expense("e", "gone", 5.0, "2026-11-12"),   // its category was deleted
            Expense("f", "food", 40.0, "2026-11-15"),  // Sunday
            Expense("g", "food", 99.0, "2026-11-16"),  // next week
            Expense("h", "food", 300.0, "2026-10-31"),
        ),
    )

    @Test
    fun weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 9), Reports.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(LocalDate.of(2026, 11, 9), Reports.weekStart(LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 10, 26), Reports.weekStart(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun weekReport() {
        val r = Reports.week(data, LocalDate.of(2026, 11, 9))
        assertEquals(listOf(45.0, 0.0, 0.0, 5.0, 0.0, 0.0, 40.0), r.spentByDay)
        assertEquals(90.0, r.spent, 1e-9)
        assertEquals(200.0, r.saved, 1e-9)
        assertEquals(20.0, r.previousSpent, 1e-9)
        assertEquals(520.0 * 12 / 52, r.guide, 1e-9)  // planned needs + wants only
        assertEquals(listOf("f", "e", "d", "c", "b"), r.expenses.map { it.id })
        // Biggest category first; the deleted category is still counted as spending.
        assertEquals(listOf("food" to 70.0, "fun" to 15.0, null to 5.0), r.byCategory.map { it.first?.id to it.second })
    }

    @Test
    fun betweenIncludesBothEnds() {
        val ids = Reports.between(data, LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2)).map { it.id }
        assertEquals(listOf("a", "h"), ids.sorted())
    }

    @Test
    fun newestFirstPutsTheLatestEntryOfADayOnTop() {
        assertEquals(listOf("g", "f", "e", "d", "c", "b", "a", "h"), Reports.newestFirst(data.expenses).map { it.id })
    }

    @Test
    fun cumulativeStopsAtToday() {
        val line = Reports.cumulative(data, YearMonth.of(2026, 11), today = LocalDate.of(2026, 11, 12))
        assertEquals(30, line.size)
        assertEquals(0.0, line[0]!!, 1e-9)
        assertEquals(20.0, line[1]!!, 1e-9)
        assertEquals(65.0, line[8]!!, 1e-9)    // 9 Nov: + 30 + 15
        assertEquals(65.0, line[9]!!, 1e-9)    // the deposit on the 10th is not spending
        assertEquals(70.0, line[11]!!, 1e-9)
        assertNull(line[12])
        // A finished month runs to its last day.
        assertEquals(300.0, Reports.cumulative(data, YearMonth.of(2026, 10), LocalDate.of(2026, 11, 12)).last()!!, 1e-9)
    }

    @Test
    fun groupsAndMonthTotals() {
        val nov = Reports.monthExpenses(data, YearMonth.of(2026, 11))
        assertEquals(
            listOf(Group.FOOD to 189.0, Group.LIFESTYLE to 15.0, Group.OTHER to 5.0),
            Reports.byGroup(data, Reports.spending(data, nov)),
        )
        val totals = Reports.monthTotals(data, YearMonth.of(2026, 11), 3)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), totals.map { it.month })
        assertEquals(listOf(0.0, 300.0, 209.0), totals.map { it.spent })
        assertEquals(listOf(0.0, 0.0, 200.0), totals.map { it.saved })
    }

    @Test
    fun averagesLeaveOutEmptyMonthsAndTheMonthInProgress() {
        val nov = YearMonth.of(2026, 11)
        val totals = Reports.monthTotals(data, nov, 6)
        // June–September are empty, November is still running: only October counts.
        assertEquals(listOf(YearMonth.of(2026, 10)), Reports.averagedMonths(totals, current = nov).map { it.month })
        // Looking back from a later date, both months are complete.
        assertEquals(2, Reports.averagedMonths(totals, current = YearMonth.of(2027, 1)).size)
        // The very first month of use is averaged as it is rather than not at all.
        val firstMonth = Reports.monthTotals(data.copy(expenses = data.expenses.filter { it.date >= "2026-11-01" }), nov, 6)
        assertEquals(listOf(nov), Reports.averagedMonths(firstMonth, current = nov).map { it.month })
        // No data anywhere: still some months to divide by, so the average is a plain zero.
        assertEquals(5, Reports.averagedMonths(Reports.monthTotals(AppData(), nov, 6), current = nov).size)
    }
}
