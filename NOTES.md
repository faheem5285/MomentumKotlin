# Momentum project notes

Native Android app: Kotlin, Jetpack Compose, Room, ViewModel. Built with Gradle from VS Code.

## Run on phone
    gradlew.bat installDebug      (phone connected by USB, USB debugging and Install via USB on)

## Structure (app/src/main/java/com/faheem/habits)
- MainActivity.kt      app shell: Home (module tiles) -> opens a module screen; ModuleHost applies per-module accent colour
- HomeScreen.kt        dashboard with live summary per module, locked Vault tile (password manager, not built)
- Data.kt              Room entities, DAOs, database (version 3)
- HabitsScreen.kt      habits, daily check-ins, streaks
- ExpensesScreen.kt    expenses, monthly budget (budget saved in SharedPreferences)
- LoansScreen.kt       loan ledger (lent / borrowed, settle)
- FilesScreen.kt       file library (links to files via document picker)
- ScreenTimeScreen.kt  today's app usage (needs Usage access permission)
- Util.kt              currency label (CURRENCY), date helpers
- Theme.kt / Ui.kt     colors (light+dark), shared components (HeroCard, ProgressRing, FormSheet, ...)

## Known limits / next steps
- Database uses fallbackToDestructiveMigration: changing the schema WIPES data.
  Before using real data, replace it with proper Room migrations.
- Reels counter is not built (needs an Accessibility Service, fragile).
- Password manager is not built. Do it carefully: Android Keystore + encrypted storage
  (for example SQLCipher or EncryptedFile), app lock with BiometricPrompt, no cloud sync at first.
- Release build for speed testing: gradlew.bat installRelease (R8 on, signed with debug key).
