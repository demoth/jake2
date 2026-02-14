# IdTech2 HUD Runtime

## Overview
This package owns IdTech2 HUD layout parsing and rendering during gameplay frames.

Owned here:
- Tokenizing IdTech2 layout strings (`LayoutParserCompat`).
- Executing HUD layout commands directly against `SpriteBatch` (`Hud.executePipeline`).
- IdTech2-to-libGDX coordinate conversion (`LayoutCoordinateMapper`).
- Timed center-print rendering state in HUD (`Hud.showCenterPrint`, `Hud.update`).

Not owned here:
- Configstring storage/resource lifetime (`org.demoth.cake.GameConfiguration`).
- UI style creation/disposal (`org.demoth.cake.ui.GameUiStyleFactory`).
- Server message dispatch and frame orchestration (`Game3dScreen`).

## Key Types
- `Hud` - Runtime HUD renderer for status layouts, inventory, crosshair, and center-print.
- `LayoutDataProvider` - Abstraction for layout-facing game data lookups.
- `GameConfigLayoutDataProvider` - `GameConfiguration` adapter used by `Game3dScreen`.
- `LayoutParserCompat` - Legacy-compatible tokenizer for IdTech2 layout scripts.
- `LayoutParser` - Command compiler used for parity/tests (runtime draws directly).
- `LayoutCoordinateMapper` - Explicit coordinate origin conversion helpers.

## Data / Control Flow
```text
Server messages
  -> Game3dScreen updates GameConfiguration (config strings, layout, playerIndex)
  -> Game3dScreen.render
     -> Hud.update (center-print timer + draw)
     -> Hud.executePipeline(statusbar layout)
     -> optional Hud.executePipeline(extra layout)
     -> optional Hud.drawInventory
```

## Invariants
- HUD command coordinates are interpreted in IdTech2 top-left pixel space.
- Rendering always applies explicit conversion to libGDX bottom-left origin.
- `GameConfiguration.playerIndex` is the source of truth for local-player highlighting/filtering.
- Layout parsing must never crash on malformed indices; invalid branches are skipped with warning.

## Decision Log
Newest first.

### Decision: Parse and draw on the fly in runtime HUD
- Context: compile-then-render pipeline made runtime path harder to reason about.
- Options considered:
1. Keep compile list per frame then render.
2. Parse layout and render directly.
- Chosen option & rationale: parse + draw directly in `Hud.executePipeline` to reduce moving parts and keep state local.
- Consequences: less intermediate allocation and simpler runtime flow; command compiler retained for tests/parity only.
- Status: accepted.

### Decision: Keep `playerIndex` in `GameConfiguration` as source of truth
- Context: HUD, prediction, and entity visibility all need the local player slot.
- Options considered:
1. Keep separate providers/lambdas per subsystem.
2. Store authoritative value in `GameConfiguration` and consume it everywhere.
- Chosen option & rationale: `GameConfiguration.playerIndex` avoids duplicated state and keeps serverdata ownership explicit.
- Consequences: subsystems depend on config lifecycle; value is normalized to unknown/valid range.
- Status: accepted.

### Decision: Recreate game UI style only on `ServerDataMessage`
- Context: duplicate style reload points caused unnecessary churn.
- Options considered:
1. Reload on init and serverdata.
2. Reload only on serverdata.
- Chosen option & rationale: serverdata is the authoritative point where game/mod is known.
- Consequences: fallback style is used until serverdata arrives.
- Status: accepted.

## Quirks & Workarounds
- Inventory hotkey column is blank intentionally.
- Why: bind lookup is not wired into Cake HUD yet.
- Work with it: do not treat inventory hotkey text as authoritative.
- Removal plan: integrate input bindings into inventory row formatting.

- `LayoutParser` still exists alongside direct runtime rendering.
- Why: parity/unit tests compare generated commands with legacy harness.
- Work with it: use `Hud.executePipeline` in runtime code paths; use `LayoutParser` in tests.
- Removal plan: remove compiler when parity tests are migrated to runtime-level assertions.

## How to Extend
1. Add new IdTech2 command branch in `Hud.executePipeline`.
2. Keep IdTech2 coordinate semantics and use `LayoutCoordinateMapper` only at draw call sites.
3. Mirror branch behavior in `LayoutParser.compile` if parity tests rely on command generation.
4. Add or update parity tests under `cake/core/src/test/kotlin/org/demoth/cake/stages`.
