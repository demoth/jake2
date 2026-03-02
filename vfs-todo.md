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
- [ ] Phase 7: add server compatibility adapter (`FS` delegates to VFS).
- [x] Phase 8: add Cake adapter (`CakeFileResolver` delegates to VFS).
- [ ] Phase 9: add model viewer adapter (`ModelViewerFileResolver` delegates to thin viewer VFS wrapper).
- [ ] Phase 10: convert `QuakeFile` into VFS-backed compatibility adapter (remove direct legacy FS search dependency).
- [ ] Phase 11: implement Cake save persistence via JSON/Jackson (no legacy binary compatibility target).
- [ ] Phase 12: (optional, legacy/server path) introduce explicit `VfsDataInput/VfsDataOutput` for binary save serialization and migrate game/server save-load code.
- [ ] Phase 13: add integration tests across server + Cake + model viewer + save/load paths.
- [ ] Phase 14: remove duplicated resolver logic and decommission legacy FS internals once parity is verified.

Phase 8 progress:
- Added `CakeVfsAssetSource` adapter over qcommon `DefaultVirtualFileSystem`.
- `CakeFileResolver` now delegates game-data lookup (mod/base loose + packages) to VFS.
- Classpath/internal behavior stays as fallback-only, after mod/base data layers.

Phase 7 progress:
- Added `VfsBackedFileSystem` compatibility wrapper in `qcommon.filesystem`.
- `FS.LoadFile` and `FS.FileExists` now use VFS fast-path first, with legacy search-path fallback preserved.
- Added `fs_casesensitive` compatibility wiring so FS-side VFS lookup can switch strict mode.
- `FS.LoadFile` now skips legacy lowercase fallback when `fs_casesensitive` is enabled.
- `FS.LoadMappedFile` now uses VFS first; loose files are mapped directly, package entries are returned as read-only buffers.
- `FS.FOpenFile` now uses VFS for loose files, `.pak` entries (offset-open), and ZIP-backed entries (temp-file compatibility bridge).
- Restored legacy `fs_links` precedence for `FileExists`, `LoadFile`, and `LoadMappedFile` before VFS fallback.
- Remaining Phase 7 work: broader FS command/listing parity and full delegation cleanup.

## Open questions

- Should numbered official packs be marked as "protected" by default in Jake2?
- Should runtime-mounted downloaded packs always take highest mod-pack priority?
- Do we need optional case-sensitive mode for strict debugging?
- Should engine fallback be represented as a VFS layer or handled purely by Cake adapter?
- Should `fs_casesensitive` be global, or overridable per VFS instance (server/client/viewer)?
