# Technical documentation

## 1. Overview

Finance Plan is a single-activity Android app written in Kotlin with Jetpack Compose (Material 3).
It has no server, no account and no network permission: all data is stored in one JSON file in the
app's private storage. The app ships with **no personal data**: on first start it creates
35 predefined categories (no amounts); the user enters income, plan, expenses and goals.

| Item | Value |
|---|---|
| Package | `com.vladk.financeplan` |
| Min / target SDK | 26 (Android 8.0) / 36 |
| Language | Kotlin (AGP 9 built-in Kotlin, Kotlin 2.4) |
| UI | Jetpack Compose, Material 3, Compose BOM 2024.12.01 |
| Storage | `kotlinx.serialization` JSON file |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.8.1 (wrapper) |

## 2. Project structure

```
Finance App/
├─ build.gradle.kts, settings.gradle.kts, gradle.properties   Gradle setup
├─ gradle/wrapper/                                             Gradle wrapper (gradlew / gradlew.bat)
├─ docs/                                                       This documentation + user guide
└─ app/
   ├─ build.gradle.kts                                         App module: SDK levels, dependencies
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ res/                                                  App name, theme, launcher icon
      └─ java/com/vladk/financeplan/
         ├─ MainActivity.kt                 Entry point; hosts the Compose UI
         ├─ data/
         │  ├─ Models.kt                    Data classes saved to disk (incl. Group enum)
         │  ├─ Presets.kt                   The 35 predefined categories (BG + EN names)
         │  ├─ Repository.kt                Loads/saves the JSON file, seeds presets, exposes a StateFlow
         │  ├─ Backup.kt                    Reads and checks saved data and backups; CSV export
         │  ├─ Regular.kt                   Regular (monthly) payments: which are due, adding them
         │  ├─ Calc.kt                      Budget formulas (pure functions)
         │  └─ Reports.kt                   Weekly / monthly / trend report formulas
         ├─ reminder/Reminders.kt           Daily reminder: alarm, notification, boot receiver
         └─ ui/
            ├─ FinanceApp.kt                Scaffold, bottom navigation, which dialog is open
            ├─ FinanceViewModel.kt          One function per user action (save/delete …)
            ├─ Strings.kt                   All texts in Bulgarian and English
            ├─ Format.kt                    Money / % / date formatting per language
            ├─ theme/Theme.kt               Colors (light + dark), color per kind
            ├─ components/Components.kt     Cards, progress bar, status chip, rows, inputs
            ├─ components/Editors.kt        Add/edit forms as bottom sheets (expense, category, income, goals)
            ├─ components/Charts.kt         Canvas charts: bars, donut, line, stacked bar, legend
            └─ screens/                     Overview, Expenses, Reports, Budget, Goals, Settings

app/src/test/java/com/vladk/financeplan/data/    Unit tests: CalcTest, ReportsTest, BackupTest
```

## 3. Architecture

```
Compose screens ──(user action)──► FinanceViewModel ──► Repository.update { … }
       ▲                                                    │
       └──────────── StateFlow<AppData> ◄──────────────────┘ (+ async write to JSON file)
```

- **Unidirectional data flow.** Screens are stateless functions of `AppData` plus the selected month.
  Every change goes through `FinanceViewModel`, which calls `Repository.update { old -> new }`.
- **Repository** keeps the current `AppData` in a `MutableStateFlow` and, after each change, writes the
  whole file on a background thread. It writes to `finance-data.json.tmp` first and renames it, so the
  file is never half-written. If the file cannot be read at start-up it is copied to
  `finance-data.broken.json` and the app starts empty instead of crashing. A failed write (e.g. full
  storage) does not crash the app either: `saveFailed` turns true and the UI shows a message.
- **Backup** (`data/Backup.kt`) is the gate for everything read from a file. `Backup.clean` repairs
  what it can (a date like `2026-11-5`, duplicate or empty ids, negative plan amounts) and leaves
  out expenses whose date or amount cannot be read, so the rest of the app can rely on ISO dates,
  finite numbers and unique ids. `Backup.parse` also refuses any JSON that is not a backup – it
  must contain the `categories` and `expenses` lists. Every field has a default, so without this
  check `{}` would count as an empty backup and replace the user's data.
- **Calc** contains every formula as a pure function, so the UI only displays results.
- **Navigation** is a simple `Tab` enum plus a `showSettings` flag (no navigation library needed).
  Only one add/edit form is open at a time, described by the `Editor` sealed interface. The forms
  are bottom sheets drawn as a layer inside the activity (`EditorDialog` in `Editors.kt`), not
  dialog windows: only the activity window reports the keyboard's size reliably, and the sheet
  needs it to keep Cancel / Save above the keyboard. The activity handles rotation itself
  (`configChanges` in the manifest), so a half-filled form survives turning the phone.
- **Language** is not taken from the phone's locale; it is a setting stored in `AppData`, so it can be
  switched inside the app instantly. `LocalStrings` and `LocalFmt` provide the texts and formatters.

### Daily reminder (`reminder/Reminders.kt`)

- `Settings.reminder` (`enabled`, `hour`, `minute`, `skipIfRecorded`) is part of `AppData`, so it is
  in the backup too.
- `Reminders.schedule` sets **one** alarm for the next reminder time (`AlarmManager`, exact where
  Android allows it, otherwise within a 10-minute window). It is called when the app starts, when
  the setting changes and after a backup is restored.
- `ReminderReceiver` runs when the alarm fires: it reads the data file (`Repository.readOnce`), shows
  the notification (`Reminders.message` decides the text, or silence when `skipIfRecorded` applies)
  and sets the alarm for the next day. Alarms are lost when the phone restarts, so the same receiver
  also listens for `BOOT_COMPLETED`, app updates and clock / time-zone changes and sets it again.
- Tapping the notification starts `MainActivity` with the `ADD_EXPENSE` extra, which opens the
  new-expense form. The launcher shortcut (`res/xml/shortcuts.xml`) uses the same extra.
- Permissions: `POST_NOTIFICATIONS` (asked for when the reminder is switched on, Android 13+),
  `RECEIVE_BOOT_COMPLETED`, and `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` for on-the-minute
  delivery. **If the app is ever published on Google Play, remove `USE_EXACT_ALARM`** – Play accepts
  it only from alarm and calendar apps; the reminder then falls back to the 10-minute window.
- Limits: "Force stop" in the phone's settings cancels the alarm until the app is opened again, and
  some phones' battery savers delay or block alarms for apps that are rarely opened.

### Regular payments (`data/Regular.kt`)

- `AppData.recurring` holds `Recurring(id, categoryId, amount, note, day, lastAdded = "yyyy-MM")`.
  Ticking *Repeat every month* on a new expense creates one with `lastAdded` = that expense's month.
- `Regular.addDue(data, today)` runs when the app starts and when the date changes while it is open.
  For each payment it adds an ordinary expense for every month after `lastAdded` whose date
  (`day`, clamped to the month's length) is not after today – at most 12 months back – and moves
  `lastAdded` forward. The expense id is `regular-<id>-<yyyy-MM>`, so a payment can never be added
  twice for the same month. Deleting a category removes its regular payments.

## 4. Data model (`data/Models.kt`)

```kotlin
AppData(
  version, settings: Settings(language = "bg" | "en", currency = "€"),
  income: Income(salary, vouchers, investSharePercent, extras: List<ExtraIncome(id, name, amount)>),
  categories: List<Category(id, name, kind, planType, amount, percent, minusVouchers, note,
                            group, icon, presetId)>,
  expenses:   List<Expense(id, categoryId, amount, date = "yyyy-MM-dd", note)>,
  goals:      List<Goal(id, name, target, saved, monthly, note)>,
  emergency:  EmergencyFund(months, saved, monthly),
  investing:  Investing(age, annualReturnPercent),
  presetsVersion,          // 0 = predefined categories not added yet
)
```

- `Kind` = `NEEDS`, `WANTS`, `SAVINGS`, `INVESTMENTS`. Savings and investments together are the
  "savings" part of the 50/30/20 rule (`Kind.isSaving`).
- `PlanType` = `FIXED` (use `amount`) or `PERCENT` (use `percent` of the salary).
- `Group` = `HOUSING`, `FOOD`, `TRANSPORT`, `HEALTH`, `EDUCATION`, `LIFESTYLE`, `IRREGULAR`, `OTHER`,
  `SAVINGS`, `INVESTMENTS` – each with an emoji and a default `Kind`. Groups organise the budget
  and give each category its chart colour.
- Predefined categories have `presetId` and an **empty `name`**, so their name follows the app
  language (`Category.label(lang)` in `Presets.kt`). Renaming one stores the new name. Their ids are
  stable (`preset-<id>`), so backups and re-added presets match.
- Percentages are stored as human numbers (12.5 means 12.5%).
- Expenses are kept for all months; a month is selected by the `yyyy-MM` prefix of `date`. Dates
  are always ISO `yyyy-MM-dd`, which sorts the same as text, so date ranges are string comparisons.
- Deleting a category also deletes its expenses.
- The backup file (*Settings → Save backup*) is exactly this JSON. Unknown fields are ignored when
  reading, so older backups keep working when fields are added.
- *Settings → Export expenses (CSV)* writes one row per expense (date, category, group, type,
  amount, note) with a UTF-8 byte-order mark: `;` and a decimal comma in Bulgarian, `,` and a
  decimal point in English – what Excel expects in each language.

## 5. Formulas (`data/Calc.kt`)

These match the formulas of the original *Finance Plan BG* spreadsheet.

| Value | Formula | Spreadsheet equivalent |
|---|---|---|
| Planned amount of a category | `FIXED`: `amount`; `PERCENT`: `percent% × salary`, minus vouchers if ticked (never below 0) | Бюджет!F: `IF(Тип="Фиксиран", D, E×заплата)`, groceries `MAX(0, E×заплата − ваучери)` |
| Actual of a category | sum of its expenses in the month | Бюджет!H "Реално (€)" |
| Status | spending: `actual ≤ planned` → Within plan, else Over budget; savings/investments: `actual ≥ planned` → Goal reached, else Below goal; `actual = 0` → No data yet | Преглед!G |
| Unallocated | `salary − Σ planned` | Преглед!D17 |
| Left per day | `(planned spending − actual spending) / days left in the month, today included`; current month only | – |
| 50/30/20 | planned/actual % of salary for Needs, Wants, Savings+Investments vs. 50 / 30 / 20 | Преглед rows 21–24 |
| Extra incomes | total; `× investShare%` → investments; rest → savings | Преглед rows 27–32 |
| Monthly investing | planned Investments + extra income to investments | Преглед!D33 |
| Monthly saving | planned Savings + extra income to savings | Преглед!D34 |
| Savings rate | (monthly investing + monthly saving) / (salary + extra incomes) | Преглед!D35 |
| Emergency fund target | `months × planned Needs` | Цели!B7 |
| Months to goal | `ceil((target − saved) / monthly)`; "n/a" if monthly is 0 | Цели!B11, G15 |
| Investing projection | `FV = m × ((1 + r)^n − 1) / r`, `r = annual% / 12`, `n = years × 12` | Цели!D29: `FV(r/12, n×12, −m)` |

### Reports (`data/Reports.kt`)

"Spending" = expenses in *Needs* / *Wants* categories; expenses in *Savings* / *Investments*
categories are counted as "saved".

| Value | Formula |
|---|---|
| Week | Monday to Sunday: `weekStart(d) = d.with(previousOrSame(MONDAY))` |
| Weekly guide | planned needs + wants per month × 12 / 52 (a month ≈ 4.33 weeks, like Monarch's weekly flex) |
| Daily guide | weekly guide / 7 (dashed line in the daily bar chart) |
| Week vs. week before | spending this week − spending in the previous Monday–Sunday |
| Daily average | week spending / days elapsed (7 for past weeks) |
| Spending through the month | running total per day; stops at today for the current month |
| Plan line | planned spending × day / days in month |
| Cash flow | income (salary + extra incomes) → spent, saved, left = income − spent − saved |
| Trends | spending and saved for the last 6 months; averages start at the first month with data and leave out the month in progress (unless it is the only one with data) |

Amount input (`Calc.parseAmount`) accepts `12,50`, `12.50`, `1 250` and sums like `4,40+7,10`;
the result is rounded to cents.

## 6. Charts (`ui/components/Charts.kt`)

All charts are drawn with Compose `Canvas` (no chart library):

- `BarChart` – daily (Mon–Sun) and monthly bars; tap a bar to select it; optional dashed guide line.
- `DonutChart` – spending by group; tap a slice to see it in the centre.
- `LineChart` – cumulative spending: this month, last month, plan; tap or drag for a read-out.
- `StackedBar`, `Legend`, `BarRow` – cash flow / 50-30-20 split and ranked category lists.

They follow one set of rules: a fixed, colour-blind-checked categorical palette (blue, orange,
aqua, yellow, magenta, green, violet, red) with separate light and dark steps (`Theme.kt`); every
group keeps the same colour everywhere; thin marks, 4dp rounded bar ends, 2dp gaps between
segments, hairline grid, muted axis text; a legend whenever there is more than one series; one
value label at a time; status shown with icon + text, never colour alone.

## 7. Building

Requirements: Android Studio (it bundles the JDK and SDK manager). The project uses the Gradle wrapper,
so no separate Gradle install is needed.

```
gradlew assembleDebug        # debug APK → app/build/outputs/apk/debug/app-debug.apk
gradlew installDebug         # install on a connected phone / running emulator
gradlew testDebugUnitTest    # unit tests for the formulas, reports and backup handling
```

`local.properties` (path to the Android SDK) is created by Android Studio and is not committed.

For a release build that can be shared, create a signing key in Android Studio
(*Build → Generate Signed App Bundle / APK*). Never commit `*.jks` / `*.keystore` files
(they are in `.gitignore`).

## 8. Privacy

- No internet permission, no analytics, no accounts. The only permissions are for the optional
  daily reminder (notifications, start after boot, exact alarm).
- Data: `/data/data/com.vladk.financeplan/files/finance-data.json` (private to the app).
- Backup files contain all personal figures; they are ignored by git (`*-backup*.json`).

## 9. Ideas for later

- Per-month income (when the salary differs from month to month).
- Home-screen widget for adding an expense in one tap.
