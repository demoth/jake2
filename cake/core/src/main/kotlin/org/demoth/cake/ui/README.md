# IdTech2 UI Style

## Overview
This package owns shared content style resources (`GameUiStyle`) used by runtime HUD rendering and content-styled menus.

Owned here:
- Building HUD font from `pics/conchars.pcx` (`ConcharsFontLoader`).
- Loading numeric HUD glyph pictures (`num_*`, `anum_*`) and exposing them as `HudNumberFont`.
- Deriving content-styled menu label/button styles from the active HUD font.
- Selecting style implementation in `GameUiStyleFactory`.

Not owned here:
- HUD layout parsing and draw command execution (`../stages/ingame/hud`).
- Style lifetime and serverdata-driven swap decisions (`../Cake.kt`).
- Configstring/gameplay resource ownership (`../Config.kt`).

## Key Types
- `GameUiStyle` - Runtime style contract consumed by HUD.
- `EngineUiStyle` - Fallback style backed by Scene2D default skin.
- `IdTech2UiStyle` - IdTech2 style aggregate (`conchars` font + number font).
- `GameUiStyleFactory` - Style selection and per-instance asset acquisition.
- `MenuWidgetStyles` - Shared menu label/button styles exposed by `GameUiStyle`.
- `ConcharsFontLoader` - Converts 16x16 conchars atlas into `BitmapFont` glyph set.
- `IdTech2HudNumberFont` - Draws number fields with `num_*` / `anum_*` textures.

## Data / Control Flow
```text
ServerDataMessage
  -> Cake resolves/reuses shared GameUiStyle for current resolver context
  -> Cake rebuilds content-styled menu stages when the style instance changes
  -> Game3dScreen.setHudStyle(style)
  -> Game3dScreen.processServerDataMessage(msg)
  -> Hud(spriteBatch, style, dataProvider)

Render frame
  -> Hud.executeLayout(...)
  -> style.hudFont for text
  -> style.hudNumberFont for hnum/anum/rnum/num
  -> MainMenuStage uses style.menuWidgets for label/button rendering
```

## Invariants
- Style swap happens at `ServerDataMessage` handling time, but ownership lives at `Cake` scope.
- Menu stage rebuilding is the supported Scene2D style-switch path; widgets are not restyled in place.
- Each `IdTech2UiStyle` instance acquires its own `AssetManager` refs and is released by `Cake`.
- Conchars atlas mapping is always `16 x 16`; cell size is derived from real texture dimensions.
- Alternate text color/style is represented by legacy high-bit glyph toggle (`char ^ 0x80`), not by tinting.

## Decision Log
Newest first.

### Decision: Attempt IdTech2 HUD style for every game/mod, then fallback
- Context: map/content-only mods often keep baseq2 HUD assets and should inherit them automatically.
- Options considered:
1. Keep explicit IdTech2 game-name gate.
2. Always try IdTech2 HUD assets via resolver mod/base layering and fallback only on missing assets.
- Chosen Option & Rationale: Option 2 avoids false fallback to engine skin for unknown-but-IdTech2-compatible mods.
- Consequences: unknown mods can still use IdTech2 HUD immediately when `pics/conchars.pcx` and number glyphs are available via mod or baseq2.
- Status: accepted.
- References: thread issue where map-only mods resolved to engine skin despite baseq2-compatible assets.
- Definition of Done: style creation first attempts `pics/conchars.pcx` + `num_*`/`anum_*`, then falls back to `EngineUiStyle` only if that load path fails.

### Decision: Acquire/release style textures per style instance
- Context: style ownership must follow HUD/screen lifecycle without unloading assets still in use by another owner.
- Options considered:
1. Reuse existing loaded textures without ref increment.
2. Always load/finalize for this style instance and unload on dispose.
- Chosen Option & Rationale: Option 2 gives deterministic refcount behavior during transitions.
- Consequences: additional explicit acquisition bookkeeping in factory.
- Status: accepted.
- References: thread regression around style reload lifecycle.
- Definition of Done: disposing `IdTech2UiStyle` unloads only paths acquired by that style instance.

### Decision: Build conchars font from raw atlas dimensions (not fixed pixel width)
- Context: real conchars assets may be `128x128` (8px cell) or `256x256` (16px cell).
- Options considered:
1. Require fixed `256x256` atlas.
2. Derive cell size from texture dimensions with fixed 16x16 grid.
- Chosen Option & Rationale: Option 2 matches legacy 256-glyph indexing while supporting variant atlas sizes.
- Consequences: glyph metrics scale with atlas cell size.
- Status: accepted.
- References: thread issue `Unexpected conchars width: 128, expected: 256`.
- Definition of Done: both `128x128` and `256x256` conchars atlases render all 256 glyphs.

### Decision: Keep HUD number style separate from conchars font
- Context: IdTech2 HUD numbers use dedicated picture glyphs and include red blink style.
- Options considered:
1. Draw numbers with generic bitmap font.
2. Load `num_*` and `anum_*` pictures and use dedicated renderer.
- Chosen Option & Rationale: Option 2 matches legacy `DrawField` output and blinking color branches.
- Consequences: extra asset set and renderer path for numbers.
- Status: accepted.
- References: thread request for health/ammo custom number style in former `LayoutExecutor.drawNumber` path.
- Definition of Done: `hnum`, `anum`, `rnum`, and `num` branches render with picture glyphs, including alt/red style.

## Quirks & Workarounds
- Unknown/non-idtech2 games may still trigger IdTech2 style attempt first.
- Why: content compatibility is determined by actual asset presence, not name gate.
- How to work with it: if IdTech2 HUD assets are unavailable, factory falls back to `EngineUiStyle` automatically.
- Removal plan: replace with registry-driven mapping when game-install configuration exists.

- Conchars transparency depends on decoded texture data from Cake PCX pipeline.
- Why: font loader builds glyph metrics only and does not post-process alpha.
- How to work with it: keep PCX decoder behavior compatible with IdTech2 transparent palette handling.
- Removal plan: none currently; this is expected responsibility split.

## How to Extend
1. Add a new `GameUiStyle` implementation for a new engine/game family.
2. Keep number rendering isolated behind `HudNumberFont`.
3. Wire style selection in `GameUiStyleFactory.create`.
4. Ensure style-owned assets are acquired/released per style instance.
5. Add tests for atlas mapping and number renderer behavior.

## Open Questions
- Should style selection move to a data-driven game registry once game installation metadata exists?
