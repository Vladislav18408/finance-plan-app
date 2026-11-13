package com.vladk.financeplan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    private val data = AppData(
        income = Income(salary = 2000.0, extras = listOf(ExtraIncome("x1", "Стипендия", 100.0))),
        categories = Presets.defaults(),
        expenses = listOf(
            Expense("e1", "preset-groceries", 12.5, "2026-11-12", "Lidl; хляб"),
            Expense("e2", "preset-rent", 300.0, "2026-11-01"),
            Expense("e3", "deleted-category", 7.0, "2026-10-30", "=SUM(A1)"),
        ),
        goals = listOf(Goal("g1", "Лаптоп", 1200.0, 300.0, 80.0)),
        presetsVersion = Presets.VERSION,
    )

    @Test
    fun aBackupComesBackUnchanged() {
        val restored = Backup.parse(Backup.encode(data))
        assertEquals(data, restored.data)
        assertEquals(0, restored.skipped)
    }

    @Test
    fun aFileSavedWithAByteOrderMarkStillLoads() {
        assertEquals(data, Backup.parse(Char(0xFEFF) + Backup.encode(data)).data)
    }

    @Test
    fun otherFilesAreNotMistakenForAnEmptyBackup() {
        // Each of these used to be accepted as "a backup with nothing in it", wiping the data.
        listOf("{}", """{"name":"something else"}""", "[]", "42", "", "not json", """{"categories":{},"expenses":[]}""").forEach {
            assertThrows("should be rejected: $it", Exception::class.java) { Backup.parse(it) }
        }
    }

    @Test
    fun unknownFieldsFromANewerVersionAreIgnored() {
        val text = """{"version":9,"somethingNew":true,"categories":[],"expenses":[{"id":"a","categoryId":"c","amount":5.0,"date":"2026-01-02","tags":["x"]}]}"""
        assertEquals(listOf(Expense("a", "c", 5.0, "2026-01-02")), Backup.parse(text).data.expenses)
    }

    @Test
    fun isoDate() {
        assertEquals("2026-11-05", Backup.isoDate("2026-11-05"))
        assertEquals("2026-11-05", Backup.isoDate("2026-11-5"))
        assertEquals("2026-11-05", Backup.isoDate(" 2026-11-05T10:30:00 "))
        listOf("", "yesterday", "05.11.2026", "2026-13-01", "2026-02-30", "2026-11", "2026-11-051").forEach {
            assertNull("\"$it\" is not a date", Backup.isoDate(it))
        }
    }

    @Test
    fun cleaningRepairsWhatItCanAndSkipsTheRest() {
        val messy = data.copy(
            settings = Settings(language = "de", currency = "   "),
            income = Income(salary = Double.NaN, vouchers = -5.0, investSharePercent = 250.0),
            expenses = listOf(
                Expense("e1", "preset-groceries", 10.0, "2026-11-5"),          // date is repaired
                Expense("e1", "preset-groceries", 20.0, "2026-11-06"),         // same id twice
                Expense("", "preset-groceries", 30.0, "2026-11-07"),           // no id
                Expense("e4", "preset-groceries", 40.0, "someday"),            // skipped
                Expense("e5", "preset-groceries", Double.NaN, "2026-11-08"),   // skipped
            ),
            emergency = EmergencyFund(months = -3, saved = Double.POSITIVE_INFINITY),
        )
        val (clean, skipped) = Backup.clean(messy)

        assertEquals(2, skipped)
        assertEquals(listOf("2026-11-05", "2026-11-06", "2026-11-07"), clean.expenses.map { it.date })
        assertEquals(listOf(10.0, 20.0, 30.0), clean.expenses.map { it.amount })
        assertEquals("every expense has its own id", 3, clean.expenses.map { it.id }.toSet().size)
        assertTrue(clean.expenses.none { it.id.isBlank() })
        assertEquals("e1", clean.expenses[0].id)

        assertEquals(Settings(language = "bg", currency = "€"), clean.settings)
        assertEquals(0.0, clean.income.salary, 0.0)
        assertEquals(0.0, clean.income.vouchers, 0.0)
        assertEquals(100.0, clean.income.investSharePercent, 0.0)
        assertEquals(EmergencyFund(months = 0, saved = 0.0), clean.emergency)
        // Clean data passes through untouched.
        assertEquals(Cleaned(data, 0), Backup.clean(data))
    }

    @Test
    fun firstStartGetsThePredefinedCategories() {
        val fresh = Repository.withPresets(AppData())
        assertEquals(Presets.all.size, fresh.categories.size)
        assertEquals(Presets.VERSION, fresh.presetsVersion)
        // Someone who deleted every category keeps an empty list.
        val emptied = AppData(presetsVersion = Presets.VERSION)
        assertEquals(emptied, Repository.withPresets(emptied))
    }

    @Test
    fun presetIdsAreUnique() {
        assertEquals(Presets.all.size, Presets.all.map { it.id }.toSet().size)
    }

    private fun csv(language: String) = Backup.expensesCsv(
        data = data,
        language = language,
        header = listOf("Date", "Category", "Group", "Type", "Amount", "Note"),
        kindName = { it.name },
        groupName = { it.name },
        unknownCategory = "Deleted",
    )

    @Test
    fun csvForBulgarianExcel() {
        val lines = csv("bg").removePrefix(Char(0xFEFF).toString()).trimEnd().split("\r\n")
        assertEquals("Date;Category;Group;Type;Amount;Note", lines[0])
        // Oldest first; a deleted category keeps its row; a note that looks like a formula is kept as text.
        assertEquals("2026-10-30;Deleted;;;7,00;'=SUM(A1)", lines[1])
        assertEquals("2026-11-01;Наем / общежитие;HOUSING;NEEDS;300,00;", lines[2])
        // The note contains the separator, so it is quoted.
        assertEquals("2026-11-12;Хранителни стоки;FOOD;NEEDS;12,50;\"Lidl; хляб\"", lines[3])
        assertEquals(4, lines.size)
    }

    @Test
    fun csvForEnglishExcel() {
        val text = csv("en")
        assertTrue("starts with a byte-order mark so Excel reads it as UTF-8", text.startsWith(Char(0xFEFF)))
        val lines = text.trimEnd().split("\r\n")
        assertEquals("2026-11-01,Rent / dorm,HOUSING,NEEDS,300.00,", lines[2])
        assertEquals("2026-11-12,Groceries,FOOD,NEEDS,12.50,Lidl; хляб", lines[3])
    }
}
