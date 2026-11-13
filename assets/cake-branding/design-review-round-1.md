# Cake design review round 1

Completed 8 November 2026.

The first review established a shared visual baseline in Figma and refined
Cake's colors, logo, and UI frames. The preferred direction combines a dark
chocolate palette, strawberry accents, a ten-tooth cog and sliced-cake logo,
and smooth rounded frames in two weights.

## Existing UI documented in Figma

The existing engine and game UI building blocks were extracted into a reusable
Figma library, with applicable native engine variants for the standalone viewer.
The library includes original artwork, all 256 conchars cells, typography,
widget states, and reference captures.

- [Current component reference](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=6-3971)
- [Conchars source grid](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=15-257)
- [Engine typography and font reference](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=3-82)

The engine typeface is **Share Tech Mono Regular**, available in the repository
as [ShareTechMono-Regular.ttf](../cake-ui/Share_Tech_Mono/ShareTechMono-Regular.ttf).

Engine menu artwork uses a 3× source resolution and is displayed at ⅓ scale;
native viewer sizing remains distinct. Reference captures use **1440 × 900**
with **`cl_ui_size 720`**. Reproduction is documented in the
[automation guide](../../cake/core/src/main/kotlin/org/demoth/cake/automation/README.md),
using [ui-sample.cfg](../automation/ui-sample.cfg).

## Colors refined

The new [candidate palette](cake-colors.md) contains 23 colors organized around
chocolate backgrounds and surfaces, cream text, strawberry highlights, and
supporting status colors.

Three UI treatments were compared using the same controls and states:
Cream-led, Strawberry accents, and Filled strawberry.

**Preferred UI direction: Strawberry accents.**

- [Candidate palette](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=27-2)
- [Archived UI color comparisons](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=61-11523)

Final color assignments for individual roles remain open. The comparisons show
focus, selection, disabled controls, and status feedback together so those
assignments can be judged in context. Engine and game UI are **dark-only**;
light-theme support is out of scope.

## Logo refined

The original generated concept was reconstructed as
editable vector geometry. The cog, cake bodies, and rims have independent color
roles. The cog and five slices retain separate layers with a shared center for
later animation.

Four proportion presets were compared across three candidate colorways.

**Selected icon variation: ten teeth with the Strawberry colorway.**

| Element | Color | Hex |
| --- | --- | --- |
| Cog | Strawberry | `#F07883` |
| Cake bodies | Vanilla cream | `#FFF3E3` |
| Cake rim | Highlight Raspberry | `#D95769` |
| App icon tile | Dark cocoa | `#191412` |

The original orange was a placeholder and has no branding significance.

- [Color and proportion comparisons](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=26-514)
- [Selected icon and size previews](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=33-567)
- [App icon SVG](cake-icon-strawberry-10t.svg), [512px PNG](cake-icon-strawberry-10t.png), and [recipe](cake-icon-strawberry-10t.json)
- [Transparent mark SVG](cake-mark-strawberry-10t.svg)
- [Logo generator](generate_logo.py) for exact geometry and color experiments

## UI elements refined

The main refinement replaces the angular beveled frame with rounded inner and
outer corners, a broad flat rim face, restrained directional shading, and a
flat center. There is no ornament, scalloping, texture, or literal frosting
decoration.

**Selected treatment after follow-up comparison: flat rim faces with softened
corner transitions, in thin and thick weights.**

The rim uses white highlights (`#FFFFFF`), a light-gray face (`#C8C8C8`), and
dark-gray shadows (`#707070`). Gradients soften the outer highlight and inner
return around the top-right and bottom-left corners. The broad face stays flat;
the fully smooth tube appearance and sharp three-shade version remain comparison
studies.

| Weight | Intended use | Source rim | Content inset |
| --- | --- | --- | --- |
| Thick | Panels, grouped content, action buttons | 12px | 24px |
| Thin | Inputs, dropdowns, toggles, compact controls | 6px | 12px |

Both use 72 × 72 source tiles with fixed 24px corners. Only the edge middles
and center stretch. The artwork is strictly **white and gray, without a color
tint**; color can be applied later in the skin definition. The center is flat
dark gray (`#171717`). The fades remain inside the fixed corner pieces and match
the straight-edge shades at their endpoints, preserving seamless nine-patch
stretching.

- [Selected frame treatment and size examples](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=46-1672)
- [Selected SVG source sheet](rounded-panels/soft-corner-panels.svg)
- [Selected frame generator](rounded-panels/generate_soft_corner_panels.py)
- [Earlier smooth frame study](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=37-3)
- [Earlier sharp three-shade study](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=44-1356)

## Next work

The [round 1 implementation plan](implementation-plan-round-1.md) maps these
decisions to asset generation, skin changes, desktop icons, and runtime checks.

- Assign final skin color roles using the preferred Strawberry accents direction
  and the accepted rounded frames together.
- Rasterize and pack the selected soft-corner artwork, integrate it and the color
  roles into the engine skin, then inspect
  full client screens and the standalone viewer at their actual UI sizes.
- Refine the icon for small sizes if needed, prepare platform-specific icon
  exports, and explore logo animation.

Implementation slice 1 installs the selected thin/thick artwork in
`assets/ui/engine-panels.svg` and `.png`, preserving existing colors, resource
names, insets, and scaling. Palette mapping will keep the existing single-tint
drawables and can adapt the candidate scheme; independent body/rim layers are
postponed. Full application-screen studies remain a subsequent pass.

Slice 1 verification: engine/game gallery capture at 1440 × 900 with
`cl_ui_size 720`; `UiSamplePreviewGlTest` passed (1 test, no skips); neutral RGBA
artwork, exact tile packing, transparent corners, and fixed 24px corners checked
at five sizes including a 48px minimum dimension. The game capture is
pixel-identical before and after. Runtime captures are under
`build/captures/design-round-1/slice-1/{before,after}/{engine,game}.png`.

The user reviewed and verified slice 1. Slice 2 now applies the palette through
the existing skin: cream text, Strawberry action rims, Biscuit input rims,
Dark caramel panel rims, and Cocoa selected rows. The selected artwork's dark
centers remain multiplied by their border tint; concept surface swatches are
adapted rather than implemented as independent fills. Native viewer surfaces
use Chocolate, and scrollbar thumbs use Strawberry over Milk chocolate tracks.
Content menus retain game artwork and green hover/selection feedback; shared
primary and disabled text use the warm palette.

Slice 2 checks: all four selected tests passed with no skips
(`GameUiStyleFactoryTest`, `UiSamplePreviewGlTest`, `MenuStagesGlTest`). The GL
test verifies engine/game color isolation and that constructing content styles
does not change native viewer styles. Engine/game galleries were captured at
1440 × 900, `cl_ui_size 720`; the standalone viewer was captured at 1024 × 768
with local `pak0.pak` content. Captures:
`build/captures/design-round-1/slice-2/{engine,game,viewer}.png`.

Computed contrast checks include primary text on the native surface (15.12:1),
selected text (11.49:1), placeholder text on the tinted input body (5.46:1),
the main input rim against its body (3.62:1), and scrollbar thumb/track (5.32:1).
These checks cover the stated opaque colors; they do not claim full-screen
accessibility validation. Broader state/screen review remains the next slice.

The user reviewed and verified slice 2. Progress knob, separator, and scrollbar
shape changes are deferred; their remaining square artwork is recorded for a
later pass.

Slice 3 replaces Cake app icons using the selected ten-tooth mark and tile.
The user selected Strawberry for Fullgame, Blueberry for Viewer, and Pistachio
for Client. Their cake centers stay Vanilla cream. The color recipes are in
`app-icons.json`; `generate_app_icons.py` generates window PNGs, per-app
packaging SVG/PNG/ICO/ICNS files, and generic Strawberry compatibility copies.
Each app owns its window PNGs in `src/main/resources/icons/` and packaging files
in `icons/`. Shared launcher code loads identical classpath names, and Gradle
uses `project.file('icons')`. No app identity enum or shared icon copies are
needed. Window titles distinguish Client and Fullgame as well as Viewer. The
libGDX placeholder resources are retired, and `assets/icons/logo*` copies are
refreshed. The user reviewed the icon visuals and approved this app-local layout.

Slice 3 checks: shared desktop tests passed; all three Linux `jpackageImage`
builds succeeded; each packaged app launched, and its actual window icon
property matched the exported pixels at all five sizes (16/32/64/128/256).
Each Linux package's launcher PNG matches its app-specific source. PNG, ICO,
and ICNS files decode, including ICO sizes 16–256 and ICNS up to 1024 physical
pixels. Native Windows/macOS packaging and desktop presentation remain untested.

Review comparison: `build/captures/design-round-1/slice-3/app-icons.png`, with
large variants plus 16/32/64/128px exports shown at native pixels. Window check
results: `build/captures/design-round-1/slice-3/window-icon-checks.json`.

After moving exports into the app modules, regeneration preserved every icon
byte. All three Linux application images were rebuilt and launched again; their
window icon properties still match all five app-local exports. JAR inspection
confirmed that each app contains its own icons and desktop-common contains no
app icons or identity enum. Shared desktop tests passed again.

## Figma cleanup after round 1

The active Figma reference now follows the implemented engine skin:

- [Current runtime references](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=66-6885): refreshed engine/game galleries at 1440 × 900, `cl_ui_size 720`, plus reviewed standalone browser/model captures.
- [Implemented palette and roles](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=3-44): source values match `assets/ui/uiskin.json`. Original palette variable IDs were migrated to runtime names/values; game-specific greens remain separate.
- [Current components](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=6-3971): engine variants retain their IDs, with source-derived rounded frames and current colors. Original conchars geometry remains in place. Scrollbar thumbs and slider/progress knobs now use solid rounded rectangles and circles; separators retain their existing artwork.
- [Accepted neutral frames](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=46-1672): promoted from Frame studies to Foundations, along with their source components.
- [Selected Strawberry concept](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=61-11526): retained as a frozen historical intent reference, not an exact runtime specification. Runtime uses a single tint for the rim and center.
- [Round 1 archive](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=61-8787): old runtime baseline, frozen original palette/component/color comparison boards, and editable smooth/three-shade frame studies.

The twenty obsolete beveled full-tile/slice components were removed after checking that no instances remained on any page. Historical comparison snapshots are intentionally frozen so changes to the active library cannot rewrite the review history. Typography, conchars source cells, candidate palette and logo exploration remain available.

The control follow-up doubles the circular knob to a 24px native diameter (8 menu units). [Neutral control sources and size specimens](https://www.figma.com/design/ATgASPuXFCxO40preR7b3h?node-id=72-564) and the existing component/state examples reflect this geometry. Game-style controls remain unchanged.
