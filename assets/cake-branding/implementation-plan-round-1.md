# Implementing Cake design review round 1

Implement the decisions in [design review round 1](design-review-round-1.md):
the Strawberry accents direction, the selected ten-tooth icon, and thin/thick
rounded frames with flat faces and softened corner transitions.

The implementation should land in small, reviewable changes, with the gallery
providing the first runtime comparison before broader screen validation.

## Scope and working assumptions

- Apply Cake styling to engine-owned UI: the standalone viewer, console,
  engine-style menus, and engine fallback controls.
- Preserve game/mod-provided conchars, HUD pictures, fonts, and frame artwork.
  Shared skin colors can still affect game-styled controls; check those effects
  explicitly and keep content-specific overrides deliberate.
- Keep Share Tech Mono, existing font metrics, native viewer sizing, and the
  engine menu's ⅓ source scale.
- Keep current widget behavior and navigation. Screen redesign, logo animation,
  new typography, and light-theme support are separate work.
- Use the approved repository vector sources for generation. Figma remains the
  visual reference; implementation does not depend on a live Figma connection.

## 1. Make the selected artwork reproducible

Promote [the soft-corner generator](rounded-panels/generate_soft_corner_panels.py)
and [selected SVG sheet](rounded-panels/soft-corner-panels.svg) into the runtime
asset generation path.

- Add an explicit rasterize-and-pack command for the selected artwork. Keep SVG
  rendering as a development dependency; ship generated PNGs with the app.
- Preserve `panel-thick` and `panel-thin`, their positions in the 148 × 76 sheet,
  72 × 72 tile sizes, 24px splits, and 24/12px content insets.
- Generate the runtime `assets/ui/engine-panels.svg` and `engine-panels.png`
  from the accepted source. Keep earlier studies available as design references.
- Pack the standalone tiles without independently resampling the sheet, so the
  atlas contains their exact pixels and preserves edge alpha.
- Keep border artwork neutral grayscale. The palette belongs in the skin.

**Acceptance:** deterministic regeneration; valid atlas regions; transparent outer
corners; flat centers; matching corner-to-edge shades; fixed corner pixels when
stretched. Verify the 48px source minimum and the ⅓ menu presentation.

## 2. Keep the existing single-tint styling

Use the existing `TintedDrawable` and `NinePatchDrawable` setup. A tint colors
both the rim and its dark center; accept that relationship and adapt the
candidate palette to the actual runtime result. Independent surface/border
colors and layered drawables are postponed.

- Keep the source artwork neutral grayscale and put tint choices in the skin.
- Preserve existing drawable types, content insets, minimum sizes, and scaling.
- Treat the concept color assignments as a direction rather than fixed values.
- Reuse current elements; do not add widgets or a theme framework in this pass.

**Acceptance:** the simple tint setup produces readable surfaces and distinct
states at both native viewer sizing and the engine menu's ⅓ scale.

## 3. Apply semantic colors and state rules

Use [cake-colors.md](cake-colors.md) as the candidate source, with unique names
for distinct Cocoa and Raspberry values. Map colors to roles in
`assets/ui/uiskin.json` and consume those roles from engine styling code.

Starting assignments for runtime validation:

| Role | Candidate |
| --- | --- |
| Canvas and input body | Dark cocoa `#191412` |
| Panel body | Dark chocolate `#211915` or Chocolate `#251D19` |
| Hover/raised surface | Milk chocolate `#332720` |
| Primary text | Vanilla cream `#FFF3E3` |
| Secondary text | Whipped cream `#C9B8A5` |
| Placeholder text on dark input bodies | Biscuit `#968477` |
| Disabled text | Muted cocoa `#71635A` |
| Quiet borders | Dark caramel `#66503F` |
| Action accent | Strawberry `#F07883` |
| Hover and focus accent | Light strawberry `#FF9BA3` |
| Pressed accent | Highlight Raspberry `#D95769` |
| Selected row | Cocoa `#403027`, cream text, strawberry marker |
| Success / warning / error / info | Pistachio / Caramel / Error Raspberry / Blueberry |

These are initial role assignments, subject to the runtime gallery check.
Selection and errors must remain distinguishable through treatment and text,
not color alone.

`GameUiStyle.kt` currently applies green hover and selected-text colors even
when constructing engine styles. Resolve engine colors through the skin while
keeping content-specific game overrides explicit. Avoid merely changing JSON
values and leaving these Kotlin overrides in place.

Cover buttons, boolean toggles, text fields, dropdowns, lists, trees, browser
tiles, scrollbars, sliders, progress indicators, console output, and diagnostics.
Check the native `ContentBrowserStyles.fromSkin` path as well as the scaled menu
path; their backgrounds and tree styles currently differ.

**Acceptance:** normal, hover, pressed, checked, selected, focused, disabled, and
error states follow a consistent role mapping. Small text is readable on its
actual background; enabled labels do not inherit disabled colors.

## 4. Install the selected icon across desktop targets

Use [the selected icon recipe](cake-icon-strawberry-10t.json) and its SVG as the
source for the ten-tooth Strawberry icon.

- Generate window PNG sizes 16, 32, 64, 128, and 256 under each app module's
  `src/main/resources/icons/`, using identical classpath names.
- Shared launcher code loads those classpath names without selecting an app;
  the app module supplies the correct colorway. Titles are ordinary arguments.
- Generate packaging PNG, ICO, and ICNS files under each app's `icons/` directory;
  `cake/desktop.gradle` resolves it with `project.file('icons')`.
- Check small sizes visually. Make a documented optical-size variant only if
  simple downscaling loses the mark's readability.
- Audit `assets/icons` references before updating or retiring that older copy.

**Acceptance:** client, fullgame, viewer, and locally buildable application images
use the selected Cake icon. Record platform-specific checks that need another
OS rather than claiming cross-platform validation from one host.

## 5. Validate standalone Viewer screens and update the references

Review engine styling in the standalone Viewer at native sizing, across PAK
browsing, MD2/BSP previews, image previews, sprite/sound/cinematic/text views,
and empty/error states. Check hover, focus, selection, disabled controls,
readability, clipping, and larger UI/window cases. Game menus use game styling
and are outside this engine-skin review.

Add reproducible Viewer captures before this review. Reuse the shared screenshot
writer and frame-driven capture behavior where practical; avoid bootstrapping
the game/client host solely for screenshots. Preserve `ui-sample.cfg` as the
engine/game component baseline, rather than treating game menus as engine skin.

Run relevant Viewer/style/GL tests and keep generated assets reproducible.
After runtime review, update the Figma implementation examples and repository
asset documentation to reflect what shipped. Progress knob, separator, and
scrollbar shape refinements remain deferred.

**Acceptance:** no missing skin resources, broken layouts, clipped labels,
corner seams, or unintended game-art replacements. Runtime captures and source
asset paths are recorded alongside the accepted design.

## Suggested change sequence

1. Selected frame generation and runtime texture replacement, retaining existing
   color behavior for a geometry check.
2. Existing single-tint styling and the engine semantic role mapping,
   validated first in the gallery and viewer.
3. App-specific window and packaging icons: Fullgame Strawberry, Viewer Blueberry,
   Client Pistachio, keeping the selected mark's geometry.
4. Remaining control/state consistency and full-screen adjustments supported by
   captures. Progress knob, separator, and scrollbar shape refinements are deferred.
5. Final reference captures, documentation, and Figma implementation reconciliation.

The Figma extraction is already complete. Its implementation task is maintaining
that reference after the runtime changes, rather than rebuilding the library.

Review each slice before proceeding to the next. The first slice changes frame
artwork only; palette changes follow after its geometry is reviewed.

## Progress

- Slice 1: reviewed and verified by the user. Selected artwork is installed.
- Slice 2: reviewed and verified by the user. Existing single-tint drawables use warm
  surfaces, cream text, Strawberry actions, quiet input rims, and Cocoa selected
  rows. The combined rim/body tint intentionally adapts the concept colors.
  Engine hover/selection follows the skin; content menus retain green feedback.
- Slice 3: visual icons reviewed. Window and packaging assets belong to each app
  module, with shared generation/loading code and no app identity enum. Generic
  Strawberry assets remain under `assets/icons`.
- Broader screen/state review and final Figma reconciliation remain subsequent
  slices. The progress knob, separator, and scrollbar shapes are recorded for later.
