# Cake UI skin sample specification

The sample will make Cake's supported resources, widget styles, states and screen
usages visible in one place. It supports consistent styling and deliberate visual
decisions across engine and Quake-derived themes. Coverage is tracked in
[the skin inventory](../assets/ui/INVENTORY.md). The basic internal gallery and
Engine/Game switching are implemented. Remaining target widgets and composed
screen cases are planned below.

The primary host is an internal screen in Cake client/fullgame, opened through
a console command. Build the layout once and switch its style source between
Engine and Active game. Both modes use the same fixture data and layout rules.

The [execution plan](#execution-plan) below defines the implementation slices,
their deliverables and completion checks. The compact gallery and capture command
and the conchars panel/slider experiment are complete. Broader widget coverage
and screen compositions remain planned.

## Proportions and resource decision

Quake II's fixed conchars artwork is the proportion reference. Engine font and
panel artwork can be changed freely, including using higher resolution while
retaining comparable logical proportions. Use the existing style builders and
simple sizing choices; retain expected differences rather than building a
general geometry framework. Keep the HUD text behaviour separate from any UI
presentation choices.

The sample exposes two panel roles: thick frames for groups and action buttons,
and thin frames for checkbox toggles, inputs and dropdowns. User testing selected
this distinction for both themes.
Quake uses its fixed conchars art; engine now has distinct thick/thin frames
with 24-pixel corners and 24/12-pixel content insets respectively.

The accepted engine asset reference is **3× native Quake resolution**, displayed
at **1/3 scale in menus**. Quake's on-screen size stays native. Engine source
panels keep 24-pixel corners and 24/12-pixel insets; menu copies use 8-pixel
corners and 8/4-unit insets. Raw engine skin usage outside menus remains unchanged.

Visible font capital height is the typography comparison: a menu-specific Share
Tech Mono bitmap is generated at size 34 (cap height 24, line height 38), then
scaled by 1/3 to cap height 8. Its natural widths and line spacing remain intact.
The original raw UI font remains size 21/line height 24. Matching engine/Quake
line heights, square glyphs and identical wrapping are not goals. Both menu
themes use the same vertical `cl_ui_size`; HUD metrics stay native.

## Sample coverage follows the target hierarchy

Use **elements → styles → widgets → screens** throughout the sample. The
[target widget list](../assets/ui/INVENTORY.md#target-widgets-for-cake-screens)
defines its core scope: label, action button, checkbox, text input, selectable
list, dropdown, scroll pane, tree, split pane, panel, content tile, image view,
horizontal slider, horizontal progress indicator and static separator.
Composed and specialized widgets cover the browser, resource information,
playback, console output, scene preview, diagnostics and HUD.

The gallery must let a reader trace each target widget to its style roles and
their elements. Screen compositions show how those same widgets are reused.
Start with current implementations, including the `x` checkbox toggle. Stock
skin styles outside the target belong to an optional inspection area; listing
them does not commit Cake screens to using them.

## Coverage and consistency workflow

1. Maintain the target hierarchy and baseline inventory, separating intended
   widget support from existing definitions, Quake coverage and actual usage.
2. Build an internal sample screen with Engine and Active game modes using one
   layout builder. Borrow the host's existing styles through a small adapter.
3. Identify game-derived styling and engine fallback in the same cases; use the
   host's current profile resolution rather than loading another content context.
4. Inspect states and compositions, recording gaps, inconsistent usages and
   intentional differences.
5. Decide which roles should share resources and which need distinct styles.
   Use optional visual variants when comparison helps answer a specific question.
6. Apply agreed consistency changes in small slices and inspect affected usages
   in the sample and real screens.

The sample is a reference and inspection tool. It should work with deterministic
fixtures, without connecting to a server, changing profiles, loading a PAK or
playing media. It uses the already active profile's styles without requiring a
running game session.

## Invocation and screen behaviour

Console commands available in client/fullgame:

| Command | Behaviour |
| --- | --- |
| `ui_sample` | Open the sample in its last selected mode; default to Engine |
| `ui_sample engine` | Open or switch to engine styling |
| `ui_sample game` | Open or switch to the active game's resolved styling |
| `ui_sample off` | Close the sample and restore the previous menu/game view |

Keep this out of ordinary menu navigation. Start with the console command;
a configurable key binding can invoke it later without a dedicated hidden-key
implementation. The screen itself has Engine/Game controls and a Close action.
Escape closes it. Global console access remains available; issuing the command
from the console closes the console so the sample immediately receives input.

The sample temporarily takes menu-like input priority and releases mouse capture.
Apply the host's existing menu/console pause policy, keep network processing
running, and restore the prior view/input routing on close. Wire rendering,
resize, input, pause policy and disposal explicitly in Cake.

The first slice provides labels, action/disabled buttons, the existing checkbox
toggle, editable/hint/password/disabled inputs, a selectable list, dropdowns,
a panel and a scrolling specimen. They fit on one virtual screen, with short
captions rather than resource descriptions. Every label and control, including
Engine/Game/Close, uses the selected style. A common neutral background avoids
mixing either mode with the active profile's backdrop. Game mode explicitly
identifies whole-theme engine fallback. Game styling retains the engine caret
resource but uses its own shared green selection fill.
The outer panel and scrolling specimen use the thick container role; the list
uses the thin control role. The scrolling content has no inner frame in either
theme. Slider and progress share a track and knob, with a separate
disabled slider specimen. The progress indicator follows the editable slider.

## Automated captures

Capture Engine and Game using the same layout, resolution and active profile:

```sh
./gradlew :cake:cake-client:run --args='+set cfg_autosave 0 +set vid_hidden 1 +set vid_fullscreen 0 +set vid_windowed_width 1440 +set vid_windowed_height 900 +set cl_ui_size 720 +ui_sample_capture both /tmp/cake-ui'
```

`ui_sample_capture <engine|game|both> <directory>` opens the sample, waits two
rendered frames per mode, writes `engine.png` and/or `game.png`, and exits. It
captures directly after the sample is drawn, before console/diagnostic overlays.
It uses the active profile's resolved Game style, including visible engine
fallback when game content is unavailable. Existing output images are replaced.
Keep the capture command last; it handles exiting without an additional `+quit`.

`cfg_autosave 0` prevents automatic writes of temporary settings into the profile.
`vid_hidden 1` keeps the window invisible. Both controls are session-only cvars;
they use the same `+set` mechanism as video and UI settings. Explicit
`writeconfig` still writes if requested. A graphics context/display is required
even for hidden captures. Capture failures propagate as process failures.

For a path with spaces, keep it quoted inside the Gradle argument string, for
example `+ui_sample_capture both "/tmp/cake UI"`. Startup command handling preserves
those argument boundaries and embedded hyphens, plus signs and semicolons.

Verification covers the actual client launcher and Gradle run task, two distinct
1440×900 images with real Quake assets, unchanged profile file contents/timestamps,
explicit and normal saving, capture without a configured profile, and nonzero
exit for an invalid output directory. The command-buffer suite and focused Cake
startup/video/UI tests pass. These checks use temporary profiles; no normal
profile configuration needs to be changed for capture verification.

## Two views within the internal sample screen

### Resource and control gallery

The default gallery is a compact widget form. Keep technical resource names in
the inventory; additional element/style inspection cases can be separate views.
Keep sample content and layout rules constant while changing themes so supported
behaviour and visual differences can be understood.

| Area | Inventory coverage | Content and interaction |
| --- | --- | --- |
| Palette | C01–C08 | Named swatches beside text on actual surfaces; include translucent surfaces over light and dark backdrops |
| Typography | F01–F05, W01 | UI font roles, mixed case, numbers, punctuation, long path, wrapping, ellipsis, unsupported-glyph examples; Quake normal/alternate text in compatibility area |
| Surfaces | D01–D06, D11, D21 | Panel and button samples at minimum, normal and large sizes; show nine-patch padding and edges |
| Buttons | W02, W03, W10, W11 | Action, current `x` boolean toggle, and plain browser tile; idle/hover/pressed/disabled, checked on/off, keyboard focus where supported |
| Input | W04 | Empty field with hint, entered text, caret, selection, focused/unfocused, disabled, long value, password masked with `*`; feedback as adjacent text |
| Selection | W05, W06 | List and open dropdown with long choices; selected/unselected, hover, disabled dropdown, scrolling |
| Navigation | W07–W09 | Horizontal/vertical scrolling, tree expanded/collapsed and selected, draggable splitter, overflowing content |
| Two panels | Thick/thin frame roles | Thick container and action buttons; thin frames for other controls; same roles in both themes; available |
| Horizontal controls | W15–W16 | Interactive slider and read-only progress indicator using a shared value; available |
| Extensions | W12–W14, W17–W19 | Separate optional area for native checkbox/radio, icon buttons, tooltip, window and touchpad; vertical/curved slider/progress variants remain optional |

Show fixed visual specimens of transient states beside a live control. Render
fixed specimens using the same resolved style resources as live widgets, with
explicit state labels. Keyboard focus must be exercised on live controls where
supported; mark missing feedback as a gap. Do not invent hover/press behaviour
in a screenshot and count it as an implemented interaction.

The gallery should report a missing required resource with its name/type rather
than silently substituting it. Intentionally resolved engine fallback in a Quake
theme is valid and should be identified as such. Keep optional extensions separate
so their absence cannot prevent reviewing the required controls.

### Screen compositions

Use selectable scenarios within the same application rather than squeezing every
screen into one crowded view. Fixtures should resemble current Cake usage:

| Scenario | Contents | Styling and support questions |
| --- | --- | --- |
| Menu | Vertical navigation over a neutral backdrop; profile label; enabled and disabled actions | Button proportions, text hierarchy, readability over a backdrop |
| Form | Profile list beside name/path fields, dropdown, `x` boolean toggle, wrapped description, feedback and Apply/Back | Alignment, density, field states, form scrolling |
| Browser | Folder tree, horizontal split, wrapping tiles, long breadcrumb/path, selected and hovered tiles, metadata and Back | Tree affordance, selection, flat idle tiles, thumbnail contrast, resize behaviour |
| Preview controls | Information panel, image placeholder, frame label, Previous/Play/Pause/Loop/Stop/Next, skin selector; sound duration variant | Toolbar spacing, narrow-window wrapping, disabled single-choice selector |
| Console and diagnostics | Info/warning/error lines, clipped/wrapped output, prompt and command field; metric labels and simple graph | Severity contrast, panel padding, caret/selection, dense text |
| Quake compatibility | Normal/alternate glyph samples and numeric fields on representative surfaces | Original glyph colours, font size, digits, explicit engine fallback |

Use fixed generated thumbnails and a neutral scene placeholder; full image/model
loading is unnecessary for a skin prototype. Reuse existing leaf widgets when
they can accept fixtures cheaply. Keep fake playback local to the sample and
reuse its actual button/label styles; no sound or animation backend is required.
An embedded Quake browser composition initially demonstrates resolved styling,
not completed production embedding.

## Theme and viewport controls

Engine mode borrows the host's engine Skin. Active game mode borrows its current
GameUiStyle; today this is Quake-derived where content loads successfully and
engine fallback otherwise. Show the selected mode, effective style and fallback
roles so a game-mode fallback is not mistaken for native game coverage. Named
visual variants can be added later for specific decisions.

Use one layout builder with explicitly supplied resolved styles. Switching modes
rebuilds its actors from the same fixture model, preserving entered text, checked
states, selections and scroll positions where applicable. The fixture content,
layout rules, viewport and scale stay the same; natural size differences caused
by font/drawable metrics remain visible. Two independently maintained screen
implementations would make comparisons unreliable.

The sample owns its Stage, fixture textures and UI state, and borrows host styles.
Closing it does not dispose the engine Skin, active game style or AssetManager-owned
textures. The current implementation closes the sample before profile changes
replace game styling; reopen it to inspect the new profile. Mode controls use the
selected style, consistently with the other widgets.

The sample uses its own UiViewport and follows `cl_ui_size` live. The setting is
the minimum virtual height, with a 4:3 reference width; other aspect ratios extend
the available area. Lower values make elements larger. The existing default is
720 and minimum is 640. Both Engine and Game modes share this mapping, and the
compact form fits within one reference screen at either size. Shared fonts are
not mutated to implement scaling. Video resolution determines the physical area
onto which the virtual screen is mapped; the setting retains its vertical meaning.

Start visual review at 960×720 and 1440×900, plus a narrow 640×720 window and a
wide 1920×1080 window. These are prototype review sizes, not a new minimum window
size contract. Inspect text clipping, scrollability, dropdown placement, tile
reflow and nine-patch borders at the chosen `cl_ui_size`. Include a high-resolution
window to verify that controls scale with the virtual reference height.

## Compact UI atlas and separate font texture

Use conchars as inspiration for a compact sheet of reusable UI primitives:
panel corners/edges/centre, fills, marks and small controls. Fonts stay in their
own bitmap texture and metrics file. The engine already loads Share Tech Mono
separately; old font regions in the UI atlas are not current typography inputs.

Named atlas regions are the runtime interface. A grid can make authoring easier,
but neither a fixed grid size nor a fixed cell order is required for engine art.
The Quake adapter maps legacy cell indices to the same semantic roles. A new
engine atlas should not require a Quake-specific loader.

Atlas roles, to refine against actual style usages:

| Primitive | Uses | Metadata or state requirement |
| --- | --- | --- |
| Neutral fill | Selection, tintable surface, cursor or dim overlay | Opaque neutral pixels for predictable tinting |
| Thick panel frame | Action buttons, console, information panel, form/browser container | Quake cells 1–9; matching engine proportions, nine-patch splits and content padding |
| Thin control frame | Checkbox toggles, fields, dropdown | Quake cells 18–26; matching engine role and state treatments |
| Check mark | Optional native checkbox; current boolean toggle may remain text | On/off and disabled appearance |
| Tree controls | Expand/collapse | Distinguishable at the smallest logical size |
| Track and thumb | Horizontal/vertical scrollbars; optional sliders | Fixed borders, stretch direction, minimum thumb size |
| Horizontal slider/progress track and knob | Editable slider and read-only position indicator | Quake cells 128–131; fixed end caps/knob, stretchable or repeated middle |
| Split handle | Browser split | Useful hit area and visible handle |
| Direction/icon marks | Optional dropdown arrow and later icon buttons | Legible at control size; add only with an actual style consumer |

Keep source artwork size, logical dimensions, padding, hit area and stretch rules
explicit. A 3×3 panel assembled from separate Quake glyph cells can satisfy the
same drawable role as an engine nine-patch region. They need not share texture
packing. Preserve Quake pixel colours where meaningful; multiplying them by an
engine palette colour is a design choice, not a neutral conversion.

The atlas is a resource organization option; adopting it does not require a new
visual style. Use the sample to judge whether existing resources can be shared
consistently and where distinct artwork is justified. Set palette values, atlas
resolution, cell size and icon treatment through concrete usage decisions.

## Completion and evidence

The prototype is ready for coverage and consistency review when the required
inventory has gallery cases and composition cases, live controls can be exercised,
and both engine and Quake coverage can be inspected with visible fallback/gap information.
Optional extensions do not block that milestone.

Save relevant gallery and composition captures with theme name, viewport policy
and dimensions. Record findings against inventory IDs and update the coverage
and consistency tracker only for states and usages actually reviewed. For each
visual decision, record what is shared, what differs intentionally and why.
The sample remains useful as a reference even when the current appearance is kept.

Initial verification should check required resource resolution, independent
theme ownership, theme switching, actual input/selection, overflow and resizing.
Use focused interactive checks; introduce automated tests only for reusable
behaviour such as coverage validation if it becomes part of the implementation.
Do not build a permanent rendering-test framework for the prototype.

## Execution plan

Complete and verify one slice at a time. Estimates below are approximate changed
application/build lines, excluding documentation; revise them against the actual
diff before starting each slice. Later slices depend on what the sample exposes.

### Slice 1 Internal gallery with engine and game modes

**Status: complete.** `UiSkinSample.kt` contains the Stage, layout and small style
adapter; `Cake.kt` owns command registration and host integration. Application
diff: 254 lines added and 4 removed across those two files (258 changed), below
the 280–450 estimate. No build changes, moved code or permanent tests were added.

Verification: client/fullgame compilation and 23 existing style/viewport/menu
tests pass. A temporary hidden-window OpenGL check used real baseq2 content and
an empty profile to exercise both native content styling and engine fallback.
It checked typed-value/selection retention, pointer clicks, popup removal, nested
wheel focus, scroll restoration with clamping, console priority, Escape/Close,
reopening, narrow resize and profile invalidation. A host spy checked pause
requests and continued host updates. Live game-session and native executable
verification remain open; visual consistency decisions remain for review.

Compact layout verification at that slice: the engine form needed 597 virtual units of
height, fitting within the 608 units available after padding at `cl_ui_size 640`.
The game form also fits. A hidden-window check verified all visible labels and
inputs use the selected theme, values survive switching, and changes to
`cl_ui_size` apply live. Both modes were checked at a 3840×2160 framebuffer;
console/close/profile lifetime checks still pass.

User verification: the compact, consistently themed gallery and vertical
`cl_ui_size` scaling were confirmed, and this slice was accepted for commit.
Tree, splitter, tiles, image view and composed screen cases remain for slice 2.

- Add a sample Stage under
  `cake/core/src/main/kotlin/org/demoth/cake/ui/sample/`, with one layout builder,
  a fixture model and a small adapter for engine/game styles.
- Register `ui_sample [engine|game|off]` in `Cake.kt`. Integrate the screen with
  rendering, input priority, cursor capture, menu-like pause policy, resizing,
  Escape/Close and disposal. Restore the previous view when closing it.
- Borrow the engine Skin and current GameUiStyle. Resolve styles on open/switch;
  close the sample when the host replaces profile styling. The sample does
  not own these resources or create a separate content mount.
- Show labels, action buttons, the existing boolean toggle, text input,
  selectable list, dropdown, scroll pane and panel. Include disabled controls, checked/on-off
  examples, password input, hints and long text. Live controls expose transient
  hover, press, caret and selection states.
- Keep specimen captions concise and use the selected style throughout.
  Track technical style/resource mappings in the inventory rather than adding
  descriptions between the controls.

**Deliverable:** open the gallery from the client/fullgame console and switch
Engine/Game without changing layout rules or fixture state.
**Done when:** open/switch/close, console access, input restoration, resize and
resource lifetime work. Game fallback is visible; compare both styles without
joining a server. Mark only demonstrated inventory cases Available.
**Estimate:** 280–450 changed lines across the sample, adapter and Cake host.
Host lifecycle integration is the main uncertainty. Verify it before expanding
the gallery. No standalone launcher or Gradle task is needed for this slice.

### Conchars panels and horizontal controls

**Status: implemented.** The sample uses thick/thin panel roles and standard
Slider/ProgressBar widgets. Game controls use cells 18–26 with half-cell insets;
container surfaces use 1–9. Track/knob cells 128–131 are shared by the slider and
read-only progress. At that slice the engine reused existing art, with gray/cyan slider colours
for visibility. The subsequent engine-resource slice below adds distinct panels.

Verification: focused style/viewport/menu tests and client/fullgame compilation
pass. A hidden-window check exercised pointer input at minimum/midpoint/maximum,
linked progress, disabled input, value retention, mode switching, fallback and
profile lifetime. At that slice engine needed 597 virtual units within the 608-unit content area
at `cl_ui_size 640`; game mode fits too. Captures were checked at 1440×900 and
3840×2160. Visual choices remain available for user review.

1. Map the existing panel cells to two named drawable roles in the current style
   builder: thick container (1–9) and thin control frame (18–26). Reuse the
   existing nine-patch construction. Change widget backgrounds to thin frames
   while keeping thick frames for the containing panel.
2. Build a horizontal track from start/middle/end cells 128–130 and use cell 131
   as its knob. Reuse it in standard Slider and ProgressBar styles. The initial
   progress example is a read-only position indicator sharing the slider's value;
   filled progress is a later choice if needed.
3. Add a small nested-panel group, slider and progress indicator to the compact
   sample. Keep the full view within one virtual screen at `cl_ui_size 640`.
   Use the selected theme throughout. Existing engine assets can be reused for
   the corresponding roles initially; identify any incomplete role explicitly.
4. Capture both modes with the existing command. Review frame nesting, control
   insets, slider minimum/middle/maximum and disabled appearance. Use these
   Quake-derived proportions to guide engine resource changes afterward.

**Done when:** both frames and both horizontal controls are visible in the sample,
the slider works, and captures show the intended container/control distinction.
The user confirmed the panel/slider appearance. Production game menu controls
receive thin-frame styles through the existing factory. Sound options use the
shared slider for `s_volume` over 0–1 in increments of 0.05, with a two-decimal
readout and edits staged until Apply. Untouched console-configured values retain
their original precision. Other numeric editors retain text input.

Volume verification exercised all 21 step values through the actual options
screen/backend, Apply/Back behaviour, active-theme styling and preservation of
an untouched 0.73 value. The 23 focused style/viewport/menu tests pass.

### Slice 2 Complete the target widgets and screen compositions

With the conchars experiment available, add tree, split pane,
content tile and image view to complete the broader target set.

- Add tree, split pane, content tile and image view to complete the broader target
  widget kinds. Show relevant colours, fonts and drawable primitives beside them.
- Add a form composition and a browser composition. Use fixture VfsEntry values
  with the existing ContentBrowser where practical; use generated placeholder
  textures and callbacks rather than opening archives.
- Add representative information/transport and console specimens using the same
  style resources as their real consumers. Show custom HUD/diagnostic rendering
  in a separate compatibility area as needed to cover the inventory.
- Keep each sample view within one virtual screen, following `cl_ui_size`.
  Keep layout rules and fixture content stable across Engine/Game modes.

**Done when:** every target widget has an inspectable case; required states and
usages have named cases or an explicit gap. Check narrow/wide layouts, overflow,
dropdown placement, tree selection and splitter dragging. Capture the engine
baseline and update inventory coverage; this does not imply visual acceptance.
**Estimate:** 200–350 changed lines, mainly sample fixture/layout construction.
Reassess custom console/HUD reuse before adding adapters for those components.

### Slice 3 Complete game coverage and compatibility cases

- Extend the engine/game adapter to the remaining target roles and composed cases.
  Where the existing content bundle lacks a role, identify engine fallback.
- Expose font metrics, normal/alternate glyph text, unsupported text and HUD
  numeric rendering using the host's current resources. Show factory fallback as
  it actually behaves; independent fallback changes are a separate decision.
- Verify repeated mode switching and profile changes, including a profile that
  falls back to engine styling. Keep the UI atlas and font roles separate.

**Done when:** all target cases can be inspected in both modes; effective style
and remaining gaps are explicit; switching and closing retain correct ownership.
Run existing relevant font/style tests and interactive lifecycle checks.
**Estimate:** 100–200 changed lines beyond the initial adapter, depending on
custom HUD/console reuse. Split work that requires production style changes.

### Slice 4 Record concrete consistency decisions

Review the rendered sample together. For each finding, record the inventory ID,
affected widgets/screens, shared elements, applicable states, intended differences
and the decision. Resolve these initial questions using the visible cases:

- Which text roles share fonts/colours, and which differ for feedback?
- Which buttons/panels/fields share frames, insets and state treatments?
- What implements the checkbox role, and how is its state shown in each theme?
- Which Quake roles are derived and which intentionally use engine fallback?
- Which interaction states lack useful feedback or differ unintentionally?

**Deliverable:** a short decision list and a mapping from target style roles to
resource names for both themes, with explicit gaps. Keep current appearances
where they meet the agreed rules. The atlas grid/packing and optional visual
variants become implementation work only when a decision calls for them.

### Slice 5 Apply the agreed rules to actual consumers

First resolve target roles through the shared Skin-based contract. Then migrate
one consumer group per patch: menu/form widgets, browser and preview controls,
then console/diagnostics where relevant. Reuse standard libGDX style classes;
introduce custom style objects only for concrete custom-widget needs.

Verify each changed group in the sample and in its actual host. Check profile
changes, fallback, viewport behaviour and resource lifetime when those paths are
affected. Update the inventory with the resulting support and remaining gaps.
Estimate each patch after slice 4 establishes its precise changes.

**Completion:** the target widget set is inspectable, engine/Quake support is
explicit, equivalent usages resolve consistent styles, and intentional differences
have recorded reasons. The sample remains the reference for future additions.

Native packaging, optional widget extensions and visual variant loading can be
scheduled separately when needed. No new automated tests are required for static
fixture layout; use focused existing checks and interactive verification. Add
tests when an implemented reusable contract or ownership behaviour merits them.


### Engine resources at the 3× reference

**Status: implemented; visual review pending.** Share Tech Mono was regenerated
with the existing `BitmapFontGeneratorTool` at size 21, yielding line height 24
while preserving natural glyph proportions and generated metrics. Reproduction
commands are recorded in [the asset README](../assets/ui/README.md).

`engine-panels.svg` is the editable source for a small separate atlas page with
two 72×72 frame regions. Both preserve 24-pixel corners; the thick container has
24-pixel content insets, while the thin control drawable has 12-pixel insets and
48-pixel minimum extents. Initially buttons, checkbox toggles, inputs and dropdowns
shared the thin frame; the user-tested button decision below supersedes this. Normal input/dropdown borders use the existing text tint and
focused inputs use cyan so those frames remain visible. Existing palette colours
are retained. The legacy atlas page remains available pending its own cleanup.

Sample row spacing was reduced from 6 to 3 virtual pixels. At that slice the engine
form needed 603 units within 608 available at `cl_ui_size 640`; game needed 319.
The resource slice establishes the native size reference. Theme display
normalization, slider proportions and other inherited artwork remain to review.

Verification: focused style/viewport tests passed. A hidden-window graphics check
asserted the engine's 24-pixel font line height, 24/12-pixel frame insets and
48-pixel thin minimum. Both themes fit at reference heights 640 and 720 and at
4K. Pointer input, slider endpoints, linked progress, disabled controls, typed
state across theme switching, console/close and profile fallback checks passed.
Automated captures were inspected for the new font and frame usage.


### Button frame choice after user testing

Action buttons now use the thick frame in both themes, retaining their existing
hover/pressed/disabled treatments. Checkbox toggles, inputs, dropdowns and the
control-panel role retain thin frames. The engine checkbox explicitly borrows
the thin input background so changing action-button artwork does not change it.

To keep the sample on one virtual page with taller buttons, the heading now
identifies the theme and replaces the separate mode-description row; the text
specimen is one line. The input specimen still contains a long resource path,
and unavailable game styling is explicitly identified as engine fallback.
At that slice, game caret and text selection still inherited engine resources;
the later shared-selection fix below replaces game text selection with green.

Verification: four focused style/viewport tests passed. The graphics probe
confirmed thick action-button and thin checkbox insets in both themes, preserved
interaction/state behavior, theme switching and fallback, and page fit at
reference heights 640/720 and 4K. Current preferred height is 599 for engine and
316 for game, within the 608-unit content area at `cl_ui_size 640`.


### Menu display normalization (superseded)

**Status: superseded.** Enlarging Quake menus was rejected in user testing; see
the native-size correction below. At this slice, The Quake menu font has its own metrics/cache over the
same conchars texture as the native HUD font. Menu font and derived panels,
slider track/knob, selection fill and vertical scroll drawables use a fixed 3×
scale. Fonts and drawables agree on 24-unit cells, thick insets 24, thin insets
12 and thin minimum extents 48. The HUD remains at its native 8-pixel metrics;
engine glyph widths, texture artwork and viewport/cvar semantics are unchanged.
The style disposes both borrowed-texture fonts; partial factory failures also
release created fonts before unloading texture references.

Shorter gallery captions keep wide Quake glyphs within the existing shared
layout. The percentage column reserves enough width for `100%` to prevent
slider geometry from changing during a drag. Option names wrap in their column
so wider Quake text cannot overlap editors in Sound options or other sections.
The name/description column minima are 220/280 units, preserving the form's
existing overall minimum width while keeping common names such as `s_volume`
on one line.

Verification: focused font/style/viewport checks and client/fullgame compilation
passed. The opt-in OpenGL atlas test confirms independent menu/HUD metrics and
shared texture sampling. A graphics probe using real baseq2 content confirms
24/12-unit resources, HUD line height 8, thick buttons/thin checkboxes, pointer
slider endpoints/midpoint, disabled state, linked progress, typed state across
theme switches, profile fallback/disposal and console/close behavior. Both
sample modes fit at `cl_ui_size 640` and 720 and at 4K: engine needs 599 virtual
units and game 588, within the 608-unit content area at reference height 640.
Sound options retain all 21 volume values at step 0.05, staged Apply/Back behavior
and untouched off-grid values. Clean Engine/Game captures are produced through
`ui_sample_capture`; no capture settings are saved.

Visible engine text is narrower and has different cap-height/descender metrics;
Quake's square cells remain intact. Exact wrapping is intentionally different.
The slider knob is 24 units high in both themes but differs in width/artwork,
and scroll surface nesting differed at that slice. These remained targeted visual
choices for subsequent review, rather than a requirement for a geometry system.


### Native Quake size and engine menu adaptation

**Status: implemented.** Quake menu metrics/drawables return to native size;
HUD/menu font separation remains. Engine menus use the independent Skin `menu`
font and copied drawables at 1/3 scale. Native cap height 24 becomes 8; font
advances and line spacing retain their natural proportions. Raw engine skin
fonts and panels used by the console/viewer/diagnostics remain unchanged.
The sample now uses the same engine menu style builder as real menus.

The shared menu-button helper retains frame left/right insets instead of
replacing them with zero. The option-name wrapping/column changes made solely
for enlarged Quake text are reverted, as are shortened gallery captions. The
fixed-width percentage column remains useful for stable slider dragging.

Verification: six focused font/style/viewport tests, the opt-in OpenGL atlas/style
test, and both client builds pass. The GL test checks visible cap height 8,
engine natural line height 38/3, native Quake/HUD metrics, 8/4 frame insets,
16-unit thin minimums and unmodified raw engine font/panels. Real-content probes
check sample interaction, slider endpoints/midpoint, disabled controls, linked
progress, typed-state retention, theme/fallback changes, console/close and 4K.
The sample needs 361 units in engine mode and 316 in game mode, within 608
available at `cl_ui_size 640`.

A broader audit captures 13 screens in both themes at reference heights 640 and
720: main, multiplayer, profiles, singleplayer setup, hosting, join, player setup,
options hub, Sound, Video, Input, Renderer and Client options (52 captures).
No menu buttons lie outside the stage or discard frame insets. The profile
editor's two columns are visible again; longer Video forms retain vertical
scrolling as needed. Both themes pass the Sound volume interaction probe,
including all 21 increments, Apply/Back and off-grid value preservation.

This supersedes using a fixed 3× display scale for Quake menus. The 3× factor is
an engine source-resolution reference; overall UI density remains controlled by
`cl_ui_size`. Further engine font/art refinement can use this native Quake
baseline without enlarging the game theme.


### Dropdown popup frame and indicator support

Game dropdown popups use the same thin frame as the closed field. This changes
only the SelectBox's copied ScrollPaneStyle; ordinary scroll panes keep their
existing container frame and the popup list has no extra background.

The pinned libGDX 1.13.1 SelectBoxStyle has no separate arrow/icon property. It
supports normal/hover/open/disabled background drawables, which also provide
left/right content insets. An indicator can be included or composed into those
backgrounds with a reserved right inset, without changing widget behavior. The
legacy engine `select` atlas region already includes a down triangle; current
engine/game thin-frame backgrounds do not include an indicator. Adding one to
both themes is explicitly deferred by the user; it is not a Content Browser
prerequisite. Revisit background composition/right-inset allocation when resumed.


## Browser preparation before client embedding

UI preparation proceeds in small slices; browser embedding remains a later
slice. The dropdown arrow indicator is recorded as deferred. The existing ContentBrowser already implements folders, wrapping
file tiles, thumbnail placeholders, selection metadata, scrolling and preview/Back
callbacks. Reuse it rather than introducing another browser or UI framework.

### Required coverage and current gaps

| Element / widget | Existing support | Work before client embedding |
| --- | --- | --- |
| Labels, action buttons, panels | Resolved engine/game menu styles; native Quake size restored | Reuse; preserve the standalone viewer's existing engine sizing |
| Separator | Both orientations implemented: conchars 29–31 and engine gray line, shown in sample | Available for browser use; static only, themed drag handle remains deferred |
| Tree | Resolved engine/game TreeStyle and labels, with an interactive sample | Available: triangle 13 right/down, selection and hover. Adopted through ContentBrowserStyles; native viewer adapter retained |
| Split pane | Standard libGDX SplitPane; browser uses left/right panes with a vertical handle | Themed handle artwork/interaction refinements are deferred; retain the existing engine handle as an explicit fallback when adapting the browser |
| File/folder tile | Resolved tile ButtonStyle with transparent idle and shared tree selection | Adopted in both hosts; themed labels/placeholders and borrowed viewer thumbnails |
| Image / placeholder | Existing Image/Container, aspect-fit thumbnails and unavailable placeholder | Include representative image, folder, unsupported and unavailable cases in a browser sample; no new ImageStyle is required |
| Scroll panes and long text | Existing styles/behavior; grid is vertical-only, tree may overflow horizontally | Check tree/grid wheel focus, long/deep paths, ellipsis/wrapping and any engine horizontal-scroll fallback explicitly |
| Browser shell / metadata / Back | Explicit ContentBrowserStyles and existing preview callbacks | Implemented: native engine adapter for viewer, active-profile menu adapter for client |

Keep decorative separators separate from draggable split panes in the inventory.
Reuse a drawable between them where useful. A horizontal bar assembled from
29/30/31 can be presented vertically by rotating the drawable; no new PCX asset
is needed. The browser already has libGDX SplitPane dragging behavior. A new
themed drag handle or grip is not required now; defer that work and retain the
existing engine handle as a fallback. Static separator coverage can proceed
independently.

### Small implementation slices

1. **Static separators — complete.** Add horizontal/vertical separator roles in both themes
   using existing engine line/split resources and conchars 29–31. Show both
   orientations in the sample; check fixed end caps, stretching and minimum
   size at native Quake sizing and current `cl_ui_size`. Themed drag handles are
   deferred. No general Separator class is needed.
2. **Browser style coverage and adoption — complete.** Extend existing resolved styles with
   TreeStyle, tile ButtonStyle and the shell background role; reuse existing
   label/button/scroll styles and retain the existing engine SplitPaneStyle as
   an explicit fallback. TreeStyle already has plus/minus drawable slots: the
   preferred candidate is triangle 13 (right collapsed, rotated down expanded),
   matching the current engine marks; conchars +/- are available alternatives. Replace ContentBrowser's direct
   per-widget Skin lookups with these explicit styles. Preserve the standalone
   viewer's current engine presentation; the client will use menu-sized styles.
   No theme registry, extra skin loader or fallback framework is required.
3. **A browser composition in the sample — deferred optional fixture view.**
   Live client/browser verification now covers the first integration; the compact
   ui_sample remains a widget gallery. A separate fixture view can still reuse the styled ContentBrowser with
   fixture VfsEntry values and placeholder images/callbacks. Include expanded and
   collapsed folders, tree selection, tile hover/selection, long names/paths,
   empty folders, failed thumbnails and a preview/Back transition. Verify split
   dragging, nested scroll focus, resize and theme switching in both themes.
   Use a compact browser sample/view so each sample view fits one virtual screen.
4. **Client integration — tree/grid/metadata complete.** Add the entry point and
   browse the active profile's resolved VFS snapshot. Show the winning source
   alongside logical path and size; VfsEntry already carries source/layer data.
   Handle close/Escape, input priority, pause policy, profile changes and style
   lifetime through existing host mechanisms. Start with tree/grid/metadata;
   adapting media preview ownership/toolbars and override inspection remain
   separate slices. Do not change UI theme merely by inspecting other content.

Themed split handles, dropdown triangles, broad atlas cleanup, optional widgets and exact pixel/wrap
matching are not gates. Broader console/HUD specimens are useful inventory work
but are not prerequisites for this browser shell. Native viewer verification
remains deferred as in its existing plan.


### Follow-up scope: split handle deferred, tree marks

A custom themed draggable handle is explicitly deferred. The first separator
slice covers static horizontal/vertical decoration only. The existing SplitPane
can retain its current engine-style handle; no new conchars grip artwork or
changed dragging behavior is a prerequisite for the browser.

The conchars sheet already contains a right-facing triangle at zero-based glyph
13 (first row, column 14). Preferred tree presentation is right for collapsed and
rotated down for expanded, resembling the engine's existing tree marks. libGDX
TreeStyle provides plus/minus drawable slots for these states; no widget subclass
is needed. The normal font's + and - glyphs (43/45) remain simple alternatives.
This tree work is independent of the deferred dropdown indicator, whose widget
has no dedicated icon slot. Confirm appearance in the sample as tree coverage
is added; this is a working visual choice rather than a new asset requirement.


### Static separator slice

**Status: implemented.** MenuWidgetStyles now exposes horizontal and vertical
separator Drawables. Game styling assembles conchars 29/30/31 as a horizontal
nine-patch: 8-unit fixed end caps, stretchable middle, 24-unit minimum length and
8-unit cell thickness. A clockwise Drawable wrapper swaps dimensions and draws
inside the supplied vertical bounds, placing the start cap at the top. It borrows
the same texture and does not alter batch transforms or allocate another image.

Engine styling uses the existing `line-h` primitive tinted gray through the Skin's
`separatorHorizontal` role. Its menu copy is 1 unit thick with minimum length 24;
the vertical variant reuses the same rotation wrapper. Its flat-line appearance
is an initial engine styling choice, not an attempt to copy Quake's border art.
The sample centres these drawables in their cells using Image scaling: one
horizontal specimen demonstrates stretching and one vertical specimen shows
orientation. Fixed end caps at different lengths remain covered by the GL check. No new UI widget, input behavior, texture asset or dependency is
introduced. The themed drag handle and dropdown indicator remain deferred;
right/down triangle tree styling remains the next browser coverage slice.


Verification: client/fullgame compilation and the four focused style/viewport
tests passed. The existing OpenGL atlas test now uses synthetic red/green/blue
separator cells to check fixed eight-unit caps, stretched middle, clockwise
start/end orientation and drawing bounds. The runtime sample probe checks
interaction/state retention, theme/fallback switching, minimum-height fit and
4K scaling; engine/game sample heights are 437/387 within 608 available at
`cl_ui_size 640`. Both automatic captures were visually checked.

Actual implementation scope: 48 added production lines (including the Skin
resource), 40 added lines extending the existing tests/fixtures, and documentation;
no moved code, new textures, dependency or separate widget class. This is within
the approximate 50–80 production-line estimate; the rotation helper is local to
the existing style builder.


### Resolved tree styling and sample

**Status: implemented.** MenuWidgetStyles now exposes a TreeStyle. Game marks
use native conchars glyph 13 for collapsed and its clockwise rotation for
expanded. Selection and hover use solid square glyph 11 (green/gray tints) so
row feedback is visible independently of the dark panel-centre texture. Leaves
have no expand mark, following standard libGDX Tree behavior.

The raw engine TreeStyle is unchanged. A Skin `menu` TreeStyle uses the existing
engine right/down shapes with readable text-colour tints and a subdued hover
fill. Copies use 24-unit source slots, becoming 8-unit menu slots; the existing
16px textures remain unchanged. Node labels use the selected theme's LabelStyle.

The sample includes a compact folder tree (now beside the scrolling panel,
separated by a vertical divider). It has
single required selection, expanded and collapsed branches, leaves, a selected
path readout and a thick outer scroll frame. Clicking a mark toggles its branch;
clicking its label selects the folder. Expanded paths, selected path and scroll
position survive engine/game switching. Tree wheel focus stays independent of
the other scrolling specimen. The existing ContentBrowser is not changed yet.

Verification: focused style/viewport tests and client/fullgame compilation pass.
The existing GL atlas/style test checks 8-unit marks, native glyph-13 UV mapping,
selection/hover availability and unchanged raw engine mark size. A real-content
pointer probe in both starting themes checks expand/collapse clicks, leaf/parent
selection, hover, wheel focus/scroll, retained expansion/selection/scroll across
theme changes, close and 4K pointer coordinates. The sample needs 517 units in
engine mode and 462 in game mode, within 608 available at `cl_ui_size 640`.
Automatic captures and interaction captures were visually inspected.

Actual scope: 99 added / 5 removed production lines (94 net), mostly sample
fixture/state handling, plus 13 lines extending existing tests/fixtures and
documentation. This is approximately the 100–150 added-line estimate. No new
texture, loader, dependency, Tree subclass or browser integration is introduced.
The sample uses the small standard Tree.Node subclass required by its generic
node/label types. Themed drag handles and dropdown indicators remain deferred.


### Shared game selection background

Game text inputs, selectable lists, dropdown rows and tree nodes now share the
same green selection Drawable: conchars solid square 11 tinted with the existing
menu selection colour. Text input selection no longer inherits the engine's
magenta fill, and list/dropdown selection no longer uses the dark panel-centre
cell. The engine palette and game caret resource are unchanged. The style-level
GL check asserts the shared selection identity across all four usages.


### Game scrollbar thumb from separator artwork

Game vertical scrollbar thumbs now share the same rotated 29/30/31 Drawable as
the vertical separator. Native dimensions are 8 units wide and at least 24 long,
with fixed start/end caps and a stretchable middle. The separate thin-panel
track backdrop is removed; the thumb sits directly on the pane background.
This applies through the shared ScrollPane
styles to scrolling specimens, trees, menus and scrollable dropdown popups.
Engine scrollbar styling is unchanged; themed SplitPane handles remain deferred.

Verification: focused style tests and both client compilations pass. The GL style
check asserts shared separator/thumb identity for standard and dropdown scroll
styles. A real-content pointer probe drags the thumb to both ends and exercises
wheel scrolling; tree state/hover/scroll/4K and dropdown selection probes pass.

Follow-up: the vertical track drawable is explicitly null in both ordinary and
dropdown scroll styles. ScrollPane retains its scrollbar hit area and native
thumb drag behavior without drawing a thin-panel backdrop.


### Separators as layout boundaries

The sample now uses its single horizontal separator above the bottom group,
then lays out tree → vertical separator → panel/scrolling side by side. List
and dropdown occupy two columns above the slider/progress rows. The separate
separator specimen area and label are removed, giving both dividers a purpose
and reducing unused vertical space. Both scrolling panes keep independent wheel
focus and their existing content/state.

Verification: client build and both automatic captures pass. The tree interaction
probe retains expansion/selection/scroll state across themes and passes at 4K;
scrollbar top/bottom dragging and wheel scrolling also pass after relocation.
Current sample heights are 482 engine / 417 game within 608 available at
`cl_ui_size 640` (35/45 units shorter than the previous arrangement).


### Browser style adaptation

ContentBrowser now accepts explicit borrowed ContentBrowserStyles. The native
Skin constructor remains available to the standalone viewer and preserves its
font, panels, tree, splitter and tile selection. The client adapter uses resolved
menu labels, buttons, tree marks and selection, a thick outer container and
unframed inner scroll panes. Tiles share the themed tree selection fill; the
existing engine SplitPane handle remains an explicit fallback in both themes.
No new atlas resources or resource ownership are introduced.

Thumbnail loading is optional: without a loader, tiles show their file extension
and do not attempt media loading or display a misleading load failure. Existing
viewer thumbnail and preview callbacks retain their behavior.

Validation: client/viewer compilation, folder hierarchy and fallback-style tests
pass. The existing GL style test also checks native viewer resource identity and
resolved client style/selection/scrollbar wiring for both themes. Client hosting
is the next slice.


### Client Content Browser host

Main menu → Content browser opens a VFS snapshot from the active profile. The
shared browser shows folders/files, logical path, byte size and the winning
source layer plus package/directory name. It lists profile content only: bundled
engine assets and remote download fallbacks are excluded. The VFS may expose a
package itself as a loose file in addition to its winning contents. Listing does
not extract package entries or load media. Reopening starts at the root with a
new snapshot of the current mounted VFS index; it does not rescan the filesystem.

ContentBrowserStage shares the menu UiViewport and cl_ui_size convention. It uses
the resolved theme throughout except the deferred engine splitter/horizontal
scrollbar fallbacks. The browser has one thick outer frame, themed tile selection
and unframed inner scroll panes. Without a media loader, image tiles show the
file extension. Empty/missing profiles and listing failures have a message.
Back/Escape return to the main menu. Existing console input priority and menu
pause policy apply. Profile/style changes rebuild the stage and replace the old
snapshot; stages borrow skin/style resources and own no media textures.

Verification: 45 focused tests pass (directory hierarchy, resolver snapshots and
override winners, menu navigation/sounds, style fallback, viewport), and client,
fullgame and viewer compile. The first slice's existing GL check validates both
style adapters. Temporary desktop probes verify real profile content, metadata
selection, independent wheel focus, grid scrolling, splitter drag, Back/Escape,
root reset on reopening, profile/style replacement, missing profile, pause and
4K pointer input. Both theme captures and a 4K capture were inspected. A separate
standalone probe loads a real PAK thumbnail, opens a full image preview and
returns to its grid while retaining the raw 24-unit engine font. Probe settings
use an isolated temporary home with cfg_autosave disabled.

The client host adds about 110 production lines, including the stage, navigation,
VFS snapshot access and source metadata. No loader, atlas, dependency, custom
input framework or persistent capture harness was added. Embedded thumbnails,
media preview toolbars and override inspection remain later slices. The optional
fixture browser view was deferred in favor of verifying the actual host.


### Client splitter uses themed separators

The client browser now assigns separatorVertical directly to SplitPaneStyle.handle
in both themes: conchars 29–31 for game, gray line for engine. SplitPane provides
the existing hit detection, cursor-coordinate handling and drag behavior. The
separator's minimum width defines the handle width (8 game / 1 engine logical
units). No extra grip, resource or interaction code is needed. This supersedes
the earlier decision to retain the engine splitter fallback. The standalone
viewer adapter continues to use its native engine SplitPaneStyle.

Verification: the existing GL style check asserts shared separator/handle
identity for both themes; all three hosts compile. The client desktop probe
checks splitter dragging in both themes alongside scrolling, metadata selection,
profile replacement and 4K input.


### Client image thumbnails

Visible PCX/WAL/TGA tiles in the client now load image thumbnails through the
existing ContentBrowser callbacks and 4 ms per-frame loading budget. A single
synchronous decode may exceed that budget. This uses existing full-size texture
loaders with aspect-fit display, matching the viewer; it adds no background jobs,
thumbnail resizing pipeline or persistent cache. Folder and unsupported-file
placeholders retain their existing presentation, and failed images display
Unavailable without interrupting browsing.

ImageThumbnails extracts the viewer's existing texture/failure cache into a small
shared owner. Both hosts create their own textures by invoking the existing
PCX/WAL loaders directly (TGA uses Texture), avoiding shared AssetManager references
and failed manager tasks. Client image resolution uses resolveContent, excluding
remote downloads and bundled missing-image fallbacks. WAL keeps the existing
q2palette.bin palette. Metadata listing does not extract packages; loading an
image materializes its entry through the existing VFS adapter.

The client stage owns thumbnails while browser actors borrow them. Scrolling
retains the current folder's cache. Folder navigation, Back/Escape, profile
replacement, server-data context changes and stage disposal clear it. Actors
are detached before stage cleanup, and profile changes clear thumbnails before
reconfiguring the resolver. Reopening starts a new folder lifetime. Console or
UI-sample overlays preserve the underlying browser. Shared game textures and
style assets retain their existing ownership. Full image/media previews remain
a separate slice.

Validation: focused resolver/navigation/hierarchy/preview ownership tests and all
three host builds pass. The existing GL test now checks separate thumbnail/shared
texture ownership, cache reuse, disposal, reloading and malformed-image failure
isolation. A temporary desktop probe checks real PCX/WAL files, a generated TGA,
a corrupt PCX placeholder, progressive visible loading, caching during scrolling,
folder/Back/profile/disposal cleanup, theme replacement, resize and 4K. Both-theme
and WAL captures were inspected. The standalone archive thumbnail → full image
preview → Back probe also passes after extraction. Temporary probes use isolated
settings with cfg_autosave disabled.

Two commits separate shared ownership extraction from client lifecycle wiring.
Production scope is about 80 added lines and 35 removed, within the 60–90 added
line estimate. No new loader, asset or preview toolbar is introduced.


### Client full-image previews

Double-clicking a PCX/WAL/TGA tile opens a full-image preview with dimensions and
a themed pan/zoom hint. Shared ImagePreviewPane (moved from viewer to ui) supplies
aspect-preserving fit, pointer-anchored wheel zoom, left-drag pan and clipping.
Small images enlarge to fit. Relative zoom/pan survive resize; reopening starts
fitted. The standalone image and sprite callers use the same moved pane.

The client preview borrows the existing full-size folder-cache texture through
ImageThumbnails.load; it adds no asset-manager reference, duplicate texture or
new resource lifetime. Back to folder retains the grid, its scroll position and
folder cache. Escape closes a preview first; a subsequent Escape leaves the
browser through the existing menu intent. The top Back button still leaves the
browser directly. Corrupt images show a dismissible inline error. Folder/profile
changes and shutdown retain the preceding slice's cleanup policy.

Validation: client/fullgame/viewer compile and focused menu/preview ownership
checks pass. A temporary desktop probe verifies real PCX/WAL and generated TGA
previews in both themes, aspect fit, shared texture identity, pointer anchoring,
pan, nonzero grid-scroll restoration by Back/Escape, reopening reset, folder and
profile replacement during an open preview, corrupt-image recovery and 4K
resize/input. Both-theme image captures were inspected. The standalone PAK
thumbnail/full-image/Back probe also passes after the shared-pane move.
Probes use isolated temporary settings with cfg_autosave disabled.

The change is split into the shared-pane move and client wiring commits. Added
production scope is about 35 lines, within the 35–55 line estimate; no new loader,
cache, artwork or graphics harness was needed. Audio, sprite and model/map
previews remain later slices.


### Escape follows the browser hierarchy

Both client and standalone viewer now call ContentBrowser.navigateBack. It closes
an open full preview first, otherwise selects the current folder's parent through
the existing tree-selection path. It returns false at the root so the client can
post its normal main-menu intent and the standalone viewer can exit. Escape in
the standalone empty/loose-resource view still exits directly. Explicit top Back
and Back to folder buttons retain their existing meanings. Folder-up navigation
clears child thumbnails through the existing folder lifecycle; returning from a
preview retains its folder cache and grid scroll position. No navigation stack
or separate shortcut handling was introduced.

Validation: all three hosts compile; menu/folder tests and existing GL checks
pass. Temporary desktop probes check preview → same folder → immediate parent
→ root → host exit, nested folders in both client themes, thumbnail cleanup,
standalone render-time Escape polling and loose-file exit.

The standalone probe also caught a thumbnail-extraction regression: using plain
AssetManager.get for the WAL palette assumed it had already been loaded. The
shared owner now restores the viewer's getLoaded behavior. A separate atomic
fix and GL regression fixture verify that a WAL thumbnail lazily acquires its
palette and releases its own texture on folder cleanup. The standalone folder
navigation probe passes without missing-palette errors.


### Client WAV audition and controls

The client now matches standalone WAV interaction: one click auditions a sound
while retaining the folder grid; double-click opens duration and Play/Pause/Loop/
Stop controls without restarting it. Play selects/resumes single playback, Pause
retains position, Loop selects/resumes repeated playback and Stop resets. Space
toggles pause/resume while retaining loop mode; S stops. Clicking a WAV again
restarts its audition. Known durations stop the playback state on completion.

SoundPlayback moved from viewer to audio, with its existing timing tests retained.
SoundPreviewControls moved to ui and accepts explicit label/button styles; its
Skin constructor preserves the standalone viewer's native font/artwork. Client
controls use the active resolved menu styles. The four action buttons no longer
retain a checked highlight after clicking, in either host.

ContentBrowserStage owns at most one preview sound and its playback instance.
Cake supplies a direct ConvertingSoundLoader.loadSync callback restricted to
resolveContent, reusing 8-bit PCM conversion and duration registration. Sounds
are independent of shared game AssetManager references; failed loads do not
create manager tasks or require global cache recovery. Selecting another file,
Back to folder, changing/up folder, leaving the browser, profile/style replacement,
server-data context changes and disposal stop and release the sound. A failed
path is retained for the current selection to avoid repeating a failed load on
the second click, and opening controls shows a dismissible themed error. No
new audio loader, preview framework, cache or persistent test harness was added.

Validation: focused SoundPlayback, WAV conversion, menu and preview ownership
tests pass; client/fullgame/viewer compile. A temporary hidden-window probe uses
real WAV content and a recording audio backend to verify audition, no restart
on double-click, Play/Pause/Loop/Stop, momentary buttons, keyboard handling,
known duration, themed fonts, 4K and cleanup on selection/folder/Back/profile/
shutdown. A corrupt WAV is recoverable, and another file can play afterward.
A standalone PAK probe checks native-font controls, the same playback semantics
and Back cleanup after extraction. A separate native OpenAL check uses its null
output device and zero volume to verify actual WAV decoding, conversion, play/
pause/resume/stop and malformed-input rejection without audible output.
Both-theme control captures were inspected. Probes use isolated temporary
settings with cfg_autosave disabled.

Two commits separate shared playback/style extraction from client ownership and
input wiring. Production scope is about 100 added lines, within the 90–130 line
estimate. Sprite, MD2 and BSP previews remain later slices.


### Client SP2 sprite previews

Client SP2 tiles now match the viewer: single-click starts one playback in the
tile, while double-click opens a larger preview without resetting the animation.
The shared SpriteFrameImage keeps a fixed maximum-frame-size canvas and centres
each frame at its declared dimensions. ImagePreviewPane supplies fit, zoom/pan
and clipping. Themed frame metadata and Previous/Play/Pause/Loop/Stop/Next
controls reuse FramePlaybackControls. Space toggles play/pause, Left/Right step
and pause, and Stop resets to the first frame. One-shot playback holds its final
frame; Loop repeats. Frame action buttons remain momentary in both hosts.

SpriteFrameImage/SpritePreview, FramePlayback and FramePlaybackControls moved
from viewer to ui. Native Skin constructors preserve standalone appearance;
client constructors take resolved label/button styles. The existing frame
playback tests follow the shared implementation. MD2's standalone transport
also continues to use the same shared controls.

The client stage owns a private sprite AssetManager, configured with existing
Sp2Loader, PCX/WAL and default image loaders. Sprite/frame paths resolve only
through the active profile; q2palette.bin retains normal engine resolution.
This isolates preview textures and failed dependency tasks from game assets.
Selecting another file, Back to folder, folder/up navigation, browser exit,
profile/style/server context changes and disposal clear the sprite manager and
stop playback; disposal also closes the private manager. WAV and sprite audition
are mutually exclusive. Missing, malformed and empty sprites show a recoverable
error; the private manager is cleared and another file can open. No new loader
or general preview framework was added.

Validation: animation, sound, menu and preview ownership tests pass; all three
hosts compile. Temporary desktop probes check a real game SP2 plus varying-size
TGA-frame fixtures, single-click tile animation, no double-click reset, transport
buttons and keyboard, one-shot completion, stable fit, zoom/pan, resize and 4K
in both themes. They check independent preview/game texture identity, game
reference survival, frame disposal on selection/folder/Back/profile/shutdown,
and recovery from missing/partial dependencies, malformed headers and zero
frames. Shared game UI textures remain alive after failures. A real standalone
PAK probe checks tile/full previews, native control fonts and frame cleanup; the
client WAV regression probe also passes. Both-theme sprite captures were
inspected. Probe settings use a temporary home with cfg_autosave disabled.

Two commits separate shared frame/style extraction from client resource/input
wiring. Production scope is about 115 added lines, within the 100–150 estimate.
MD2 and BSP previews remain later slices.

### Client MD2 previews

MD2 double-click opens orbit/zoom, animation transport, frame names and a themed
skin selector. Native viewer adapters remain available. Shared 3D panes now size
render targets from actual screen pixels, including the client virtual viewport.
The browser's private preview manager loads model/skin dependencies; renderers,
panes and model references close before that manager clears. No-skin models use
the existing labelled generated default skin; malformed models show an inline
error. Real-model desktop probes pass in both themes and at 4K, including skin
switching, animation, orbit/zoom, target sizing and Back/profile/disposal cleanup.
Focused animation/ownership tests and all host compilations pass. BSP integration
and the requested cleanup pass follow in separate commits.
