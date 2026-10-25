# Assets Package

## Overview
This package owns runtime asset resolving/loading for the Cake client (maps, models, textures, sounds) and shader-specific model data.

It does **not** own gameplay selection rules (for example which player model/skin to pick from `CS_PLAYERSKINS`) - that logic lives in `PlayerConfiguration` in [the configuration package](../config/README.md).

## Key Types
- `CakeFileResolver` - thin resolver that delegates game-data lookup to qcommon VFS layers and keeps classpath/internal fallbacks, including synthetic player MD2 variant keys.
- `CinematicCinDecoder` - shared CIN video/palette/PCM decoding; gameplay and preview hosts own playback and output.
- `ResourceKind` / `MissingResourceException` - asset-kind policy and typed missing-resource failure contract for Cake runtime.
- `CakeVfsAssetSource` - Cake adapter over qcommon VFS that materializes packaged entries as temp files for libGDX loaders.
- `CakeShaderProvider` - shared dispatch between MD2, lightmapped BSP, and default shaders.
- `Sp2Loader`, texture/sound loaders - format-specific loaders used by `AssetManager`.
- `md2` and `bsp` - model/map loading and rendering pipelines linked below.

## Resolving Assets

`CakeFileResolver` checks the per-mod download cache, shared VFS game-data layers,
then bundled resources. `CakeVfsAssetSource` adapts VFS entries to libGDX file
handles, materialising packaged assets as temporary files when needed. Shared VFS
layering is documented in [the VFS guide](../../../../../../../../../qcommon/vfs/README.md).

`resolve(...)` applies missing-resource policy after looking up the requested path:

- Missing PCX, WAL, MD2, and SP2 assets throw `MissingResourceException` by default.
  With `fs_debug_loaders` enabled, their bundled `_missing.*` replacements are used.
- Missing sounds are tolerated and return `null`, with a warning.
- BSP is recognised but has no fallback; missing BSP assets still throw.
- Unsupported kinds have no resolver fallback, and this policy does not recover
  malformed content.

Fallback content retains the originally requested `AssetManager` key, so ownership
and unloading still refer to the requested asset rather than the replacement path.
Warnings are emitted once per normalised path/resource kind for the lifetime of the resolver.
Use `CakeFileResolver.tryResolve(...)` or `FileHandleResolver.tryResolveRaw(...)`
for existence probes and candidate selection, without substituting missing assets.

Runtime shader text is loaded through `AssetManager` and `TextAssetLoader`, using
`CakeFileResolver` for mod/base lookup and bundled fallback. Bundled sources live in
`assets/shaders`, including `md2.vert/.frag`, `bsp_lightmap.vert/.frag`, and
`bsp_world_batch.vert/.frag`.

## Application Integration

- During precache, `CakeDownloadSession` coordinates missing-resource downloads:
  queue policy (`CakeDownloadManager`), resumable files (`CakeDownloadTransfer`),
  reliable `download`/`nextdl` commands, and progress logging.
- After the queue drains, Cake queues the returned precache retry in `Cbuf`;
  successful precache releases deferred assets and clears the retry token.
- Missing-on-server results let Cake disconnect and show the console. Disconnect
  and fresh serverdata reset download state, preserving partial files for resume;
  network statistics are reset separately by Cake.

A missing-resource failure that reaches `Cake.render()` is recovered by
disconnecting, disposing the game screen, and showing the console. This includes
missing-resource exceptions wrapped in another runtime exception; unrelated runtime
exceptions are rethrown. Best-effort acquisition failures handled locally do not
trigger this recovery.

`Cake` also owns the optional `pics/conback.pcx` background outside the game screen,
so it can be shown before gameplay. Applying a profile reloads it through
`applyGameProfile(...)`; it is drawn while there is no game screen. An absent
background is silently skipped, and a background loading failure is logged without
terminating the application.

## Rendering Pipelines

- [MD2: loading, skins, GPU animation, and lifetime](md2/README.md).
- [BSP: loading, visibility, world/inline rendering, and lighting](bsp/README.md).
