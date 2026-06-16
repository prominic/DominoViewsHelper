# CLAUDE.md — DominoViewsHelper

Entry point for AI agents / developers. **Read the linked docs before changing
things** — this file is just the map plus the rules that must never be broken.

## What this is

A HCL Domino **Java server add-in** (`runjava`) that refreshes (re-indexes) views on
a schedule, driven by a configuration database (`viewshelper.nsf`). Entry point:
[`ViewsHelper`](src/main/java/ViewsHelper.java); worker:
[`EventViews`](src/main/java/EventViews.java).

## Where to look

| Doc | Use it for |
|-----|------------|
| [DEVELOPMENT.md](DEVELOPMENT.md) | Architecture, the GJA base-class contract, the "events idea", config-form contract, build/release. **Read before editing code.** |
| [BUSINESS.md](BUSINESS.md) | How it works and how to operate it — e.g. registering a database for view refresh, console commands. |
| [README.md](README.md) | Install / build quickstart. |

## Non-negotiable rules (details in DEVELOPMENT.md)

1. **Recycle every Domino object** via `DominoUtils.recycle(...)` — including
   non-obvious ones like the `DateTime` from `database.getLastModified()`. Leave
   `m_session` to the base's `terminate()`.
2. **Reuse `net.prominic.gja_v085.utils`** (`DominoUtils`/`FileUtils`/`StringUtils`).
   Don't hand-roll recycling, file IO, or joins.
3. **Follow the GJA pattern:** `super(args)` in the constructor; override
   `runNotesAfterInitialize` / `resolveMessageQueueState` (call `super` first) /
   `showHelpExt` / `showInfoExt` — **not** `showHelp`. Register work via
   `eventsAdd(...)`; `run()` is the single refresh path.
4. **No duplicate / dead code.** One concept, one method.
5. **Form actions call agents** (`@Command([RunAgent]; "(...)")`), never inline
   LotusScript and never a copy of an agent's body.
6. **Releases are cut by pushing a git tag `vX.Y.Z`** (`release.yml`); pushing to
   `main` only validates + uploads an artifact (`build.yml`). Bump `pom.xml`
   `<version>` and keep `getJavaAddinVersion()` / `getJavaAddinDate()` in sync with
   the tag. Build deps are **vendored in [`lib/`](lib/)** — no external downloads.
7. The `NSF/` on-disk project is a **gitignored** local working copy; design of
   record lives in the database (sync via Domino Designer).
