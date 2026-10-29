# Content UI Styles

Shared styles and preview widgets for [menus](../stages/README.md),
[HUD](../stages/ingame/hud/README.md), and CakeViewer.
Configstring assets belong to [game configuration](../config/README.md).

## Ownership and lifetime

| Component | Responsibility |
|---|---|
| `GameUiStyle` | HUD fonts, number glyphs, menu widgets, optional sounds; owns acquired asset references. |
| `GameUiStyleFactory` | Tries IdTech2 assets through mod/base lookup for every mod; releases partial acquisitions and falls back to `EngineUiStyle` on failure. |
| `Cake` | Caches style by base-directory/mod, closes samples before style disposal, rebuilds menus and supplies the HUD style. |
| `MenuStages` | Owns menu stages; rebuilds them when the shared style changes. |
| `UiSamplePreview` | Owns gallery, commands, local mode changes, and capture; requests application transitions through the menu bus. |

- Styles acquire their own `AssetManager` references even for already-loaded textures.
- HUD/menu font instances share a conchars texture but have independent metrics; dispose fonts before releasing the texture.
- Hosts remove preview actors and dispose renderers/targets before releasing assets.

## Fonts and widgets

Both menu themes use the vertical `cl_ui_size` convention, 8-unit cap heights,
and thick/thin insets of 8/4. Engine menu fonts and drawables use 1/3 scale;
console, viewer, and diagnostics retain native sizing. Skin owns its raw/menu fonts.

| Element | IdTech2 source / behavior |
|---|---|
| Text | `ConcharsFontLoader`: `pics/conchars.pcx`, fixed 16×16 grid with texture-derived cell size (128² or 256² atlases). Alpha comes from PCX decoding. |
| Alternate HUD text | `char ^ 0x80`, preserving alternate glyphs/colors. |
| HUD numbers | `HudNumberFont`: separate `num_*` / `anum_*` pictures. |
| Thick panels / thin controls | Conchars cells 1–9 / 18–26; controls use half-cell insets. |
| Sliders / progress | Track 128–130, knob 131. |
| Separators / vertical scroll thumbs | Cells 29–31, fixed caps and stretchable middle; rotated for vertical use. ScrollPane handles dragging. |
| Trees | Cell 13 right/down marks, square 11 selection/hover; labels use resolved styles. |

`MenuWidgetStyles` covers labels, buttons, fields, lists, select boxes, scroll
panes, sliders, progress, and trees. Engine styles use existing panel/slider/tree
artwork and atlas lines; tree slots match the 8-unit game theme.

## Content browser and previews

| Component | Responsibility |
|---|---|
| `ContentBrowserStage` / `ContentBrowser` | Client layout; shared VFS browsing and back navigation. Hosts supply the root action. |
| `DemoFiles` / `DemoMetadata` | Merge profile recordings with installed demo entries and read bounded startup metadata on selection, without extracting packages or loading game assets. |
| `ContentBrowserStyles` | Resolved menu styles; standalone Skin adapter preserves viewer sizing. |
| `FileResourcePreview` / `CinematicPreview` | Shared read-only text and CIN widgets; owns the palette reference, cinematic texture/pixmap and audio device. Closing detaches the widget and releases resources. |
| `ImageThumbnails` | Progressive visible PCX/WAL/TGA and first-frame CIN loading; owns folder-scoped textures, released on folder change, exit, or profile change. |
| `ContentPreview` | Client media loading, playback, and cleanup; owns converted sounds and a private preview AssetManager. |
| `ImagePreviewPane` | Shared aspect-fit display, pointer-anchored zoom, and pan; borrows image textures. |
| `SoundPlayback` / `SoundPreviewControls` | Shared audio playback and explicitly styled controls. |
| `SpriteFrameImage`, `SpritePreview`, `FramePlayback`, `FramePlaybackControls` | Shared SP2 playback and controls; centered frames on a fixed maximum-size canvas. |
| `SceneResourcePreview` / `PreviewResources` | Shared MD2/BSP setup, rendering, controls, and scoped resource ownership. |
| `ScenePreviewPane` | Render target uses transformed screen dimensions to retain resolution under UI scaling and high-DPI displays. |

- Preview content resolves within the profile; engine palette/shaders remain available.
- Selection, navigation, profile changes, and failures clear preview resources without unloading shared game textures.
- Back/Escape restores folder scroll, then ascends folders; only root exits the browser.

| Media | Interaction |
|---|---|
| Images | Zoom/pan in the shared pane. |
| WAV | Click auditions; double-click opens controls without restarting. Space toggles; S stops. |
| SP2 | Click plays once; double-click opens controls without restarting. Space toggles; Left/Right step and pause. |
| MD2 | Orbit/zoom, frame transport, skins. |
| BSP | Focused fly input; static world/brush rendering. |

## Menu sounds

- Optional `misc/menu1.wav`, `menu2.wav`, and `menu3.wav` supply enter, hover, and exit sounds.
- MenuController emits navigation-depth signals; Cake plays queued sounds.
- Shared button helpers apply hover sound/text feedback and reset it on click or menu changes.

## Developer gallery

| Command / flag | Behavior |
|---|---|
| `ui_sample [engine\|game\|off]` | Switch themes while retaining fixture edits, tree state, and scroll; Escape/Close returns to the prior view. |
| `ui_sample_capture <engine\|game\|both> <directory>` | Write PNGs after two rendered frames per mode, then exit. Capture failures propagate. |
| `+set cfg_autosave 0` | Avoid persisting temporary settings. |
| `+set vid_hidden 1` | Start with a hidden window. |

- Open/close/capture requests follow `MenuIntent → MenuController → MenuSignal → Cake`;
  console commands take effect on the next menu pump without changing menu navigation.
- Engine/Game buttons and capture mode advances stay local. Capture timing starts
  when the open request is handled; closing cancels active capture.
- Cake retains visibility/input/pause policy and closes the gallery synchronously
  before style disposal or shutdown.

The gallery follows `cl_ui_size` live and identifies engine fallback explicitly.
`writeScreenshot` serves gallery captures and profile screenshots; callers choose
paths and error handling. See the [coverage inventory](../../../../../../../../../assets/ui/INVENTORY.md)
and [sample plan](../../../../../../../../../info/ui-skin-sample.md).
