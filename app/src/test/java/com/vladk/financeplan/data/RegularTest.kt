package com.vladk.financeplan.data

import com.vladk.financeplan.reminder.Reminders
import com.vladk.financeplan.ui.noteSuggestions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RegularTest {

    private val categories = listOf(
        Category(id = "rent", name = "Rent", kind = Kind.NEEDS),
        Category(id = "tv", name = "TV", kind = Kind.WANTS),
    )

    private fun data(vararg recurring: Recurring, expenses: List<Expense> = emptyList()) =
        AppData(categories = categories, expenses = expenses, recurring = recurring.toList())

    @Test
    fun aRepeatedExpenseStartsFromTheNextMonth() {
        val rent = Expense("e1", "rent", 300.0, "2026-11-01", "наем")
        val regular = Regular.from(rent)!!
        assertEquals(1, regular.day)
        assertEquals("2026-11", regular.lastAdded)
        assertEquals(300.0, regular.amount, 0.0)
        assertEquals("наем", regular.note)

        val d = data(regular, expenses = listOf(rent))
        // Still November: nothing more to add.
        assertSame(d, Regular.addDue(d, LocalDate.of(2026, 11, 30)))
        // 1 December: added once, dated the 1st.
        val december = Regular.addDue(d, LocalDate.of(2026, 12, 1))
        assertEquals(listOf("2026-11-01", "2026-12-01"), december.expenses.map { it.date })
        assertEquals("2026-12", december.recurring.single().lastAdded)
        assertEquals(300.0, december.expenses.last().amount, 0.0)
        assertEquals("наем", december.expenses.last().note)
        // Opening the app again the same month adds nothing.
        assertSame(december, Regular.addDue(december, LocalDate.of(2026, 12, 20)))
    }

    @Test
    fun aPaymentIsNotAddedBeforeItsDay() {
        val tv = Recurring("r1", "tv", 25.0, day = 15, lastAdded = "2026-11")
        val d = data(tv)
        assertSame(d, Regular.addDue(d, LocalDate.of(2026, 12, 14)))
        assertEquals(listOf("2026-12-15"), Regular.addDue(d, LocalDate.of(2026, 12, 15)).expenses.map { it.date })
    }

    @Test
    fun monthsMissedWhileTheAppWasClosedAreCaughtUp() {
        val tv = Recurring("r1", "tv", 25.0, day = 15, lastAdded = "2026-11")
        val result = Regular.addDue(data(tv), LocalDate.of(2027, 2, 20))
        assertEquals(listOf("2026-12-15", "2027-01-15", "2027-02-15"), result.expenses.map { it.date })
        assertEquals("2027-02", result.recurring.single().lastAdded)
        // … but at most a year back, however long the phone was in a drawer.
        val old = Recurring("r2", "tv", 25.0, day = 1, lastAdded = "2020-01")
        assertEquals(13, Regular.addDue(data(old), LocalDate.of(2027, 2, 20)).expenses.size)
    }

    @Test
    fun the31stFallsOnTheLastDayOfShorterMonths() {
        assertEquals(LocalDate.of(2027, 2, 28), Regular.dateIn(YearMonth.of(2027, 2), 31))
        assertEquals(LocalDate.of(2028, 2, 29), Regular.dateIn(YearMonth.of(2028, 2), 31))
        assertEquals(LocalDate.of(2026, 11, 30), Regular.dateIn(YearMonth.of(2026, 11), 31))
        val rent = Recurring("r1", "rent", 300.0, day = 31, lastAdded = "2027-01")
        assertEquals(listOf("2027-02-28"), Regular.addDue(data(rent), LocalDate.of(2027, 2, 28)).expenses.map { it.date })
    }

    @Test
    fun runningTwiceNeverAddsTheSamePaymentTwice() {
        val tv = Recurring("r1", "tv", 25.0, day = 1, lastAdded = "2026-11")
        val once = Regular.addDue(data(tv), LocalDate.of(2026, 12, 5))
        // Even if the "last added" mark were lost (e.g. an older backup restored over newer expenses).
        val markLost = once.copy(recurring = listOf(tv))
        val again = Regular.addDue(markLost, LocalDate.of(2026, 12, 5))
        assertEquals(1, again.expenses.size)
        assertEquals("2026-12", again.recurring.single().lastAdded)
    }

    @Test
    fun aPaymentWhoseCategoryWasDeletedIsLeftAlone() {
        val orphan = Recurring("r1", "gone", 10.0, day = 1, lastAdded = "2026-01")
        val d = data(orphan)
        assertSame(d, Regular.addDue(d, LocalDate.of(2026, 12, 5)))
    }

    @Test
    fun cleaningKeepsReminderAndRegularPaymentsValid() {
        val messy = AppData(
            settings = Settings(reminder = Reminder(enabled = true, hour = 99, minute = -5)),
            recurring = listOf(
                Recurring("r1", "rent", 300.0, day = 45, lastAdded = "soon"),
                Recurring("r1", "tv", 25.0, day = 0, lastAdded = "2026-11"),
                Recurring("r3", "tv", Double.NaN, day = 1),
            ),
        )
        val clean = Backup.clean(messy).data
        assertEquals(Reminder(enabled = true, hour = 23, minute = 0), clean.settings.reminder)
        assertEquals(listOf(31, 1), clean.recurring.map { it.day })
        assertEquals(listOf("", "2026-11"), clean.recurring.map { it.lastAdded })
        assertEquals(2, clean.recurring.map { it.id }.toSet().size)
        // And both survive a backup round trip.
        assertEquals(clean, Backup.parse(Backup.encode(clean)).data)
    }

    @Test
    fun reminderTimeIsLaterTodayOrTomorrow() {
        val evening = LocalDateTime.of(2026, 11, 13, 18, 0)
        assertEquals(LocalDateTime.of(2026, 11, 13, 20, 30), Reminders.nextTrigger(evening, 20, 30))
        // Exactly at (or after) the time: the next one is tomorrow, so it never fires twice in a row.
        assertEquals(LocalDateTime.of(2026, 11, 14, 20, 30), Reminders.nextTrigger(LocalDateTime.of(2026, 11, 13, 20, 30), 20, 30))
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), Reminders.nextTrigger(LocalDateTime.of(2026, 11, 30, 23, 59), 8, 0))
    }

    @Test
    fun reminderTextDependsOnWhatIsRecordedToday() {
        val today = LocalDate.of(2026, 11, 13)
        val empty = AppData(settings = Settings(language = "en", reminder = Reminder(enabled = true)))
        assertEquals("Nothing recorded yet today. Tap to add an expense.", Reminders.message(empty, today)!!.second)

        val some = empty.copy(expenses = listOf(Expense("a", "tv", 4.5, "2026-11-13"), Expense("b", "tv", 5.5, "2026-11-13"), Expense("c", "tv", 99.0, "2026-11-12")))
        val text = Reminders.message(some, today)!!.second
        assertTrue(text, text.startsWith("Recorded today: 10.00") && text.contains("(entries: 2)"))

        // "Only if nothing is recorded yet": silent on a day that has entries, still asks on an empty one.
        val quiet = Settings(language = "en", reminder = Reminder(enabled = true, skipIfRecorded = true))
        assertNull(Reminders.message(some.copy(settings = quiet), today))
        assertEquals("Did you record today's expenses?", Reminders.message(empty.copy(settings = quiet), today)!!.first)
    }

    @Test
    fun noteSuggestionsAreTheMostUsedNotesOfThatCategory() {
        val expenses = listOf(
            Expense("1", "food", 1.0, "2026-11-01", "Lidl"),
            Expense("2", "food", 1.0, "2026-11-02", "Billa"),
            Expense("3", "food", 1.0, "2026-11-03", " Lidl "),
            Expense("4", "food", 1.0, "2026-11-04", ""),
            Expense("5", "tv", 1.0, "2026-11-05", "Netflix"),
        )
        assertEquals(listOf("Lidl", "Billa"), noteSuggestions(expenses, "food"))
        assertEquals(listOf("Netflix"), noteSuggestions(expenses, "tv"))
        assertEquals(emptyList<String>(), noteSuggestions(expenses, "other"))
    }
}
