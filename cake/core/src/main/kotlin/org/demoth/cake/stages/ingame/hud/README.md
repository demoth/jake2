# IdTech2 HUD Runtime

## Responsibilities

`Hud` renders layout scripts, inventory, crosshair, timed text, and FPS overlays.
`LayoutParserCompat` tokenises IdTech2 layout scripts; `executeLayoutScript` parses
and executes them against current frame data on each call, without a compiled
command cache.

A `LayoutDataProvider` is injected when the HUD is constructed.
`GameConfigLayoutDataProvider` adapts [game configuration](../../../config/README.md),
including `gameConfig.playerConfiguration.playerIndex` for local-player highlighting.
[UI styles](../../../ui/README.md) supply fonts and numeric glyphs. Server message
routing belongs to `Cake` and `Game3dScreen`.

## Runtime Flow

```text
ServerDataMessage -> Game3dScreen -> Hud(..., GameConfigLayoutDataProvider(gameConfig))

Frame render with SpriteBatch active
  -> Hud.update(): timed text and FPS overlays
  -> drawCrosshair()
  -> executeLayout(): statusbar and optional extra layout
  -> optional drawInventory()

PrintMessage / PrintCenterMessage
  -> Game3dScreen -> Hud.showPrintMessage() / showCenterPrint()
```

Layouts read current stats, configstrings, and server frame values. Coordinate
instructions use IdTech2 top-left pixels; `LayoutCoordinateMapper` converts them
to libGDX coordinates at draw time. Invalid stat/client indices are guarded, and
unparseable numeric tokens default to zero.

The HUD batch uses the [shared UI viewport](../../README.md#presentation) projection
and virtual dimensions, so layouts, inventory, crosshair and timed text scale with
menus. The 320×240 centered layout region remains part of layout command semantics;
it is not a separate rendering viewport. The scene framebuffer retains its physical
pixel dimensions.

## Text and Overlays

- Notify text displays up to four recent lines, each expiring after 3000 ms.
- Center-print text lasts 2.5 seconds; vertical placement depends on line count.
- Alternate text toggles the glyph high bit (`char ^ 0x80`) so mod-provided conchars
  atlases control alternate appearance.
- `cl_showfps` modes are `0` off, `1` average FPS, `2+` minimum/maximum/average FPS,
  and `3+` an additional frame-time statistics line.

Inventory's hotkey column is blank: active input bindings are not integrated into
inventory row rendering.
