# VFS TODO (Common + Cake)

This document defines the target virtual file system (VFS) design for Jake2, based on:
- current Jake2 split (`qcommon` server FS vs Cake resolver)
- reference engines (Yamagi, Q2PRO, KMQuake2)
- required behavior for runtime mod/package mounting

## Goal

Build one shared VFS core in `qcommon` that:
- provides deterministic layered lookup
- supports loose files + multiple package formats
- supports runtime remount/reindex (`game` change, future downloads)
- exposes O(1) path resolution after index build
- is used by Cake via a thin libGDX adapter

## Requirements (from product goals)

- Core VFS lives in common code, not in Cake-specific classes.
- Cake integration is a thin wrapper (`CakeFileResolver` delegates to VFS).
- VFS supports different runtime modes:
  - server mode (no engine asset fallback)
  - client mode (optional engine fallback for built-in assets/shaders)
- Layer order must be:
  1. mod loose files
  2. mod pack files
  3. base loose files
  4. base pack files
- Default path matching is case-insensitive (lowercased canonical keys).
- Strict case-sensitive mode is optional via runtime flag/cvar (`fs_casesensitive 1`).
- Pack formats must include: `.pak`, `.pk2`, `.pk3`, `.pkz`, `.zip`.
- VFS starts from `basedir`, but can be updated at runtime:
  - gamedir/mod change
  - adding newly downloaded packs (future)
- Lookup after initialization must be O(1), using hash index(es).
- Model viewer must use a thin dedicated VFS adapter instead of resolver-specific path logic duplication.
- Legacy `FS` must be decommissioned:
  - minimum: become a thin compatibility wrapper over new VFS
  - target: remove direct legacy search-path logic entirely
- `QuakeFile` must be reviewed for decommission:
  - read-only `fromPack` checks should move to VFS metadata
  - writable save/config flows must be handled by VFS-backed writable API, not by direct path-bound `RandomAccessFile` constructors.
- Cake savegame compatibility with legacy binary format is explicitly not required.
- Cake save persistence should use a JSON layer (Jackson), replacing legacy-style binary save writing.
- Writable path target:
  - Cake write root should be `$HOME/.cake/<mod>` (savegames, screenshots, configs).
  - Legacy/server write root can remain under current policy until migrated.
  - Current accepted mismatch:
    - server/game save state still writes under `$HOME/.jake2/<mod>/save/...`
    - Cake-owned client writable data uses the Cake write root
    - this is acceptable for now because the server-side save flow has no profile concept yet
- VFS diagnostics commands are required (planned, not immediate):
  - `fs_files`: print all resolved files in ascending order.
  - `fs_mounts`: print mounted loose roots/packages in effective priority order; include package file counts.
  - `fs_overrides`: print logical paths that exist in more than one source (override collisions).

## Current Jake2 baseline (must preserve or replace)

- `qcommon.filesystem.FS` already supports:
  - loose + `.pak`
  - game-dir switching (`SetGamedir`)
  - map/load APIs used by server/legacy client (`LoadFile`, `LoadMappedFile`, `FOpenFile`)
- `FS` current gaps:
  - no `.pk2/.pk3/.pkz/.zip`
  - limited pack scan (`pak0..pak9`)
  - no deterministic handling for custom pack names
- Cake path (`CakeFileResolver`) currently:
  - loose-file lookup only (`basedir/gamemod`, `basedir/baseq2`, classpath/internal fallback)
  - case-insensitive fallback
  - no package mounting/indexing

Implication:
- the new VFS must become the single source of truth for package/loose precedence, with `FS` and Cake both delegating to it.
- decommissioning needs to include both read path and writable persistence path (savegame/config/server state), because current code uses `QuakeFile(..., \"rw\")` directly in game/server save logic.

## Target state (write path)

- Read path: unified shared VFS index for loose + package data.
- Write path: simple writable-root resolver, not search-path based.
- Cake writable root: `$HOME/.cake/<mod>`.
- Current transitional state:
  - server/game save files are written under `$HOME/.jake2/<mod>/save/...`
  - Cake-specific writable output already targets `$HOME/.cake/<mod>/...`
  - keep this mismatch documented until profile-aware server-side write policy exists
- Writable categories under root:
  - `save/`
  - `scrnshot/`
  - `config.cfg` and related user configs

## Findings From Reference Implementations

## Startup indexing

All three references index base/mod paths at startup (or during first FS init trigger):
- Yamagi: `FS_InitFilesystem` -> base search path build; optional mod path build.
- Q2PRO: `FS_Init` -> `fs_game_changed` -> `setup_base_paths` + `setup_game_paths`.
- KMQuake2: `FS_InitFilesystem` -> `FS_AddGameDirectory(base...)`; optional `FS_SetGamedir`.

## Runtime remount

All three also support post-start mutation:
- Yamagi can mount downloaded packages from gamedir (`FS_AddPAKFromGamedir`).
- Q2PRO restarts/rebuilds FS on download/mod changes (`FS_Restart`).
- KMQuake2 mounts downloaded pk3/pak and supports `FS_SetGamedir`.

## Pack ordering and determinism

- Q2PRO has the cleanest deterministic ordering:
  - `pakNN` first (numeric), then alphabetical for others.
- Yamagi distinguishes numbered "protected" packs but non-numbered order may depend on FS listing order.
- KMQuake2 supports wide compatibility and extensions, but has more ad-hoc logic and less strict layering semantics.

## Design decision

Use Q2PRO-style structure and determinism as primary model, while preserving compatibility features from Yamagi/KMQuake2 (multi-format packages and runtime mounting).

## Proposed Architecture

## Module placement

- New VFS core package in `qcommon`:
  - suggested package: `jake2.qcommon.vfs`
- Existing `FS` can be migrated to delegate to VFS internals in phases.
- Cake-specific classes stay in `cake/core` as adapters only.

## Core API (target)

```java
public interface VirtualFileSystem {
    VfsResult<VfsHandle> open(String logicalPath, OpenOptions options);
    VfsResult<byte[]> loadBytes(String logicalPath, OpenOptions options);
    boolean exists(String logicalPath, OpenOptions options);
    Optional<VfsEntryInfo> stat(String logicalPath, OpenOptions options);

    VfsSnapshot snapshot(); // debug/inspection

    void reconfigure(VfsConfig config); // basedir/gamedir/runtime flags
    void mountPackage(Path packagePath, MountOptions options); // runtime download path
    void unmount(String mountId);
    void rebuildIndex(RebuildScope scope);
}
```

Writable-side companion interfaces (for save/config/state I/O):

```java
public interface WritableFileSystem {
    VfsResult<VfsWritableHandle> openWrite(String logicalPath, WriteOptions options);
    VfsResult<VfsReadableHandle> openReadReal(String logicalPath, OpenOptions options); // real-files-only read path
    Path resolveWritePath(String logicalPath);
}
```

## Config and runtime modes

`VfsConfig` should include:
- `Path basedir`
- `String baseGame` (`baseq2` default)
- `String gameMod` (optional)
- `boolean serverMode` (true disables client-only fallback sources)
- `boolean enableEngineFallback` (classpath/internal-like fallback for Cake client)
- `boolean caseSensitive` (default `false`; `true` when `fs_casesensitive=1`)
- `List<Path> extraRoots` (optional)
- `Set<String> supportedExtensions` (`pak/pk2/pk3/pkz/zip`)

Mode examples:
- dedicated server: `serverMode=true`, `enableEngineFallback=false`
- Cake client: `serverMode=false`, `enableEngineFallback=true`

## Mount model

Represent mounts explicitly:
- `MOD_LOOSE`
- `MOD_PACK`
- `BASE_LOOSE`
- `BASE_PACK`
- optional `ENGINE_FALLBACK` (client only)

Each mounted source has:
- `mountId`
- `layer`
- `priority` (deterministic tie-break)
- source descriptor (`directory` or `pack`)

## Pack backend abstraction

```java
public interface PackReader {
    String type(); // pak/pk3/zip...
    List<PackEntry> entries(); // normalized paths + offsets
    InputStream openEntry(PackEntry entry);
    Optional<ByteBuffer> mapEntry(PackEntry entry); // optional optimization
}
```

Backend implementations:
- `PakReader` for Quake `.pak`
- `ZipReader` for `.pk2/.pk3/.pkz/.zip` (ZIP container)

## Indexing and O(1) lookup strategy

## Path normalization

Before indexing and lookup:
- convert `\` to `/`
- collapse `.` and repeated separators
- reject invalid traversal (`..`) for logical paths
- canonical key policy:
  - case-insensitive mode (`default`): store lookup keys in lowercase
  - strict mode (`fs_casesensitive=1`): preserve case and match exactly

Keep original case in metadata for diagnostics/UI.

If strict mode misses while case-insensitive match exists, emit diagnostic warning to help locate case bugs.

## Two-level index

Use both:
- per-layer map: `Map<String, VfsEntryRef>` for diagnostics and selective rebuild
- flattened winner map: `Map<String, VfsEntryRef>` for hot-path resolution

`flattenedIndex` is built by walking layers in fixed precedence order and first-wins insertion:
- O(total indexed entries) rebuild
- O(1) lookup for `exists/open/load`

This gives:
- fast runtime lookup
- predictable conflict resolution
- simple incremental remount support

## Rebuild granularity

Support scoped rebuild:
- `FULL`
- `MOD_ONLY`
- `BASE_ONLY`
- `PACK_ONLY` (runtime added package)

For `mountPackage(...)`, incremental update can insert only affected package entries into layer and recompute winner map for touched keys.

## Layer precedence policy

Implement exactly:
1. mod loose
2. mod packs
3. base loose
4. base packs
5. engine fallback (client-only, lowest)

Inside each pack layer:
- deterministic order:
  - numbered `pakNN` first (ascending NN)
  - then non-numbered packs (alphabetical, case-insensitive)
- newer runtime-mounted package can be inserted at layer head when desired (configurable policy).

## Runtime operations

## Game/mod change

`reconfigure(gameMod=...)` should:
- unmount previous mod loose + mod pack layers
- mount new mod layers from configured roots
- rebuild affected indexes
- keep base layers intact

## Downloaded package mount (future)

`mountPackage(path, targetLayer=MOD_PACK)` should:
- validate extension and package readability
- index entries
- update layer + flattened map
- expose provenance in metadata (`source=download`)

## Server policy hooks

Expose per-entry source info:
- `fromPack`
- `packType`
- `isProtected` (for numbered official paks if policy enabled)
- `sourcePath`

This supports classic download restrictions like "maps from protected packs are not downloadable" if needed.

## Cake integration (thin wrapper)

`CakeFileResolver` becomes adapter only:
- delegate logical path resolution to VFS
- convert resolved result to libGDX `FileHandle` or stream-backed handle abstraction
- keep synthetic key behavior (`<skin>|<model>`) outside VFS (resolver pre-processing step)

Rules:
- no package parsing/indexing logic in Cake resolver
- no path-order duplication between Cake and server code

## Model viewer integration (thin wrapper)

`ModelViewerFileResolver` should be replaced/refactored to delegate to a small model-viewer-oriented VFS adapter:
- no pack parsing/indexing logic in model viewer resolver
- viewer adapter can add viewer-specific roots (opened-file directory + ancestors)
- keep model-viewer-only fallback behavior outside core VFS policy
- reuse same case-sensitivity and layer resolution semantics as main VFS where possible

## Compatibility and migration notes

- Existing `qcommon.filesystem.FS` API should remain temporarily for compatibility.
- Internally, migrate `FS.LoadFile/FOpenFile/...` to call new VFS services.
- Avoid hard cutover; keep old behavior toggles until parity tests pass.
- Existing `QuakeFile` is currently used heavily by save/load serialization in `game` and `server` modules.
- `QuakeFile` transition strategy:
  1. thin adapter backed by VFS handles (preserve existing call signatures temporarily),
  2. for legacy/server modules, optional later replacement by explicit serialization I/O interfaces (`VfsDataInput` / `VfsDataOutput`) and removal of path-bound constructors.
- Cake path diverges intentionally:
  - no binary savegame compatibility target
  - use JSON/Jackson for save persistence format

## Data structures (initial proposal)

```java
record VfsEntryRef(
    String normalizedPath,
    String displayPath,
    VfsLayer layer,
    int layerOrder,
    VfsSource source,
    long size,
    long mtime
) {}

record VfsSource(
    SourceType type,      // DIRECTORY or PACKAGE_ENTRY
    Path containerPath,   // dir path or pack path
    String entryPath,     // null for loose
    String packType,      // pak/pk3...
    boolean fromPack,
    boolean protectedPack
) {}
```

Optional serialization bridge types for `QuakeFile` replacement:

```java
public interface VfsDataInput {
    int readInt() throws IOException;
    float readFloat() throws IOException;
    String readString() throws IOException;      // legacy length-prefixed semantics
    int readEntityRef() throws IOException;      // edict index or -1
}

public interface VfsDataOutput {
    void writeInt(int value) throws IOException;
    void writeFloat(float value) throws IOException;
    void writeString(String value) throws IOException;
    void writeEntityRef(int entIndexOrMinusOne) throws IOException;
}
```

## Test plan (must-have)

- Precedence tests:
  - mod loose overrides mod pack/base
  - mod pack overrides base loose/base pack
  - base loose overrides base pack
- Pack order tests:
  - `pak0 < pak2 < pak10 < abc.pak`
- Multi-format tests:
  - `.pak` + `.pk3` + `.zip` mixed mounts
- Path normalization tests:
  - slash normalization, case-insensitive lookup, traversal rejection
  - strict mode exact-case behavior (`fs_casesensitive=1`)
- Runtime tests:
  - gamedir switch updates winners correctly
  - runtime package mount updates winners without full restart
- Cake adapter tests:
  - resolver returns same winners as VFS core
  - fallback behavior differs correctly between server/client mode
- Model viewer adapter tests:
  - opened-file local roots and ancestor roots are mounted as expected
  - viewer resolver returns same results as adapter policy
- Legacy compatibility tests:
  - `FS` wrapper parity for `LoadFile/FOpenFile/LoadMappedFile`
  - map-download policy parity for `fromPack`-like checks via VFS metadata
- Save/load tests:
  - legacy/server: keep existing binary save/load regression coverage during migration
  - Cake: JSON save/load round-trip tests (schema versioned), no backward-compat assertions with legacy binary saves

## Performance targets

- O(1) average lookup after index build.
- Initialization and remount cost proportional to indexed files.
- No directory scanning on normal lookup path.
- Keep optional counters for:
  - loose directories visited during index build
  - loose files visited during index build
  - package files discovered during mount scan
  - package entries indexed
  - indexed entry count
  - mount count by layer
  - lookup hits/misses
  - rebuild timings

## Phased implementation checklist

- [x] Phase 1: introduce VFS core interfaces/models in `qcommon`.
- [x] Phase 2: implement loose-directory mount/index/lookup.
- [x] Phase 2.5: add single-root `WritableFileSystem` for save/screenshot/config output.
- [x] Phase 3: implement `.pak` reader backend.
- [x] Phase 4: implement ZIP-based backend for `.pk2/.pk3/.pkz/.zip`.
- [x] Phase 5: implement deterministic pack ordering policy.
- [x] Phase 6: implement runtime `reconfigure` and `mountPackage`.
- [x] Phase 7: add server compatibility adapter (`FS` delegates to VFS).
- [x] Phase 8: add Cake adapter (`CakeFileResolver` delegates to VFS).
- [x] Phase 9: add model viewer adapter (`ModelViewerFileResolver` delegates to thin viewer VFS wrapper).
- [x] Phase 10: convert `QuakeFile` into VFS-backed compatibility adapter (remove direct legacy FS search dependency).
- [x] Phase 11: implement Cake save persistence via JSON/Jackson (no legacy binary compatibility target).
- [ ] Phase 12: (optional, legacy/server path) introduce explicit `VfsDataInput/VfsDataOutput` for binary save serialization and migrate game/server save-load code.
- [x] Phase 13: add integration tests across server + Cake + model viewer + save/load paths.
- [x] Phase 14: remove duplicated resolver logic and decommission legacy FS internals once parity is verified.
- [x] Phase 15: add VFS diagnostics commands (`fs_files`, `fs_mounts`, `fs_overrides`) and bind them to console.
- [x] Phase 16: remove dead `cddir` integration path and legacy bootstrap calls (`FS.setCDDir`) now that VFS is the only read index.
- [x] Phase 17: decommission low-value legacy FS API surface (`ListFiles`, `NextPath`, `Developer_searchpath`, `Read`) behind explicit compatibility policy.
- [x] Phase 18: document and freeze FS compatibility boundary (`absolute-path` + `fs_links` behavior) with targeted tests.
- [x] Phase 19: add `qcommon/vfs/README.md` describing VFS/FS responsibilities, API surface, and migration state.

Phase 8 progress:
- Added `CakeVfsAssetSource` adapter over qcommon `DefaultVirtualFileSystem`.
- `CakeFileResolver` now delegates game-data lookup (mod/base loose + packages) to VFS.
- Classpath/internal behavior stays as fallback-only, after mod/base data layers.

Phase 9 progress:
- `ModelViewerFileResolver` now resolves opened-file ancestor paths through a thin local VFS adapter.
- Model viewer basedir/mod fallback now reuses `CakeVfsAssetSource` and therefore supports package-aware lookup parity with the main Cake resolver.

Phase 10 progress:
- Added `FS.OpenReadFile(...)` as the preferred read-mode compatibility entry point over FS/VFS search policy.
- Migrated server/game save-load read call sites away from direct `new QuakeFile(path, "r")` constructors.
- Added `FS.OpenWriteFile(...)` and migrated server/game write call sites away from direct `new QuakeFile(path, "rw")`.
- Migrated remaining legacy-client save metadata read path to `FS.OpenReadFile(...)`, so external game code no longer opens read-mode `QuakeFile` directly.

Phase 13 progress:
- Added Cake/model-viewer resolver parity integration tests for mod/base/package precedence and missing-asset behavior.
- Added qcommon FS compatibility tests for `OpenReadFile`/`OpenWriteFile` roundtrip and missing-file behavior.
- Added qcommon VFS read/write integration coverage for deterministic read-layer precedence and writable save path roundtrip.

Phase 11 progress:
- Cake screenshot writes now target `$HOME/.cake/<mod>/scrnshot/` through `DefaultWritableFileSystem` instead of local working-directory storage.
- Added Cake JSON save metadata store (`CakeJsonSaveStore`) that persists to `$HOME/.cake/<mod>/save/<slot>/cake-save.json` via writable VFS root.
- Wired runtime Cake console commands:
  - `cake_save_meta <slot> [autosave] [title]` to write metadata snapshots.
  - `cake_load_meta <slot>` to read and print metadata snapshots.

Phase 14 progress:
- `FS.LoadMappedFile` now delegates to VFS + `FS.FOpenFile` instead of maintaining an independent legacy search-path traversal.
- Removed legacy per-pack mapped-channel cache path in `FS.pack_t`; mapped reads now share common resolver/open behavior.
- `FS.dir` now uses VFS-backed resolved file listings whenever VFS compatibility is initialized (legacy directory walk kept only for non-VFS bootstrap/fallback).
- Added shared `VfsDebugCommands` registration in `qcommon` and wired both FS and Cake startup paths to it, so `fs_files/fs_mounts/fs_overrides` are available wherever VFS is initialized.
- Removed legacy FS pack/searchpath internals (`pack_t`, `packfile_t`, `SearchPath`, `LoadPackFile`, `AddGameDirectory`) and completed FS read-path delegation to VFS.
- `FS.InitFilesystem` and `FS.SetGamedir` now reconfigure VFS/cvars/write-dir without maintaining duplicated legacy read indexes.

## Hardening Findings (2026-03-03)

- `cddir` read-index integration was dead after Phase 14 and is now removed in Phase 16 (`FS.setCDDir` + dedicated/fullgame bootstrap calls).
- The old absolute-path read shim and `fs_links` remap path were the main remaining non-VFS read behavior in `FS`; both are now decommission targets rather than preserved compatibility.
- Legacy/low-value FS API remains mostly for deprecated old-client call paths:
  - `ListFiles`, `NextPath`, `Developer_searchpath`, `Read`.
- VFS runtime mutation API exists and is test-covered (`mountPackage/unmount/rebuildIndex/snapshot`) but not yet used by live server/client flows.
- `VfsBackedFileSystem` comments still reference pre-migration fallback assumptions and should be cleaned to match current behavior.

## Operational Findings (2026-03-15)

### 1. Temp extraction is not part of VFS init, but it still happens on package-backed reads

- Core package indexing does not unpack archives during VFS init:
  - `DefaultVirtualFileSystem.rebuildPackageMounts()` only lists direct children of `<basedir>/<mod>` and `<basedir>/baseq2`, then builds `PackReader` metadata from the package itself.
  - `PakPackReader` reads the PAK directory table in place.
  - `ZipPackReader` enumerates ZIP central-directory entries in place.
- Temp extraction currently happens in adapter/compatibility layers that need a real file on disk:
  - Cake path: `CakeVfsAssetSource.resolve(...)` materializes every `PACKAGE_ENTRY` to a temp file for libGDX `FileHandle` consumption.
  - FS compatibility path: `VfsBackedFileSystem.openFile(...)` opens `.pak` entries by offset from the original pack, but extracts non-PAK ZIP-backed entries (`.pk2/.pk3/.pkz/.zip`) to temp files for `QuakeFile`.
- Current cache lifetime is process-local only:
  - Cake keeps extracted paths in `extractedPackageFiles`, clears them on reconfigure, and uses `deleteOnExit()`.
  - FS compatibility keeps extracted ZIP-entry temps in `packagedOpenCache`, clears them on reconfigure, and uses `deleteOnExit()`.
- Consequence:
  - "pak/zip files are extracted into temp folder on every run" is not true for core init.
  - It is effectively true for Cake package-backed assets, because extracted files are not persisted across process restarts.
  - It is partially true for legacy `FS.FOpenFile(...)` compatibility reads, but only for ZIP-backed packs; `.pak` stays zero-copy at the pack-file level.

### 2. Current VFS init does not scan the whole home directory

- Loose-file indexing walks only mounted loose roots:
  - `<basedir>/<gameMod>` when a mod is active
  - `<basedir>/baseq2`
- Package discovery is non-recursive:
  - `listPackageFiles(...)` uses `Files.list(root)` and only inspects direct children of each mounted root.
- The mod/base path segments are sanitized single directory names:
  - `sanitizeSegment(...)` rejects `..`, `/`, and drive-like `:`, so mod selection cannot escape `basedir`.
- `FS.autodetectBasedir()` does not recurse through `$HOME`:
  - it probes a small fixed set of Steam install candidates under `user.home` and standard Windows Steam paths via `exists()`
  - if no candidate exists, it falls back to `"."`
- Real risk boundary:
  - if `basedir` itself is misconfigured, VFS will still recurse through `<basedir>/baseq2` and `<basedir>/<mod>`, because loose mounts use `Files.walkFileTree(...)`
  - there is currently no counter exposing how many directories were visited during that walk

### 3. Observability gap to close

- Existing diagnostics are useful but incomplete:
  - `fs_mounts` shows per-mount indexed file counts only
  - `VfsSnapshot` tracks total entries + mount counts by layer only
- Missing startup metrics:
  - directories visited per loose mount
  - files visited per loose mount
  - package files discovered per root
  - package entries indexed per package
  - per-stage timing (`rebuildLooseMounts`, package discovery, package entry enumeration, flattening)
  - temp extraction count/bytes, kept separate from init stats so read-path cost is not confused with startup cost

### 4. Follow-up work to add

- Add `VfsBuildStats` / `VfsTraversalStats` to `VfsSnapshot` and keep the most recent rebuild stats in `DefaultVirtualFileSystem`.
- Extend `fs_mounts` or add `fs_stats` to print:
  - mounted roots
  - directories visited
  - files visited
  - package files found
  - package entries indexed
  - rebuild duration
- Record temp materialization stats separately for:
  - Cake package entry extraction
  - FS compatibility ZIP-entry extraction
- Add a Cake-side `VfsFileHandle` adapter that wraps a logical VFS path without requiring a backing temp file on disk:
  - implement `read()` / `readBytes()` / `exists()` / `length()` over `VirtualFileSystem`
  - preserve path-navigation methods (`parent()` / `child()` / `sibling()`) for loaders that resolve adjacent assets
  - use temp-file fallback only for loaders/backends that prove they require a real OS file
- Reduce per-run temp extraction for Cake/package-backed reads:
  - preferred: return `VfsFileHandle` from `CakeFileResolver` and keep loaders on stream/byte-array access where possible
  - fallback: use a persistent extracted-asset cache keyed by `(pack path, entry path, pack mtime)` instead of `deleteOnExit()` temp files
  - keep `.pak` offset-open behavior on the FS compatibility path

### 5. `VfsBackedFileSystem` cleanup status

- `VfsBackedFileSystem` is already contained:
  - production usage is only behind `qcommon.filesystem.FS`
  - the rest of the codebase calls `FS`, not `VfsBackedFileSystem` directly
- Remaining reasons it still exists:
  - `FS.FOpenFile(...)` / `FS.OpenReadFile(...)` still return `QuakeFile`
  - `QuakeFile` carries legacy compatibility fields such as `fromPack`
  - one live server policy check still depends on `FS.FOpenFile(name).fromPack`
  - legacy absolute-path reads/writes and `fs_links` are intentionally retained outside pure VFS lookup
  - deprecated old-client APIs (`ListFiles`, `NextPath`, `Developer_searchpath`) still hang off `FS`
- Cleanup can be split into two scopes:
  - small/medium scope: collapse `VfsBackedFileSystem` into thinner helpers under `FS`, keeping `FS` + `QuakeFile` compatibility surface
  - larger scope: remove `VfsBackedFileSystem` entirely by replacing `QuakeFile`-based read paths and the remaining compatibility-only `FS` APIs
- Concrete blockers before full removal:
  - replace `FS.FOpenFile(...)` / `OpenReadFile(...)` call sites that expect `QuakeFile`
  - move `fromPack` checks to VFS metadata APIs instead of `QuakeFile.fromPack`
  - decide whether absolute-path and `fs_links` remain in `FS` permanently or move behind a smaller compatibility shim
  - finish decommissioning deprecated old-client FS APIs if old client support is reduced further

### 6. `QuakeFile` spread and replacement direction

- Current spread is concentrated, not broad:
  - `QuakeFile` appears in 20 production files total
  - excluding `qcommon.filesystem` itself, only 17 production files still mention it
  - module split of those 17 files:
    - `game`: 12 files
    - `server`: 3 files
    - `qcommon`: 1 file (`entity_state_t`)
    - `client`: 1 file (`Menu`)
- No production code outside `qcommon.filesystem` constructs `new QuakeFile(...)` directly anymore.
  - This is an important containment milestone: construction is already centralized in `FS` and `VfsBackedFileSystem`.
- Most remaining usage is binary save/load serialization, not general asset lookup:
  - `game` data serializers/deserializers (`GameEntity`, `GamePlayerInfo`, `level_locals_t`, `game_locals_t`, monster structs, item/adapters)
  - server save/config persistence (`GameImportsImpl`, `SV_MAIN`, `SV_CCMDS`)
  - one old-client menu read path
  - one `qcommon` serializer helper (`entity_state_t`)
- `QuakeFile` currently mixes three concerns:
  - legacy random-access file handle shape (`RandomAccessFile`)
  - package provenance metadata (`fromPack`, `length`)
  - Quake-specific binary helpers (`readString/writeString`, `readVector/writeVector`, `readEdictRef/writeEdictRef`)
- Replacement direction:
  - treat `QuakeFile` as a legacy compatibility API, not as the long-term VFS abstraction
  - keep it only at the `FS` boundary until serializers migrate
  - introduce explicit VFS-backed binary I/O interfaces for game/server persistence:
    - readable handle interface for primitive/binary reads
    - writable handle interface for primitive/binary writes
    - package/source metadata queried separately from the read handle
- Cleanup priority inside that migration:
  1. Move `fromPack` checks to VFS metadata so server policy no longer depends on `QuakeFile`.
  2. Replace game/server save-load serializer signatures from `QuakeFile` to dedicated binary reader/writer interfaces.
  3. Leave old-client compatibility paths on `QuakeFile` last, or retire them together with deprecated client-only FS APIs.

## Recommended Cleanup Plan

The proposed direction is sound, with one important adjustment:

- Yes: drop legacy binary save/load compatibility.
- Yes: remove `QuakeFile` as the long-term game/server persistence API.
- Yes: remove `VfsBackedFileSystem` after the remaining `FS` compatibility surface is either replaced or intentionally frozen.
- But: do not try to Jackson-dump the live runtime object graph directly.

Reason:

- Current save state is not a flat POJO graph.
- `GameEntity` and related types contain entity references, adapter/function-pointer replacements, and runtime-only fields/components.
- Adapter restore currently depends on explicit adapter IDs (`SuperAdapter` registry), not classpath-default object reconstruction.
- Entity references are currently serialized as edict indices, which is the right shape to preserve in a JSON snapshot too.

So the recommended plan is JSON saves via explicit snapshot DTOs, not direct `ObjectMapper.writeValue(liveGameState)`.

### Proposed staged plan

1. Define the new save policy and scope.
   - Save format target is Jake2-only JSON.
   - No compatibility with vanilla/binary save files.
   - Read/write path goes through writable/readable VFS APIs, not `QuakeFile`.

2. Put Jackson where the new save code actually lives.
   - Jackson is already available in `cake/core`.
   - It is not currently declared for `game`, `server`, or `qcommon`.
   - Add Jackson dependency to the module that will own JSON save snapshots and mapping code.
   - Prefer keeping Jackson out of low-level VFS core unless there is a strong reason to push it into `qcommon`.

3. Introduce explicit save snapshot DTOs.
   - `GameSaveSnapshot`
   - `LevelSaveSnapshot`
   - `EntitySnapshot`
   - `ClientSnapshot`
   - adapter identifier fields instead of serializing adapter instances
   - entity references encoded as stable indices/IDs, not object pointers
   - exclude runtime-only/transient structures that should be rebuilt on load

4. Implement mapping layer between runtime state and snapshots.
   - runtime -> snapshot extractor
   - snapshot -> runtime rehydration
   - keep this mapping code near game/server save ownership, not inside core VFS
   - explicitly reconstruct adapter references via registry lookup by ID

5. Add JSON save store on top of writable/readable VFS.
   - replace `WriteGame/ReadGameLocals/WriteLevel/ReadLevel` binary flows with JSON file read/write
   - use writable VFS paths instead of `QuakeFile`
   - version the JSON schema from day one

6. Migrate server policy away from `QuakeFile.fromPack`.
   - expose `fromPack` / `sourceType` / `isProtected` through VFS metadata lookup
   - replace the remaining `FS.FOpenFile(name).fromPack` usage with metadata query

7. Decommission `QuakeFile` from production save/load code.
   - move serializer signatures off `QuakeFile`
   - keep `QuakeFile` only for compatibility paths still intentionally retained
   - old-client compatibility can remain last if still needed

8. Collapse/remove `VfsBackedFileSystem`.
   - once `FS` no longer needs `QuakeFile` bridging for active code paths, fold remaining logic into `FS` or remove it entirely
   - preserve only the intentional FS compatibility boundary if desired:
     - absolute-path direct access
     - `fs_links`
     - deprecated old-client APIs, if still kept

### Recommended order of attack

- First target save/load migration, not `VfsBackedFileSystem` internals.
- After JSON save/load exists, `QuakeFile` usage shrinks sharply because most of its remaining spread is serializer-related.
- After `fromPack` metadata is exposed separately, `VfsBackedFileSystem` becomes much easier to collapse or delete.

### Practical risk notes

- Lowest-risk simplification:
  - replace binary save/load with JSON snapshots
  - do not attempt automatic Jackson serialization of live entities/components
- Main design rule:
  - VFS owns file access
  - save module owns schema + mapping
  - runtime entities remain runtime-oriented objects, not persistence-oriented DTOs

### Approved execution roadmap

Implementation progress (landed commits):
- `53e4a101` `chore: add shared qcommon json save support`
  - added Jackson dependency to `qcommon`
  - added shared `SaveJson` mapper helper + test
- `fdc451b1` `refactor: store server save metadata as json`
  - migrated server-owned save metadata (`server_mapcmd.ssv.json`, `server_latched_cvars.ssv.json`) away from `QuakeFile` binary content
  - kept legacy filenames to avoid churn in save copy/wipe flows
- `9d8df91a` `refactor: expose pack provenance through fs metadata`
  - added explicit FS/VFS metadata lookup for `fromPack`
  - removed the remaining live server dependency on `FS.FOpenFile(...).fromPack`
- `6f595093` `feat: add initial game save snapshots`
  - added shared JSON DTOs for `game_locals_t`, `client_persistant_t`, and `client_respawn_t`
  - added explicit runtime <-> snapshot mapping helpers in `game`
  - added focused round-trip tests for primitive fields, inventories, and item-index restoration
- `edc7310c` `feat: add player state save snapshots`
  - added shared JSON DTOs for `pmove_state_t` and `player_state_t`
  - added explicit shared runtime <-> snapshot mapping helpers in `qcommon`
  - added focused round-trip tests for movement/player state restoration
- `e649e837` `refactor: store game locals saves as json`
  - added Kotlin `game.ssv.json` JSON layer (`GameSaveJson.kt`) with grouped DTOs, `GamePlayerInfo` mapping, and VFS-backed store logic
  - replaced `GameExportsImpl.WriteGame(...)` / `readGameLocals(...)` binary `QuakeFile` flow with JSON over writable VFS
  - kept `WriteLevel(...)` / `ReadLevel(...)` on binary `QuakeFile` for now; that remains the next migration slice
  - added focused tests for `GamePlayerInfo` snapshot round-trip and `game.ssv.json` JSON store round-trip
- `7dbfb5d5` `refactor: store level saves as json`
  - added Kotlin `level.sav.json` JSON layer (`LevelSaveJson.kt`) with grouped DTOs for `level_locals_t`, sparse in-use entities, `entity_state_t`, `monsterinfo_t`, `mmove_t`, and `mframe_t`
  - replaced `GameExportsImpl.WriteLevel(...)` / `ReadLevel(...)` binary `QuakeFile` flow with JSON over writable VFS
  - preserved explicit adapter IDs, edict references, client indices, item indices, and sparse entity numbering in the JSON schema
  - added focused tests for level/entity snapshot round-trip and `level.sav.json` JSON store round-trip
- `c3878f02` `fix: normalize json save paths under write root`
  - fixed a regression where server callers still passed absolute save paths (`FS.getWriteDir() + ...`) into the new JSON stores
  - the writable VFS expects root-relative logical paths, so without normalization the files were written/read under the wrong nested location
  - added regression tests covering absolute-path `game.ssv.json` and `level.sav.json` callers, matching the live server save/load path
- `a806b684` `refactor: store server level state as json`
  - added Kotlin `ServerLevelJsonStore.kt` for per-level server state previously stored in `.sv2.json`
  - replaced binary configstring/portal-state save-load in `SV_WriteLevelFile()` / `SV_ReadLevelFile()` with JSON over writable VFS
  - kept the `.sv2.json` filename stable so save copy/wipe flows remain unchanged
  - added focused tests for server level-state round-trip and portal/configstring restoration
- `5a1ccb61` `refactor: use explicit json save filenames`
  - renamed JSON save files to explicit suffixed names: `game.ssv.json`, `level.sav.json`, `*.sv2.json`, `server_mapcmd.ssv.json`, `server_latched_cvars.ssv.json`
  - updated save-slot copy/wipe logic, previous-level detection, and focused tests to use the explicit JSON filenames consistently

Current phase status:
- Phase A: in progress
- Phase B: in progress
- Phase C: complete
- Phase D: complete
- Phase E: complete
- Phase F: in progress
- Phase G: not started

Phase A: JSON save foundation
- Add Jackson dependency to the module(s) that will own save snapshot code.
- Recommended placement:
  - `qcommon`: shared JSON mapper/config and snapshot DTOs used by both server and Cake
  - `game`: game/level/entity/client snapshot DTOs + mapping
  - `server`: server-owned save metadata and server-specific snapshot helpers
- Add versioned JSON save root DTOs.
- Landed so far:
  - shared `SaveJson` mapper/config lives in `qcommon`
  - server-owned metadata JSON store is in place
  - first shared game/client snapshot DTOs now exist in `qcommon`

Exit criteria:
- save schema types exist without touching `QuakeFile`
- module boundaries are explicit and compile cleanly

Phase B: Snapshot mapping layer
- Implement runtime -> snapshot extraction for:
  - game locals
  - level locals
  - game clients/player info
  - entities
  - adapter IDs / item IDs / entity reference indices
- Implement snapshot -> runtime rehydration.
- Rebuild runtime-only fields/components explicitly after load.
- Landed so far:
  - `GameSaveSnapshots` maps `game_locals_t`
  - `GameSaveSnapshots` maps `client_persistant_t` using item indices instead of object serialization
  - `GameSaveSnapshots` maps `client_respawn_t`
  - `PlayerStateSnapshots` maps `pmove_state_t`
  - `PlayerStateSnapshots` maps `player_state_t`
  - `GamePlayerSnapshots` maps full `GamePlayerInfo` state for the `game.ssv.json` scope
  - focused round-trip tests cover these first mappings

Exit criteria:
- no direct Jackson serialization of live `GameEntity` / adapter graphs
- snapshot round-trip tests cover representative save state

Phase C: Replace binary game/level save flows
- Replace `GameExportsImpl.WriteGame(...)`
- Replace `GameExportsImpl.readGameLocals(...)`
- Replace `GameExportsImpl.WriteLevel(...)`
- Replace `GameExportsImpl.ReadLevel(...)`
- Store these via VFS-backed readable/writable APIs and JSON files.
- Landed so far:
  - `WriteGame(...)` and `readGameLocals(...)` now persist `game.ssv.json` as JSON via writable VFS
  - the new filename now makes the JSON format explicit (`*.json`)
  - `WriteLevel(...)` and `ReadLevel(...)` now persist `level.sav.json` as JSON via writable VFS
  - sparse in-use entity saves still preserve entnum identity explicitly in the JSON schema

Exit criteria:
- active save/load path for Jake2 uses JSON, not binary `QuakeFile`
- game module no longer requires `QuakeFile` for main save/load path

Phase D: Remove remaining server-side `QuakeFile` save persistence
- Replace binary save helpers in server-owned flows:
  - map command persistence
  - latched cvars persistence
  - configstring dump/load path, if still needed in current form
- Use JSON or other simple text formats through VFS write/read APIs.
- Landed so far:
  - `server_mapcmd.ssv.json` is JSON
  - `server_latched_cvars.ssv.json` is JSON
  - per-level `.sv2.json` server state (configstrings + portal state) is now JSON
  - active server save/persistence flows no longer open `QuakeFile`

Exit criteria:
- server save/persistence flows no longer open `QuakeFile`

Phase E: Remove `fromPack` dependency from `QuakeFile`
- Add VFS metadata query API reachable from `FS`
- Replace the remaining `FS.FOpenFile(name).fromPack` server policy check with metadata lookup

Exit criteria:
- no production code depends on `QuakeFile.fromPack`

Phase F: Retire `QuakeFile` from active production code
- Migrate any remaining non-deprecated production signatures away from `QuakeFile`
- Keep deprecated old-client compatibility only if still intentionally supported
- Mark `QuakeFile` and `FS.FOpenFile/OpenReadFile/OpenWriteFile` as compatibility-only during transition

Verified status after JSON save migration:
- Active server persistence no longer uses `QuakeFile`.
- Active game persistence no longer uses `QuakeFile`.
- The deprecated `client` module is not part of the active Gradle build (`settings.gradle.kts` does not include `:client` or `:fullgame`), so it should not block decommission planning.
- Dead binary serializer methods that still referenced `QuakeFile` in `game`/`qcommon` have now been removed.
- Remaining `QuakeFile` references in active modules are concentrated in the FS compatibility layer itself (`FS`, `VfsBackedFileSystem`, `QuakeFile`) plus compatibility-oriented adapter helpers.
- `FS`, `QuakeFile`, and `VfsBackedFileSystem` are now explicitly annotated as deprecated-for-removal in code, so the remaining migration work is visible at compile time.
- A shared read-side VFS owner now exists outside `FS` (`EngineVfs`), and active read callers have started moving to it.
- A shared write-root owner now exists outside `FS` (`EngineWriteRoot`), and active server/game save paths have started moving to it.
- Current `QuakeFile` footprint is now:
  - production bridge code: `qcommon.filesystem.FS`, `qcommon.filesystem.VfsBackedFileSystem`, `qcommon.filesystem.QuakeFile`
  - test coverage for that bridge: `FSCompatibilityTest`, `VfsBackedFileSystemTest`
  - one non-functional comment reference in `server.save.ServerSaveJsonStore`
- This means the next trim step is straightforward:
  - keep test coverage only as long as the compatibility bridge still exists
  - once `FS.OpenReadFile/OpenWriteFile/FOpenFile` and `VfsBackedFileSystem.openFile(...)` are removed, those remaining test-side `QuakeFile` usages can be deleted together

Exit criteria:
- `QuakeFile` no longer participates in active server/game persistence flow
- remaining usage is compatibility-only and explicitly documented

Phase G: Collapse/remove `VfsBackedFileSystem`
- If `FS` still needs a small shim, inline the remaining logic into `FS`
- Otherwise remove `VfsBackedFileSystem` entirely
- Retain only explicitly chosen FS compatibility behavior:
  - deprecated old-client APIs, if still kept

Exit criteria:
- no separate `VfsBackedFileSystem` compatibility bridge remains
- VFS read metadata and `FS` compatibility boundary are simpler and easier to reason about

### Verified decommission target

The intended future stack should be:
- `VirtualFileSystem` for read-side lookup and package/loose layering
- `WritableFileSystem` for save/config output
- thin module-specific adapters only where needed (`CakeFileResolver`, JSON save stores, debug commands)

This means all three legacy types can be treated as decommission targets:
- `FS`: compatibility facade to be reduced and eventually removed from active code
- `QuakeFile`: legacy binary/file-shaped compatibility type, no longer the persistence abstraction
- `VfsBackedFileSystem`: transitional bridge from `FS` to the real VFS, removable once `FS` is no longer needed for active code

### Concrete decommission plan

1. Deprecate `FS` as compatibility-only in active modules.
   - New code should use `VirtualFileSystem` / `WritableFileSystem` directly, not `FS`.
   - Existing active server/game callers should be migrated off `FS` where practical (`LoadFile`, `FileExists`, `CreatePath`, etc.).

2. Remove dead binary serializer APIs that still mention `QuakeFile`.
   - `game`: `GameEntity`, `GamePlayerInfo`, `client_persistant_t`, `client_respawn_t`, `game_locals_t`, `level_locals_t`, `GameItems`, `monsterinfo_t`, `mmove_t`, `mframe_t`
   - `qcommon`: `entity_state_t`
   - These methods are no longer needed for active save/load after the JSON migration.

3. Replace remaining active `FS` read-side callers with direct VFS access.
   - Server-side today, VFS is already used indirectly via `FS`:
     - `SV_MAIN`: map list and map existence checks
     - `SV_USER`: download reads and `IsFromPack`
     - `Cmd`/`Cvar`/`CM`: common engine reads and gamedir reconfigure
   - Landed so far:
     - introduced shared `EngineVfs` in `qcommon.vfs`
     - migrated `SV_MAIN`, `SV_USER`, `Cmd`, and `CM` off `FS.LoadFile` / `FS.IsFromPack`
     - `FS.LoadFile`, `FS.FileExists`, and `FS.IsFromPack` now delegate to the shared `EngineVfs` instance instead of owning separate read logic
   - This is now complete for active read callers in `server` and `qcommon`.

4. Move write-root policy out of `FS`.
   - Landed so far:
     - introduced shared `EngineWriteRoot` in `qcommon.vfs`
     - migrated active server/game save paths off `FS.getWriteDir()` and `FS.CreatePath()`
     - `FS` now reuses `EngineWriteRoot` for its remaining compatibility helpers instead of owning write-root policy itself
   - The next step is to keep `FS` only for lifecycle/config compatibility (`InitFilesystem`, `SetGamedir`, `ExecAutoexec`) until those seams are replaced too.

5. Remove `VfsBackedFileSystem`.
   - Once `FS` no longer needs to return `QuakeFile`, the bridge can be inlined away or deleted.
   - This also removes the remaining ZIP temp-extraction path used only to fabricate `QuakeFile`.

6. Remove `QuakeFile`.
   - After `FS` no longer exposes it and binary serializer methods are deleted, `QuakeFile` becomes removable.

### Server-side VFS status

Server-side VFS is already real, but mostly hidden behind `FS`:
- Read path:
  - `FS` still initializes/configures the shared VFS lifecycle
  - active server reads now go through `EngineVfs`, backed by the common VFS
- Write path:
  - active server/game save state already uses `WritableFileSystem` directly through JSON stores
  - write-root policy now goes through `EngineWriteRoot`, preserving the current `~/.jake2/<mod>` layout
  - this includes `server_mapcmd.ssv.json`, `server_latched_cvars.ssv.json`, `game.ssv.json`, `level.sav.json`, and per-level `.sv2.json`

So the server is no longer “client-only VFS plus legacy server writes”.
The remaining issue is now mostly lifecycle/config compatibility: `FS` still owns startup/mod-change wiring and the `QuakeFile` bridge.

### Immediate next implementation target

- Keep the focus on Phase B + Phase C now that shared JSON support exists and part of Phase D/E has landed.
- The highest-value next step is to retire `QuakeFile` from active production code entirely, now that game and server persistence paths are JSON/VFS-backed.
- After that, the remaining work is compatibility-bridge cleanup: `FS` legacy surface, temp-extracting adapter paths, and `VfsBackedFileSystem`.
- Do not start by refactoring `VfsBackedFileSystem`; that would create churn before the serializer dependency on `QuakeFile` is removed.

## Remaining Work For Full Server+Client VFS Switch

Core migration for shared server+client VFS is complete.

Optional follow-ups:

1. Automatic runtime package lifecycle hooks.
   - Manual runtime lifecycle is available via `fs_mount`, `fs_unmount`, and `fs_rebuild`.
   - Integrate these APIs with download/mod-change flows when that runtime path is implemented.
2. Optional Phase 12 for legacy binary save pipeline modernization (`VfsDataInput/VfsDataOutput`).

Phase 15 progress:
- Added console commands in `FS`: `fs_files`, `fs_mounts`, `fs_overrides`.
- Added VFS debug views for resolved winners, mount order + file counts, and override collisions.

Phase 16 progress:
- Removed dead `FS.setCDDir()` integration from FS initialization.
- Removed obsolete dedicated/fullgame bootstrap calls that re-applied `cddir` after early reconfigure.
- Kept legacy `cddir` cvar behavior outside FS read-indexing path for compatibility with old client-side audio paths.

Phase 17 progress:
- Removed `FS.Read(...)` and migrated remaining qcommon callers to `RandomAccessFile.readFully(...)`.
- Marked `FS.ListFiles(...)`, `FS.NextPath(...)`, and `FS.Developer_searchpath(...)` as deprecated legacy compatibility API.
- Kept these three APIs alive for deprecated old-client code paths while explicitly documenting them as non-target APIs for server/cake VFS flows.

Phase 18 progress:
- Removed the explicit FS compatibility boundary for absolute-path reads and `fs_links`.
- `FS.FileExists`, `FS.IsFromPack`, `FS.FOpenFile`, `FS.LoadFile`, and `FS.LoadMappedFile` now resolve only through VFS-backed logical paths.
- `FSCompatibilityTest` now asserts the stricter contract:
  - absolute-path reads are rejected outside VFS
  - `fs_links` prefixes are no longer resolved
  - `OpenWriteFile`/`CreatePath` remain as transitional write-side helpers

Runtime lifecycle alignment progress:
- Exposed manual runtime VFS mutation commands through shared command registration:
  - `fs_mount <packagePath>`
  - `fs_unmount <mountId>`
  - `fs_rebuild [full|mod|base|pack]`
- Wired FS compatibility provider to `mountPackage/unmount/rebuildIndex` so runtime mutation now works in live server/client sessions where FS is initialized.
- Kept automatic download/mod-change runtime hook integration as a follow-up task.

Phase 19 progress:
- Added `qcommon/vfs/README.md` documenting VFS goals, layering, API surface, FS compatibility boundary, integration points, and command semantics.
- Captured current migration status and remaining optional follow-up tasks.

Phase 7 progress:
- Added `VfsBackedFileSystem` compatibility wrapper in `qcommon.filesystem`.
- `FS.LoadFile` and `FS.FileExists` now use VFS fast-path first, with legacy search-path fallback preserved.
- Added `fs_casesensitive` compatibility wiring so FS-side VFS lookup can switch strict mode.
- `FS.LoadFile` now skips legacy lowercase fallback when `fs_casesensitive` is enabled.
- `FS.LoadMappedFile` now uses VFS first; loose files are mapped directly, package entries are returned as read-only buffers.
- `FS.FOpenFile` now uses VFS for loose files, `.pak` entries (offset-open), and ZIP-backed entries (temp-file compatibility bridge).
- The old absolute-path and `fs_links` read-side compatibility shim has since been removed; active FS reads now depend on VFS logical paths only.
- `FS.path` now prints VFS mounts (with legacy fallback), and `FS.dir` now lists VFS-resolved files for wildcard queries before legacy directory fallback.
- `FS.NextPath` and `FS.Developer_searchpath` now use VFS loose mount state first, preserving legacy fallback behavior.

Implementation progress (2026-03-15):
- Removed the remaining `FS` read-side compatibility boundary:
  - no absolute-path direct reads in `FileExists`, `IsFromPack`, `FOpenFile`, `LoadFile`, or `LoadMappedFile`
  - no `fs_links` remap state or `link` console command
- Kept `OpenWriteFile` and `CreatePath` only as transitional write-side helpers while `FS` still exists.
- Marked `FS`, `QuakeFile`, and `VfsBackedFileSystem` as deprecated-for-removal, and did the same for the remaining `QuakeFile`-returning `FS` entry points (`FOpenFile`, `OpenReadFile`, `OpenWriteFile`).
- Removed the obsolete binary save/load helper methods from:
  - `game`: `GameEntity`, `GamePlayerInfo`, `client_persistant_t`, `client_respawn_t`, `game_locals_t`, `level_locals_t`, `GameItems`, `monsterinfo_t`, `mmove_t`, `mframe_t`
  - `qcommon`: `entity_state_t`, `player_state_t`, `pmove_state_t`
- Introduced `EngineVfs` as the shared non-legacy read-side VFS access point.
- Migrated active read callers off `FS.LoadFile` / `FS.IsFromPack` in:
  - `server`: `SV_MAIN`, `SV_USER`
  - `qcommon`: `Cmd`, `CM`
- `FS` now reuses the shared `EngineVfs` instance for `LoadFile`, `FileExists`, and `IsFromPack`, while `VfsBackedFileSystem` remains only for `QuakeFile`/mapped-file compatibility.
- Introduced `EngineWriteRoot` as the shared non-legacy write-root owner.
- Migrated active save/state path construction off `FS.getWriteDir()` / `FS.CreatePath()` in:
  - `server`: `GameImportsImpl`, `SV_MAIN`, `SV_GAME`, `SV_CCMDS`
  - `game`: `GameExportsImpl`
- Introduced `EngineFilesystemLifecycle` as the shared non-legacy lifecycle/bootstrap owner for filesystem startup and mod changes.
- Migrated the remaining active lifecycle callers off `FS`:
  - `dedicated`: `Jake2Dedicated` now initializes filesystem lifecycle through `EngineFilesystemLifecycle`
  - `qcommon`: `Cvar` now routes `game` cvar changes through `EngineFilesystemLifecycle`
- Removed `FS.InitFilesystem`, `FS.SetGamedir`, and `FS.ExecAutoexec` entirely after migrating the remaining call sites and test/bootstrap usage.
- Documented current write-root mismatch explicitly:
  - server/game save flow writes to `$HOME/.jake2/<mod>/save/...`
  - Cake-owned client writable data targets `$HOME/.cake/<mod>/...`
  - keep this split until server-side save policy learns about client profiles
- Verified with focused `qcommon` tests:
  - `FSCompatibilityTest`
  - `VfsBackedFileSystemTest`
  - `VfsReadWriteIntegrationTest`

## Decommission Review (2026-03-15)

The remaining decommission work is now narrow and explicit.

### `FS` remaining role

- Active non-test `FS` call sites for startup/mod-change lifecycle are now gone.
- This means active content reads, save-path construction, and lifecycle/bootstrap are already off `FS`.
- The remaining responsibilities inside `FS` are:
  - compatibility debug commands (`path`, `dir`, `packfiles`)
  - `QuakeFile` bridge methods (`FOpenFile`, `OpenReadFile`, `OpenWriteFile`, `LoadMappedFile`)

### `QuakeFile` remaining role

- `QuakeFile` no longer participates in active save persistence.
- Remaining production references are effectively compatibility-only:
  - `qcommon.filesystem.FS`
  - `qcommon.filesystem.VfsBackedFileSystem`
  - `qcommon.filesystem.QuakeFile`
- The old `SuperAdapter.writeAdapter/readAdapter` binary helpers have now been removed.
- Remaining test references are only bridge coverage:
  - `FSCompatibilityTest`
  - `VfsBackedFileSystemTest`

### `VfsBackedFileSystem` remaining role

- `VfsBackedFileSystem` is no longer part of the active read architecture.
- It now exists only to fabricate `QuakeFile` compatibility views over VFS results and to back a few compatibility/debug helpers.
- The most important behavior still trapped there is the ZIP package temp-extraction bridge:
  - `.pak` entries are opened by offset from the pack
  - ZIP-backed package entries are extracted to temp files only because `QuakeFile` requires an OS file

### Concrete blockers before deletion

1. Move bootstrap ownership out of `FS`.
   - Done: `EngineFilesystemLifecycle` now owns startup/bootstrap state and VFS compatibility initialization.
2. Move gamedir-change and autoexec behavior out of `FS`.
   - Done: `Cvar` now calls `EngineFilesystemLifecycle` directly for `game` cvar changes.
3. Remove `QuakeFile` bridge APIs.
   - Delete `FS.FOpenFile(...)`, `FS.OpenReadFile(...)`, `FS.OpenWriteFile(...)`.
   - Replace or delete `FS.LoadMappedFile(...)` compatibility usage.
4. Delete `VfsBackedFileSystem`.
   - After no code needs `QuakeFile` views over VFS results, remove the bridge and its ZIP temp-extraction path.
5. Delete `QuakeFile`.
   - Once no production code or tests depend on it, remove the type entirely.
6. Reduce or delete `FS`.
   - Preferred end state: `FS` disappears.
   - Acceptable temporary end state: `FS` becomes a minimal deprecated bootstrap shim with no file I/O API surface.

### Recommended deletion order

1. Remove `FS` file I/O entry points and `LoadMappedFile`.
2. Delete `VfsBackedFileSystem`.
3. Delete `QuakeFile`.
4. Delete or trivialize `FS`.

### End-state criteria

`FS`, `QuakeFile`, and `VfsBackedFileSystem` can be considered fully decommissioned when:

- all active startup/mod-change flows go through non-legacy VFS lifecycle owners
- no active code returns or accepts `QuakeFile`
- no ZIP package entry is materialized only to satisfy `QuakeFile`
- the remaining tests validate `EngineVfs` / `WritableFileSystem` behavior directly instead of bridge compatibility

## Open questions

- Should numbered official packs be marked as "protected" by default in Jake2?
- Should runtime-mounted downloaded packs always take highest mod-pack priority?
- Do we need optional case-sensitive mode for strict debugging?
- Should engine fallback be represented as a VFS layer or handled purely by Cake adapter?
- Should `fs_casesensitive` be global, or overridable per VFS instance (server/client/viewer)?
