package com.vladk.financeplan.data

/**
 * A predefined category. The list is built from the "Finance Plan BG" spreadsheet (marked XLSX)
 * plus the essentials Monarch's budgeting guide recommends (gifts, travel, repairs, insurance …).
 * Presets carry no amounts – the user fills in the plan.
 */
data class Preset(
    val id: String,
    val emoji: String,
    val group: Group,
    val kind: Kind,
    val planType: PlanType,
    val bg: String,
    val en: String,
    val minusVouchers: Boolean = false,
)

object Presets {

    /** Bump when presets are added, so existing users can be offered the new ones. */
    const val VERSION = 1

    private val F = PlanType.FIXED
    private val P = PlanType.PERCENT

    val all: List<Preset> = listOf(
        // Housing & bills
        Preset("rent", "🏠", Group.HOUSING, Kind.NEEDS, F, "Наем / общежитие", "Rent / dorm"), // XLSX
        Preset("electricity", "💡", Group.HOUSING, Kind.NEEDS, P, "Ток и отопление", "Electricity & heating"), // XLSX
        Preset("water", "🚰", Group.HOUSING, Kind.NEEDS, P, "Вода", "Water"), // XLSX
        Preset("internet", "🌐", Group.HOUSING, Kind.NEEDS, F, "Интернет", "Internet"), // XLSX
        Preset("phone", "📱", Group.HOUSING, Kind.NEEDS, F, "Телефон", "Phone"), // XLSX
        Preset("household", "🧴", Group.HOUSING, Kind.NEEDS, P, "Консумативи и почистване", "Household supplies"), // XLSX
        // Food
        Preset("groceries", "🛒", Group.FOOD, Kind.NEEDS, P, "Хранителни стоки", "Groceries", minusVouchers = true), // XLSX
        Preset("dining", "☕", Group.FOOD, Kind.WANTS, P, "Ядене навън и кафе", "Restaurants & coffee"), // XLSX
        // Transport
        Preset("public_transport", "🚌", Group.TRANSPORT, Kind.NEEDS, F, "Градски транспорт", "Public transport"), // XLSX
        Preset("fuel", "⛽", Group.TRANSPORT, Kind.NEEDS, P, "Гориво", "Fuel"), // XLSX
        Preset("car_service", "🔧", Group.TRANSPORT, Kind.NEEDS, F, "Сервиз на автомобила", "Car service & repairs"),
        Preset("car_insurance", "🛡️", Group.TRANSPORT, Kind.NEEDS, F, "Застраховка, данък и винетка", "Car insurance, tax & vignette"),
        Preset("taxi", "🚕", Group.TRANSPORT, Kind.WANTS, F, "Такси", "Taxi & rides"),
        // Health & personal care
        Preset("medical", "💊", Group.HEALTH, Kind.NEEDS, P, "Лекарства, лекар и зъболекар", "Medicine, doctor & dentist"), // XLSX
        Preset("hygiene", "💇", Group.HEALTH, Kind.NEEDS, P, "Хигиена и фризьор", "Hygiene & haircut"), // XLSX
        Preset("sport", "🏋️", Group.HEALTH, Kind.WANTS, F, "Спорт и фитнес", "Sport & fitness"), // XLSX
        // Education
        Preset("education", "🎓", Group.EDUCATION, Kind.NEEDS, P, "Такси, книги и курсове", "Tuition, books & courses"), // XLSX
        // Shopping & fun
        Preset("entertainment", "🎉", Group.LIFESTYLE, Kind.WANTS, P, "Излизане и забавления", "Going out & entertainment"), // XLSX
        Preset("subscriptions", "📺", Group.LIFESTYLE, Kind.WANTS, F, "Абонаменти", "Subscriptions"), // XLSX
        Preset("clothing", "👕", Group.LIFESTYLE, Kind.WANTS, P, "Дрехи и обувки", "Clothing & shoes"), // XLSX
        Preset("electronics", "💻", Group.LIFESTYLE, Kind.WANTS, F, "Техника", "Electronics"),
        Preset("hobbies", "🎨", Group.LIFESTYLE, Kind.WANTS, F, "Хобита", "Hobbies"),
        // Irregular & occasional
        Preset("gifts", "🎁", Group.IRREGULAR, Kind.WANTS, F, "Подаръци и дарения", "Gifts & donations"),
        Preset("travel", "✈️", Group.IRREGULAR, Kind.WANTS, F, "Пътувания и почивки", "Travel & holidays"),
        Preset("home_repairs", "🛠️", Group.IRREGULAR, Kind.NEEDS, F, "Ремонти и обзавеждане", "Home repairs & furniture"),
        Preset("annual_fees", "📅", Group.IRREGULAR, Kind.NEEDS, F, "Годишни такси и застраховки", "Annual fees & insurance"),
        // Other
        Preset("bank_fees", "🏦", Group.OTHER, Kind.NEEDS, F, "Банкови такси", "Bank fees"),
        Preset("pets", "🐾", Group.OTHER, Kind.NEEDS, F, "Домашни любимци", "Pets"),
        Preset("misc", "📦", Group.OTHER, Kind.WANTS, F, "Други разходи", "Miscellaneous"),
        // Savings
        Preset("emergency", "🆘", Group.SAVINGS, Kind.SAVINGS, P, "Авариен фонд", "Emergency fund"), // XLSX
        Preset("short_term", "🎯", Group.SAVINGS, Kind.SAVINGS, P, "Краткосрочни цели", "Short-term goals"), // XLSX
        Preset("long_term", "🐷", Group.SAVINGS, Kind.SAVINGS, P, "Дългосрочни спестявания", "Long-term savings"), // XLSX
        Preset("reserve", "🧰", Group.SAVINGS, Kind.SAVINGS, P, "Резерв за нередовни разходи", "Reserve for irregular costs"), // XLSX
        // Investments
        Preset("etf", "📈", Group.INVESTMENTS, Kind.INVESTMENTS, P, "ETF / индексни фондове", "ETFs / index funds"), // XLSX
        Preset("pension", "👴", Group.INVESTMENTS, Kind.INVESTMENTS, P, "Пенсионен фонд", "Pension fund"), // XLSX
    )

    private val byId = all.associateBy { it.id }

    fun name(presetId: String, language: String): String? =
        byId[presetId]?.let { if (language == "en") it.en else it.bg }

    /** Stable ids, so a preset keeps the same id on every device and after re-adding. */
    fun toCategory(p: Preset) = Category(
        id = "preset-${p.id}",
        name = "",
        kind = p.kind,
        planType = p.planType,
        minusVouchers = p.minusVouchers,
        group = p.group,
        icon = p.emoji,
        presetId = p.id,
    )

    fun defaults(): List<Category> = all.map(::toCategory)

    fun missing(existing: List<Category>): List<Preset> {
        val have = existing.mapNotNull { it.presetId }.toSet()
        return all.filter { it.id !in have }
    }
}

/** The name to show: the user's own name, or the preset name in the current language. */
fun Category.label(language: String): String =
    name.ifBlank { presetId?.let { Presets.name(it, language) }.orEmpty() }

fun Category.emoji(): String = icon.ifBlank { group.emoji }
