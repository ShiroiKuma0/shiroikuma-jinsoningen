# Changelog — 白い熊 人造人間

Everything this fork adds on top of stock [Droid-ify](https://github.com/Droid-ify/client).
Upstream's own changelog lives in `metadata/en-US/changelogs/`.

## 0.7.4+011 — 2026-08-06

### "Update all" reports what it is doing

- The button was never dead: a tap enqueued the downloads and the installs ran. Nothing on screen
  said so, though — the rows carry no download state, there is no snackbar, and the button itself
  never changed — so the only feedback was a notification, and a row that vanished tens of seconds
  later. It read as a dead button.
- **The button answers the tap.** It switches to "アップデート中…" immediately, before the settings
  read and the database query that precede the first download, and is disabled while work is in
  flight — a second tap used to re-queue everything mid-flight. It holds that state for eight
  seconds on the strength of the tap alone; real queue state takes over as soon as there is any,
  and holds until the last install finishes.
- **Every row carries its own state**, in the summary line with a slim bar along the bottom edge of
  the card: download queued → connecting → downloading, `4.2 MB / 12.0 MB` against a determinate
  bar → install queued → installing. Going idle restores the row's own summary. The list adapter is
  shared, so the Explore and Installed tabs show it too. Every row string is upstream's own and
  already translated; only the button's "Updating…" is new.
- `AppListProgress` folds the download queue and the install queue into one state. `awaitingInstall`
  covers the seam between them: a finished download leaves `DownloadService.State.Success` standing
  while the file waits for `InstallManager` to claim it, and during an "update all" that wait lasts
  as long as the install ahead of it — the row would otherwise look idle at exactly the moment it
  sits between two queues. A package leaves the set the moment the installer reports any state of
  its own, so a failed install — whose entry `SessionInstallerReceiver` removes again — cannot
  strand a row on "queued".
- Progress is reported per read, far faster than a list can usefully repaint, so the download state
  is sampled at 200 ms and rows rebind through a RecyclerView payload: a tick touches the status
  line and the bar, never the icon load.
- Material refuses to switch a **visible** progress indicator into indeterminate mode, and every
  download→install transition asks for exactly that; the row hides the bar across the switch.
- The bar is painted by the fork's own paint routine, called on the leaf: AppCompat substitutes no
  progress indicator, so the tinting inflater never sees one. Under Light it keeps stock colours.

## 0.7.4+010 — 2026-08-04

### Imported fonts reach the last screens that were missing them

- Several adapters build their views **in code** rather than inflating them —
  `AppDetailAdapter`, `CustomButtonsAdapter`, the app-list header, the screenshot strip, the section
  title — so they never pass the tinting inflater. Their *colours* were always right (they resolve
  them in the view-holder constructor through the patched `getColorFromAttr`); their **typeface was
  not**, so an imported font skipped them entirely, on every path.
- `applyTypography()` sets typeface, slant and letter spacing and **never colour**. That constraint
  is what makes it safe to run over adapter-built rows at any moment: it cannot undo a distinction
  an adapter drew, which is why a blanket repaint was the wrong tool. It is hooked to each
  RecyclerView's child-attach, so rows take the font as they scroll into view.
- **Knob changes now reach live legacy screens.** The UI state fires a plain change listener — the
  legacy Views cannot observe Compose state — and `MainActivity` repaints the visible hierarchy and
  rebuilds its RecyclerViews from it, carrying scroll position across. Detaching and re-attaching
  the adapter is what forces `onCreateViewHolder` to run again; `notifyDataSetChanged()` alone
  re-binds the holders it already has. The refresh is posted, so dragging a slider coalesces onto
  the next frame instead of rebuilding a list per pixel, and it is unregistered in `onDestroy`
  because the UI state is a process-wide singleton.

## 0.7.4+009 — 2026-08-04

### The Light theme is a real escape hatch

- **Light means upstream's light, dark means the house black-yellow.** Picking Light in
  Settings › Theme now hands back Droid-ify's own light style untouched. `SYSTEM` and
  `SYSTEM_BLACK` follow the system honestly — light in day mode, the house theme in night mode —
  and `DARK` / `AMOLED` are always the house theme.
- Diverting the theme resolution alone would not have worked: the house look lives in four places,
  and the View tinter would have repainted a light theme black a moment after the picker set it.
  All four now read one live flag, published by `MainActivity` from the same settings flow that
  calls `setTheme`:
  - `getThemeRes` returns upstream's `Theme_Main_{Light,DynamicLight}`;
  - `Context.getColorFromAttr` answers nothing, so every lookup falls through to the stock theme;
  - the tinting inflater, the per-fragment paint and the window background all no-op;
  - `DroidifyTheme` defaults `useHouseTheme` off, so the Compose screens use upstream's schemes.
- Under Light the UI page's knobs are still stored but nothing reads them, so the **Colours**
  section says so plainly instead of leaving the sliders looking dead — dim text, not a warning
  colour. Switching back to Dark or Amoled restores every setting exactly as it was.

## 0.7.4+008 — 2026-08-04

The first published build. Base: upstream release tag `v0.7.4` (`versionCode` 740).

### Major features

- **The 白い熊 人造人間 UI page** — every colour, font and size on one page, reachable from Settings
  or by **long-pressing the toolbar cog** on the main screen (which opens it directly, skipping
  Settings). Sections: Export / Import · Colours (Surfaces · Text · Accents and lines) · Typography
  (Body · Headings · Secondary lines · Spacing) · Shape and borders · Icons · Density · Reset.
- **Live theming.** Every write lands in SharedPreferences *and* in Compose state, so dragging a
  slider repaints the app underneath the page. Each group ends in a preview, and the page is themed
  by the values it edits.
- **RGBA colour picker** with four channel sliders over a live preview drawn on the real app
  background (so alpha reads honestly), above a one-click row of the colours picked before — seeded
  with the house palette so it is useful on a fresh install.
- **Imported fonts.** Any `.ttf`/`.otf` is copied into app storage and offered app-wide; the picker
  draws **every candidate in its own glyphs**, with a Remove action per imported file. Built-ins
  (system, sans, serif, monospace, cursive) sit above them.
- **Sliders that reach zero.** Border thickness, divider thickness, corner roundness and letter
  spacing all treat 0 as "draw nothing", not "fall back to the Material default".
- **Export / Import** — a category ZIP covering the UI knobs, app settings, repositories, custom
  app-detail buttons and imported fonts, written to a settable SAF folder.
- **The 保存復元 automation contract**, so 白い熊 自由作業盤 can back this app up headlessly.

### Export / Import

- Filenames follow the family convention — `shiroikuma-jinsoningen_<yyyy-MM-dd_HH-mm-ss>.zip`, no
  version and no suffix — because every sister app backs up into one directory.
- **Atomic writes**: a `.part` document first, renamed only once the archive is complete and deleted
  on any failure, so a killed export never leaves something indistinguishable from a real backup.
- The panel is the Kōjiki sheet format: one bordered rounded box inset from the screen edges, a
  centred bold title over a dim intro, the backup folder as its own bordered box, a bold **Select
  all** over the checklist between thin dividers.
- The chosen folder is **queried for the newest backup when the panel opens**, and its name and
  timestamp shown; `.part` files are never mistaken for the latest backup.
- **Red until set**: the folder box and the UI page's own summary row both read in the warning
  colour while no backup directory is configured, and in the accent colour once it is.
- **ArcaneChat button bar** — Cancel alone on the left, Import and Export grouped right, all fully
  round pills (black fill, yellow stroke, yellow text).
- Result dialogs carry a yellow border; failures carry the warning colour instead. A **successful**
  export closes the info dialog, the panel beneath it and the UI page; a successful import does the
  same on "Later", or relaunches the app on "Restart now". Failures — "Export failed…", "No
  categories selected." — leave the panel open so they can be corrected.
- Import **merges per key** and skips categories the archive does not carry. Settings are restored
  through upstream's own `SettingsRepository.import`, so its merge semantics (favourites unioned
  rather than replaced) stay upstream's.

### 保存復元 automation contract

- Three token-gated actions on **one exported receiver**: `EXPORT_STATE`, `LIST_CATEGORIES`,
  `CANCEL_EXPORT`. No `android:permission` — the caller cannot hold one, so the token is the gate.
- The receiver does **no work**: it checks the switch and token, answers `LIST_CATEGORIES` inline,
  and hands `EXPORT_STATE` to a `dataSync` **foreground service**. A manifest receiver that overruns
  the broadcast window gets the app ANR'd and killed mid-write.
- `items` absent means **our default set**, not everything. Unknown ids are rejected up front, where
  the error is still cheap to report.
- **Progress with real counts**, never a percentage — `区分 3/5 — …` — carrying the category `id` so
  the caller's panel can move its highlight, with the position (not the finished count) as `current`.
- Exactly **one terminal reply**, guarded by an `AtomicBoolean`, sent as a fresh broadcast with
  `FLAG_INCLUDE_STOPPED_PACKAGES` — never a Binder, which EMUI will not carry reliably.
- `ERROR:no-storage-access` is returned from an explicit `isExternalStorageManager()` check *before*
  touching an absolute `path`, rather than discovered by failing halfway.
- **Cancel** sets a volatile flag the writer checks at each category boundary, deletes the `.part`, and
  replies `ERROR:cancelled`. It sends no reply of its own, and is a silent no-op when nothing is
  running or the `reply_id` is for another run.
- A partial wakelock guards longer exports (EMUI dozes the CPU with the screen off), always released
  in a `finally`. The in-progress guard is a process-local `AtomicBoolean` — never persisted, so a
  crash cannot wedge the app.
- The switch defaults **off**; the 24-byte SecureRandom hex token is minted lazily, compared
  constant-time, and lives in its own preferences file so it can never travel inside a backup ZIP.
- Both rows sit **inside the Export / Import section** — a backup feature lives where backup lives,
  and every sister app looks the same. Tap the token to copy it; Regenerate is on the right.

### UI & theming

- **House black-yellow by default** — black background and surfaces, pure `#FFFF00` text, borders,
  dividers and icons, dimmed yellow for secondary lines, and a distinct warning colour.
- The Compose screens derive their `ColorScheme`, `Typography` and `Shapes` from the knobs.
- The **legacy View screens** are covered three ways, since an Android theme is resource-backed and
  cannot carry runtime values:
  - `Theme.Main.Jinsoningen` is the floor. It inherits `Theme.Main.Amoled`, so upstream's widget
    styles and text appearances still come through; only the colours are ours.
  - A tinting `LayoutInflater.Factory2` on `MainActivity`, plus a per-fragment paint in
    `onFragmentViewCreated`, applies the **current** knob values — toolbars, tabs, cards, buttons,
    switches, text fields, progress bars, dividers, icon tints, and the font family, weight, slant
    and letter spacing.
  - `Context.getColorFromAttr` answers from the knob mapping, redirecting every colour the legacy
    Kotlin resolves; the two `MaterialColors.getColor` call sites route through the same mapping.
- **Every theme choice resolves to the house theme.** `MainActivity` calls `setTheme()` at runtime,
  *after* the manifest theme — so without this the whole View shell kept Droid-ify's green palette.
  Upstream's light/dark/amoled/dynamic resolution is preserved verbatim as `stockThemeRes`.
- **Dialogs get a yellow edge.** A drop-in `AlertDialog` with Material's signature carries it, so a
  call site adopts it by changing one import; adopted by the installer/theme/sort pickers, the
  delete-repository prompt, the custom-button list and editor, and the text-input prompts. The
  legacy `MaterialAlertDialog`s get the same edge once, through `@style/Theme.Alert`.
- **Tight rows.** Row padding defaults to 4 dp, with generous spacing only between top-level groups.

### Icon & branding

- **Black-yellow traced launcher icon** — upstream's droid-head-and-download-arrow redrawn as
  stroke-only line-art, `#FFFF00` on `#000000`, at upstream's own stroke ratios (dome stroke 29 % of
  its radius, arrow 0.76 of the dome, antennae at 45°) so the silhouette still reads as the same app.
  `design/shiroikuma-jinsoningen-icon.svg` is the source; the adaptive foreground, monochrome layer,
  all nine mipmaps, the play-store icon and the metadata icon are generated from it.
- The adaptive foreground **fills the 72×72 safe zone** rather than sitting inside it — an inset icon
  renders visibly smaller than its neighbours in a launcher or file list.
- **De-branded**: the app label, the app-list title, the Settings credits row and its source link,
  the HTTP User-Agent, the settings/repo backup filenames, `BuildConfig.VERSION_NAME` and the Gradle
  project name are all ours. The FoxyDroid credit is kept — third-party attribution, not upstream's
  own branding.
- Kept as **infrastructure, not branding**: the `droidify.app` deep-link host and share links,
  bundled repository addresses, and the IzzyOnDroid endpoints.

### Integrations & behaviour

- **Shizuku prefers 白い熊 雫.** The installer resolves `shiroikuma.shizuku` first, then whichever
  package declares the stock client permission, then `moe.shizuku.privileged.api`. Ours installs
  beside stock Shizuku and therefore cannot define the stock permission (duplicate-permission
  install failure), declaring `af.shizuku.plus.permission.API_V23` instead — which the app now also
  requests, so our Shizuku can grant it.
- **Settings' red battery-optimisation banner removed.** It was alarmist for what is a preference.
  The prompt stays reachable as a quiet **Background access** row in the UI page beside the
  automation switch, which re-reads its state on resume (the system prompt returns no result).
- **A real settings cog** on the main toolbar, replacing the icon-less overflow entry — tap for
  Settings, long-press for the UI page.

### Packaging

- `applicationId` `shiroikuma.jinsoningen`; code namespace `com.looker.droidify` left untouched so
  rebases stay small. App label `白い熊 人造人間`.
- Fork versioning: `versionName = "<upstream>+<NNN>"` and `versionCode = <upstream code> * 10000 + N`,
  both derived from upstream's own literals so a rebase brings the new base in automatically.
  `BUILD_NUMBER` bumps per build and resets to 1 on every upstream sync.
- Release signed from a gitignored `keystore.properties`; the `buildFork` task assembles, copies the
  APK to `~/tmp/` and bumps the counter.
- Upstream tracked by **release tag** (`main` mirrors `v0.7.4`), with all work on `custom`.
- `CLAUDE.md` plus `build-apk` and `upstream-new-version` skills; the latter presents a
  proceed-gated table of upstream's changes before anything is rebased.
