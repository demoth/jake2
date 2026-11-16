# Jake2 VFS

The shared filesystem implementation lives in
[`jake2.qcommon.vfs`](../qcommon/src/main/java/jake2/qcommon/vfs). It indexes loose files and
packages for reads and provides a separate, explicitly rooted API for writes.
The server and client use separate instances of this
shared VFS implementation, with the same lookup semantics.

## Owners and Integration

- `DefaultVirtualFileSystem` implements `VirtualFileSystem`: configuration, indexed
  lookup, stream/byte reads, package mounts, and rebuilds.
- **Server:** `EngineVfs` owns the read instance. The dedicated server uses
  `EngineFilesystemLifecycle` for startup, game-directory changes, write-root
  setup, and diagnostics/runtime commands. Cake Fullgame uses `EmbeddedCakeHost`
  to configure `EngineVfs` from the selected profile and set `EngineWriteRoot`
  to that profile’s storage directory.
- **Client:** `CakeVfsAssetSource` owns a `DefaultVirtualFileSystem`, adapted to
  libGDX file handles. `CakeFileResolver` adds download-cache precedence and bundled
  fallbacks; see [Cake asset loading](../cake/core/src/main/kotlin/org/demoth/cake/assets/README.md).
  The viewer uses the same resolver with startup hints derived from the opened
  file and current directory.
- `DefaultWritableFileSystem` implements `WritableFileSystem` for a caller-selected
  root. `EngineWriteRoot` supplies the server's writable instance.

The client gets its startup game location from the selected profile.
Steam detection bootstraps a default profile on first run; if no usable profile is
available, the client opens profile editing.

## Read Lookup and Precedence

The shared engine VFS resolves logical paths such as `maps/base1.bsp` or `demomap demo1` in this
order (highest priority first):

| Layer | Source |
| --- | --- |
| `PROFILE_LOOSE` (for profile demos) | `$HOME/.cake/<profileId>/demos/*.dm2` |
| Download cache (Cake runtime only) | `$HOME/.cake/downloads/<mod>/`;|
| `MOD_OPTIONAL_PACK` | Selected non-numbered packages in `<basedir>/<mod>` |
| `MOD_LOOSE` | Loose files in `<basedir>/<mod>` |
| `MOD_PACK` | Numbered `pakNN.pak` files in `<basedir>/<mod>` |
| `BASE_OPTIONAL_PACK` | Selected non-numbered packages in `<basedir>/baseq2` |
| `BASE_LOOSE` | Loose files in `<basedir>/baseq2` |
| `BASE_PACK` | Numbered `pakNN.pak` files in `<basedir>/baseq2` |
| `ENGINE_FALLBACK` | Optional extra loose-file roots |

Simplifying: Mod goes first, then base game, then engine fallback (useful during development).

Mod layers apply when a mod (aka 'game') is configured (not equal to 'baseq2').

### Package selection and ordering

Server and client configurations support `.pak`, `.pk2`, `.pk3`, `.pkz`, and
`.zip`. PAK uses its own reader; the other extensions use ZIP containers.
Only names matching `pak[0-9]+.pak` (case-insensitive) are numbered packages.
Other supported archives belong to the optional-package layers.

`VfsConfig.packageSelection` controls automatic discovery:

- With `automount` enabled, all supported archives in each game directory are
  included.
- With `automount` disabled, numbered PAKs are still included; other archives must
  appear in `includedArchives`. Entries are exact paths relative to Basedir,
  such as `baseq2/textures.zip` or `mymod/maps.pk3`.

Cake passes the selected profile’s package policy to both its client VFS and
embedded server. The profile editor’s **Mods** interface manages archive choices.
The dedicated-server lifecycle uses automatic discovery.

Within a package layer, later entries in the logical load order win: higher
numbered PAKs override lower ones (`pak1.pak` overrides `pak0.pak`), and optional
archives later in case-insensitive alphabetical order override earlier ones.
The implementation indexes packages in reverse load order and keeps the first
matching entry.

Explicit runtime mounts bypass the discovery selection policy and are inserted
ahead of existing packages in their layer. A numbered PAK remains below loose
files; a non-numbered archive uses an optional-package layer above loose files.

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
roots and any enabled fallback roots (does not follow symbolic links). Profile
recordings are scanned separately by Cake’s demo code.
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

The dedicated server’s `EngineFilesystemLifecycle` provider also supports:

- `fs_mount <packagePath>`: mount a package and return its mount id.
- `fs_unmount <mountId>`: remove a package mount by id.
- `fs_rebuild [full|mod|base|pack]`: rebuild the index.

Cake registers its client provider for the shared command names, including in
Fullgame. That provider does not implement
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
| Cake Fullgame saves and previews | `$HOME/.cake/<profileId>/save/...` |
| Cake demo recordings | `$HOME/.cake/<profileId>/demos/...` |
| Dedicated-server saves | Under `EngineWriteRoot/save/`; normally `$HOME/.jake2/<game>/save/...` |

Downloads use `baseq2` when no mod is selected. Client profile ids and mod names
are separate concepts: Fullgame saves are profile-scoped, while the dedicated
server chooses its write root through `EngineFilesystemLifecycle`. The initial
dedicated write root is `$HOME/.jake2/baseq2`; game-directory changes reset it
using the supplied game-directory name.

Selecting a profile configures Fullgame’s read location and write root separately:
game data stays under Basedir, while saves go under the profile storage directory.
See the [Cake guide](../cake/README.md#game-data-and-profiles) for profile setup.

Saving and loading client bindings and archived cvars is described in the
[input guide](../cake/core/src/main/kotlin/org/demoth/cake/input/README.md).
