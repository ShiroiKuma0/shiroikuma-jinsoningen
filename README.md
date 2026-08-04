<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 人造人間 icon" />

# 白い熊 人造人間

**A clutter-free F-Droid client, in the house black and yellow.**

A fork of [Droid-ify](https://github.com/Droid-ify/client) — renamed, re-signed and re-badged so it
installs **side-by-side** with upstream (app id `shiroikuma.jinsoningen`).

</div>

---

## What this fork is

Upstream Droid-ify is a fast, Material-design F-Droid client: browse and install from F-Droid
repositories, background auto-updates, Session / Root / Shizuku installers, one-tap custom
repositories, fully offline after the first sync. All of that is unchanged here.

What differs is identity and looks:

| | |
| --- | --- |
| App id | `shiroikuma.jinsoningen` — installs alongside stock Droid-ify |
| Label | 白い熊 人造人間 |
| Icon | upstream's droid-head-and-download-arrow redrawn as stroke-only line-art, pure `#FFFF00` on black |
| Signing | our own key, so our updates install over each other and never over upstream's |
| Branding | our name and our source link everywhere the app names itself |

The code namespace stays `com.looker.droidify` on purpose — only the installed application id
differs. Renaming it would turn every upstream rebase into a mass conflict for no user-visible gain.

## The icon

Upstream's mark traced rather than replaced: the dome is a 242° sweep opening downward, the antennae
sit at 45°, and the download arrow keeps its broad, squat head with its tip dropping just below the
head. Stroke weights are taken from upstream's own vector — the dome at 29 % of its radius, the arrow
at 0.76 of the dome — so the silhouette reads as the same app, in our colours. The source of truth is
[`design/shiroikuma-jinsoningen-icon.svg`](design/shiroikuma-jinsoningen-icon.svg); every launcher
resource is generated from it.

## Versioning

`versionName` is upstream's version plus our build counter — `0.7.4+001` — and `versionCode` is
upstream's code times 10 000 plus that counter (`740 × 10000 + 1 = 7400001`). The counter bumps on
every build and resets to `001` on every upstream sync, so `+N` always reads as "our Nth build on
this upstream base".

## Branches

- **`custom`** — all our work; the default branch.
- **`main`** — mirrors the upstream **release tag** we are based on (currently `v0.7.4`), never any
  fork work.

Upstream is tracked by release tag rather than by branch tip, so every base is a state upstream
itself called finished.

## Building

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFork
```

`buildFork` assembles the signed release, copies it to `~/tmp/` as
`shiroikuma-jinsoningen_<version>.apk`, and bumps the build counter. Signing reads a gitignored
`keystore.properties` at the repo root. Upstream's own [building guide](docs/building.md) still
applies for everything else.

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

Credit for the original client goes to [Droid-ify](https://github.com/Droid-ify/client) and, before
it, [Foxy Droid](https://github.com/kitsunyan/foxy-droid).
