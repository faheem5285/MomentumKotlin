# Momentum project notes

Native Android app: Kotlin, Jetpack Compose, Room, ViewModel. Built with Gradle from VS Code.

## Run on phone
    gradlew.bat installDebug      (phone connected by USB, USB debugging and Install via USB on)

## Structure (app/src/main/java/com/faheem/habits)
- MainActivity.kt      app shell: Home (module tiles) -> opens a module screen; ModuleHost applies per-module accent colour
- HomeScreen.kt        dashboard with live summary per module
- Data.kt              Room entities, DAOs, database (version 3)
- HabitsScreen.kt      Habits module shell (4 tabs: Today, Library, Insights, Manage) + HabitViewModel
- HabitsToday.kt / HabitsTemplatesScreen.kt / HabitsInsightsScreen.kt / HabitsManageScreen.kt / HabitEditor.kt  the four Habit screens + create/edit sheet
- HabitLogic.kt        schedule (daysMask), streak + insights maths, icons, colours, achievements
- HabitTemplates.kt    100 ready-made habit templates (generated list)
- ExpensesScreen.kt    expenses, monthly budget (budget saved in SharedPreferences)
- LoansScreen.kt       loan ledger (lent / borrowed, settle)
- FilesScreen.kt       file library (links to files via document picker)
- ScreenTimeScreen.kt  screen time overview, today/7-day app usage, daily goals, and Instagram scroll estimate
- InstagramScrollCounterService.kt  opt-in Accessibility service, scoped to Instagram scroll events (not a verified Reels view count)
- VaultScreen.kt / VaultCrypto.kt  offline master-password vault with encrypted logins and secure notes
- Util.kt              currency label (CURRENCY), date helpers
- Theme.kt / Ui.kt     colors (light+dark), shared components (HeroCard, ProgressRing, FormSheet, ...)

## Known limits / next steps
- Database uses fallbackToDestructiveMigration: changing the schema WIPES data.
  Before using real data, replace it with proper Room migrations.
- Instagram scroll estimate needs the user to enable Momentum's Accessibility service in Android Settings. It counts debounced Instagram scroll events, not verified Reels views; it does not read on-screen content and can include non-Reels scrolling.
- Vault uses a master-password-derived AES-256-GCM key with PBKDF2-HMAC-SHA256 (310,000 rounds), random salt and per-record nonces. Forgotten master passwords cannot be recovered. Room version 4 -> 5 adds the encrypted vault table through a migration.
- Vault blocks screenshots while open, locks when the app backgrounds, and clears copied values after 30 seconds when possible. No server sync or biometric unlock is implemented yet.
- Release build for speed testing: gradlew.bat installRelease (R8 on, signed with debug key).
