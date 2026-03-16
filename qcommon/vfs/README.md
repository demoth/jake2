# Jake2 VFS

This package contains the active virtual filesystem used by engine, server, Cake, and the model viewer.

## Overview

The current VFS design is:

- one shared read-side index in `qcommon`
- one explicit write-root policy, separate from read lookup
- thin adapters in higher-level modules
- no active `FS`, `QuakeFile`, or `VfsBackedFileSystem` layer in the build

Primary owners:

- `DefaultVirtualFileSystem`: core read-side implementation
- `EngineVfs`: shared engine-facing read-side owner
- `DefaultWritableFileSystem`: writable-root implementation
- `EngineWriteRoot`: engine-facing write-root policy
- `EngineFilesystemLifecycle`: startup, gamedir changes, and debug command registration

## Read Path

The read index resolves winners in this order:

1. mod loose files
2. mod packages
3. base loose files
4. base packages
5. engine fallback, client-only

Supported package formats:

- `.pak`
- `.pk2`
- `.pk3`
- `.pkz`
- `.zip`

Package ordering inside a layer is deterministic:

- numbered `pakNN` first, ascending
- then non-numbered pack names, alphabetical and case-insensitive

Lookup is O(1) after index build through the flattened winner map in `DefaultVirtualFileSystem`.

Default path matching is case-insensitive. Strict mode is available through `fs_casesensitive`.

## Runtime Lifecycle

`EngineFilesystemLifecycle` owns startup and mod-change wiring:

- resolves `basedir`
- initializes `EngineVfs`
- sets the engine write root
- handles `game` cvar changes
- registers VFS debug/runtime commands

Runtime mutation is supported through:

- `fs_mount <packagePath>`
- `fs_unmount <mountId>`
- `fs_rebuild [full|mod|base|pack]`

The API surface behind that is the shared `VirtualFileSystem` contract:

- `configure(...)`
- `resolve(...)`
- `openRead(...)`
- `loadBytes(...)`
- `exists(...)`
- `setGameMod(...)`
- `mountPackage(...)`
- `unmount(...)`
- `rebuildIndex(...)`

## Write Path

Read lookup and write policy are intentionally separate.

Writable implementations:

- `WritableFileSystem`
- `DefaultWritableFileSystem`

Current roots:

- Cake-owned client writable data: `$HOME/.cake/<mod>/...`
- server/game save state: `$HOME/.jake2/<mod>/save/...`

That mismatch is currently intentional. Server-side save flow does not yet know about Cake profiles, so server/game state remains under `.jake2` for now.

## Integration Points

Current module usage:

- dedicated/server bootstrap: `EngineFilesystemLifecycle`
- engine/server reads: `EngineVfs`
- Cake asset resolution: `CakeVfsAssetSource` and `CakeFileResolver`
- model viewer: thin VFS adapter over the same shared semantics
- save/config/state writes: `DefaultWritableFileSystem` plus JSON stores

## Diagnostics

Available commands:

- `fs_files`: print resolved winner paths
- `fs_mounts`: print mounted roots/packages in effective order
- `fs_overrides`: print override collisions
- `fs_mount`: runtime package mount
- `fs_unmount`: runtime package unmount
- `fs_rebuild`: rebuild the index

## Operational Notes

### Package extraction

Core VFS init does not extract pack contents.

At mount/index time:

- `.pak` mounts read the header and directory table in place
- ZIP-based mounts enumerate the central directory in place

Current extraction behavior is adapter-side:

- Cake still materializes package-backed assets to temp files in `CakeVfsAssetSource` because libGDX currently receives file-oriented handles there
- there is no longer an `FS`/`QuakeFile` compatibility extraction path in the active build

Implication:

- package files are not extracted on every run during VFS init
- repeated temp extraction concern is still valid on the Cake side

### Scan scope

Current VFS init does not scan the whole home directory.

What it does:

- recursively indexes loose files only under `<basedir>/baseq2` and optional `<basedir>/<mod>`
- discovers packages only from direct children of those roots
- probes a small fixed set of Steam install candidates when autodetecting `basedir`

What it does not do:

- recurse through `$HOME`
- scan arbitrary directories outside the configured base/mod roots

The main remaining observability gap is that directory-visit counts and rebuild timings are not exposed yet.

## Current Status

Completed:

- shared package-aware VFS in `qcommon`
- deterministic loose/package precedence
- runtime remount/rebuild support
- Cake integration on the read path
- model viewer integration
- JSON save/state persistence over writable VFS APIs
- removal of active `FS`, `QuakeFile`, and `VfsBackedFileSystem`
- removal of the `VfsLookupOptions` wrapper in favor of simple default methods plus a plain `gameDataOnly` boolean where fallback filtering is needed

## Active Follow-Ups

The architecture cleanup is complete. The remaining work is operational and client-facing:

1. Add VFS traversal/build stats.
   - directories visited
   - files visited
   - package files found
   - package entries indexed
   - rebuild timings

2. Add a richer diagnostics view, likely `fs_stats`.

3. Replace Cake temp extraction with a proper VFS-backed `FileHandle`-like adapter where possible.

4. Revisit write-root policy once server-side save flow becomes profile-aware.

5. Hook runtime package lifecycle into download/mod-change flows instead of relying only on manual commands.
