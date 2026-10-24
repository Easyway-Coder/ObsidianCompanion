# Obsidian Companion — Project Development Roadmap

This document outlines the detailed roadmap for building **Obsidian Companion**, a lightweight Android application designed to seamlessly parse, manage, and synchronize task checklists from Markdown (`.md`) files in Obsidian vaults, featuring a Focus Timer, Next-Day Planner, adaptive Material 3 UI, and Home Screen Widgets.

---

## Step 1: Set up my First Simple Android App

### Process
Establish the baseline native Android project configured with modern Jetpack Compose tooling, Kotlin, and Gradle version catalogs. Configure target SDKs, basic theme scaffolds, and essential activity lifecycles to ensure a clean build pipeline.

### Algorithmic Breakdown / Explanation
- **Application Initialization Flow:**
  1. Operating system instantiates `Application` process.
  2. `MainActivity` sub-classes `ComponentActivity` and executes `onCreate(savedInstanceState: Bundle?)`.
  3. `setContent` binds the Compose runtime to the activity window, initialising the top-level `MaterialTheme` and `Surface`.
  4. Jetpack Lifecycle handles activity state transitions (`ON_CREATE`, `ON_START`, `ON_RESUME`).

### Smaller Steps
1. Create new Android Studio project targeting Android SDK 28+ (Android 9.0+) up to SDK 35/37.
2. Configure Gradle version catalog (`gradle/libs.versions.toml`) with Kotlin, Android Gradle Plugin (AGP), Compose BOM, and AndroidX Core libraries.
3. Implement `MainActivity.kt` with basic `ComponentActivity` and Compose `MaterialTheme` set up.
4. Set up top-level project structure (`data/`, `ui/`, `viewmodel/` packages).
5. Verify build and run on Android Emulator or physical device.

### Plausible Dependencies Required
- `androidx.core:core-ktx`
- `androidx.activity:activity-compose`
- `androidx.compose.ui:ui`
- `androidx.compose.material3:material3`
- `androidx.lifecycle:lifecycle-runtime-ktx`

---

## Step 2: Learn how to make small checkboxes

### Process
Build modular, accessible, and customizable checkable list item components in Jetpack Compose. Implement state hoisting for task item rows, custom sizing for compact/dense layouts, and interactive touch response.

### Algorithmic Breakdown / Explanation
- **State Hoisting & Recomposition Loop:**
  1. Composable function `TaskRow` receives state (`title: String`, `checked: Boolean`).
  2. User interacts with row or checkbox, firing `onCheckedChange: (Boolean) -> Unit` callback.
  3. Parent composable or ViewModel handles event, updating state container (`MutableStateFlow<List<Task>>`).
  4. Compose runtime observes state emission and triggers efficient recomposition for affected nodes only.

### Smaller Steps
1. Design custom `TaskRow` layout using `Row`, `Checkbox`, `Text`, and `Modifier.toggleable`.
2. Implement custom check mark density / size constraints using Compose `Modifier.size()` or scaled `CompositionLocal` density settings.
3. Add accessibility semantics via `Role.Checkbox` and semantic content descriptions.
4. Handle state hoisting for checked/unchecked toggles.
5. Build a scrollable container (`LazyColumn` or `Column` with `verticalScroll`) to render multiple task rows efficiently.

### Plausible Dependencies Required
- `androidx.compose.foundation:foundation`
- `androidx.compose.material3:material3`
- `androidx.compose.ui:ui-semantics`

---

## Step 3: Connect the checklist to .md files

### Process
Integrate Android Storage Access Framework (SAF) to pick and gain persistent permissions (`takePersistableUriPermission`) for user Markdown (`.md`) files. Implement Regex-based parsing to read markdown task syntax (`- [ ]`, `- [x]`), modify task states in memory, and safely overwrite target files without corrupting surrounding markdown content.

### Algorithmic Breakdown / Explanation
- **Regex Task Parsing & Atomic Sync Algorithm:**
  1. Read file input stream line-by-line using `ContentResolver.openInputStream(uri)`.
  2. Apply Regex `^\s*[-*+]\s+\[([ xX])]\s+(.*)` to match list items:
     - Group 1: Check state mark (`" "` vs `"x"` / `"X"`).
     - Group 2: Task title text.
  3. Maintain task `lineIndex` relative to file position.
  4. On toggle event:
     a. Re-read file fresh under mutex lock (`Mutex.withLock`) to prevent concurrent modification conflicts with Obsidian.
     b. Verify target line index matches expected title text.
     c. Replace exact range of the checkmark character (`" "` $\rightarrow$ `"x"` or `"x"` $\rightarrow$ `" "`).
     d. Atomically write updated lines back via `ContentResolver.openOutputStream(uri, "wt")` (write + truncate).

### Smaller Steps
1. Implement `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` to let users choose `.md` files.
2. Request and retain persistable URI read/write permissions (`FLAG_GRANT_READ_URI_PERMISSION`, `FLAG_GRANT_WRITE_URI_PERMISSION`).
3. Save `note_uri` in `SharedPreferences` or `DataStore`.
4. Create `TaskViewModel` to handle asynchronous coroutine I/O on `Dispatchers.IO` guarded by `Mutex`.
5. Integrate `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` to refresh task state automatically whenever app returns to foreground.

### Plausible Dependencies Required
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.lifecycle:lifecycle-runtime-compose`
- `kotlinx-coroutines-core`
- `kotlinx-coroutines-android`

---

## Step 4: Set up the Focus Timer

### Process
Build a configurable Pomodoro/Focus Countdown Timer that runs seamlessly, supporting play, pause, reset, and interval adjustments. Implement background execution support via Android Foreground Service and Notifications so the timer continues accurately when the app is minimized.

### Algorithmic Breakdown / Explanation
- **Tick Engine & State Machine:**
  1. Define timer state enum: `IDLE`, `RUNNING`, `PAUSED`, `FINISHED`.
  2. Use Kotlin Coroutines `flow` with `delay(1000ms)` or `SystemClock.elapsedRealtime()` calculations for precision drift prevention:
     $$\text{Remaining Time} = \text{Target End Time} - \text{Current Time}$$
  3. When running, start a `ForegroundService` with notification showing live remaining time.
  4. Upon reaching zero, play notification alert/vibration via `Vibrator` and `NotificationManager`.

### Smaller Steps
1. Create `FocusTimerViewModel` managing timer state, duration (e.g. 25 min focus, 5 min break), and elapsed time.
2. Implement timer UI with circular progress indicator (`CircularProgressIndicator`), remaining time display, and action buttons.
3. Build `FocusTimerService` extending `Service` with `ForegroundServiceType.SPECIAL_USE` or `MEDIA_PLAYBACK` / `SHORT_SERVICE`.
4. Add notification channel and ongoing status bar notification with Pause/Resume actions.
5. Sync completed focus sessions back as completed task notes or focus logs in Markdown if linked.

### Plausible Dependencies Required
- `androidx.core:core-ktx` (NotificationCompat)
- `androidx.lifecycle:lifecycle-service`
- `kotlinx-coroutines-core`

---

## Step 5: Create the Next-Day Planner

### Process
Develop a daily planning module that helps users review uncompleted tasks from today, carry them over to tomorrow's daily note, or schedule new tasks into future Markdown daily notes following standard Obsidian daily note templates (e.g. `YYYY-MM-DD.md`).

### Algorithmic Breakdown / Explanation
- **Rollover & Template Injection Algorithm:**
  1. Parse current daily note for unchecked tasks (`done == false`).
  2. Compute target date URI (e.g., `currentDate.plusDays(1)` formatted as `YYYY-MM-DD.md`).
  3. Check if target date file exists in vault directory via DocumentFile API / SAF directory picker.
  4. If file exists, append unchecked tasks under target section (`## Tasks`); if not, generate file using predefined Markdown template schema.
  5. Mark rolled-over tasks in original file as deferred or copy/migrate based on user preference.

### Smaller Steps
1. Set up vault directory picker (`ActivityResultContracts.OpenDocumentTree()`) for folder-wide SAF permissions.
2. Build date calculation utilities (`java.time.LocalDate`, `java.time.format.DateTimeFormatter`).
3. Design Next-Day Planner UI screen showing carry-over tasks and schedule builder.
4. Implement vault directory scanner to detect daily notes matching `YYYY-MM-DD.md` pattern.
5. Create task rollover and note generation logic in `PlannerViewModel`.

### Plausible Dependencies Required
- `androidx.documentfile:documentfile`
- `java.time` (Java 8+ API desugaring or native Android SDK 26+)

---

## Step 6: Design the app & widgets

### Process
Refine the user experience with cohesive Material 3 styling (Dark/Light themes, Dynamic Color / Monet), smooth layout animations, and Home Screen Widgets built with Jetpack Glance to display quick task checklists and Focus Timer controls directly on the Android launcher.

### Algorithmic Breakdown / Explanation
- **Glance AppWidget Lifecycle & Interoperability:**
  1. User adds Widget to Home Screen.
  2. System triggers `GlanceAppWidgetReceiver`.
  3. Widget reads persisted `note_uri` from shared storage / Glance `PreferencesGlanceStateDefinition`.
  4. Composable Glance UI renders `LazyColumn` of task items directly into RemoteViews hierarchy.
  5. Checking a task on widget fires `ActionCallback`, updating underlying `.md` file and calling `GlanceAppWidget.updateAll(context)`.

### Smaller Steps
1. Define unified Material 3 color schemes, typography, and shape themes (`Theme.kt`).
2. Add dynamic animation transitions (`AnimatedVisibility`, `Crossfade`) for task toggling and timer state changes.
3. Implement Home Screen Widget using `androidx.glance:glance-appwidget` and `glance-material3`.
4. Add Glance `ActionCallback` to handle task toggles directly from the home screen.
5. Test widget responsiveness across diverse grid sizes (2x2, 4x2, 4x4) and dark/light system modes.

### Plausible Dependencies Required
- `androidx.glance:glance-appwidget`
- `androidx.glance:glance-material3`
- `androidx.compose.animation:animation`

---

## Step 7: Launch into my phone

### Process
Prepare the application for production release, optimize binary size with R8 code shrinking, configure debug/release build variants, generate a signed APK / Android App Bundle (AAB), and deploy directly to physical Android device via ADB or Wireless Debugging.

### Algorithmic Breakdown / Explanation
- **R8 Shrinking & Deployment Pipeline:**
  1. Enable `isMinifyEnabled = true` and `isShrinkResources = true` in `app/build.gradle.kts`.
  2. R8 analyzer performs static code analysis, dead code elimination, and obfuscation based on keep rules.
  3. Keystore signs APK/AAB artifact using `apksigner` with V2/V3/V4 signature schemes.
  4. Device installation via `adb install -r app-release.apk` verifies signature matches and launches activity.

### Smaller Steps
1. Create release signing keystore using `keytool` or Android Studio Generate Signed Bundle/APK wizard.
2. Configure `signingConfigs` and `buildTypes.release` in `app/build.gradle.kts`.
3. Enable R8 minification and test build optimization.
4. Perform manual end-to-end testing on physical Android device (SAF permissions, background timer, home screen widget).
5. Deploy signed APK to phone via USB/Wireless ADB or install via release channel.

### Plausible Dependencies Required
- Android SDK Build Tools (`apksigner`, `zipalign`)
- Gradle App Signing plugin

---

## Project Dependency Summary

| Module / Feature | Key Libraries / Dependencies |
| :--- | :--- |
| **Core UI & Activity** | `androidx.core:core-ktx`, `androidx.activity:activity-compose`, `androidx.compose.material3:material3` |
| **State & Lifecycle** | `androidx.lifecycle:lifecycle-viewmodel-compose`, `androidx.lifecycle:lifecycle-runtime-compose` |
| **Async & I/O** | `kotlinx-coroutines-core`, `kotlinx-coroutines-android`, `androidx.documentfile:documentfile` |
| **Home Screen Widgets** | `androidx.glance:glance-appwidget`, `androidx.glance:glance-material3` |
| **Testing** | `junit:junit`, `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core` |
