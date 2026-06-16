# DominoViewHelper

A Domino Java server add-in that refreshes (re-indexes) views on a schedule, so
frequently-queried views stay up to date without waiting for a user to open them.

It is built on the [JavaServerAddinGenesis](https://github.com/DominoGenesis/JavaServerAddinGenesis)
(GJA) framework (v0.8.5) and driven by a configuration database (`viewshelper.nsf` by default).

## How it works

- Each document in the config database (`View` form) describes **one target database**
  and the views to keep refreshed.
- The add-in loads those documents at startup and re-evaluates them on every timer tick,
  refreshing a database's views when it is due (see *Refresh triggers* below).

## Install

1. Build `ViewsHelper-<version>.jar` (`mvn package`) and drop it into the server's
   `domino/ndext` (or another folder on the Java classpath).
2. Create/sign the configuration database from the on-disk project in [`NSF/`](NSF/)
   and add `View` documents (see *Configuration* below).
3. Load the add-in, pointing it at the config database:

   ```
   load runjava ViewsHelper viewshelper.nsf
   ```

   The `.nsf` parameter is optional; it defaults to `viewshelper.nsf`.

## Configuration (`View` form)

| Field         | Meaning                                                                            |
|---------------|------------------------------------------------------------------------------------|
| Server        | Server hosting the target database (blank = local).                                |
| Database      | File path of the target database, e.g. `folder\sandbox.nsf`.                       |
| **All views** | If checked, **every** view/folder in the database is refreshed and the *Views* selection below is ignored. Newly added or deleted views are picked up automatically on the next pass. |
| Views         | Explicit list of views to refresh. Use the **select** button to pick them and the **count** button to show how many views/folders the target database has. |
| Interval      | Refresh the views every N days/hours/seconds. Set `0` to disable interval refresh. |
| Run If Database Modified | Refresh when the target database has been modified since the last run.  |
| Log           | `0` none, `1` log to file, `2` log to file and Domino console.                     |

### Refresh triggers

For each configured database, a refresh pass runs when **either**:

- *Run If Database Modified* is on and the database changed since the last pass, **or**
- *Interval* is greater than `0` and at least that many seconds have elapsed.

When a pass runs, **all** of the configured views (or all views in the database, in
*All views* mode) are refreshed.

## Console commands

```
tell ViewsHelper <command>
   quit             Unload addin
   help             Show help information (or -h)
   info             Show version and configuration
   trigger          Refresh all configured views now
   update           Reload configuration from the config database
```

After editing `View` documents, run `tell ViewsHelper update` to apply the changes.

## Build

```
mvn package
```

Dependencies are vendored in [`lib/`](lib/) and installed into the local Maven repo
with `mvn install:install-file` (no external downloads) — see
[`.github/workflows/build.yml`](.github/workflows/build.yml) for the exact commands.
Pushing to `main` builds and uploads a jar artifact; pushing a tag `vX.Y.Z` publishes
a GitHub release via [`.github/workflows/release.yml`](.github/workflows/release.yml).

Copyright (C) Prominic.NET, Inc. See https://prominic.net for more details.
