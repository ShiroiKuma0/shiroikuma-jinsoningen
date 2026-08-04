---
name: upstream-new-version
description: Sync the shiroikuma-jinsoningen fork onto a new upstream release tag of Droid-ify/client — advance main to the new tag, rebase custom, reset BUILD_NUMBER, build the new +001. Use when 白い熊 says a new upstream version is out, asks to check/update/sync to upstream, or to rebase custom onto the latest Droid-ify release. ALWAYS present the proceed-gated upstream-changes table BEFORE rebasing.
---

# Sync shiroikuma-jinsoningen onto a new upstream Droid-ify release

This fork tracks [Droid-ify/client](https://github.com/Droid-ify/client) — a fast material-design
F-Droid client. `main` mirrors the newest upstream **release tag**; `custom` carries our patches and
is rebased onto it.

**We follow release TAGS, not the branch tip** (白い熊, 2026-08-04). Droid-ify tags every release
(`v0.7.4`, `v0.7.3`, …) while its `main` keeps moving in between — at fork time `main` was already 90
commits past `v0.7.4`. Basing on tags means every base is a state upstream itself called finished, and
the upstream version literal really moves on each sync. So a sync happens when a **new tag** appears,
not when commits land on `main`. The global `/git-versioning` skill does **not** apply here — we use
the plain `+NNN` versionName.

## Branch / remote model

| Branch | Role | Update mode |
| --- | --- | --- |
| `main` | Mirrors the newest upstream release tag. No fork work here. | reset to the new tag each sync |
| `custom` | Our patches; the working branch and the GitHub default branch. | rebased onto `main` each sync |

`origin` = `git@github.com:ShiroiKuma0/shiroikuma-jinsoningen` (push). `upstream` =
`https://github.com/Droid-ify/client` (fetch only; push URL is `DISABLED`).

## Steps

1. **Fetch upstream and see whether a new release exists:**
   ```bash
   git fetch upstream --tags
   git tag --sort=-version:refname | head -5          # newest upstream tags
   git describe --tags --exact-match main 2>/dev/null # the tag main currently sits on
   git show <newtag>:app/build.gradle.kts | grep -E 'latestVersionName|versionCode = '
   ```
   If the newest tag is the one `main` already points at, stop and report "already current" — do
   **not** sync just because commits landed on `upstream/main`.

2. **PROCEED GATE — present the upstream changes as a table, then STOP.** 白い熊's standing
   requirement: **before** anything is rebased, show what the new upstream release actually brings.

   Gather the material from all of these — they complement each other:
   ```bash
   git log --oneline --no-merges <oldtag>..<newtag>      # what really landed
   git log --merges --format='%s' <oldtag>..<newtag>     # which PRs were merged
   git diff --stat <oldtag>..<newtag>                    # where the weight is
   gh release view <newtag> -R Droid-ify/client          # upstream's own release notes
   ls metadata/en-US/changelogs/                         # per-versionCode store notes
   ```
   Weblate translation merges are a large share of Droid-ify's commit traffic — fold them into one
   row, do not list them individually.

   Render **one markdown table**, ordered most-significant first, in this exact shape:

   | # | Change | Kind | What it means in the app | Touches our patches? |
   | --- | --- | --- | --- | --- |
   | 1 | … | Feature / Fix / UI / Perf / Refactor / Dependency | one clear sentence, in plain terms | No — or: yes, `<file>` (our icon / label / version block …) |

   Rules for the table:
   - **Every** notable upstream change gets a row — do not summarise into "various fixes". Group only
     genuinely trivial churn (translation drops, dependency bumps, typo fixes) into a single final
     row, and say how many were folded in.
   - The **last column is the point**: flag every change landing in a file we patch. The list, so a
     hit is recognised rather than rediscovered:
     `app/build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`, `.gitignore`,
     `AndroidManifest.xml` (our receiver/service/permissions), the launcher icon resources,
     `values/strings.xml` · `styles.xml` · `base_theme.xml` · `ids.xml`,
     `MainActivity.kt` (theme application, the inflater install, the fragment paint, the change
     listener, `navigateJinsoningenUi`), `datastore/extension/Preferences.kt` (`getThemeRes`),
     `compose/theme/Theme.kt`, `utility/common/extension/Context.kt` (`getColorFromAttr`),
     `ui/tabsFragment/TabsFragment.kt` (the cog), `ui/settings/SettingsFragment.kt`,
     `compose/settings/SettingsScreen.kt` (the UI-page entry, the credits row, the removed banner),
     the five Compose files importing our `AlertDialog`,
     `installer/installers/InstallerPermission.kt`, `ui/repository/RepositoriesAdapter.kt`,
     `di/NetworkModule.kt`, and `utility/notifications/UpdateNotification.kt`.
     Those are the rebase conflicts, predicted in advance. Our own `jinsoningen/` trees are
     fork-only and will not conflict — but they can be *broken* by upstream refactors, which is
     what step 6 checks.
   - Below the table, add the base line: old tag → new tag, old `versionCode`/`versionName` → new, and
     the resulting fork version (`<newVersionName>+001`, code `<newVersionCode>*10000+1`).

   **Then stop and wait for 白い熊's explicit go-ahead.** Do not move `main`, do not rebase, do not
   build until they say proceed. If they decline, nothing has been touched.

3. **Advance `main` to the new release tag** (mirror; no fork work lives here):
   ```bash
   git checkout -B main <newtag>
   git push --force-with-lease origin main
   ```
   (`--force-with-lease` because `main` is reset to a tag, which is not always a fast-forward.)

4. **Rebase `custom`:**
   ```bash
   git checkout custom
   git rebase main
   ```
   Resolve conflicts so **all** our customizations survive (table in step 6). Upstream's
   `latestVersionName` / `versionCode` literals in `app/build.gradle.kts` flow in automatically — keep
   **upstream's** values for those two lines; our fork lines sit right after them and derive from
   them, so they are never edited by hand.

   If upstream restructured a screen we de-branded, port our change to the new structure rather than
   forcing the old diff. Droid-ify is **mid-migration from Fragments to Compose**, so a screen we
   patched under `ui/` may have been rewritten under `compose/` — look for the replacement before
   concluding our change was dropped. Re-check for **new** upstream branding the rebase introduced:
   new strings saying "Droid-ify", new `github.com/Droid-ify` links, a new About entry.

5. **Reset the build tail:** in `gradle.properties`, set **`BUILD_NUMBER=1`** — a new upstream base
   starts its `+N` at 1.

   Consequence to expect: if upstream shipped a release *without* bumping `versionCode`, the reset
   lowers our `versionCode` below the installed build. Deliver such a build with
   **`adb install -r -d`** (`-d` allows a version-code downgrade); plain `-r` fails with
   `INSTALL_FAILED_VERSION_DOWNGRADE`. Never `adb uninstall` to work around it — that wipes the
   repository list, the sync state and the installed-app database.

6. **Verify our customizations are intact after the rebase:**

   | What | Expected | Where |
   | --- | --- | --- |
   | Installed app id | `shiroikuma.jinsoningen` | `app/build.gradle.kts` → `defaultConfig.applicationId` |
   | Code namespace | `com.looker.droidify` (**unchanged** from upstream) | `app/build.gradle.kts` → `namespace` |
   | App label | `白い熊 人造人間` | `application_name` in `app/src/main/res/values/strings.xml` |
   | Fork version block | upstream literals + `forkVersionName` / `forkVersionCode` lines after them | `app/build.gradle.kts` |
   | `BuildConfig.VERSION_NAME` | `"$forkVersionName"`, not upstream's `"v$latestVersionName"` | `app/build.gradle.kts` → `buildTypes { all { } }` |
   | Signing config | `keystore.properties` block + `signingConfig` on `release` | `app/build.gradle.kts` |
   | `buildFork` task + `archivesName` | present at the end of the script | `app/build.gradle.kts` |
   | Build tail | `BUILD_NUMBER=1` | `gradle.properties` |
   | Black-yellow icon | traced line-art; adaptive foreground **fills** the 72×72 safe zone | `design/…icon.svg`, `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `values/ic_launcher_background.xml`, `mipmap-*/` |
   | Our source tree | present and compiling | `jinsoningen/`, `compose/jinsoningen/`, `ui/jinsoningen/` |
   | House theme | `Theme.Main.Jinsoningen` + `getThemeRes` returning it for every non-Light choice | `values/jinsoningen_theme.xml`, `datastore/extension/Preferences.kt` |
   | Legacy-View theming | `install()` called **before** `super.onCreate`; fragment paint + change listener registered, listener removed in `onDestroy` | `MainActivity.kt` |
   | Attr redirect | `JinsoningenViewTheme.attrColorStateList` consulted first | `utility/common/extension/Context.kt`, `ui/repository/RepositoriesAdapter.kt` |
   | Toolbar cog | `R.id.toolbar_settings` item + its long-press handler | `ui/tabsFragment/TabsFragment.kt`, `values/ids.xml` |
   | UI page entry | `onJinsoningenUiClick` wired through to `navigateJinsoningenUi()` | `compose/settings/SettingsScreen.kt`, `ui/settings/SettingsFragment.kt`, `MainActivity.kt` |
   | Dialog borders | every Compose dialog imports our `AlertDialog`, not Material's | `compose/**` — `grep -rn "^import androidx.compose.material3.AlertDialog$"` must return nothing |
   | Legacy dialog style | yellow-edged background | `values/styles.xml` → `Theme.Alert`, `drawable/jinsoningen_dialog_background.xml` |
   | Automation contract | receiver (3 actions, exported) + `dataSync` service + `MANAGE_EXTERNAL_STORAGE` | `AndroidManifest.xml`, `jinsoningen/automation/` |
   | Shizuku permissions | APK carries **both** `af.shizuku.plus…` (ours, in the manifest) and `moe.shizuku.manager…` (merged by the API library) — check the APK, not the manifest: `aapt2 dump permissions <apk> \| grep shizuku` | `AndroidManifest.xml` |
   | Shizuku preference | `shiroikuma.shizuku` resolved **before** the stock permission/package | `installer/installers/InstallerPermission.kt` |
   | No alarmist banner | no `WarningBanner` at the top of Settings; Background access row present in the UI page | `compose/settings/SettingsScreen.kt`, `compose/jinsoningen/JinsoningenUiScreen.kt` |
   | De-branding | no "Droid-ify" in user-visible text, our GitHub link in Settings, our User-Agent | `values/strings.xml`, `compose/settings/SettingsScreen.kt`, `di/NetworkModule.kt`, `settings.gradle.kts` |
   | Kept as functional | `droidify.app` deep-link host + share links, bundled repo addresses, IzzyOnDroid endpoints, the FoxyDroid credit | `utility/common/Deeplinks.kt`, `AndroidManifest.xml`, repo defaults |
   | Committed agent files | `CLAUDE.md`, `.claude/` un-ignored; signing material ignored | `.gitignore` |

   Sanity check that the build script still evaluates, then that it compiles and the tests pass:
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:tasks --console=plain | head
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew :app:testDebugUnitTest --console=plain
   ```
   (`testReleaseUnitTest` does not exist — the unit-test task is the debug one.)

   **Upstream is mid-migration from Fragments to Compose**, so a screen we patched under `ui/` may
   have been rewritten under `compose/` — look for the replacement before concluding our change was
   dropped, and vice versa. In particular: if upstream wires `MainComposeActivity` into the manifest
   and retires `MainActivity`, the whole `JinsoningenViewTheme` layer becomes dead weight and the
   Compose path is the only one left — that is a re-plan, not a conflict resolution. Stop and tell
   白い熊.

7. **Build the new `+001`** via the **build-apk** skill
   (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork < /dev/null`),
   then deliver it via the global **/after-build** skill (no transfer prompt). This is the first build
   of the new upstream base.

8. **Stop.** Let 白い熊 test. Commit/push only on their explicit **"Push"**. `custom` was rebased, so
   it needs `git push --force-with-lease origin custom`.

## Hard rules

- **Never rebase before the step-2 table has been shown and approved.**
- Sync on a **new tag**, not on commits landing in `upstream/main`.
- Never `adb uninstall` — it destroys the repository list and the local database.
- Never commit/push unprompted; wait for "Push".
- `keystore.properties`, `*.jks` and `local.properties` are gitignored — never commit them.
- Never rename the `com.looker.droidify` namespace.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
