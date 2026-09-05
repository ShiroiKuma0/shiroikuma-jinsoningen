---
name: build-apk
description: Build the signed release APK of 白い熊 人造人間 (shiroikuma-jinsoningen, our Droid-ify F-Droid-client fork) with the buildFork Gradle task, then deliver it automatically via the global /after-build skill. Build PROACTIVELY as soon as a coherent code change compiles — do NOT wait for 白い熊 to say "build it". Also use whenever 白い熊 asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the release APK and deliver it

This is **shiroikuma-jinsoningen** — 白い熊's fork of [Droid-ify](https://github.com/Droid-ify/client),
a fast material-design F-Droid client, renamed to `shiroikuma.jinsoningen` ("白い熊 人造人間") so it
installs side-by-side with upstream. Kotlin + Compose/ViewBinding + Hilt, no native code — so the APK
is universal, no ABI suffix.

## When to build

Build **proactively** — do NOT wait for "build it" and do NOT ask "want me to build?" first. As soon
as a coherent set of code changes compiles, run the steps below. Don't rebuild after every tiny
intermediate edit — build once the change is in a testable state. Skip the build for non-functional
edits (docs, comments).

This removes only the *ask-before-build* wait. The repo's commit/push rules are unchanged: a
commit/push still waits for 白い熊's explicit **"Push"**.

## Steps

1. **Note the output filename.** The version base comes from upstream's own literals in the build
   script, the tail from `gradle.properties`:
   ```bash
   grep -E 'latestVersionName|versionCode = ' app/build.gradle.kts   # upstream's two literals
   grep -E '^BUILD_NUMBER' gradle.properties    # the N used for THIS build, before the task bumps it
   ```
   - APK will be `shiroikuma-jinsoningen_<upstream versionName>+<NNN>.apk`, the counter **zero-padded
     to three digits** — `BUILD_NUMBER=7` → `0.7.4+007`. The padding is applied in
     `app/build.gradle.kts`; `gradle.properties` stores the plain integer.
   - `versionCode` for this build = `<upstream versionCode> * 10000 + BUILD_NUMBER`
     (e.g. `740 * 10000 + 7 = 7400007`).

2. **Build** (Gradle runs on JDK 21, and since upstream `v0.7.7` there is no toolchain declaration,
   so JDK 21 is also what compiles the code):
   ```bash
   JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/home/shiroikuma/android-sdk ./gradlew buildFork < /dev/null
   ```
   (`< /dev/null` guarantees it never blocks on stdin.)
   - `buildFork` runs `assembleRelease` (signed from `keystore.properties`), copies the signed APK to
     `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - It prints `>>> <path>` and `>>> versionCode <n>`. Confirm `BUILD SUCCESSFUL` and take the exact
     filename/code from those lines.
   - If it fails with **`SDK location not found`**, create the gitignored `local.properties` at the
     repo root with `sdk.dir=/home/shiroikuma/android-sdk`.
   - If the APK comes out unsigned, `keystore.properties` is missing from the repo root — see below.
   - **The first build after a fresh clone is slow**: it downloads the Gradle 9.7.1 distribution,
     then runs a full R8 pass. Run
     it with `run_in_background` if it may exceed the foreground timeout; later builds are much faster
     with a warm configuration cache.
   - **`release` is the build type we ship.** `alpha` (`.alpha` suffix) and `debug` (`.debug` suffix)
     are upstream's; don't build them unless 白い熊 asks.

3. **Deliver automatically via the global /after-build skill** — every build, no asking. After the
   signed APK is in `~/tmp/`, invoke **/after-build**: it runs `/adb-check` UNSANDBOXED (a sandboxed
   check falsely reports no device), then `/adb-push` to `/sdcard/tmp/` if a phone is connected,
   otherwise `/scp` to `skhw:~/tmp/`, and announces the filename. Deliver to exactly ONE target.

## Signing

Release signing is non-interactive. `app/build.gradle.kts` reads a `keystore.properties` at the repo
root (gitignored):

```
storeFile=/home/shiroikuma/.android-keystores/shiroikuma-jinsoningen.jks
keyAlias=jinsoningen
storePassword=…
keyPassword=…
```

The keystore is PKCS12/RSA-4096, alias `jinsoningen`, created 2026-08-04, 10000-day validity, store
password = key password. Its password is recorded in
`~/〇/[666] 私資料/[666][27] 暗号/android-keystores.org`, and the `.jks` is backed up to
`~/〇/[666] 私資料/[666][27] 暗号/android-keystores/`. If `keystore.properties` is absent the build
still succeeds but the APK is **unsigned** and will not install.

## Notes / invariants

- **Toolchain:** Gradle wrapper 9.7.1 on JDK 21, which is also what compiles (no toolchain block);
  Android SDK at `~/android-sdk`; `compileSdk 36`, `minSdk 23`; configuration cache on.
- **Minified release** — upstream ships `isMinifyEnabled = true` + `isShrinkResources = true`. If a
  Compose/Hilt/Room class disappears at runtime, the fix is a keep rule in `app/proguard.pro`, not
  turning minification off.
- **Universal APK** — no ABI splits, no native libs, so no `_arm64-v8a` tail in the filename.
- **Config-cache discipline:** anything the `buildFork` task needs must be captured at configuration
  time (see the task's `val`s). Don't touch `layout` / `rootProject` inside `doLast`.
- **Upgrade installs after an upstream sync:** `/upstream-new-version` resets `BUILD_NUMBER` to 1, so
  the first build on a new base can carry a *lower* `versionCode` than what is installed if upstream
  did not bump its own code. Deliver such a build with `adb install -r -d` (`-d` allows the
  version-code downgrade). Never `adb uninstall` to work around it — that wipes the repository list,
  the sync state and the installed-app database.
- **Never commit/push on your own.** Wait for 白い熊's explicit "Push". Build artifacts (`*.apk`),
  `keystore.properties`, `*.jks` and `local.properties` are gitignored.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
