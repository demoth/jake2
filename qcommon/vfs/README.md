# Jake2 VFS

The shared filesystem implementation lives in
[`jake2.qcommon.vfs`](../src/main/java/jake2/qcommon/vfs). It indexes loose files and
packages for reads and provides a separate, explicitly rooted API for writes.
The server and client use separate instances of this
shared VFS implementation, with the same lookup semantics.

## Owners and Integration

- `DefaultVirtualFileSystem` implements `VirtualFileSystem`: configuration, indexed
  lookup, stream/byte reads, package mounts, and rebuilds.
- **Server:** `EngineVfs` owns the read instance. `EngineFilesystemLifecycle`
  configures it at startup, handles game-directory changes, sets `EngineWriteRoot`,
  and registers diagnostics and runtime commands.
- **Client:** `CakeVfsAssetSource` owns a `DefaultVirtualFileSystem`, adapted to
  libGDX file handles. `CakeFileResolver` adds download-cache precedence and bundled
  fallbacks; see [Cake asset loading](../../cake/core/src/main/kotlin/org/demoth/cake/assets/README.md).
  The model viewer uses the same resolver with startup hints derived from the opened
  file and current directory.
- `DefaultWritableFileSystem` implements `WritableFileSystem` for a caller-selected
  root. `EngineWriteRoot` supplies the server's writable instance.

The client gets its startup game location from the selected profile.
Steam detection bootstraps a default profile on first run; if no usable profile is
available, the client opens profile editing. The main client does not use JVM `basedir` /
`game` system properties as its startup configuration.

## Read Lookup and Precedence

Logical paths, such as `maps/base1.bsp`, resolve in this order:

1. Mod loose files: `<basedir>/<mod>`.
2. Mod packages.
3. Base loose files: `<basedir>/baseq2`.
4. Base packages.
5. Optional fallback roots (`ENGINE_FALLBACK`), enabled only in client mode.

Optional fallback roots are supplied through `VfsConfig.extraRoots`; the client
handles bundled fallbacks in its resolver. `resolve(path, true)` and
`exists(path, true)` exclude the optional fallback layer.

Supported package extensions in server and client configurations are `.pak`, `.pk2`,
`.pk3`, `.pkz`, and `.zip`. PAK uses its own reader; the others use ZIP containers.
Automatically discovered packages in each layer are ordered by numbered `pakNN`
first (ascending number), then other names alphabetically, case-insensitive.
The first matching entry wins: `pak0` takes precedence over `pak1` within that
layer. A runtime mount is inserted ahead of existing packages in its layer;
loose-file precedence still applies.

`VfsPathNormalizer` converts backslashes, removes empty/`.` segments, collapses
`..` segments, and rejects paths that escape above the logical root. Matching is
case-insensitive by default. The server exposes strict matching through
`fs_casesensitive`; the client’s `CakeFileResolver` accepts a `caseSensitive` setting, defaulting
to false.

After indexing, normalised paths resolve through a flattened winner map.
`resolve()` returns source metadata, `openRead()` opens a handle, and `loadBytes()`
reads the whole resource. Close read handles after use. `VfsOpenOptions.realFilesOnly`
rejects a package-backed winner; it does not search for a lower-priority loose copy.

## Indexing and Package Reads

Index construction recursively visits loose files under the configured base/mod
roots and any enabled fallback roots (does not follow symbolic links). 
Package discovery considers direct children of the base/mod roots (a symlink to a supported package can be mounted). 
It does not search the whole home directory; 
Steam detection probes a fixed set of installation candidates separately from indexing.

Core indexing does not extract packages: PAK readers inspect header/directory data,
and ZIP readers enumerate the central directory. Package contents are read on
request through streams.

The client materialises requested package entries as temporary files for libGDX loaders.
It caches them by package path, logical path, and package modification time;
reconfiguration clears the cache and deletes those files. Temporary files are also
registered for deletion at JVM exit. Loose files are returned directly.

## Diagnostics and Runtime Changes

These lookup diagnostics are available through both server and client providers:

- `fs_files`: list indexed winner paths.
- `fs_find <regex>`: filter winner paths by regular expression.
- `fs_mounts`: show mounted roots/packages and indexed file counts.
- `fs_overrides`: show paths with competing sources.

The server provider also supports:

- `fs_mount <packagePath>`: mount a package and return its mount id.
- `fs_unmount <mountId>`: remove a package mount by id.
- `fs_rebuild [full|mod|base|pack]`: rebuild the index.

The client registers the shared command names but its provider does not implement
mount/unmount/rebuild operations. Use its lookup diagnostics to inspect asset
resolution; these commands do not mutate the client's asset index. In particular,
`fs_rebuild` can print completion while its client provider performs no rebuild.

`configure()` and `setGameMod()` rebuild the core index. All requested rebuild
scopes currently perform a full rebuild, rediscovering packages from configured
roots. Runtime mount/unmount changes are therefore not persistent across rebuilds.
Traversal counts and rebuild timings are not exposed by the current diagnostics.

## Writes and Persistent Data

Writes target one explicit root; read lookup precedence does not choose the write
location. `DefaultWritableFileSystem` creates parent directories and supports
truncate, append, or create-new writes. It rejects paths containing `..` and checks
that the normalised destination stays beneath the root. `openReadReal()` reads
from that same writable root without consulting package layers.

Current default locations:

| Data | Location |
| --- | --- |
| Client profile registry | `$HOME/.cake/profiles.json` |
| Client profile config and console history | `$HOME/.cake/<profileId>/...` |
| Client downloaded game assets | `$HOME/.cake/downloads/<mod>/...` |
| Server/game saves | `$HOME/.jake2/<mod>/save/...` |

Downloads use `baseq2` when no mod is selected. Client profile ids and mod names are
separate concepts; server save paths remain mod-based and are not profile-aware.

Saving and loading client bindings and archived cvars is described in the input guide (cake/core/src/main/kotlin/org/demoth/cake/input/README.md)