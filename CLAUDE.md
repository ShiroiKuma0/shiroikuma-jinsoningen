# CLAUDE.md — guide for Claude Code in this repo

**shiroikuma-jinsoningen** — 白い熊's fork of [Droid-ify](https://github.com/Droid-ify/client), a fast,
material-design F-Droid client (Kotlin, Jetpack Compose + View/ViewBinding hybrid, Hilt, Room; no
native code). Renamed to `shiroikuma.jinsoningen` / **白い熊 人造人間** so it installs side-by-side
with upstream.

This repo (`ShiroiKuma0/shiroikuma-jinsoningen`) is a real GitHub fork of `Droid-ify/client`. We track
upstream **release tags** on `main` and layer our customizations on `custom`.

## Read this first

Before any work, read **`.claude/skills/build-apk/SKILL.md`** (canonical build + delivery) and
**`.claude/skills/upstream-new-version/SKILL.md`** (upstream sync + rebase, with the mandatory
proceed-gated upstream-changes table). Publishing a release uses the **global** `/publish-version`
skill — this repo has no local copy. `CHANGELOG.md` is the exhaustive record of what the fork adds.

## Fork workflow — READ THIS FIRST

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-jinsoningen` (push here).
- `upstream` → `https://github.com/Droid-ify/client` (fetch only; its push URL is `DISABLED`).
- `main` — mirrors the latest upstream **release tag**, no fork work. Named `main` to match
  upstream's own branch name (白い熊, 2026-08-04).
- `custom` — all our work, and the GitHub default branch so the repo page lands on the fork.

**Upstream tracking: release TAGS, not the branch tip** (白い熊, 2026-08-04). Droid-ify tags every
release (`v0.7.4`, `v0.7.3`, …) and `main` keeps moving between them — at fork time `main` was
already 90 commits past `v0.7.4`. We base on the newest tag, so the upstream version literal
actually moves when we sync and every base is a state upstream itself called finished. Because the
base version therefore always changes on a sync, the global **`/git-versioning`** skill does **not**
apply here — we use the plain `+NNN` versionName.

### Our customizations (install identity + build)

| What | Value | Where |
| --- | --- | --- |
| applicationId | `shiroikuma.jinsoningen` | `app/build.gradle.kts` → `defaultConfig` |
| namespace (R/BuildConfig pkg) | `com.looker.droidify` (**never rename**) | `app/build.gradle.kts` |
| App label | `白い熊 人造人間` | `application_name` in `app/src/main/res/values/strings.xml` |
| App icon | black-yellow traced line-art (yellow `#FFFF00` on black) | `design/…icon.svg` → `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`, `mipmap-*/`, `ic_launcher-playstore.png` |
| Version tail | `versionName = "<upstream>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/build.gradle.kts` fork blocks |
| `BuildConfig.VERSION_NAME` | our fork version (`0.7.4+010`), not upstream's `v0.7.4` | `app/build.gradle.kts` → `buildTypes { all { } }` |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-jinsoningen.jks` (alias `jinsoningen`) | `app/build.gradle.kts` |
| House theme | `Theme.Main.Jinsoningen` for every theme choice except Light | `values/jinsoningen_theme.xml`, `datastore/extension/Preferences.kt` |
| Toolbar cog | tap → Settings, long-press → the UI page | `ui/tabsFragment/TabsFragment.kt`, `drawable/ic_settings.xml`, `values/ids.xml` |
| De-branding | our name + our GitHub links everywhere user-visible | `values/strings.xml`, `SettingsScreen.kt`, `NetworkModule.kt` (User-Agent), `settings.gradle.kts` |

### Versioning & APK naming

- The upstream base lives in `app/build.gradle.kts` as upstream's own `val latestVersionName = "0.7.4"`
  and `versionCode = 740` literals. Our fork lines sit **immediately after** them and multiply/append,
  so a rebase brings the new base in automatically. **Never hand-edit those two literals.**
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<upstream name>+<N zero-padded to 3>"` (e.g. `0.7.4+010`),
  `versionCode = <upstream code> * 10000 + N` (plain integer, e.g. `7400010`).
  The `buildFork` task bumps `BUILD_NUMBER` after every successful build; `/upstream-new-version`
  resets it to `1` on every sync, so `+N` always reads as "our Nth build on this upstream base".
- APK: `shiroikuma-jinsoningen_<versionName>.apk`, copied to `~/tmp/`. **No ABI suffix** — the app has
  no native code, so the APK is universal. The versionName contains no `_`, so the
  `shiroikuma-jinsoningen_*.apk` globs in `/adb-push`, `/scp` and `/publish-version` still resolve.

### Build commands

```bash
# Our build: signed release → ~/tmp + bump BUILD_NUMBER (use this)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork < /dev/null
# Release APK only (no copy / no bump)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:assembleRelease
# Tests — note the task is testDebugUnitTest; there is no release variant of it
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:testDebugUnitTest
```

The `alpha` and `debug` build types are upstream's (`.alpha` / `.debug` applicationId suffixes) — we
build and ship `release` only.

### Toolchain

- Gradle **runs** on JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`); the host default `java` is older,
  so always set `JAVA_HOME`. The build itself declares a **JetBrains JDK 17 toolchain**, auto-provisioned
  by the `foojay-resolver` plugin in `settings.gradle.kts` — the first build downloads it.
- Android SDK at `~/android-sdk` via the gitignored `local.properties`; `compileSdk 36`, `minSdk 23`.
  Gradle wrapper 9.6.1, configuration cache **on**.
- Release is minified + resource-shrunk (`isMinifyEnabled` / `isShrinkResources`) — that is upstream's
  setting; leave it. If a Compose/Hilt/Room class disappears at runtime, the fix is a keep rule in
  `app/proguard.pro`, not turning minification off.

## Our subsystems (fork-only code)

| Where | What |
| --- | --- |
| `jinsoningen/JinsoningenUiConfig.kt` | SharedPreferences store for every UI knob, seeded to the house black-yellow; `toJson`/`fromJson`; the recent-colour ring. `resetToDefaults()` deliberately **keeps** the export dir and swatches |
| `jinsoningen/JinsoningenUiState.kt` | observable mirror; writes land in prefs AND Compose state, so edits repaint live. Derives `ColorScheme`/`Shapes`/`Typography`, holds `houseThemeActive`, and fires the change listener the legacy Views need |
| `jinsoningen/JinsoningenFonts.kt` | `.ttf`/`.otf` import into app storage + family/Typeface resolution |
| `jinsoningen/JinsoningenBackup.kt` | the category ZIP. `writeZip(context, categories, out, onProgress, isCancelled)` is the ONE export implementation; atomic `.part`-then-rename on both destinations |
| `jinsoningen/JinsoningenViewTheme.kt` | the house look for the LEGACY View screens — the attr→knob mapping, the tinting inflater, `paintTree`, `refreshTree`, `applyTypography` |
| `jinsoningen/automation/` | the 保存復元 contract — `AutomationAuth` (token, switch default OFF), `StateExportReceiver` (exported, 3 actions), `StateExportService` (foreground `dataSync`) |
| `compose/jinsoningen/JinsoningenUiScreen.kt` | the 白い熊 人造人間 UI page, in kxkb's grammar |
| `compose/jinsoningen/JinsoningenDialogs.kt` | RGBA colour picker with recent swatches, font picker (each font in its own glyphs), the Export/Import panel, the result dialog |
| `compose/jinsoningen/JinsoningenAlertDialog.kt` | drop-in `AlertDialog` with the house yellow border |
| `ui/jinsoningen/JinsoningenUiFragment.kt` | the `ComposeView` host, mirroring upstream's own `SettingsFragment` |
| `design/` | the icon's source SVG — every raster is generated from it |

### How the theming actually reaches the app

An Android theme is **resource-backed**, so arbitrary runtime knob values can never become theme
attributes. The house look therefore arrives four ways, and all four read
`JinsoningenUiState.houseThemeActive` so **Light stays a real escape hatch**:

1. **`Theme.Main.Jinsoningen`** (`values/jinsoningen_theme.xml`) — the static floor. It inherits
   `Theme.Main.Amoled`, so upstream's widget styles and text appearances still come through; only
   the colours are ours.
2. **`Configuration.getThemeRes`** (`datastore/extension/Preferences.kt`) returns it for every
   choice except Light. Upstream's own resolution is preserved verbatim as `stockThemeRes` and is
   what the light outcomes still use.
3. **The tinting `LayoutInflater.Factory2`** installed by `JinsoningenViewTheme.install(activity)`,
   plus a per-fragment `paintTree` in `MainActivity`'s `FragmentLifecycleCallbacks`.
4. **`Context.getColorFromAttr`** (patched) answers from `JinsoningenViewTheme.attrColor`, which
   redirects every colour the legacy Kotlin resolves.

On a knob change the UI state fires its listener, and `MainActivity` calls
`JinsoningenViewTheme.refreshTree(main_content)`.

## Things that will bite you if you don't know them

- **`MainActivity.setTheme` runs AFTER the manifest theme.** Styling `MainTheme` in
  `base_theme.xml` reaches nothing on the main screen — upstream re-applies its own
  `Theme.Main.*` at runtime from the settings flow. The fork diverts `getThemeRes` instead.
- **`activity.delegate.createView` returns null for anything AppCompat does not substitute.**
  The tinting inflater therefore sees `TextView`/`Button`/… but never `MaterialToolbar`,
  `TabLayout`, `MaterialCardView` or a plain `ViewGroup`. Those are covered by the per-fragment
  `paintTree`, not by the inflater. Do not "fix" the inflater by creating views yourself — that
  loses AppCompat's substitution.
- **`JinsoningenViewTheme.install()` MUST be called before `super.onCreate`.** A `Factory2` can
  only be set once per `LayoutInflater`, and AppCompat installs its own in there.
- **`applyTypography()` must never touch colour.** That constraint is the only reason it is safe to
  run over adapter-built rows on attach. A blanket repaint there would flatten the deliberate
  distinctions adapters draw (an "installed" chip against a plain row).
- **Adapters that build views in code** (`AppDetailAdapter`, `CustomButtonsAdapter`, the app-list
  header, `ScreenshotsAdapter`, the `TabsFragment` section title) colour themselves in the
  view-holder constructor via `getColorFromAttr` — so their colours are knob-correct **when built**.
  To refresh them, `refreshTree` detaches and re-attaches the adapter; `notifyDataSetChanged()`
  alone re-binds the holders it already has and never re-runs `onCreateViewHolder`.
- **`JinsoningenUiState` writers are `updateX()`, not `setX()`** — the properties' generated setters
  already own the `setX` JVM signature, and a clash is a compile error.
- **The UI state is a process-wide singleton** (`JinsoningenUi.get`), because the legacy Fragment
  shell and each `ComposeView` must read and write the same live state. Anything registering a
  change listener on it must unregister in `onDestroy` or it leaks the Activity.
- **The adaptive icon fills the 72×72 safe zone**, it does not sit inside it. An inset adaptive icon
  renders visibly smaller than every neighbour in a launcher or file list. The design SVG frames the
  same path data differently (~78 % of the square) for the unmasked rasters.
- **Shizuku: ours declares a different permission.** 白い熊 雫 (`shiroikuma.shizuku`) installs beside
  stock Shizuku, so it cannot define `moe.shizuku.manager.permission.API_V23` (duplicate-permission
  install failure) and declares `af.shizuku.plus.permission.API_V23` instead. Upstream resolved
  Shizuku only by that stock permission and a hardcoded package, so it could never find ours —
  `InstallerPermission.kt` now asks by package first. Our `AndroidManifest.xml` declares the
  **Plus** permission; the stock one is merged in by the Shizuku API library, so the built APK
  carries both — verify with `aapt2 dump permissions <apk> | grep shizuku` rather than by reading
  the manifest, which shows only one.
- **A dialog needs a border here.** On a black ground a borderless dialog has no edge and reads as
  text floating over the page beneath it. Use the drop-in `AlertDialog` from
  `compose/jinsoningen/` — a screen adopts it by changing one import — and `@style/Theme.Alert` for
  the legacy `MaterialAlertDialog`s.
- **`testReleaseUnitTest` does not exist**; the unit-test task is `:app:testDebugUnitTest`.

## Export / Import and the 保存復元 contract

- Filenames follow the **mandatory family convention** —
  `shiroikuma-jinsoningen_<yyyy-MM-dd_HH-mm-ss>.zip`, no version and no suffix — because every
  sister app backs up into one directory. `.part` files are never treated as the latest backup.
- Categories: `ui`, `settings`, `repositories`, `custom_buttons`, `fonts` — all on by default.
  Settings restore goes through upstream's own `SettingsRepository.import`, so its merge semantics
  stay upstream's; repositories serialise through upstream's Jackson helpers with ids stripped.
- The panel is the **Kōjiki sheet format** with the **ArcaneChat button bar**; the folder box and
  the UI page's summary row are **red until a directory is set**.
- On success the result dialog closes the whole chain (dialog → panel → UI page); failures leave
  the panel open. Import offers "Restart now" / "Later".
- The contract itself is `~/git/shiroikuma-jiyusagyoban/sister-app-contract-backup-automation-hand-off.md`.
  Re-read it before touching `jinsoningen/automation/` — every constraint there is hard-won
  (the receiver must not run the export, the reply must be a fresh broadcast, `items` absent means
  our default set, `current` is the position not the finished count, cancel must delete the `.part`).

## Architecture (upstream Droid-ify)

Single `:app` module, `com.looker.droidify` namespace, Hilt DI throughout. The UI is **mid-migration**:
newer screens are Compose (`compose/`), older ones are Fragments + ViewBinding (`ui/`). Both are live —
when de-branding or theming, check both trees.

**`MainActivity` (Fragments) is the launcher activity.** `MainComposeActivity` exists but is **not in
the manifest** — it is upstream's work-in-progress migration and is unreachable. `SettingsFragment`
is a `ComposeView` hosting the Compose `SettingsScreen`, so that screen *is* live; `AppListScreen`
is not.

| Area | Where |
| --- | --- |
| Compose screens (settings, repo detail, app detail, app list) | `app/src/main/kotlin/com/looker/droidify/compose/` |
| Legacy Fragment/ViewBinding UI — the live shell | `app/src/main/kotlin/com/looker/droidify/ui/` |
| Repository sync + index parsing (V1/V2) | `sync/`, `index/` |
| Room DB + legacy SQLite | `data/local/`, `database/` |
| Installers (session, root, Shizuku, legacy) | `installer/` |
| Networking (OkHttp/Ktor, User-Agent) | `network/`, `di/NetworkModule.kt` |
| Settings storage (DataStore proto) | `datastore/` |
| Deep links | `utility/common/Deeplinks.kt` |

**`droidify.app` is functional infrastructure, not branding** (白い熊, 2026-08-04). It is both the
incoming deep-link host and the base of the shareable app links the app builds
(`https://droidify.app/app/?id=…`). We own no replacement domain, so it stays — same call
shiroikuma-mise made for Aurora's dispenser URL. Nothing user-visible reads "Droid-ify" because of it.
The bundled F-Droid repository addresses, IzzyOnDroid endpoints and the `com.looker.droidify` package
name are likewise working parts, not name badges. The **FoxyDroid credit** in Settings stays too —
third-party attribution, not upstream's own branding.

## Hard rules

- **Build proactively** after any coherent code change — never ask "shall I build?" — and deliver via
  the global `/after-build` skill. Delivery goes to exactly ONE target.
- **Never commit/push unprompted.** Wait for 白い熊's explicit "Push". `custom` is rebased on every
  upstream sync, so after a sync it pushes with `git push --force-with-lease origin custom`.
- **`/upstream-new-version` must show the proceed-gated upstream-changes table before rebasing.**
  This is a standing requirement, not a nicety — see the skill.
- **Never rename the `com.looker.droidify` namespace.** Only `applicationId` differs; renaming would
  make every rebase a mass-conflict.
- `keystore.properties`, `*.jks` and `local.properties` are gitignored — never commit them.
- **Never run `adb` inside the sandbox** — always `dangerouslyDisableSandbox: true`, or `adb devices`
  reports no device. Disconnect wireless adb at the end of every delivery batch.
- **Never delete an APK from the phone.** Push the new one and leave every earlier one in
  `/sdcard/tmp/`.
- Distinguish **branding** from **infrastructure** when de-branding: the app name, the source-code
  link, the HTTP User-Agent and any "about this app" text are ours; repository addresses, the
  `droidify.app` deep-link host, index endpoints and installer integrations are Droid-ify's working
  parts and stay.
- **Nothing alarmist.** Upstream's red battery-optimisation banner was removed (白い熊, 2026-08-04);
  the prompt lives on as a quiet **Background access** row in the UI page. Reserve `warnColor` for
  things that genuinely warrant it — an unset backup folder, a failed export.

## Releases so far

Base `v0.7.4` throughout. Tags carry no leading `v` and the counter is zero-padded.

| Tag | What it added |
| --- | --- |
| `0.7.4+008` | first published build — the UI page, Export/Import, the automation contract, the legacy-View theming, the icon, de-branding, dialog borders, the Shizuku preference |
| `0.7.4+009` | the Light theme as a real escape hatch |
| `0.7.4+010` | imported fonts reach code-built adapter views; knob changes refresh live legacy screens |

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the
last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
