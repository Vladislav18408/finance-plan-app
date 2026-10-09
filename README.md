# Finance Plan – personal budget app for Android

A simple Android app (Kotlin + Jetpack Compose) for planning a monthly budget and tracking what you
actually spend. It is a mobile version of the *Finance Plan BG* spreadsheet: the same ideas
(plan as a % of the salary, needs / wants / savings, 50/30/20 rule, emergency fund, savings goals,
investing projection), but made for quick use on the phone.

The design follows the ideas of [Monarch](https://www.monarch.com/): category groups with emoji,
budget progress bars, spending-over-time charts and reports.

**No personal data is built in.** The app starts with 35 ready-made categories (see below) but
no amounts – you enter your income, your plan, every expense and your goals yourself.
All data stays on your phone.

Interface language: **Bulgarian** (default) or **English** – switch in *Settings*.

## Features

| Screen | What you do there |
|---|---|
| **Overview** (Преглед) | The month at a glance: spent vs. planned, saved + invested, unallocated money, **this week** (Mon–Sun) against the weekly guide, a **line chart of spending through the month** (this month vs. last month vs. plan), the budget by group with a progress bar + status per category, and the latest expenses. |
| **Expenses** (Разходи) | Everything you recorded, grouped by day with day totals. Filter by category. **Tap any expense to change or delete it.** |
| **Reports** (Отчети) | Three tabs: **Week** – Monday to Sunday: total vs. weekly guide, comparison with the week before, daily bar chart (tap a bar to see that day), spending by category, the week's expenses. **Month** – donut chart by group, cash flow (income → spent / saved / left), spending through the month, top categories. **Trends** – spending and savings for the last 6 months, averages, average per category. |
| **Budget** (Бюджет) | Salary, food vouchers and extra incomes. The 50/30/20 split as a bar and a table. All categories by group – tap one to set a fixed amount or a % of the salary, rename it, change its emoji, group or type. |
| **Goals** (Цели) | Emergency fund (N months of planned needs), savings goals with progress, and an investing projection for 5–40 years. |
| **Settings** (Настройки) | Language, currency (€, лв., $, £), save/restore a backup file, delete all data. |

Adding an expense is one button (**+ Add expense**): type the amount, tap a category
(recently used ones are on top), done. The **+** inside the amount field adds up a receipt,
e.g. `11,60+4,40+10`.

## Predefined categories

Built from the *Finance Plan BG* spreadsheet (★) plus the essentials Monarch's budgeting guide
recommends. All can be renamed, re-grouped or deleted; *Budget → Add the suggested categories*
brings deleted ones back.

| Group | Categories |
|---|---|
| 🏠 Housing & bills | Rent / dorm ★, Electricity & heating ★, Water ★, Internet ★, Phone ★, Household supplies ★ |
| 🍽️ Food | Groceries ★ (food vouchers are subtracted), Restaurants & coffee ★ |
| 🚗 Transport | Public transport ★, Fuel ★, Car service & repairs, Car insurance / tax / vignette, Taxi |
| 💊 Health & personal care | Medicine, doctor & dentist ★, Hygiene & haircut ★, Sport & fitness ★ |
| 🎓 Education | Tuition, books & courses ★ |
| 🛍️ Shopping & fun | Going out ★, Subscriptions ★, Clothing & shoes ★, Electronics, Hobbies |
| 🎁 Irregular costs | Gifts & donations, Travel & holidays, Home repairs & furniture, Annual fees & insurance |
| 📦 Other | Bank fees, Pets, Miscellaneous |
| 🐷 Savings | Emergency fund ★, Short-term goals ★, Long-term savings ★, Reserve for irregular costs ★ |
| 📈 Investments | ETFs / index funds ★, Pension fund ★ |

## Install on your phone

**Option A – Android Studio (recommended)**
1. Open Android Studio → *Open* → select this folder.
2. Wait for the Gradle sync to finish.
3. On the phone: *Settings → About phone →* tap *Build number* 7 times, then enable
   *Developer options → USB debugging*.
4. Connect the phone with USB, choose it in the device list and press **Run ▶**.

**Option B – APK file**
1. In Android Studio: *Build → Generate App Bundles or APKs → Build APK(s)*
   (or run `gradlew assembleDebug`).
2. Copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it
   (allow "Install unknown apps" when asked).

## Back up your data

The data lives only inside the app on your phone. Uninstalling the app deletes it.
Use *Settings → Save backup* regularly and keep the file somewhere safe (e.g. Google Drive).
*Restore backup* loads such a file again – also on a new phone.

## Project

- Kotlin, Jetpack Compose, Material 3
- Android 8.0 (API 26) or newer
- Build: Android Gradle Plugin 9.4, Gradle 9.8 (wrapper included)

Technical details – structure, data model, all formulas – are in
[docs/DOCUMENTATION.md](docs/DOCUMENTATION.md). A step-by-step guide for using the app is in
[docs/USER_GUIDE.md](docs/USER_GUIDE.md).
