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

The accepted engine asset reference is **3×**: Quake's 8-pixel font line height
corresponds to engine line height 24. `BitmapFontGeneratorTool` generates this at
size 21 from Share Tech Mono; engine glyphs retain their natural widths.
Square glyphs, identical wrapping and identical widget footprints are not goals.
The native resource ratio is separate from display scaling. Menu normalization
now applies a fixed 3× scale to Quake menu metrics/drawables, so both themes use
the engine's existing UI units and the same vertical `cl_ui_size`. HUD metrics
and the original texture remain unchanged. Slider and other inherited engine
artwork still have visual differences; captures guide further direct choices.

## Sample coverage follows the target hierarchy

Use **elements → styles → widgets → screens** throughout the sample. The
[target widget list](../assets/ui/INVENTORY.md#target-widgets-for-cake-screens)
defines its core scope: label, action button, checkbox, text input, selectable
list, dropdown, scroll pane, tree, split pane, panel, content tile, image view,
horizontal slider and horizontal progress indicator.
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
identifies whole-theme engine fallback and its inherited engine caret/selection.
The outer panel uses the container role; the list and inner scrolling content use
the control role. Slider and progress share a track and knob, with a separate
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
Game caret and text selection still inherit engine resources, as before.

Verification: four focused style/viewport tests passed. The graphics probe
confirmed thick action-button and thin checkbox insets in both themes, preserved
interaction/state behavior, theme switching and fallback, and page fit at
reference heights 640/720 and 4K. Current preferred height is 599 for engine and
316 for game, within the 608-unit content area at `cl_ui_size 640`.


### Menu display normalization

**Status: implemented.** The Quake menu font has its own metrics/cache over the
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
and scroll surfaces still differ in nesting. These remain targeted visual
choices for subsequent review, rather than a requirement for a geometry system.
