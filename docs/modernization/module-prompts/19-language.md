# Module Prompt 19 — Language & Translation

Standalone OpenCode prompt. Implement on a dedicated branch. Read audit sections 2, 4 (U6/U7), 7 (S21), 10 (T5/T9), 11, 12, 13 and this prompt. Translation text can be personal: never log it or send it to a cloud provider without explicit, accurate disclosure. Report only tests actually executed; model availability and network behavior are unverified.

## 1. Actual source files involved
- `app/src/main/java/com/faheem/habits/features/language/LanguageScreen.kt` — ~2,540 lines: `LanguageStudyLogic` (`:87-113`), `LanguageViewModel` (`:115-266`), screen/tabs (`:302-544`), Vocabulary (`:782`), Grammar (`:1119`), Practice (`:1432-1717`), Translate (`:1724-2126`), History (`:2188-2376`), dialogs (`:2449,2571`).
- `LanguageTranslator.kt` — cache/cloud-first path (`:30-89`), Google `translate_a/single` request with spoofed UA (`:92-116`), dictionary parsing (`:142-168`), ML Kit language ID (`:181-189`).
- `LanguageProofreader.kt:16-32` — LanguageTool online API, 20 KB cap, 5s/8s timeouts.
- `core/data/Data.kt` — `VocabularyWord`, `GrammarNote`, `ReviewState`, `TranslationHistory` (`:259-298`), DAOs (`:840-907`), `MIGRATION_21_22` (`:1394-1407`).
- `core/data/backup/MomentumBackup.kt` — language tables currently excluded (prompt 18).
- `res/values/strings.xml` — almost no UI strings extracted; `core/ui` components/tokens (prompt 20).
- Tests: `LanguageStudyLogicTest.kt:8-63`.

## 2. Existing functionality (verified)
- Five tabs: Vocab, Grammar, Practice, Translate, History. Leitner intervals are 1/2/4/8/16 days with a 10-minute retry after a wrong answer; the logic has focused unit tests.
- Vocabulary/grammar CRUD, favorites, review states, translation history and save-as-word flows are implemented locally with Room; `removeWord` and `review` use `withTransaction`.
- Translation tries a cloud endpoint first (1.8s/2.5s timeouts) and falls back to ML Kit; results are cached and inserted into history on success.
- Proofreading is explicitly disclosed as online and only runs on tap.
- All language flows use `collectAsState()` rather than `collectAsStateWithLifecycle()`.

## 3. Problems discovered
- **Privacy misrepresentation**: UI says "On-Device Translator", "Private & offline capable after first download", "Offline Ready" (`LanguageScreen.kt:1761-1784,2229`) while `LanguageTranslator.kt:45-54` sends text to Google first. No consent or opt-out was found.
- Cloud request sets `User-Agent: Mozilla/5.0 (Android; Mobile)` (`LanguageTranslator.kt:102`) to impersonate a browser; brittle and undocumented.
- `TranslationResult` has no engine/source indicator, so users cannot tell cloud from on-device or cache.
- Error copy says "On-device translation failed" even when the cloud path failed (`LanguageScreen.kt:211`).
- `LanguageScreen.kt` is giant and suppresses `LongMethod`/`ComplexCondition`; state, UI, platform IO and dialogs are colocated.
- `LanguageViewModel` calls `AppDatabase.get(app)` directly instead of Hilt/repository injection.
- Test coverage is limited to SRS logic; no translator/proofreader/VM/history/Compose tests.
- Language tables are omitted from backup (prompt 18) despite containing user study data.

## 4. Recommended UI improvements
- Split the screen into a coordinator plus Vocab/Grammar/Practice/Translate/History sections; keep behavior and IDs unchanged.
- Show an engine badge: On-device, Cloud, or Cache; make the active policy visible before the user translates.
- Make copy truthful. Choose one: offline-first by default with explicit opt-in for cloud, or cloud-first with prominent disclosure. Do not keep "private/offline" claims while cloud-first.
- Keep the honest proofreading disclosure; show 20 KB/5000-char limits and network errors clearly.
- Use lifecycle-aware flow collection and accessible labels for tabs, cards and review actions.

## 5. Recommended backend and architecture improvements
- Introduce an injectable `TranslationService` with explicit `OnDevice`, `Cloud` and cache implementations plus a policy decision; remove UA spoofing and use a documented provider contract or remove the cloud path.
- Carry engine/source through `TranslationResult` and history if product approves a schema addition; otherwise derive it at runtime without changing stored rows.
- Move language data access behind a repository/Hilt provider; do not call `AppDatabase.get(app)` from the ViewModel.
- Preserve SRS intervals/retry behavior unless explicitly approved; if changed, keep a migration for existing review state.
- Coordinate backup inclusion with prompt 18 and no-log policy with prompt 22.

## 6. Useful features to add
- Offline-only mode toggle with model download status.
- Translation history filter by engine/date/favorite and one-tap word saving with duplicate handling.
- Proofreading suggestion preview with safe offset/length validation.

## 7. Unnecessary elements to simplify or remove
- Browser UA spoofing.
- Misleading "private/offline" copy if cloud remains enabled.
- Duplicate error strings and hardcoded UI text; extract to resources without changing meaning.
- `collectAsState()` in this module.

## 8. Data safety requirements
- Preserve vocabulary/grammar IDs, favorites, created timestamps, review boxes/counters, `nextReviewAt`, and history rows. No destructive table clears.
- Do not log source text, translated text, proofread text or filenames.
- If a schema change is required, add an additive migration with exported schema and data-bearing tests; never edit shipped migrations.
- Language data remains local-only for sync unless an explicit ownership design and RLS review are approved.

## 9. Security considerations
- Translation/proofreading text can be personal; cloud use must be opt-in or clearly disclosed before sending.
- Do not persist provider API keys; use only documented public endpoints or a provider SDK configured from build properties.
- Model availability, provider terms and network behavior are unverified; do not claim guaranteed offline operation.

## 10. Required tests
- Translator matrix: cache hit, cloud success, cloud failure → ML Kit fallback, offline-only policy, blank/over-limit text, malformed JSON, unsupported pair.
- GTx parsing tests for sentence and dictionary meanings; proofreader tests for 20 KB cap, `auto`, malformed response, timeout, safe suggestion offsets.
- ViewModel tests: history insert only on success, favorite/delete/clear, save-as-word mapping.
- Keep and extend `LanguageStudyLogicTest`; add backup round-trip for the four language tables (prompt 18).
- Compose tests for truthful disclosure copy, engine badge, tabs and review states. No tests were executed in this audit.

## 11. Dependencies on other modules
- Prompt 18 backup coverage, prompt 21 Room/DAO/Hilt, prompt 20 design system/string resources, prompt 14 notification text policies if reminders are added, prompt 22 logging checks.

## 12. Acceptance criteria
- UI copy matches the actual translation path; no hidden cloud transmission or UA spoofing.
- Existing study progress and history preserved; all executed tests pass; provider/model claims marked unverified until tested.

## 13. Files that must not be changed without justification
- SRS intervals/retry in `LanguageScreen.kt:88-112` without product approval.
- `VocabularyWord`, `GrammarNote`, `ReviewState`, `TranslationHistory` in `Data.kt` without data-preserving migrations.
- Proofreading disclosure at `LanguageScreen.kt:1900-1904` — do not weaken it.
- `MIGRATION_21_22` or exported schema JSON files.
