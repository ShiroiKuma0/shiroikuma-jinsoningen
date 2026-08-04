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
skill — this repo has no local copy.

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
| App icon | black-yellow traced line-art (yellow `#FFFF00` on black) | `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`, `mipmap-*/ic_launcher*.png` |
| Version tail | `versionName = "<upstream>+NNN"`, `versionCode = <upstream code>*10000+N` | `app/build.gradle.kts` fork blocks |
| `BuildConfig.VERSION_NAME` | our fork version (`0.7.4+001`), not upstream's `v0.7.4` | `app/build.gradle.kts` → `buildTypes { all { } }` |
| Signing | gitignored `keystore.properties` → `~/.android-keystores/shiroikuma-jinsoningen.jks` (alias `jinsoningen`) | `app/build.gradle.kts` |
| De-branding | our name + our GitHub links everywhere user-visible | `values/strings.xml`, `SettingsScreen.kt`, `NetworkModule.kt` (User-Agent), About/Settings screens |

### Versioning & APK naming

- The upstream base lives in `app/build.gradle.kts` as upstream's own `val latestVersionName = "0.7.4"`
  and `versionCode = 740` literals. Our fork lines sit **immediately after** them and multiply/append,
  so a rebase brings the new base in automatically. **Never hand-edit those two literals.**
- `BUILD_NUMBER` (in `gradle.properties`) is our per-build `N`:
  `versionName = "<upstream name>+<N zero-padded to 3>"` (e.g. `0.7.4+001`),
  `versionCode = <upstream code> * 10000 + N` (plain integer, e.g. `7400001`).
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

## Architecture (upstream Droid-ify)

Single `:app` module, `com.looker.droidify` namespace, Hilt DI throughout. The UI is **mid-migration**:
newer screens are Compose (`compose/`), older ones are Fragments + ViewBinding (`ui/`). Both are live —
when de-branding or theming, check both trees.

| Area | Where |
| --- | --- |
| Compose screens (app list, app detail, repo detail, settings) | `app/src/main/kotlin/com/looker/droidify/compose/` |
| Legacy Fragment/ViewBinding UI | `app/src/main/kotlin/com/looker/droidify/ui/` |
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
name are likewise working parts, not name badges.

## Hard rules

- **Build proactively** after any coherent code change — never ask "shall I build?" — and deliver via
  the global `/after-build` skill. Delivery goes to exactly ONE target.
- **Never commit/push unprompted.** Wait for 白い熊's explicit "Push". `custom` is rebased on every
  upstream sync, so it pushes with `git push --force-with-lease origin custom`.
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

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer, nor a "🤖 Generated with Claude Code" /
Anthropic-attribution line, to commit messages or PR bodies in this repo. End the message at the
last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
