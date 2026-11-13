# Standalone Viewer engine UI review

Capture baseline: `83eefd04` (`Add one-shot CLI captures to standalone Cake Viewer`).
The CLI was committed before this capture pass. This review covers standalone
engine styling; game menus are outside its scope. No skin or layout changes
were made during the capture pass.

## Captures

[Overview](../../build/captures/design-round-1/slice-4/viewer/overview.png) ·
[Inputs and commands](../../build/captures/design-round-1/slice-4/viewer/manifest.json)

All captures use native Viewer font/widget sizing. Default windows are 1024×768
physical pixels; additional captures cover a narrow model view and wide browser.
Logs, fixture files, original screenshots, and the overview stay under `build/`.
Local game assets are not added to the repository.

| View | Capture | Content / coverage |
| --- | --- | --- |
| Empty | [empty.png](../../build/captures/design-round-1/slice-4/viewer/empty.png) | Drop prompt |
| PAK browser | [archive.png](../../build/captures/design-round-1/slice-4/viewer/archive.png) | Real pak0, tree, file grid, zoom buttons, scroll track |
| Browser file tiles | [archive-files.png](../../build/captures/design-round-1/slice-4/viewer/archive-files.png) | Curated PAK with two real image thumbnails, WAV/CFG tiles, a folder, and a long filename |
| Player model | [model.png](../../build/captures/design-round-1/slice-4/viewer/model.png) | Real male player model and body skin; skin selector and playback controls |
| BSP map | [map.png](../../build/captures/design-round-1/slice-4/viewer/map.png) | base1 with real textures; world/brush metadata and fly-camera hints |
| PCX image | [image.png](../../build/captures/design-round-1/slice-4/viewer/image.png) | Inventory image, dimensions, zoom/pan hints |
| WAL texture | [texture.png](../../build/captures/design-round-1/slice-4/viewer/texture.png) | metal2_1 texture, dimensions, zoom/pan hints |
| Sprite | [sprite.png](../../build/captures/design-round-1/slice-4/viewer/sprite.png) | Explosion sprite with real frame textures and playback controls |
| Sound | [sound.png](../../build/captures/design-round-1/slice-4/viewer/sound.png) | Blaster WAV; duration and Play/Pause/Loop/Stop |
| Text/config | [text.png](../../build/captures/design-round-1/slice-4/viewer/text.png) | Real default.cfg; text and scrollbar styling |
| Cinematic | [cinematic.png](../../build/captures/design-round-1/slice-4/viewer/cinematic.png) | Intro CIN, paused first frame, metadata and playback controls |
| Narrow model | [model-narrow.png](../../build/captures/design-round-1/slice-4/viewer/model-narrow.png) | 800×600; controls and hints remain inside the window |
| Wide browser | [archive-wide.png](../../build/captures/design-round-1/slice-4/viewer/archive-wide.png) | 1440×900; additional grid columns and rows |

## Initial findings for review

- Cream text, Strawberry action rims, neutral warm input rims, and the Chocolate
  browser/header surfaces are consistent across the captured resource views.
- No overlapping control labels or clipped playback buttons were seen at the
  captured sizes. Main text and metadata remain readable at native resolution.
- The 800×600 model view leaves relatively little vertical room for the model.
  The large header, repeated frame/shortcut information, and playback block are
  candidates for a later compact layout pass, if desired.
- Browser captions ellipsize long names rather than overlap adjacent tiles.
  Extensions are often hidden; selected-item metadata and interaction should be
  reviewed before deciding whether captions need wrapping or a different width.
- Media canvases are black while headers and the archive browser use Chocolate.
  This is the current composition, visible in the captures for evaluation.
- The intro cinematic's initial frame is almost black. Its metadata and controls
  render correctly; a later representative-frame policy is needed for meaningful
  OS thumbnails. This capture does not advance playback.
- Square scrollbar tracks, separators, and progress knobs remain deferred as
  previously agreed. This pass does not change their artwork.

These are initial-state screenshots. Open dropdowns, hover, keyboard focus,
pressed/disabled playback states, scrolling interactions, and recoverable error
UI still need interactive coverage. The CLI intentionally fails on load errors
instead of writing an error-screen capture. Audio is disabled during captures.

## Reproduce

Build the Viewer distribution, then provide local Quake II assets and optionally
a CIN file (defaults to `baseq2/video/ntro.cin` when present):

```sh
./gradlew :cake:cake-viewer:installDist
python3 assets/automation/capture-viewer-ui.py \
  --game-dir /path/to/quake2/baseq2 \
  --cin /path/to/quake2/baseq2/video/ntro.cin
```

The game directory must contain `pak0.pak` and a loose
`players/male/tris.md2` with its skins. The helper requires Pillow and a working
graphics context. Use `--viewer` to select another executable and `--output` to
change the output directory.

Only the opened fixture files are extracted from the PAK. A symlink to the local
PAK supplies their map/sprite dependencies. The curated browser PAK contains a
small subset of those local assets solely for review. Captures run from the
fixture game root so the resolver uses this dependency context; running from
the repository `assets/` directory can instead prioritize its bundled `baseq2`
configuration root. That working-directory behavior is a separate resolver
follow-up, not a skin failure.

Validation: all 13 CLI runs exited successfully; PNG dimensions match the
requested sizes; no engine checkerboard texture fallbacks were accepted. The
manifest records each input, command, working directory, output, and any skipped
cinematic coverage. Representative screenshots were inspected at native size,
alongside an overview of the full set.
