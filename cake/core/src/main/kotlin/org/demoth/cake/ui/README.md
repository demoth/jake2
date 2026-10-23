# Content UI Styles

## Responsibilities

The internal `ui_sample [engine|game|off]` console command opens a widget gallery
in client/fullgame. Engine and active-game modes share one layout and retain
edited fixture state when switched. Escape or Close returns to the prior view;
profile style changes close the sample before releasing borrowed resources.
The compact sample fits one virtual screen and follows the vertical `cl_ui_size`
setting live. Captions and controls all use the selected theme; fallback is
identified explicitly.
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
- Transparency comes from PCX decoding; the font loader does not post-process alpha.
- Alternate HUD text uses the glyph high-bit toggle (`char ^ 0x80`), preserving
  mod-defined alternate glyphs and colours rather than applying a tint.
- `HudNumberFont`, implemented by `IdTech2HudNumberFont`, draws dedicated `num_*`
  and `anum_*` picture glyphs for layout number commands, including alternate/red
  fields. These are separate from the conchars font.

## Menus and Sounds

`MenuWidgetStyles` provides content-styled labels, buttons, text fields, select
boxes, lists, and scroll panes. IdTech2 button borders reuse conchars box glyphs
1 through 9.

`MenuSoundStyles` provides optional sounds from `misc/menu1.wav`, `misc/menu2.wav`,
and `misc/menu3.wav`. The menu controller emits enter/exit signals for navigation
depth changes. Shared stage button helpers trigger hover sound and text-colour
feedback, resetting it on click or menu-view changes. Cake plays queued signals.
