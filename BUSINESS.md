# BUSINESS.md — DominoViewsHelper (how it works)

This document describes **what the add-in does and how to operate it**. For code
and contribution guidance see [CLAUDE.md](CLAUDE.md); for install/build see
[README.md](README.md).

## The problem it solves

A Domino view's index is normally only rebuilt when someone **opens** the view (or
on the server's own schedule). Views that are queried by code/agents/APIs but rarely
opened by a person can therefore serve **stale** data or pay a slow rebuild on first
access. **DominoViewsHelper** is a server task that keeps chosen views **refreshed on
a schedule**, so they are always current and fast to read.

## Who is involved

- **Domino administrator** — installs/loads the add-in on the server and runs
  console commands.
- **Application owner** — decides which databases/views must stay fresh and creates
  the configuration documents.
- **The add-in** (`ViewsHelper`) — reads the configuration and refreshes views on
  its timer.

## Where configuration lives

All configuration is stored as documents in a **configuration database**
(`viewshelper.nsf` by default), using the **`View` form**. **One document = one
target database.** The add-in is told which config database to use when it is loaded
(`load runjava ViewsHelper viewshelper.nsf`).

## Process: register a database for view refresh

1. Open the configuration database (`viewshelper.nsf`).
2. Create a new **View** document (the Views view has a **New** action).
3. Fill in the target:
   - **Server** — the server hosting the database to refresh (leave blank for the
     local/current server).
   - **Database** — the file path, e.g. `folder\sandbox.nsf`.
4. Choose **which views** to refresh — two options:
   - **All views** — tick this to refresh **every** view (and folder) in the
     database. Views added or removed later are picked up **automatically**; you do
     not need to edit the config again. The explicit *Views* list below is ignored.
   - **Specific views** — leave *All views* unticked and list the views in **Views**.
     Use the **select** button to pick them from the target database, and the
     **count** button to see how many views/folders that database currently has.
     (Newly added views are *not* refreshed until you add them here and re-run
     *update*.)
5. Decide **when** a refresh should happen — set either or both:
   - **Interval** — refresh every N **days / hours / seconds** (set `0` to disable
     interval-based refresh).
   - **Run If Database Modified** — refresh whenever the database has changed
     (document created / edited / deleted) since the last pass.
6. Choose **logging** level: none / log to file / log to file **and** the Domino
   console.
7. **Save** the document.
8. Tell the running add-in to pick up the change:
   ```
   tell ViewsHelper update
   ```

The database is now registered. On every pass the add-in checks each registered
database and, if a refresh is due (modified and/or interval), refreshes the chosen
views.

## Process: change or remove a registration

- **Change** a database/views/schedule: edit its **View** document, save, then
  `tell ViewsHelper update`.
- **Stop refreshing** a database: delete (or empty) its **View** document, then
  `tell ViewsHelper update`.

## Operating the add-in (admin console)

| Command | Effect |
|---|---|
| `load runjava ViewsHelper viewshelper.nsf` | Start the task against a config database. |
| `tell ViewsHelper update`  | Reload configuration after editing **View** documents. |
| `tell ViewsHelper trigger` | Refresh all configured views **right now**. |
| `tell ViewsHelper info`    | Show version + number of registered databases. |
| `tell ViewsHelper help`    | Show all commands. |
| `tell ViewsHelper quit`    | Unload the task. |

## When refreshes happen (summary)

For each registered database, a refresh pass runs when **either** *Run If Database
Modified* is on and the database changed since the last pass, **or** *Interval* is
greater than `0` and that many seconds have elapsed. When a pass runs, **all** of the
configured views (or every view, in *All views* mode) are refreshed together.
