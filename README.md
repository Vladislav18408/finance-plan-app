# Finance Plan – personal budget app for Android

A simple Android app (Kotlin + Jetpack Compose) for planning a monthly budget and tracking what you
actually spend. It is a mobile version of the *Finance Plan BG* spreadsheet: the same ideas
(plan as a % of the salary, needs / wants / savings, 50/30/20 rule, emergency fund, savings goals,
investing projection), but made for quick use on the phone.

**The app contains no data.** It starts empty and you enter everything yourself: income, budget
categories, every expense, and your goals. All data stays on your phone.

Interface language: **Bulgarian** (default) or **English** – switch in *Settings*.

## Features

| Screen | What you do there |
|---|---|
| **Overview** (Преглед) | See the month at a glance: spent vs. planned, what is left, saved + invested, unallocated money, and a progress bar + status for every category. Tap a category to see its expenses, or tap ⊕ to add one. |
| **Expenses** (Разходи) | The list of everything you recorded, grouped by day. Filter by category. **Tap any expense to change or delete it.** |
| **Budget** (Бюджет) | Enter your salary, food vouchers and extra incomes. Create categories with a fixed amount or a % of the salary. See how much of the salary is planned and how you compare to the 50/30/20 rule. |
| **Goals** (Цели) | Emergency fund (N months of planned needs), your savings goals with progress, and an investing projection for 5–40 years. |
| **Settings** (Настройки) | Language, currency (€, лв., $, £), save/restore a backup file, delete all data. |

Adding an expense is one button (**+ Add expense**): type the amount, tap a category, done.
The amount field also accepts sums, e.g. `11,60+4,40+10` – handy for a shopping receipt.

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
