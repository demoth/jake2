# Cake UI Skin Assets

## Overview
Runtime Scene2D skin assets live in `assets/ui/`:
- `uiskin.json` (style/resource map),
- `uiskin.atlas` + `uiskin.png` / `engine-panels.png` / `engine-controls.png` (drawables/nine-patch metadata),
- bitmap fonts (`share-tech-mono*.fnt/.png`).

It does not own stage/layout logic (see `cake/core/.../stages`).

Current usage and capture commands are documented in the
[UI gallery guide](../../../info/ui-skin-sample.md). Selected artwork, app icon
recipes and Figma references are in the [branding guide](../branding/README.md).

The accepted direction uses Quake's fixed conchars proportions as a reference
for engine resources, with higher-resolution engine artwork allowed. The
sample exposes thick container and thin control frames plus horizontal
slider/progress support from the existing Quake glyphs.

## Key Resources
- `console-panel` (`TintedDrawable`) - console frame drawable.
- `buttonUp`/`buttonOver`/`buttonDown` (`TintedDrawable`) - primary button chrome.
- `panel-thick` / `panel-thin` (`uiskin.atlas`) - separate 72×72 frames with 24-pixel corners.
- `control-panel` (`NinePatchDrawable`) - thin frame with 12-pixel content insets and 48-pixel minimum dimensions.
- `engine-panels.svg` (in this directory) - source for neutral rounded frames; regenerate the SVG and PNG from the repository root with `python3 cake/asset-tools/branding/rounded-panels/generate_soft_corner_panels.py --runtime` (requires Inkscape and Pillow). The generator renders each tile at 4× resolution, downsamples it once, and packs its exact pixels into the atlas page.

## Invariants
- Skin class tags are short names (`ButtonStyle`, `Color`, ...), not fully-qualified class names.
- Resource names referenced from Kotlin stages must remain stable.
- Bitmap font files must exist at paths referenced by `BitmapFont` entries.

## Decision Log
Newest first.

### Decision: Solid rounded scrollbar thumbs and circular position knobs
- Engine scrollbars use solid white capsule artwork, tinted Strawberry. Horizontal and vertical nine-patches stretch their middle while preserving 12px end caps. Their native thickness remains 24px; the minimum thumb length is 25px so both caps remain intact.
- Slider and read-only position/progress controls use a solid 24px circle (twice the initial diameter), with a 24×24 native footprint. Hover, pressed, disabled colors and the engine menu's ⅓ scale are preserved.
- Tracks and separators keep their existing artwork. Conchars controls retain their own source glyphs.
- Regenerate the neutral SVG and PNG with `python3 cake/asset-tools/ui/generate_engine_controls.py` (Inkscape and Pillow required). The generated controls occupy a separate atlas page; legacy primitives remain available for other uses.

### Decision: Strawberry accents with existing single-tint drawables
- Slice 2 maps the candidate palette to semantic skin roles. Cream text sits on dark warm surfaces; Strawberry marks actions and scroll thumbs, Light strawberry marks hover, and Highlight Raspberry marks pressed/focused controls.
- Quiet input rims use Biscuit; panel rims and separators use Dark caramel. Selected rows use Cocoa with cream text; placeholders use Biscuit and disabled content uses Muted cocoa.
- The same tint colors each frame's rim and dark center. This intentionally gives frame interiors darker values than the concept surface swatches; no layered drawables were added.
- Native viewer backgrounds use Chocolate, with Milk chocolate scroll tracks. Engine menus keep the existing ⅓ resource scale.
- Engine hover and selected text now follow the skin. Conchars menus keep their green hover/selection feedback and game-provided artwork; shared primary/disabled text colors follow the warm palette.
- `warning`, `error`, `success`, and `info` use Caramel, Error Raspberry, Pistachio, and Blueberry respectively. Historical palette names remain aliases for compatibility.
- Verification and captures: [branding guide](../branding/README.md).

### Decision: Rounded frames with softened corner shading
- Use the round 1 thin/thick frames: a flat gray face with corner-only highlight/shadow transitions, a dark center, and transparent outer corners.
- Keep the 72×72 tiles, 24px splits, 24/12px content insets, existing resource names, and menu scaling.
- Keep the existing single-tint drawables. Palette choices can adapt to their combined rim/body coloring; independent layers are postponed.
- Source generation: [branding guide](../branding/README.md).

### Decision: Preserve native Quake menu size; adapt engine menus
- Quake menu font/drawables retain their native size. The 3× relationship describes engine source resolution, not a scale applied to Quake screens.
- Engine menu drawables are copied at 1/3 scale, with thick/thin insets 8/4 and thin minimum size 16. Raw skin drawables remain unchanged.
- `menu` is a separate Skin font using `share-tech-mono-menu.fnt`: generator size 34, native cap height 24 and line height 38. `scaledSize: 8` scales it by 1/3 while retaining natural widths and line spacing (38/3 UI units).
- Visible capital height, rather than line height alone, is the typography comparison. The original size-21 `default` font remains unchanged for console/viewer/raw skin uses.
- Both themes share the same viewport and `cl_ui_size`; HUD/menu font separation is retained.
- Status: accepted and implemented; supersedes enlarging Quake menus to 3×.

To reproduce the menu font from the repository root:

```sh
./gradlew :cake:asset-tools:run --args="--ttf fonts/Share_Tech_Mono/ShareTechMono-Regular.ttf --out /tmp/cake-engine-menu-font --name share-tech-mono-menu --size 34 --page-width 1024 --page-height 512 --padding 2"
```

Copy both generated files to `assets/ui/`. The existing default font reproduction
command below remains valid for the independent console/viewer font.

### Superseded decision: Normalize Quake menus to the engine reference units
- Quake menu font metrics and derived drawables use a fixed 3× scale, producing 24-unit cells, thick insets 24 and thin insets 12.
- HUD and menu fonts have independent metrics over the same texture. HUD size and source art are unchanged.
- Both themes use the same layout and vertical `cl_ui_size`; no extra scale option or theme viewport is needed.
- Natural engine glyph widths and differences in visible cap height are retained.
- Status: superseded after broader menu testing showed excessive size and clipping.

### Decision: Use thick frames for action buttons
- User testing preferred the thick frame for buttons, with thin frames for checkbox toggles, text inputs, dropdowns and control panels.
- Both engine and Quake-derived styles follow this choice; containers keep the thick frame.
- Status: accepted and implemented.

### Decision: Author engine resources at a 3× Quake reference scale
- Quake's fixed 8-pixel cell is the reference; thick frame source insets/corners are 24, thin content insets 12. Menu typography now uses source cap height 24; the initial line-height comparison is superseded.
- Preserve the engine font's natural glyph widths. Matching square cells, text wrapping and exact widget footprints is not required.
- Native resource resolution and display normalization are separate choices. This slice establishes resources; viewport sizing comparison remains separate.
- Use distinct thick container and thin control frames; retain the current palette and standard libGDX widgets.
- Status: accepted; font and panel resources implemented. Other engine artwork still needs comparison.

To reproduce the unchanged raw/default font, run from the repository root:

```sh
./gradlew :cake:asset-tools:run --args="--ttf fonts/Share_Tech_Mono/ShareTechMono-Regular.ttf --out /tmp/cake-engine-font-3x --name share-tech-mono --size 21 --page-width 512 --page-height 512 --padding 2"
```

The tool runs in `cake/asset-tools`; its input path is relative to that directory.
Check the resulting `.fnt`: `lineHeight=24`, then copy both generated files to
`assets/ui/`. Nominal generator size 21 is not the same metric as line height.


### Decision: Keep short skin class names in `uiskin.json`
- Context: IDEA inspections flagged fully-qualified names as redundant and noisy.
- Options considered:
1. Keep fully-qualified names.
2. Use short tags supported by `Skin` JSON loader.
- Chosen Option & Rationale: Option 2 for readability and lower noise.
- Consequences: style blocks are shorter; behavior unchanged.
- Status: accepted.
- Definition of Done: IDEA reports no "Short name can be used" warnings for skin block keys.
- References: thread section "proceed with name shortening".

### Decision: Deduplicate duplicate style definitions in `uiskin.json`
- Context: repeated `default` style entries caused override ambiguity and warnings.
- Options considered:
1. Keep duplicate blocks and rely on "last wins" behavior.
2. Merge once and preserve intentional extra styles.
- Chosen Option & Rationale: Option 2 for explicit behavior and maintainability.
- Consequences: file is easier to review; fewer hidden overrides.
- Status: accepted.
- Definition of Done: IDEA skin inspection has no duplicate-resource warnings.
- References: thread section "resolve warnings - deduplicate entries".

### Decision: Use generated Share Tech Mono bitmap fonts as engine defaults
- Context: need deterministic high-resolution UI text independent of runtime font generation.
- Options considered:
1. Runtime freetype generation.
2. Pre-generated bitmap fonts in assets.
- Chosen Option & Rationale: Option 2 for stable startup behavior and reproducible skin output.
- Consequences: font updates require regeneration step.
- Status: accepted.
- Definition of Done: `uiskin.json` loads without missing font files at startup.
- References: thread section about `share-tech-mono` generation and filename mismatch fix.

## Quirks & Workarounds
- Atlas `xy` coordinates are top-left origin.
- Why: libGDX texture atlas coordinate convention.
- How to work with it: when given center coordinates, convert to top-left before editing atlas entries.
- Removal plan: none (engine convention).

- Nine-patch `split` is `left, right, top, bottom` and strongly affects perceived "inset".
- Why: split defines fixed corner/edge regions and stretch center.
- How to work with it: tune split and table/widget padding together; do not debug with tinted colors unless intended.
- Removal plan: add finalized non-debug panel art and lock split values.

- Tinted drawables multiply source colors.
- Why: Scene2D `TintedDrawable` behavior.
- How to work with it: use `white` tint when verifying raw debug art.
- Removal plan: none (expected behavior).

## How to Extend
1. Add/modify atlas region in `uiskin.atlas` (including `split`/`pad` when nine-patch).
2. Reference it from `uiskin.json` directly or via `TintedDrawable`.
3. Validate in-game stage using the drawable (`MainMenuStage`/`ConsoleStage`).
4. Run IDEA inspection for `uiskin.json` and verify no duplicate or unresolved resource warnings.

## Open Questions
- Should console panel eventually have independent insets from the thick container frame?
- Should skin validation be automated in CI (e.g., duplicate-resource and missing-reference checks)?
