# Cake branding assets

The engine uses a dark theme, Share Tech Mono, warm surfaces and cream text with
Strawberry accents. Game-provided conchars and menu artwork remain independent.
Runtime colors and state assignments live in `assets/ui/uiskin.json`; the
[candidate palette](cake-colors.md) also contains colors used by app icons and
future experiments, and is not an exact runtime role specification.

## Icons

All apps use the same ten-tooth Cake mark and dark tile. Fullgame uses Strawberry,
Viewer Blueberry, and Client Pistachio. See [app-icons.md](app-icons.md) for
recipes, output locations and platform coverage.

```sh
python3 cake/asset-tools/branding/generate_app_icons.py
```

The selected geometry/tile recipe is `cake-icon-strawberry-10t.json`; app colors
come from `app-icons.json`. App modules own their generated window and packaging
icons. `generate_logo.py` supplies the underlying vector geometry.

## Engine skin artwork

Thick and thin panel rims have rounded inner/outer corners, flat faces and soft
corner-only shade transitions. Source tiles are 72×72 with fixed 24px corners,
12/6px rims and 24/12px content insets. White/gray artwork receives one skin tint
for both its rim and dark center.

Scrollbar thumbs are solid rounded rectangles with fixed end caps. Position
knobs are solid 24px circles. Engine menus display source artwork at one-third
scale; the standalone Viewer uses native sizes. Separators keep their existing
artwork.

```sh
python3 cake/asset-tools/branding/rounded-panels/generate_soft_corner_panels.py --runtime
python3 cake/asset-tools/ui/generate_engine_controls.py
```

Generation requires Inkscape and Pillow. Keep `generate_panels.py` and
`generate_flat_panels.py`: the selected generator imports their geometry helpers.
SVG sources are in `cake/asset-tools/ui/`; PNG exports go to `assets/ui/`.
Their atlas geometry is in
`assets/ui/uiskin.atlas`. See [the skin guide](../ui/README.md) for font generation
and state rules.

## References and verification

- [Current Figma components](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=6-3971)
- [Current palette](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=3-44)
- [Neutral control specimens](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=72-564)
- [Current runtime captures](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=66-6885)
- [Archived design exploration](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=61-8787)

Use the [UI gallery](../../../info/ui-skin-sample.md) to compare engine and game
themes at 1440×900, `cl_ui_size 720`. Viewer resource captures use
`cake/asset-tools/automation/capture-viewer-ui.py`; see [Viewer documentation](../../../info/cake-viewer.md).
Generated captures and local game data stay under `build/`.

Check native Viewer sizing as well as menu scaling, normal/hover/pressed/disabled
states, and content-theme isolation. Logo animation and OS thumbnailer integration
are separate features.
