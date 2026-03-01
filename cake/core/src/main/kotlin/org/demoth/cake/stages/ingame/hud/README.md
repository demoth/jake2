# IdTech2 HUD Runtime

## Overview
This package owns IdTech2 HUD script execution and HUD-only overlays during gameplay rendering.

Owned here:
- Tokenizing IdTech2 layout scripts (`LayoutParserCompat`).
- Parsing + executing layout script commands on each frame (`executeLayoutScript`, `Hud.executeLayout`).
- IdTech2-to-libGDX coordinate mapping (`LayoutCoordinateMapper`).
- Timed HUD text overlays: center-print + top-left notify prints (`Hud.update`).
- Gameplay FPS overlay controlled by `cl_showfps` (`Hud.update`).

Not owned here:
- Server message dispatch (`org.demoth.cake.Cake`, `Game3dScreen`).
- Configstring storage and asset ownership (`org.demoth.cake.GameConfiguration`).
- Style resource loading (`org.demoth.cake.ui`).

## Key Types
- `Hud` - Frame-time HUD renderer (layout, inventory, crosshair, notify, center print).
- `LayoutDataProvider` - Read-only bridge for layout-facing game data.
- `GameConfigLayoutDataProvider` - `GameConfiguration` adapter implementation.
- `LayoutParserCompat` - Legacy-compatible tokenizer for layout scripts.
- `executeLayoutScript` - Shared parser/executor used by runtime and tests.
- `LayoutCoordinateMapper` - Explicit top-left (IdTech2) to bottom-left (libGDX) mapping.

## Data / Control Flow
```text
ServerDataMessage
  -> Game3dScreen.processServerDataMessage
  -> Hud(..., GameConfigLayoutDataProvider(gameConfig))

Frame render (SpriteBatch active)
  -> Hud.update(delta, w, h)                // notify + center print
  -> Hud.drawCrosshair(w, h)
  -> Hud.executeLayout(statusbar, ...)
  -> optional Hud.executeLayout(extra layout, ...)
  -> optional Hud.drawInventory(...)

PrintMessage / PrintCenterMessage
  -> Game3dScreen.processPrintMessage / processPrintCenterMessage
  -> Hud.showPrintMessage / Hud.showCenterPrint
```

## Invariants
- Layout coordinates are interpreted as IdTech2 top-left pixels.
- Coordinate conversion happens only at draw time.
- Layout parsing must be fail-soft: invalid indices/tokens skip branch and continue.
- `GameConfiguration.playerIndex` is the local-player source of truth for HUD-highlighted branches (`ctf`).
- Notify print keeps max 4 visible lines and expires each line after 3000 ms.
- Center print uses legacy-like timeout (2.5 s) and line-count-based vertical anchor.
- `cl_showfps` mode parity:
  - `0` off,
  - `1` average fps,
  - `2+` min/max/avg fps,
  - `3+` adds frame-time stats line.

## Decision Log
Newest first.

### Decision: Keep direct parse+execute each frame (no compiled command cache)
- Context: compile pipeline increased complexity while dynamic values (`stats`, config lookups) change every server frame.
- Options considered:
1. Compile layout to command list and execute later.
2. Parse and execute immediately each frame.
- Chosen Option & Rationale: Option 2 keeps runtime logic simple and always reads fresh frame data.
- Consequences: parser runs each frame; simplicity favored over precompilation.
- Status: accepted.
- References: thread section about simplifying former `LayoutExecutor` (now `Hud`) and dropping compilation stage.
- Definition of Done: `Hud.executeLayout` invokes `executeLayoutScript` directly with current `stats` and `serverFrame`.

### Decision: Inject `LayoutDataProvider` into `Hud` once
- Context: passing provider on every execute call added plumbing noise and weakened ownership boundaries.
- Options considered:
1. Pass provider per `executeLayout` call.
2. Inject provider in HUD constructor.
- Chosen Option & Rationale: Option 2 aligns ownership with screen lifetime and simplifies API.
- Consequences: HUD assumes provider remains valid for its lifetime.
- Status: accepted.
- References: thread follow-up about HUD ownership simplification.
- Definition of Done: `Hud.executeLayout` signature does not include provider; provider is constructor dependency.

### Decision: Route print/centerprint handling through `Game3dScreen` to `Hud`
- Context: print messages were previously managed in root `Cake` layer, bypassing HUD style/timing concerns.
- Options considered:
1. Keep print rendering at root client layer.
2. Delegate to game screen and HUD.
- Chosen Option & Rationale: Option 2 centralizes UI rendering state with HUD style ownership.
- Consequences: `Game3dScreen` now owns message-to-HUD routing; console echo is preserved for legacy parity.
- Status: accepted.
- References: thread section on `PrintCenterMessage`/`PrintMessage` ownership move.
- Definition of Done: `Cake` forwards print messages to `Game3dScreen`; HUD renders timed notify/center text.

### Decision: Share one layout parser path for runtime and tests
- Context: separate runtime/test parse implementations risk semantic drift.
- Options considered:
1. Keep distinct test parser helpers.
2. Expose one shared `executeLayoutScript` path.
- Chosen Option & Rationale: Option 2 keeps behavior verification aligned with production parser.
- Consequences: tests assert callback outputs, not renderer internals.
- Status: accepted.
- References: thread review of naive parser + verification against legacy behavior.
- Definition of Done: `HudTest` calls `executeLayoutScript` and covers core command branches.

## Quirks & Workarounds
- Inventory hotkey column is intentionally blank.
- Why: key binding data is not yet integrated into HUD inventory rendering.
- How to work with it: treat first inventory column as placeholder.
- Removal plan: wire active input binds into inventory row formatting.

- Text alt style toggles glyph high-bit (`char ^ 0x80`) instead of applying color tint.
- Why: mods may redefine alt glyphs/colors in conchars atlas.
- How to work with it: preserve source text bytes and use `alt=true` path for legacy alternate text.
- Removal plan: none planned; this is required compatibility behavior.

## How to Extend
1. Add branch handling in `executeLayoutScript` for the new token.
2. Keep branch behavior fail-soft for invalid indices/tokens.
3. Reuse `LayoutCoordinateMapper` only at draw sites.
4. Add or update `HudTest` for command semantics and coordinate expectations.

## Open Questions
- Should `LayoutParserCompat` eventually move to `qcommon` for shared client/server tools, or remain client-local?
