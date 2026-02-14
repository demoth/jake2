# IdTech2 UI Style

## Overview
This package owns game-specific HUD style resources (`GameUiStyle`) used by runtime HUD rendering.

Owned here:
- Building HUD font from `pics/conchars.pcx` (`ConcharsFontLoader`).
- Loading numeric HUD glyph pictures (`num_*`, `anum_*`) and exposing them as `HudNumberFont`.
- Selecting style implementation in `GameUiStyleFactory`.

Not owned here:
- HUD layout parsing and draw command execution (`../stages/ingame/hud`).
- Server message routing/timing (`../stages/ingame/Game3dScreen.kt`, `../Cake.kt`).
- Configstring/gameplay resource ownership (`../Config.kt`).

## Key Types
- `GameUiStyle` - Runtime style contract consumed by HUD.
- `EngineUiStyle` - Fallback style backed by Scene2D default skin.
- `IdTech2UiStyle` - IdTech2 style aggregate (`conchars` font + number font).
- `GameUiStyleFactory` - Style selection and per-instance asset acquisition.
- `ConcharsFontLoader` - Converts 16x16 conchars atlas into `BitmapFont` glyph set.
- `IdTech2HudNumberFont` - Draws number fields with `num_*` / `anum_*` textures.

## Data / Control Flow
```text
ServerDataMessage
  -> Game3dScreen.processServerDataMessage
  -> GameUiStyleFactory.create(gameName, assetManager, defaultSkin)
  -> Hud(spriteBatch, style, dataProvider)

Render frame
  -> Hud.executeLayout(...)
  -> style.hudFont for text
  -> style.hudNumberFont for hnum/anum/rnum/num
```

## Invariants
- Style swap happens at `ServerDataMessage` handling time.
- Each `IdTech2UiStyle` instance acquires its own `AssetManager` refs and releases them in `dispose()`.
- Conchars atlas mapping is always `16 x 16`; cell size is derived from real texture dimensions.
- Alternate text color/style is represented by legacy high-bit glyph toggle (`char ^ 0x80`), not by tinting.

## Decision Log
Newest first.

### Decision: Keep style selection in `GameUiStyleFactory` with IdTech2 game-name gate
- Context: Cake still has no generic game-install registry.
- Options considered:
1. Add registry-driven style binding now.
2. Keep explicit IdTech2 game-name check for now.
- Chosen Option & Rationale: Use a small hardcoded gate (`baseq2`, `rogue`, `xatrix`, `ctf`) until generic game registration exists.
- Consequences: adding another IdTech2-compatible game/mod requires code change in factory.
- Status: accepted.
- References: thread notes about future generic engine and deferred game installation model.
- Definition of Done: non-IdTech2 game names use `EngineUiStyle`; IdTech2 set uses `IdTech2UiStyle`.

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
- Supported IdTech2 game names are currently hardcoded in factory.
- Why: no game/style registry exists yet.
- How to work with it: update `IDTECH2_GAME_NAMES` when enabling another IdTech2-compatible title/mod.
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
