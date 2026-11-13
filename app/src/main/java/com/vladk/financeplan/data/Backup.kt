package com.vladk.financeplan.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Data that is safe to show, plus how many unreadable entries had to be left out. */
data class Cleaned(val data: AppData, val skipped: Int)

/**
 * Reading and checking saved data. Everything that comes from a file (the app's own file or a
 * backup the user picked) goes through [clean], so the rest of the app can rely on valid dates,
 * finite numbers and unique ids.
 */
object Backup {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(data: AppData): String = json.encodeToString(AppData.serializer(), data)

    /**
     * Parses a backup file. Throws when the text is not a Finance Plan backup. Every field of
     * [AppData] has a default, so any JSON object would otherwise be accepted as an "empty" backup
     * and wipe the current data; a real backup always has the category and expense lists.
     */
    fun parse(text: String): Cleaned {
        val root = json.parseToJsonElement(text.trim().removePrefix(BOM))
        require(root is JsonObject && root["categories"] is JsonArray && root["expenses"] is JsonArray) {
            "Not a Finance Plan backup"
        }
        return clean(json.decodeFromJsonElement(AppData.serializer(), root))
    }

    /** "2026-11-05" for anything that is clearly a date ("2026-11-5", "2026-11-05T10:30"), else null. */
    fun isoDate(text: String): String? {
        val m = DATE.find(text.trim()) ?: return null
        val (y, mo, d) = m.destructured
        return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toString() }.getOrNull()
    }

    private val DATE = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})(?:$|[T ])""")

    private val MONTH = Regex("""\d{4}-(0[1-9]|1[0-2])""")

    private fun Double.orZero() = if (isFinite()) this else 0.0
    private fun Double.amount() = if (isFinite() && this > 0.0) this else 0.0

    /** Gives every item its own non-empty id, so editing or deleting one never touches another. */
    private fun <T> List<T>.uniqueIds(id: (T) -> String, withId: (T, String) -> T): List<T> {
        val seen = HashSet<String>()
        return map { item -> if (id(item).isNotBlank() && seen.add(id(item))) item else withId(item, newId().also(seen::add)) }
    }

    fun clean(data: AppData): Cleaned {
        var skipped = 0
        val expenses = data.expenses.mapNotNull { e ->
            val date = isoDate(e.date)
            if (date == null || !e.amount.isFinite()) {
                skipped++
                null
            } else if (date == e.date) e else e.copy(date = date)
        }.uniqueIds({ it.id }) { e, id -> e.copy(id = id) }

        val categories = data.categories
            .map { it.copy(amount = it.amount.amount(), percent = it.percent.amount()) }
            .uniqueIds({ it.id }) { c, id -> c.copy(id = id) }

        val income = data.income.copy(
            salary = data.income.salary.amount(),
            vouchers = data.income.vouchers.amount(),
            investSharePercent = data.income.investSharePercent.orZero().coerceIn(0.0, 100.0),
            extras = data.income.extras
                .map { it.copy(amount = it.amount.amount()) }
                .uniqueIds({ it.id }) { x, id -> x.copy(id = id) },
        )

        val goals = data.goals
            .map { it.copy(target = it.target.amount(), saved = it.saved.amount(), monthly = it.monthly.amount()) }
            .uniqueIds({ it.id }) { g, id -> g.copy(id = id) }

        val settings = Settings(
            language = if (data.settings.language == "en") "en" else "bg",
            currency = data.settings.currency.trim().take(4).ifEmpty { "€" },
            reminder = data.settings.reminder.copy(
                hour = data.settings.reminder.hour.coerceIn(0, 23),
                minute = data.settings.reminder.minute.coerceIn(0, 59),
            ),
        )

        // A regular payment without a usable amount could never be added; its day and month are repaired.
        val recurring = data.recurring
            .filter { it.amount.isFinite() && it.amount > 0.0 }
            .map { it.copy(day = it.day.coerceIn(1, 31), lastAdded = if (MONTH.matches(it.lastAdded)) it.lastAdded else "") }
            .uniqueIds({ it.id }) { r, id -> r.copy(id = id) }

        return Cleaned(
            data.copy(
                settings = settings,
                income = income,
                categories = categories,
                expenses = expenses,
                goals = goals,
                recurring = recurring,
                emergency = data.emergency.copy(
                    months = data.emergency.months.coerceAtLeast(0),
                    saved = data.emergency.saved.amount(),
                    monthly = data.emergency.monthly.amount(),
                ),
                investing = data.investing.copy(
                    age = data.investing.age.coerceAtLeast(0),
                    annualReturnPercent = data.investing.annualReturnPercent.orZero().coerceIn(0.0, 100.0),
                ),
            ),
            skipped,
        )
    }

    /**
     * All expenses as a spreadsheet-friendly CSV, oldest first. Bulgarian Excel expects ";" between
     * columns and a decimal comma; English Excel expects "," and a decimal point. The text starts
     * with a byte-order mark so Excel reads Cyrillic correctly.
     */
    fun expensesCsv(
        data: AppData,
        language: String,
        header: List<String>,
        kindName: (Kind) -> String,
        groupName: (Group) -> String,
        unknownCategory: String,
    ): String {
        val bulgarian = language != "en"
        val sep = if (bulgarian) ";" else ","
        fun cell(text: String): String =
            if (text.any { it == '"' || it == '\n' || it == '\r' } || sep in text) {
                "\"" + text.replace("\"", "\"\"") + "\""
            } else text
        fun number(v: Double): String {
            val plain = java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            return if (bulgarian) plain.replace('.', ',') else plain
        }
        val cats = data.categories.associateBy { it.id }
        val out = StringBuilder(BOM)
        out.append(header.joinToString(sep) { cell(it) }).append("\r\n")
        data.expenses.sortedBy { it.date }.forEach { e ->
            val c = cats[e.categoryId]
            val row = listOf(
                e.date,
                c?.label(language)?.ifBlank { null } ?: unknownCategory,
                c?.let { groupName(it.group) }.orEmpty(),
                c?.let { kindName(it.kind) }.orEmpty(),
                number(e.amount),
                e.note,
            )
            // A leading = + - @ would be run as a formula by spreadsheet apps; keep notes as text.
            out.append(row.mapIndexed { i, t -> cell(if (i == 5 && t.firstOrNull() in FORMULA_START) "'$t" else t) }.joinToString(sep))
                .append("\r\n")
        }
        return out.toString()
    }

    private val FORMULA_START = setOf('=', '+', '-', '@')

    /** Byte-order mark: tells Excel the file is UTF-8, and may start a file saved by Windows tools. */
    private val BOM = Char(0xFEFF).toString()
}
