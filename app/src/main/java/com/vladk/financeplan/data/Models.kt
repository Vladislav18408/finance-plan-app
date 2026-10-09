package com.vladk.financeplan.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** Everything the app stores. Starts empty; the user enters all values in the app. */
@Serializable
data class AppData(
    val version: Int = 1,
    val settings: Settings = Settings(),
    val income: Income = Income(),
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val emergency: EmergencyFund = EmergencyFund(),
    val investing: Investing = Investing(),
    /** Version of the predefined category list that was added on first start (0 = never). */
    val presetsVersion: Int = 0,
)

@Serializable
data class Settings(
    val language: String = "bg",
    val currency: String = "€",
)

/**
 * Monthly income. [salary] is the base every "% of salary" is calculated from.
 * Extra incomes are meant to be saved/invested in full; [investSharePercent] of them goes to investments.
 */
@Serializable
data class Income(
    val salary: Double = 0.0,
    val vouchers: Double = 0.0,
    val investSharePercent: Double = 50.0,
    val extras: List<ExtraIncome> = emptyList(),
)

@Serializable
data class ExtraIncome(
    val id: String = newId(),
    val name: String,
    val amount: Double,
)

/** The four buckets the overview and the 50/30/20 comparison are built from. */
@Serializable
enum class Kind { NEEDS, WANTS, SAVINGS, INVESTMENTS;
    /** Savings and investments both count as "Savings" in the 50/30/20 rule. */
    val isSaving: Boolean get() = this == SAVINGS || this == INVESTMENTS
}

@Serializable
enum class PlanType { FIXED, PERCENT }

/**
 * Category groups (like Monarch's "Food & Dining", "Auto & Transport" …). They organise the budget
 * and the charts; [defaultKind] is suggested when a new category is put into the group.
 */
@Serializable
enum class Group(val emoji: String, val defaultKind: Kind) {
    HOUSING("🏠", Kind.NEEDS),
    FOOD("🍽️", Kind.NEEDS),
    TRANSPORT("🚗", Kind.NEEDS),
    HEALTH("💊", Kind.NEEDS),
    EDUCATION("🎓", Kind.NEEDS),
    LIFESTYLE("🛍️", Kind.WANTS),
    IRREGULAR("🎁", Kind.WANTS),
    OTHER("📦", Kind.WANTS),
    SAVINGS("🐷", Kind.SAVINGS),
    INVESTMENTS("📈", Kind.INVESTMENTS),
}

/**
 * A budget line. FIXED -> [amount] per month. PERCENT -> [percent] of the salary,
 * optionally minus food vouchers (for groceries) when [minusVouchers] is set.
 *
 * Predefined categories have a [presetId] and an empty [name]; their name is then shown in the
 * app's current language (see Presets.kt). Renaming one stores the new [name].
 */
@Serializable
data class Category(
    val id: String = newId(),
    val name: String,
    val kind: Kind,
    val planType: PlanType = PlanType.FIXED,
    val amount: Double = 0.0,
    val percent: Double = 0.0,
    val minusVouchers: Boolean = false,
    val note: String = "",
    val group: Group = Group.OTHER,
    val icon: String = "",
    val presetId: String? = null,
)

/** One spending (or, for saving kinds, one deposit). [date] is ISO yyyy-MM-dd. */
@Serializable
data class Expense(
    val id: String = newId(),
    val categoryId: String,
    val amount: Double,
    val date: String,
    val note: String = "",
)

@Serializable
data class Goal(
    val id: String = newId(),
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val monthly: Double = 0.0,
    val note: String = "",
)

@Serializable
data class EmergencyFund(
    val months: Int = 3,
    val saved: Double = 0.0,
    val monthly: Double = 0.0,
)

@Serializable
data class Investing(
    val age: Int = 0,
    val annualReturnPercent: Double = 6.0,
)

fun newId(): String = UUID.randomUUID().toString()
