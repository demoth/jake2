# Jake2 VFS (qcommon)

This module documents the unified Virtual File System used by both server-side `FS` compatibility calls and Cake-side asset resolution.

## Goals

- One deterministic read index for loose files and packages.
- Q2PRO-style layer precedence and deterministic pack ordering.
- O(1) lookup after index build.
- Shared behavior across server, Cake client, and model viewer adapters.

## Layering And Precedence

Read winners are resolved in this order:

1. mod loose files
2. mod packages
3. base loose files
4. base packages
5. engine fallback (client-only)

Pack ordering in a layer is deterministic:

- numbered `pakNN` first (ascending)
- then non-numbered pack names (alphabetical, case-insensitive)

Supported pack formats:

- `.pak`
- `.pk2`
- `.pk3`
- `.pkz`
- `.zip`

## API Surface

Core read VFS:

- `jake2.qcommon.vfs.VirtualFileSystem`
  - `configure(...)`
  - `resolve(...)`
  - `openRead(...)`
  - `loadBytes(...)`
  - `exists(...)`
  - `setGameMod(...)`
  - `mountPackage(...)`
  - `unmount(...)`
  - `rebuildIndex(...)`
  - `snapshot()`

Default implementation:

- `jake2.qcommon.vfs.DefaultVirtualFileSystem`

Writable side:

- `jake2.qcommon.vfs.WritableFileSystem`
- `jake2.qcommon.vfs.DefaultWritableFileSystem`

## FS Compatibility Facade

`jake2.qcommon.filesystem.FS` is now a compatibility facade over VFS.

Retained intentional compatibility boundary:

- absolute-path direct reads/writes (`/abs/path`)
- `fs_links` remapping behavior

Preferred retained FS entry points:

- `FOpenFile(...)`
- `OpenReadFile(...)`
- `OpenWriteFile(...)`
- `LoadFile(...)`
- `LoadMappedFile(...)`
- `FileExists(...)`
- `SetGamedir(...)`

Deprecated low-value legacy APIs (old-client compatibility only):

- `ListFiles(...)`
- `NextPath(...)`
- `Developer_searchpath(...)`

Removed legacy FS API:

- `FS.Read(...)`

## Integration Points

- Server/dedicated/fullgame bootstrap: `FS.InitFilesystem()` and `FS.SetGamedir(...)`.
- Cake: `CakeFileResolver` delegates read lookup to VFS via `CakeVfsAssetSource`.
- Model viewer: `ModelViewerFileResolver` delegates to thin viewer VFS adapter.

## Console Commands

Diagnostics:

- `fs_files` - all resolved winner paths, sorted ascending
- `fs_mounts` - mounted sources in effective priority order (+ file counts)
- `fs_overrides` - logical paths that exist in more than one source

Runtime lifecycle (manual mutation):

- `fs_mount <packagePath>` - mount runtime package and print mount id
- `fs_unmount <mountId>` - unmount previously mounted runtime package
- `fs_rebuild [full|mod|base|pack]` - trigger VFS index rebuild

## Write Path Policy

Cake write root is VFS-backed and mod-scoped:

- `$HOME/.cake/<mod>/save/`
- `$HOME/.cake/<mod>/scrnshot/`
- `$HOME/.cake/<mod>/config*`

Read path and write path are intentionally separated: read uses layered VFS index, write uses writable root policy.

## Current Migration Status

Completed:

- legacy FS search-path internals removed
- server/cake/model-viewer read-path unified on VFS
- compatibility boundary tested for absolute-path + `fs_links`
- runtime mutation commands exposed (`fs_mount`, `fs_unmount`, `fs_rebuild`)

Remaining optional follow-up:

- hook runtime package mutation into download/mod-change automation flows (manual commands already available)
