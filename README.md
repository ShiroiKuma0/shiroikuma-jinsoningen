<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 人造人間 icon" />

# 白い熊 人造人間

**A clutter-free F-Droid client that looks like the rest of the house — and backs itself up.**

A fork of [Droid-ify](https://github.com/Droid-ify/client) with **major additions**: a full
black-yellow theming page with live previews, imported fonts, a category backup with an automation
contract that can restore the app's data onto a wiped phone, updates you can watch happen, and a
Shizuku installer that prefers our own Shizuku.

Installs **side-by-side** with Droid-ify (app id `shiroikuma.jinsoningen`).

**📥 Latest release: [`0.7.6+008`](https://github.com/ShiroiKuma0/shiroikuma-jinsoningen/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-jinsoningen/releases)

</div>

---

## 🎨 The 白い熊 人造人間 UI page

Every knob that shapes the app's look, on one page — colours, fonts, weights, sizes, roundness,
border and divider widths, icon size, indent step, row padding, group spacing. It is not a settings
screen that takes effect on restart: each write lands in preferences *and* in Compose state, so
dragging a slider repaints the app underneath you. The page is themed by the very values it edits,
which makes the app its own preview.

Reachable from Settings, or by **long-pressing the toolbar cog** on the main screen — which skips
Settings entirely and opens it directly.

Colours come from an RGBA picker with a one-click row of colours you have used before, over a live
preview of the mix on the real background so alpha reads honestly. The border, divider, corner and
letter-spacing sliders all reach **0 meaning "draw nothing"** — not "draw the Material default".

Sections are laid out in the kxkb grammar: a full-width hairline, then a big bold heading carrying a
**word-width** underline; sub-headings one indent in; rows two levels in, three under a sub-heading.
Padding is tight everywhere except between top-level groups, so the grouping reads instantly.

---

## 🔤 Fonts you bring yourself

Import any `.ttf`/`.otf` and the whole app takes it. The picker renders **every candidate in its own
glyphs** — you choose a font by looking at it, not by reading its file name in the system font.
Imported files are copied into app storage, so a picked font survives the content-uri going stale,
and they travel in the backup.

---

## 💾 Backup, restore, and automation

A category ZIP — the UI knobs, app settings, repositories, custom app-detail buttons, imported fonts
— written **atomically**, `.part` first and renamed only once the archive is complete, so a killed
export never leaves something that looks like a backup. Import merges per key and skips categories
the archive doesn't carry.

The panel is the Kōjiki sheet: one bordered box, the backup folder as its own box (red until it is
set), the newest backup found there shown the moment it opens, a bold **Select all** over the
checklist, and an ArcaneChat button bar — Cancel alone on the left, Import and Export grouped right,
all fully round pills.

It also implements the **保存復元 automation contract, v2**, in two halves.

**The batch half** lets 白い熊 自由作業盤 back this app up headlessly: three actions on one exported
receiver, the export run in a foreground service rather than the broadcast window, progress reported
with real counts, and a cancel that deletes the partial file.

**The data door** lets 白い熊 応用管理 back the app up *with its data* and put it back on a wiped
phone — a `ContentProvider` that identifies its caller by exact package name, uid and a pinned
signing certificate, and moves the archive through a file descriptor the caller opens. Restoring is
possible **only** through that door, never by broadcast: an import overwrites the app's data, and the
receiver above deliberately has no permission on it.

Since v2 there is **nothing to turn on and nothing to paste** — the switch ships on and the
authorization token is opt-in, because a pasted secret cannot survive the wipe this feature exists to
recover from. Both switches, and the token when you ask for one, live in the Export / Import section.

---

## 🖤 The house look, everywhere

The app is mid-migration from Fragments to Compose, so the theming reaches both. The Compose screens
take colour, typography and shape straight from the knobs. The legacy View screens are covered three
ways: a house theme as the floor, a tinting `LayoutInflater.Factory2` plus a per-fragment paint for
the live values, and a patched attribute lookup that redirects every colour the older Kotlin
resolves.

Dialogs get a **yellow edge** — on a black ground a borderless dialog has no edge at all and reads as
text floating over the page it covers. A drop-in `AlertDialog` carries it, so a screen adopts the
styling by changing one import.

And the launcher icon is upstream's droid-head-and-download-arrow redrawn as stroke-only line-art,
pure `#FFFF00` on black, traced at upstream's own stroke ratios so the silhouette still reads as the
same app.

---

## ⬇️ Updates you can watch

**Update all** used to be a button you pressed into silence: the work started, but nothing on screen
moved. The rows carry no download state upstream, there is no snackbar, and the button never
changes — so the only sign of life was a notification, and a row that disappeared half a minute
later.

Now the button answers the tap at once and stays disabled while the queue runs, and **every row
narrates its own share of the work**: queued, connecting, downloading against a real progress bar
with the byte count beside it, waiting on the installer, installing. When a package goes idle the
row gets its summary back, so a list that finishes while you are looking at it simply goes quiet.

---

## 🔌 Shizuku, ours first

The Shizuku installer looks for **白い熊 雫 (`shiroikuma.shizuku`) first**, and only falls back to
stock Shizuku. That needs asking by package: our build installs beside stock Shizuku, so it cannot
define the stock client permission (that would fail install with a duplicate-permission error) and
declares its own instead — meaning neither the package name nor the permission upstream resolves by
could ever find it.

---

## Built on Droid-ify

A fork of [Droid-ify](https://github.com/Droid-ify/client) (app id `shiroikuma.jinsoningen`, so it
coexists with the official build). Droid-ify is a fast, Material-design F-Droid client — browse and
install from F-Droid repositories, background auto-updates, Session / Root / Shizuku installers,
one-tap custom repositories, fully offline after the first sync — and all of that is unchanged here.
Credit for the original client goes to Droid-ify and, before it,
[Foxy Droid](https://github.com/kitsunyan/foxy-droid). The code remains GPL-3.0-or-later.

The code namespace stays `com.looker.droidify` on purpose — only the installed application id
differs. Renaming it would turn every upstream rebase into a mass conflict for no user-visible gain.

## Versioning and branches

`versionName` is upstream's version plus our build counter — `0.7.6+001` — and `versionCode` is
upstream's code times 10 000 plus that counter (`760 × 10000 + 1 = 7600001`). The counter bumps on
every build and resets to `001` on every upstream sync, so `+N` always reads as "our Nth build on
this upstream base".

- **`custom`** — all our work; the default branch.
- **`main`** — mirrors the upstream **release tag** we are based on (currently `v0.7.6`).

Upstream is tracked by release tag rather than by branch tip, so every base is a state upstream
itself called finished.

## Building

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork
```

`buildFork` assembles the signed release, copies it to `~/tmp/` as
`shiroikuma-jinsoningen_<version>.apk`, and bumps the build counter. Signing reads a gitignored
`keystore.properties` at the repo root; without it the build still succeeds but the APK is unsigned.
Gradle runs on JDK 21, while the build itself declares a JDK 17 toolchain that is downloaded on
first use. The APK is universal — the app has no native code, so there are no ABI splits.

## Licence

GPL-3.0-or-later, as upstream — see [`LICENSE`](LICENSE).

```
Copyright (C) 2025 LooKeR

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
