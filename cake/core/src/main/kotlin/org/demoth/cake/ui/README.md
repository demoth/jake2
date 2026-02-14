# IdTech2 HUD Style

## Overview
This package owns runtime HUD style resources used by Cake when an IdTech2 game/mod is active.

Owned here:
- Loading and disposing style-specific HUD textures/fonts.
- Building `BitmapFont` from `pics/conchars.pcx`.
- Rendering HUD number fields from `num_*` / `anum_*` glyph pictures.

Not owned here:
- Layout script parsing/execution (`stages/Hud.kt`).
- Server message timing/lifecycle (`stages/Game3dScreen.kt`).
- Configstring resource ownership (`Config.kt`).

## Key Types
- `GameUiStyle` - Runtime style contract used by layout rendering.
- `EngineUiStyle` - Scene2D fallback style.
- `HudNumberFont` - Number-field rendering extension point.
- `IdTech2HudNumberFont` - IdTech2 numeric glyph renderer.
- `IdTech2UiStyle` - Concrete IdTech2 style aggregate.
- `GameUiStyleFactory` - Style selection + resource acquisition/disposal.
- `ConcharsFontLoader` - Converts IdTech2 conchars atlas into `BitmapFont` glyph data.

Related components:
- `../stages/Hud.kt` consumes `GameUiStyle`.
- `../stages/Game3dScreen.kt` owns style lifecycle and swap timing.
- `../Config.kt` resolves named pictures and client icons used by layout commands.

## Data / Control Flow
```text
ServerDataMessage
  -> Game3dScreen.processServerDataMessage
  -> reloadGameUiStyle()
  -> GameUiStyleFactory.create(gameName)
     -> load conchars + number glyph textures (AssetManager refs)
     -> build IdTech2UiStyle or fallback EngineUiStyle

Render frame
  -> Hud executes layout script
  -> draw text via style.hudFont
  -> draw numbers via style.hudNumberFont
```

## Invariants
- Style swap happens on `ServerDataMessage`, not during `Game3dScreen` init.
- Every `IdTech2UiStyle` instance acquires its own AssetManager refcounts and releases them on dispose.
- `Hud` compiles coordinates in IdTech2 top-left space and transforms only at draw time.
- Conchars atlas is always interpreted as a `16 x 16` grid (cell size derived from texture dimensions).
- Alternate text style uses legacy high-bit toggle (`char ^ 0x80`).

## Decision Log
Newest first.

### Decision: Reload game UI style only on `ServerDataMessage`
- Context: style reload was happening both on screen init and serverdata, causing duplicate lifecycle churn.
- Options considered:
  - Keep init + serverdata reload.
  - Reload only on serverdata.
- Chosen option & rationale: Reload only on serverdata for a single authoritative game/mod switch point - we know which gamemod to use only when the ServerDataMessage arrives.
- Consequences: initial pre-serverdata HUD uses engine fallback until first serverdata arrives.
- Status: accepted.
- References: `6ac60a95`, thread discussion about duplicate reload.
- Definition of Done: `reloadGameUiStyle()` has one call site in `processServerDataMessage`.

### Decision: Acquire UI texture refs per style instance
- Context: disposing an old style could unload textures still used by the active style.
- Options considered:
  - Reuse already-loaded textures without ref increment.
  - Always acquire a refcount slot per style instance.
- Chosen option & rationale: Always load+finishLoading to increment refcount deterministically.
- Consequences: predictable unload behavior during style swaps/map transitions.
- Status: accepted.
- References: `37d55e1a`, regression window around `3c125792`.
- Definition of Done: each style dispose unloads only refs it acquired.

### Decision: Split layout compile (IdTech2 space) from render transform
- Context: early implementation mixed parse logic and libGDX coordinate assumptions.
- Options considered:
  - Draw directly while parsing.
  - Compile to commands then render with explicit mapper.
- Chosen option & rationale: compile-then-render improves parity testing and isolates origin conversion.
- Consequences: more command types but easier parity validation.
- Status: accepted.
- References: `47d49927`, `4b0d1b5e`, `e4964942`.
- Definition of Done: coordinate behavior is covered by mapper/unit parity tests.

### Decision: Inventory panel follows legacy metrics; hotkey column intentionally blank
- Context: original inventory placement/background diverged from legacy behavior.
- Options considered:
  - Keep simplified centered list.
  - Match legacy panel dimensions and list window.
- Chosen option & rationale: match legacy layout (`256x240`, `17` rows, background image).
- Consequences: visuals align with original; hotkey bindings remain TODO.
- Status: accepted (with known gap).
- References: `b4fcef7f`, `client/CL_inv.DrawInventory`.
- Definition of Done: panel centered with background and scrolling window around selected item.

### Decision: Use dedicated numeric HUD font for `drawNumber`
- Context: default font could not reproduce `num_*`/`anum_*` styles.
- Options considered:
  - Keep default bitmap font.
  - Add numeric glyph renderer.
- Chosen option & rationale: dedicated renderer matches legacy `DrawField` visuals.
- Consequences: extra glyph assets and style resource ownership requirements.
- Status: accepted.
- References: `3c125792`.
- Definition of Done: health/ammo/armor number styles render from picture glyphs.

## Quirks & Workarounds
- Hotkey binding column in inventory is blank by design for now.
  - Why: current input/bind data is not wired into Cake inventory rendering.
  - Work with it: do not treat leading field as authoritative keybind output.
  - Removal plan: integrate bind lookup equivalent to legacy `keybindings` map.
- Style selection is hardcoded to known IdTech2 game names (`baseq2`, `rogue`, `xatrix`, `ctf`).
  - Why: no generic style registry exists yet.
  - Work with it: add game name to set only when it shares IdTech2 HUD resources.
  - Removal plan: replace with pluggable style registry when game installation model exists.
- Text drawing forces font color to white per draw call.
  - Why: protects against external font color contamination in shared `BitmapFont` state.
  - Work with it: color variants should use glyph atlas differences, not `BitmapFont` tint.
  - Removal plan: optional if renderer state isolation is introduced globally.

## How to Extend
1. Add/implement a new `GameUiStyle` + `HudNumberFont` for your engine/game family.
2. Add style selection logic in `GameUiStyleFactory.create` (prefer future registry when available).
3. Ensure all style-loaded textures are reference-counted per instance and released in dispose.
4. Keep layout command semantics in `LayoutExecutor` aligned with legacy counterpart before adding new branch logic.
5. Add parity tests for new layout branches and coordinate mapper assumptions.

## Open Questions
- Should style selection be moved from hardcoded game-name checks to a registry/config-driven model?
- Should UI resources adopt the same deferred-retirement semantics as `GameConfiguration` assets during map transitions?
- Should inventory hotkey lookup be wired to current Cake input binding state now or deferred with UI system overhaul?
