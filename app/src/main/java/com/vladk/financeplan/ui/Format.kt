package com.vladk.financeplan.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Number, money and date formatting for the chosen language and currency. */
class Fmt(private val strings: Strings, private val currency: String) {

    private val locale: Locale = if (strings.code == "en") Locale.UK else Locale.forLanguageTag("bg-BG")

    private val money2 = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    private val whole = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 0 }
    private val pct1 = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    private val plain = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 2
    }

    fun money(v: Double) = "${money2.format(v)} $currency"
    fun moneyWhole(v: Double) = "${whole.format(v)} $currency"

    /** Plain whole number for chart axes ("1 200"). */
    fun number(v: Double): String = whole.format(v)
    fun pct(v: Double) = "${pct1.format(v)}%"

    /** Pct with an explicit sign, for "difference from the rule" columns. */
    fun signedPct(v: Double) = (if (v > 0.05) "+" else "") + pct(v)

    /** Value to pre-fill an input field with; empty for 0 so the hint shows. */
    fun input(v: Double) = if (v == 0.0) "" else plain.format(v)

    fun month(m: YearMonth): String {
        val name = m.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return name.replaceFirstChar { it.titlecase(locale) } + " " + m.year
    }

    fun day(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> strings.today
            today.minusDays(1) -> strings.yesterday
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
                .replaceFirstChar { it.titlecase(locale) }
        }
    }

    fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun dayMonth(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM", locale))

    /** "6 – 12 окт 2026" (or "29 сеп – 5 окт 2026" when the week spans two months). */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        val left = if (start.month == end.month) start.dayOfMonth.toString() else dayMonth(start)
        return "$left – ${shortDate(end)}"
    }

    /** Two-letter weekday for chart labels: "пн", "Mo" … */
    fun weekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(2)
            .replaceFirstChar { it.titlecase(locale) }

    fun monthShort(m: YearMonth): String =
        m.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale).take(3)
            .replaceFirstChar { it.titlecase(locale) }
}

val LocalFmt = staticCompositionLocalOf { Fmt(Bulgarian, "€") }
