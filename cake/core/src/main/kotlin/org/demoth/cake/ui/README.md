# Content UI Styles

## Responsibilities

The internal `ui_sample [engine|game|off]` console command opens a widget gallery
in client/fullgame. Engine and active-game modes share one layout and retain
edited fixture state when switched. Escape or Close returns to the prior view;
profile style changes close the sample before releasing borrowed resources.
The compact sample fits one virtual screen and follows the vertical `cl_ui_size`
setting live. Captions and controls all use the selected theme; fallback is
identified explicitly.
`ui_sample_capture <engine|game|both> <directory>` writes snapshots after rendering
and exits. Launch with `+set cfg_autosave 0` to keep temporary settings unsaved;
`+set vid_hidden 1` starts without a visible window. The usual `+set`/`+commands`
startup syntax works in client/fullgame.
See the [coverage inventory](../../../../../../../../../assets/ui/INVENTORY.md)
and [sample plan](../../../../../../../../../info/ui-skin-sample.md).

`GameUiStyle` supplies shared HUD fonts, numeric glyphs, menu widget styles, and
optional menu sounds. `GameUiStyleFactory` selects and loads the implementation:
`IdTech2UiStyle` uses game content, while `EngineUiStyle` uses the default Scene2D
skin as fallback.

[Menu stages](../stages/README.md) consume these styles; [the HUD](../stages/ingame/hud/README.md)
parses layouts and draws overlays. `Cake` owns shared style lifetime. Configstring
resources belong to [game configuration](../config/README.md).

## Selection and Lifetime

The factory attempts IdTech2 HUD assets through mod/base lookup for every game/mod,
rather than gating by game name. This lets content-only mods inherit base-game
fonts and numeric glyphs. If style creation fails, acquired HUD assets are released
and the factory returns `EngineUiStyle`. Menu sounds are optional.

`Cake` caches the shared style for the current base-directory/mod resolver context.
Startup, profile changes, and serverdata handling can resolve that style. When it
changes, content-styled menu stages are rebuilt rather than restyled in place;
the game screen receives the shared style for HUD rendering.

Each style owns its acquired `AssetManager` references and releases them when
`Cake` disposes it. Reusing an already-loaded texture still requires an ownership
reference, so disposing one owner does not unload another owner's assets.

## Fonts and Glyphs

- `ConcharsFontLoader` builds a `BitmapFont` from `pics/conchars.pcx`. The atlas has
  a fixed 16-by-16 glyph grid; cell dimensions come from the actual texture size,
  supporting both 128-by-128 and 256-by-256 atlases.
- HUD and menus use separate font instances and metrics over the same borrowed
  conchars texture. The style disposes both fonts before releasing its texture
  reference. Partial factory failures also dispose any fonts already created.
- Quake menus and HUD retain native conchars metrics. Engine menus use the Skin's
  `menu` font (source cap height 24 at 1/3 scale) and copies of engine drawables at
  1/3 scale. Both menu themes have 8-unit cap heights, thick insets 8 and thin
  insets 4. Engine glyph widths and line spacing remain natural.
- The raw default font/panels used by console/viewer/diagnostics are unchanged.
  Skin owns the independent raw/menu fonts. Both menu themes share the vertical
  `cl_ui_size` viewport convention; there is no new scale cvar.
- Transparency comes from PCX decoding; the font loader does not post-process alpha.
- Alternate HUD text uses the glyph high-bit toggle (`char ^ 0x80`), preserving
  mod-defined alternate glyphs and colours rather than applying a tint.
- `HudNumberFont`, implemented by `IdTech2HudNumberFont`, draws dedicated `num_*`
  and `anum_*` picture glyphs for layout number commands, including alternate/red
  fields. These are separate from the conchars font.

## Menus and Sounds

`MenuWidgetStyles` provides content-styled labels, buttons, text fields, select
boxes, lists, scroll panes, horizontal sliders, progress indicators and trees.
Two Drawable roles distinguish thick containers/action buttons from thin controls:
IdTech2 thick panels use conchars cells 1–9, and control frames use 18–26 with
half-cell content insets.
Slider tracks use 128–130 with knob 131; progress shares the track and knob.
Static separators use 29–31 with fixed caps and a stretchable middle, plus a
clockwise vertical variant. Engine menus use a gray line from the existing atlas.
Both orientations are Drawables shown through Image. Game vertical scrollbar
thumbs reuse the vertical separator; standard ScrollPane supplies dragging.
TreeStyle uses conchars 13 right/down marks and square 11 for row selection/hover;
engine menus use the existing tree shapes in matching 8-unit slots. Tree node
labels use the resolved LabelStyle. The sample retains folder expansion, selection
and scroll position when changing theme. ContentBrowser now adopts these resolved styles through ContentBrowserStyles;
its standalone Skin adapter retains native engine sizing. The client host browses
profile VFS metadata and loads visible PCX/WAL/TGA thumbnails progressively.
ImageThumbnails owns folder-scoped textures independently of shared game assets;
folder changes, browser exit and profile changes release them. Client full-image
previews borrow these cached textures through shared ImagePreviewPane, with
aspect fit, pointer-anchored zoom and pan. Back to folder / Escape restore the
grid scroll position. Further Escape presses navigate to the parent folder,
leaving the browser only at the root. Both hosts call ContentBrowser.navigateBack;
the host supplies its root action. Audio/sprite/model/map previews remain standalone-only.
The sample shows both panel roles and links slider changes to progress. Engine
styles use separate thick/thin engine panel artwork and the existing slider art.

`MenuSoundStyles` provides optional sounds from `misc/menu1.wav`, `misc/menu2.wav`,
and `misc/menu3.wav`. The menu controller emits enter/exit signals for navigation
depth changes. Shared stage button helpers trigger hover sound and text-colour
feedback, resetting it on click or menu-view changes. Cake plays queued signals.
