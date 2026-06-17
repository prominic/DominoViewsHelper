# DEVELOPMENT.md — DominoViewsHelper

Developer guide: architecture, the framework contract, conventions, and
build/release. For operating the app see [BUSINESS.md](BUSINESS.md); for a
quickstart see [README.md](README.md). AI agents start at [CLAUDE.md](CLAUDE.md),
which points here.

## Architecture

A HCL Domino **Java server add-in** (`runjava` task) that refreshes (re-indexes)
views on a schedule, driven by a **configuration database** (`viewshelper.nsf` by
default); each config document (`View` form) describes one target database and which
of its views to refresh.

- Entry point: [`ViewsHelper`](src/main/java/ViewsHelper.java) — the add-in.
- Worker: [`EventViews`](src/main/java/EventViews.java) — the recurring task.

## Built on JavaServerAddinGenesis (GJA)

Extends `net.prominic.gja_v085.JavaServerAddinGenesis` (the "GJA" framework,
currently **v0.8.5**, sibling repo `JavaServerAddinGenesis`). The base class owns the
add-in lifecycle, the message-queue console loop, logging, the status line, config
files, and a timer that drives "events". **Reuse it — do not re-implement what the
base already provides.**

### The contract (how to extend the base correctly)

- **Constructor:** call `super(args)` (NOT `super()`). The base stores `args` and
  prints it in `info`. The no-arg ctor is only the fallback. This add-in reads two
  positional load args: `args[0]` = config DB path (default `viewshelper.nsf`),
  `args[1]` = server scope (`all` = every config doc; anything else/absent =
  current-server only, the default).
- **Implement the two abstract methods:** `getJavaAddinVersion()` and
  `getJavaAddinDate()`.
- **Override hooks, not the whole flow:**
  - `runNotesAfterInitialize()` — validate config + build/register events. Return
    `false` to abort load (the base then calls `terminate()`); `m_session`,
    `m_logger`, and the config-file paths are already set when this runs.
  - `resolveMessageQueueState(cmd)` — **call `super.resolveMessageQueueState(cmd)`
    first** and return early if it handled the command (`quit/help/info/fire/
    reload/restart` are built in). Only then handle this add-in's own commands.
  - `showHelpExt()` — add ONLY this add-in's extra command lines. **Do not override
    `showHelp()`** — the base already prints the usage header, the standard
    commands, calls `showHelpExt()`, and prints the copyright/footer.
  - `showInfoExt()` — add extra lines to `info`.
- `getJavaAddinName()` defaults to the class name (`ViewsHelper`) — fine, don't
  override unless you need a different MQ/status name.

### The "events idea"

`net.prominic.gja_v085.Event` = one recurring task: `(name, intervalSeconds,
fireOnStart, logger)` + a `run()` method. You register events with
`eventsAdd(event)` inside `runNotesAfterInitialize()`. The base loop then calls
`run()` on start (if `fireOnStart`) and every time `fire()` reports the interval
elapsed. Reference implementations: `EventTimeLive`, `EventLogCleaner` in the GJA
repo. The base also auto-registers `LiveDateStamp` and `LogCleaner`.

**This add-in's shape:** a *single* `EventViews` (1-second tick) holds a
`List<HashMap>` of per-database **configs** (field name: `configs`, NOT "events" —
those `HashMap`s are config rows, not GJA `Event`s). Its `run()` loops the configs
and refreshes each due database. Per-database interval/modified timing is tracked
inside the config map (`lastRun`), because the GJA base has **no API to add/remove
individual events at runtime** — so one umbrella event that re-reads its config on
`tell ViewsHelper update` is the pragmatic fit. Keep `run()` as the single refresh
code path; the `trigger` command calls `m_event.run()` (don't add a duplicate
"fire" method).

## Hard rules

1. **Recycle every Domino object** via `net.prominic.gja_v085.utils.DominoUtils.recycle(...)`
   (null-safe, varargs). This includes non-obvious ones — e.g.
   `database.getLastModified()` returns a `DateTime` that **must** be recycled.
   `Database`, `View`, `Document`, `DateTime` → recycle. `String`/`int`/`Vector<String>`
   returned by `getItemValue*` are plain Java, nothing to recycle. `m_session` is
   recycled by the base's `terminate()` — don't recycle it yourself.
2. **Reuse `net.prominic.gja_v085.utils`** (`DominoUtils`, `FileUtils`,
   `StringUtils`). Do not hand-roll recycling, file IO, or joins — if you catch
   yourself writing one, check utils first.
3. **No duplicate / dead code.** One concept, one method.
4. When the **GJA version changes**, the package name changes too
   (`gja_v084` → `gja_v085` → ...). Bump `pom.xml` and update every `import`.

## Config form contract (`View` form ⇄ `getViews()`)

`ViewsHelper.getViews()` reads these items from each doc in the `($views)` view.
Item names are case-insensitive in Notes. If you add a field, wire it through the
config `HashMap` in `getViews()` AND consume it in `EventViews.refreshView()`.

**Server scope filtering:** `getViews()` skips docs targeting another server unless
launched in `all` mode (see the constructor's `args[1]`). The own-server name is
resolved once into `m_serverName` and compared via `canonical(...)` so abbreviated
and canonical `Server` values match; a blank `Server` is treated as local and always
kept. Filtering happens at config-load (not refresh) time so `info`/`show`/counts
reflect the active scope.

| Form field        | Item read              | Meaning |
|-------------------|------------------------|---------|
| Server            | `Server`               | Target server (blank = local). |
| Database          | `Database`             | Target DB file path. |
| AllViews          | `AllViews` (`1`)       | If set, refresh **every** view via `database.getViews()` (evaluated live each pass — added/deleted views picked up automatically) and ignore `Views`. |
| Views             | `Views` (multi)        | Explicit view names; `select`/`count` agents help fill it. |
| Interval/IntervalUNIT | `interval`         | Seconds between refreshes (`0` = no interval refresh). |
| RunIfModified     | `runIfModified` (`1`)  | Refresh when the DB changed since `lastRun`. |
| Log               | `Log` (`0/1/2`)        | none / file / file+console. |

**Form actions call agents, not inline LotusScript.** The `select` and `count`
hotspots on the `View` form invoke agents via `@Command([RunAgent]; "(views.select)")`
and `@Command([RunAgent]; "(view.count)")` (the same pattern the Views view uses for
`(viewshelper.update)`). Keep form-action logic in a named agent and call it from the
hotspot/button — do not embed LotusScript in the form, and don't duplicate an agent's
body inline. These are client-side UI agents (use `NotesUIWorkspace.CurrentDocument`),
so LotusScript auto-releases handles on exit — no explicit `.Recycle` needed (unlike
the server-side Java add-in).

## Build & release

```
mvn package        # -> target/ViewsHelper-<version>.jar (shaded, includes gja)
```

- Java **8** / source-target 1.6. The non-public dependencies are **vendored in
  [`lib/`](lib/)** (`notes-10.0.jar`, `glassfish-corba-omgapi-4.2.1.jar`,
  `gja-0.8.5.jar`) and installed into the local Maven repo with
  `mvn install:install-file` — **no external downloads at build time** (this is why
  CI is reproducible regardless of whether a given GJA version is published on the
  DominoGenesis releases page). To build on a clean machine, run the three
  install-file commands once (copy them from
  [`.github/workflows/build.yml`](.github/workflows/build.yml)).
- **CI build ([`build.yml`](.github/workflows/build.yml)):** on every push to `main`
  (and manual dispatch) — installs the `lib/` jars, runs `mvn clean package`, uploads
  the jar as a workflow artifact. **No release is created here.**
- **Release ([`release.yml`](.github/workflows/release.yml)):** triggered by pushing
  a **git tag `vX.Y.Z`** — builds and publishes a GitHub release with
  `target/ViewsHelper-*.jar` and auto-generated notes.
- To cut a release: bump `pom.xml` `<version>`, keep `getJavaAddinVersion()` /
  `getJavaAddinDate()` in sync, commit, then `git tag v1.0.5 && git push --tags`.
- When upgrading GJA: drop the new `gja-<ver>.jar` into `lib/`, update the
  `install:install-file` version in **both** workflows, the `pom.xml` dependency, and
  the package imports (`gja_v0xx`).

## Repo notes

- The `NSF/` on-disk project is a **local working copy** and is **gitignored**
  (along with `*.nsf`). Design source of record lives in the database itself; sync
  via Domino Designer.
