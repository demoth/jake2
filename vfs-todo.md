# VFS Follow-Ups

The detailed VFS design and current-state documentation now lives in:

- `qcommon/vfs/README.md`

This file is intentionally reduced to active follow-up items only. Historical migration details remain in git history.

Recent simplification already landed:

- `VfsLookupOptions` was removed; the read API now uses plain default methods and a bare `gameDataOnly` boolean only on the lookup paths that still need fallback filtering.

## Follow-Ups

- Add VFS traversal/build stats to expose:
  - directories visited
  - files visited
  - package files discovered
  - package entries indexed
  - rebuild duration

- Add a richer diagnostics command, likely `fs_stats`, on top of a dedicated stats model.

- Replace Cake temp extraction for package-backed assets with a VFS-backed handle where possible.
  - preferred direction: a `VfsFileHandle`-style adapter for libGDX-facing code
  - keep temp-file fallback only for loaders/backends that prove they require a real OS file

- Remove Cake reliance on JVM `basedir`/`game` system properties for startup hints.
  - main Cake client cleanup is complete:
    - normal startup now uses the active profile store
    - autodetect is only used to bootstrap the default profile on first run
    - if no profile can be bootstrapped, Cake opens the profile editor instead of falling back to `.`
  - model viewer cleanup is complete for now:
    - it now infers simple `basedir`/`gamemod` hints from the current folder and opened file path
    - later follow-up: add profile-like configuration to the model viewer if it needs persistent startup context

- Keep the current write-root mismatch documented until server-side save flow becomes profile-aware:
  - server/game save state: `$HOME/.jake2/<mod>/save/...`
  - Cake-owned client writable data: `$HOME/.cake/<mod>/...`

- Wire runtime package lifecycle into download/mod-change flows instead of relying only on manual `fs_mount/fs_unmount/fs_rebuild`.
