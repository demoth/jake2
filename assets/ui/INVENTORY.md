# Cake skin coverage inventory

This inventory identifies Cake's skin resources, supported widget styles and
screen usages so their styling can be made consistent and visual choices can be
made deliberately. It covers both engine and Quake II styling. The sample screen
is specified in [UI skin sample](../../info/ui-skin-sample.md).

The baseline is the current engine skin and the current `GameUiStyle` factory.
Resource availability, widget support, actual usage and visual consistency are
separate concerns. A definition in the skin does not establish that all its
states work or that screens use it consistently.

## Accepted styling direction

Use the fixed Quake II conchars artwork as the reference for UI proportions.
Engine fonts and drawables can change to approach those proportions while
retaining higher source resolution and additional detail. Source texture size
does not dictate logical widget size. Compare both themes with the same layout
rules and `cl_ui_size`; approximate consistency is sufficient for this engine.

Keep this simple: reuse standard libGDX widgets, the existing style builders and
a few direct sizing choices. Adjust engine font/frame resources first, then
widget padding or construction where visible differences remain. A general
geometry system or exact matching of every pixel and text wrap is unnecessary.

The engine resource reference is **3× Quake's native vertical metrics**. This
is an asset authoring reference, separate from viewport/display scaling. Preserve
natural engine glyph widths: Quake's square character cells do not impose square
engine glyphs, identical text wrapping or identical widget widths. Regenerate the
bitmap font with `BitmapFontGeneratorTool`; do not stretch its baked glyphs or
rewrite its metrics to simulate a different font size.

| Reference | Quake | Engine | Status |
| --- | --- | --- | --- |
| Font line height | 8 | 24 (generator size 21) | Implemented; natural engine glyph advances retained |
| Panel corner / source cell | 8 | 24 | Implemented; separate thick/thin engine artwork |
| Thick frame content inset | 8 | 24 | Implemented |
| Thin frame content inset | 4 | 12 | Implemented |
| Thin frame minimum extent | 16 | 48 | Implemented |
| Slider track / knob and other inherited artwork | Conchars menu drawables at 3× | Existing engine resources | Logical knob height 24 in both; width/art treatment still differs |

Menu normalization is implemented with a fixed 3× scale on Quake menu font
metrics and conchars-derived drawables. Both themes therefore use a 24-unit font
line height, 24-unit thick insets and 12-unit thin insets in the same layout.
Native Quake textures, HUD metrics and vertical `cl_ui_size` semantics stay
unchanged; no extra scale setting or theme-specific viewport is introduced.
Menus own a separate font instance while sharing the HUD's atlas texture.

The engine font keeps narrower natural glyph advances and a different visible
cap height. Text width/wrapping and subtle row-height differences are intentional
comparison points, not reasons to force square glyphs. Slider knob width and
scroll surface nesting remain available for later visual refinement.

The sample now exposes two panel roles and horizontal slider/progress support
from the existing Quake sheet. Thick frames contain groups of widgets and draw action buttons; thin frames
define checkbox toggles, fields and dropdowns. User testing selected thick frames for action buttons and thin frames for the
other controls; both themes follow this choice.

## Target hierarchy

**Elements → styles → widgets → screens** is the organizing model:

| Layer | Responsibility | Example |
| --- | --- | --- |
| Elements | Reusable colours, fonts and drawables | Text colour, UI font, panel frame, selection fill |
| Styles | Assign elements to visual roles and interaction states | Action-button font, frame, normal/hover/pressed/disabled treatment |
| Widgets | Display content and provide behaviour using those styles | Button, checkbox, dropdown, content tile |
| Screens | Compose widgets into a task and choose the host theme | Profile editor, options, viewer, console |

Styles can reuse the same elements without looking identical. A widget can
contain other widgets: a dropdown includes a list and scrolling; a content tile
contains text and an image. These relationships do not introduce another styling
mechanism. Engine and Quake themes should cover the same target roles, with
explicit fallback where needed.

## Target widgets for Cake screens

This is the proposed working set for the existing engine, client/fullgame and
viewer screens. It defines what the sample must expose. The inventories later
in this document describe the current implementation and its gaps against this
target. Style roles below are descriptive names, not new registered Skin keys.

**Sample status:** `ui_sample` now opens the basic internal gallery in
client/fullgame. Engine/Game switching covers label, action button, checkbox,
text input, selectable list, dropdown, panel and scroll pane specimens. The
captions and mode controls use the selected style. Game mode identifies engine
fallback; its panel uses the resolved scroll/input surface. The gallery fits one
virtual screen and follows the vertical `cl_ui_size` setting live. Tree, split
pane, content tile, image view and screen
compositions remain planned. Availability does not establish visual consistency.
Two panel variants, a horizontal slider, linked read-only progress and a disabled
slider are available in both modes. Sound options now use the shared slider for `s_volume` in 0.05 increments;
other numeric editors are unchanged.

| Target widget | Style roles | Implementation basis | Screen usages |
| --- | --- | --- | --- |
| Label | UI text; supporting text; info/warning/error feedback | LabelStyle; several roles can share one style | All screens, field descriptions, metadata, feedback |
| Action button | Action; normal/hover/pressed/disabled | TextButtonStyle | Navigation, Apply/Save/Back, playback actions |
| Checkbox | Boolean option; checked/unchecked and interaction states | Current `x` TextButton toggle; native CheckBox is an implementation choice to evaluate | Options |
| Text input | Editable text; hint, caret, selection, focus and disabled treatment | TextFieldStyle; password mode uses the same style | Profile paths/names, server address, player fields, console command |
| Selectable list | Rows, selection and text | ListStyle | Profile selection; dropdown content |
| Dropdown | Closed control, open list and disabled treatment | SelectBoxStyle with ListStyle and ScrollPaneStyle | Hosting/player/options choices, MD2 skin choice |
| Scroll pane | Horizontal/vertical tracks and thumbs; optional surface | ScrollPaneStyle | Long forms, lists, browser and previews |
| Tree | Expand/collapse controls, selection and row text | TreeStyle plus LabelStyle | Content Browser folders |
| Split pane | Divider and handle | SplitPaneStyle; horizontal split initially | Content Browser tree/content division |
| Panel | Thick container frame and thin control frame, with content insets | Table/Container and Drawables; two Drawable roles, no custom Panel class required | Containers for groups; button/checkbox/field/dropdown chrome |
| Horizontal slider | Start/middle/end track, knob and disabled treatment | SliderStyle; conchars mapping available | Sample and Sound options (`s_volume`, step 0.05) |
| Horizontal progress indicator | Same track and position indicator, read-only | ProgressBarStyle; shares slider artwork | Sample experiment; production use to be chosen when needed |
| Content tile | Transparent idle surface; hover/selected treatment | Named ButtonStyle role plus label and image/placeholder | Content Browser files and folders |
| Image view | Image content inside an optional styled preview surface | Image or existing image preview widget; content texture is separate from skin artwork | Thumbnails, image and sprite previews |

For each widget, inspect applicable states together. Not every widget needs every
state: a panel has no checked state, while an input needs a caret and selection.
Wrapping, ellipsis, sizing and scrolling behaviour belong to widget/layout
coverage even when they do not require a distinct style.

The target includes **checkbox behaviour**. The existing `x` toggle and libGDX
CheckBox are two implementation options for that widget, not two separate product
requirements. Keep both visible in the baseline audit until the implementation
choice is made. The sample must represent the current toggle before comparing
alternatives.

### Composed and specialized widgets

These are also part of screen coverage. They reuse the target styles above;
their existence does not require an independent skin style for each composition.

| Widget | Building blocks or specialized rendering | Screen usages |
| --- | --- | --- |
| Content Browser | Tree, split pane, scroll panes, tiles, labels, Back button and preview host | Standalone viewer; planned client/fullgame browser |
| Resource information panel | Panel and labels | Viewer filename, metadata, controls hints and feedback |
| Playback controls | Buttons and labels; MD2 adds a dropdown | Sound, sprite and model previews |
| Console output | Custom text output with UI font and severity colours | Engine console |
| Scene preview | Existing camera/rendering widget inside a preview area | MD2 and BSP viewer |
| Diagnostic graph | Custom graph rendering and labels | Engine diagnostics |
| HUD overlay | Content text, numeric glyphs and game images | Game screen |

### Widgets outside the initial target

Radio groups, separate icon-button widgets, vertical sliders/progress bars, tooltips,
floating windows/dialogs, touchpads, multiline editors and spinners are not
required by the current screen set. Existing skin definitions remain in the
baseline inventory; their presence does not make them target widgets. Add one to
the target when a screen needs its behaviour, then specify its styles and sample
cases. Sound volume uses a horizontal slider; other numeric fields use text input.
Playback uses text buttons. Horizontal slider/progress support is also included
in the conchars sample experiment.

## Target screen composition

| Screen family | Target widgets and compositions |
| --- | --- |
| Main, multiplayer and options navigation | Labels and action buttons |
| Profile editor | Selectable list, text inputs, labels, buttons and scrolling |
| Join, hosting and Player Setup | Text inputs, dropdowns, labels, buttons and scrolling |
| Options section | Checkboxes, text inputs, dropdowns, volume slider, descriptions, buttons and scrolling |
| Console | Panel, console output and command input |
| Diagnostics | Diagnostic graphs and labels |
| Standalone viewer | Resource information panel, preview, playback controls; Content Browser for archives |
| Embedded Content Browser | Same browser composition with active-profile styles and explicit fallback; host integration is planned |
| Game | HUD overlay with game rendering |

Example dependency: font + text colour + frame drawable → action-button style
→ Apply button → Profile editor. A different screen's Back button uses the same
action style unless there is a documented reason for a separate role.

## Tracking coverage

| Status | Meaning |
| --- | --- |
| Defined | Resource or style exists in the engine skin |
| Derived | Current Quake style builds it from game content |
| Mixed | Quake style replaces some fields and retains engine resources |
| Engine only | No corresponding coverage in the current Quake style bundle |
| Local | A component builds its style outside the skin |
| Planned | Proposed coverage or sample; implementation has not started |

In the baseline tables, **Required** means used by an existing engine, menu or viewer UI. **Extension**
means an existing skin capability without a current Cake widget consumer found
in this review. Keep extensions visible without making them prerequisites for
reviewing current usages. These terms describe scope, not visual acceptance.

Use the stable IDs below when recording sample coverage and style decisions.
Review a family's relevant states and variants together; its default appearance
alone does not establish support for the other states.

## Colour resources

All 30 names below are defined in [uiskin.json](uiskin.json). Several are aliases,
and names such as `white` and `black` currently resolve to tinted palette colours.
Their values describe the current implementation. Keep or change them through
explicit decisions about their roles and usages.

| ID | Role | Current names and values | Quake coverage |
| --- | --- | --- | --- |
| C01 | Base palette | `lavender_veil` #E3D3E4; `midnight_violet` #231123; `canary_yellow` #FFFD77; `watermelon` #E83151; `raspberry_plum` #CE27A4; `malachite` #2EDC76; `strong_cyan` #50D8D7 | Engine palette retained; conchars supplies its own pixel colours |
| C02 | Light and dark roles | `white` → lavender; `black` → midnight; `background` → black | Engine roles; HUD text draws with a white multiplier |
| C03 | Feedback | `warning` → canary; `error` → watermelon; `message` → canary | Console uses engine colours; these are not Quake-derived |
| C04 | Selection and hover | `selection`, `highlight` → raspberry; `over`, `checkedOver` → selection | Menu hover text is hardcoded RGBA (0.35, 0.95, 0.35, 1); list selection fill is (0.22, 0.35, 0.22, 0.95) |
| C05 | Pressed and checked | `down`, `checked` → strong cyan | Quake button press uses a darkened panel; toggle uses font visibility |
| C06 | Neutral and disabled | `gray` = (0.5, 0.5, 0.5, 1); `up` = (0.1, 0.1, 0.1, 1); `disabled` = (0.4, 0.4, 0.4, 1) | Mixed with hardcoded panel tints |
| C07 | Split and translucent surfaces | `split` = (0.4431, 0.4784, 0.5373, 1); `semiTransparent` = (0.2, 0.2, 0.2, 0.6) | Engine roles retained where used |
| C08 | Text states | `font`, `fontOver`, `fontChecked`, `fontCheckedOver` → white; `fontDown`, `fontDisabled` → gray | Mixed engine text colours and Quake-specific hover colour |

Inspect normal, hover, pressed, selected, disabled and keyboard-focus treatments
where the widget supports them. Record missing feedback and unintended differences
between usages. Focus and invalid-input feedback do not currently have dedicated
colour roles. Add roles only with a concrete style consumer.
Debug metric colours and the 3D preview's black clear colour are currently outside
the skin; keep those visible as separate screen concerns.

## Fonts and glyphs

| ID | Role | Engine baseline | Quake baseline | Scope |
| --- | --- | --- | --- | --- |
| F01 | Main UI text | `default`: Share Tech Mono, generator size 21, line height 24, separate 512×512 `share-tech-mono.png` | Separate conchars menu font at 3× (24-unit line height); HUD remains native | Required |
| F02 | Font roles | `font`, `list`, `subtitle`, `window` all alias `default` | No separate role fonts | Defined; separate typography is optional |
| F03 | Quake normal and alternate text | Engine HUD fallback uses `default` | 16×16 conchars grid; high-bit alternate bank preserves game glyph colours | Required for HUD compatibility |
| F04 | HUD number fields | `EngineHudNumberFont` uses the default font | 22 separate `num_*` / `anum_*` PCX images, including minus | Required for HUD; separate from UI atlas |
| F05 | Unsupported text | Coverage depends on the bundled font's glyphs | Conchars has game glyph IDs 0–255, not general Unicode | Planned explicit replacement or font fallback policy |

The current conchars loader takes logical advance and line height from source
cell dimensions. A 256×256 atlas therefore produces larger text than a 128×128
atlas. Decide logical sizing separately from artwork resolution before comparing
themes. Test ASCII, punctuation, long paths, the viewer's `…` placeholder, and
non-ASCII filenames. Do not treat Quake's alternate glyph bank as Latin-1 text.

The current factory loads conchars and the numeric pictures in one attempt:
a missing digit can make menus fall back to the engine theme too. The sample
adapter should expose this coupling and any deliberately independent fallback.

## Drawable resources

The following groups list all 62 `TintedDrawable` names and the one `TiledDrawable`
in the engine JSON. Atlas regions are listed separately because a drawable is a
named, possibly tinted or stretchable use of artwork.

| ID | Role | Engine drawable names | Current Quake coverage |
| --- | --- | --- | --- |
| D01 | Console panel | `console-panel` | Engine only; tinted `panel-thick` (24-pixel corners/insets) |
| D02 | Button chrome | `buttonUp`, `buttonOver`, `buttonDown`, `buttonDisabled`, `buttonChecked`, `buttonCheckedOver` | Thick panel from conchars cells 1–9; darkened pressed/disabled variants |
| D03 | Checkbox marks | `checkBoxOn`, `checkBoxOff`, `checkBoxOver`, `checkBoxOnDisabled`, `checkBoxOffDisabled` | Engine only; content menus instead use a bordered `x` text toggle |
| D04 | Generic icon | `icon` | Engine only; currently uses the check-on artwork |
| D05 | Flat fills and highlights | `split`, `black`, `highlight`, `selection`, `alpha` | Quake list selection uses tinted cell 5; browser still uses engine resources |
| D06 | List surface | `list` | Quake standalone list background is explicitly cleared |
| D07 | Horizontal progress | `progressHorizontal`, `progressHorizontalKnob`, `progressHorizontalDisabled`, `progressHorizontalKnobDisabled` | Derived conchars track/knob for position indicator |
| D08 | Vertical progress | `progressVertical`, `progressVerticalKnob`, `progressVerticalDisabled`, `progressVerticalKnobDisabled` | Engine only |
| D09 | Progress tile | `progress-bar-knob-t` (`TiledDrawable`) | Engine only |
| D10 | Scroll tracks and thumbs | `scrollHorizontal`, `scrollVertical`, `scrollKnob`, `scrollKnobVertical` | Mixed: vertical track/thumb use a half-size conchars panel; horizontal resources remain engine |
| D11 | Scroll surface | `scrollPane` | Quake default scroll style adds a conchars panel background; form hosts remove it |
| D12 | Dropdown surfaces | `selectBox`, `selectOver`, `selectDown`, `selectDisabled`, `selectList` | Mixed: conchars panel for control; dropdown list uses resolved list/scroll styles |
| D13 | Split handle | `splitPane` | Engine only |
| D14 | Horizontal slider | `sliderHorizontal`, `sliderKnob`, `sliderKnobOver`, `sliderKnobDown`, `sliderKnobDisabled` | Derived track 128–130 and knob 131; pressed/disabled tints |
| D15 | Vertical slider | `sliderVertical`, `sliderVerticalKnob`, `sliderVerticalKnobOver`, `sliderVerticalKnobDown`, `sliderVerticalKnobDisabled` | Engine only |
| D16 | Text input | `textField`, `textFieldDown`, `textFieldDisabled`, `textFieldCursor` | Mixed: conchars backgrounds; engine cursor and selection |
| D17 | Tooltip surface | `tooltip` | Engine only |
| D18 | Touchpad | `touchpad`, `touchpadKnob` | Engine only |
| D19 | Tree surface and hover | `tree`, `treeOver` | Engine only |
| D20 | Tree controls | `treeMinus`, `treePlus` | Engine only |
| D21 | Window and panel surfaces | `window`, `windowResize`, `windowBorder` | Engine only; browser and viewer use `window` as a panel drawable |

There is no named browser-tile style yet: `ContentBrowser` constructs a
`ButtonStyle` locally, with transparent idle appearance and the engine `selection`
drawable for hover, press and checked states. The sample should make this usage
visible alongside other buttons so shared and intentionally different treatments
can be identified. Keyboard focus currently has no distinct button drawable in
the default engine style.

### Conchars source primitives

Rows below are one-based; glyph indices are zero-based in the 16×16 sheet.
The source maps were checked against the original `pics/conchars.pcx`, the
current style builder and the legacy menu drawing code.

| Source | Glyph indices | Current coverage | Role |
| --- | --- | --- | --- |
| Panel 1, row 1 | Top: 1, 2, 3; middle: 4, 5, 6; bottom: 7, 8, 9 | Derived; container, scroll surface and action buttons | Thick frame for containers/groups and action buttons |
| Panel 2, row 2 | Top: 18, 19, 20; middle: 21, 22, 23; bottom: 24, 25, 26 | Derived; control backgrounds | Thin frame for checkbox toggles, fields and dropdowns |
| Slider, row 9 | Start: 128; middle: 129; end: 130; knob: 131 | Derived SliderStyle; also used by legacy Java menus | Horizontal slider track and knob |
| Progress indicator | Reuse 128–131 | Derived ProgressBarStyle | Read-only position indicator sharing the slider track |

Use the existing nine-patch approach for panel frames. Preserve fixed borders
and the slider's fixed end caps/knob while allowing its middle to span the track.
The sheet supplies a track and knob; a filled progress treatment would be a
separate construction choice. Start with the shared read-only indicator.

Engine styling exposes corresponding roles using separate `panel-thick` and
`panel-thin` artwork. Action buttons use the thick frame; toggles, text fields,
dropdowns and the control panel share the thin frame. Horizontal slider art is shared with progress.
The slider uses gray/cyan palette roles for
visibility. Engine artwork and logical proportions remain open for refinement;
font artwork stays separate from the UI atlas.

### Engine atlas source regions

[uiskin.atlas](uiskin.atlas) declares 57 regions across two pages (55 legacy
regions plus two new panel regions). This is the artwork inventory,
not a requirement to copy every region into a new atlas. Several are alternate
or inherited assets without a current screen consumer.

| Artwork family | Current region names |
| --- | --- |
| Current panels | `panel-thick`, `panel-thin` (72×72, splits 24); source `engine-panels.svg`, separate `engine-panels.png` page |
| Legacy panel | `chamfer-panel` (33×33, splits 16; retained pending atlas cleanup) |
| Primitive fills | `white`, `dot`, `rect`, `square`, `box-rounded`, `selection` |
| Button alternatives | `button-normal`, `button-normal-over`, `button-normal-pressed` |
| Checkbox artwork | `check`, `check-on`, `checkbox`, `checkbox-over`, `checkbox-selected`, `checkbox-selected-over` |
| Radio artwork | `radio`, `radio-over`, `radio-selected`, `radio-selected-over` |
| Old font regions | `font`, `font-list`, `font-subtitle`, `font-window` (the current font uses its separate PNG) |
| Scroll and line primitives | `knob-h`, `knob-v`, `line-h`, `line-v`, `scrollbar` |
| List | `list` |
| Progress alternatives | `progress-bar`, `progress-bar-knob`, `progress-bar-square`, `progress-bar-square-knob` |
| Dropdown alternatives | `select`, `select-box`, `select-box-open` |
| Slider alternatives | `slider`, `slider-knob`, `slider-knob-over` |
| Spinner arrows | `spinner-up`, `spinner-up-over`, `spinner-up-pressed`, `spinner-down`, `spinner-down-over`, `spinner-down-pressed` |
| Spinner field | `spinner-textfield`, `spinner-textfield-selected` |
| Text input alternatives | `textfield`, `textfield-selected` |
| Tree controls | `tree-minus`, `tree-plus` |
| Window alternatives | `window`, `window-border`, `window-resize` |

## Current widget and style coverage

This is the baseline audit, not the target widget list. These rows include every
standard style family declared in the engine JSON,
plus component-specific cases that otherwise disappear from a JSON-only audit.
Quake coverage describes `GameUiStyle.menuWidgets`; directly passing the engine
skin to a widget still selects engine styles.

| ID | Element and style | Engine names or implementation | Quake coverage | Scope and consumers |
| --- | --- | --- | --- | --- |
| W01 | Label / `LabelStyle` | `default`, `window`, `list`, `subtitle`, `white` | Mixed: default label copied with conchars font | Required; all areas |
| W02 | Action button / `TextButtonStyle` | `default`, `toggle`, `text`, `textToggle` | Mixed: resolved action style with conchars font and panel | Required; menus, browser navigation, playback |
| W03 | Boolean option | Local `TextButton("x", checkBoxStyle)`; no named engine style | Derived font-and-panel toggle | Required; options; keep separate from native CheckBox |
| W04 | Text input / `TextFieldStyle` | `default`, `spinner` | Mixed; font/backgrounds replaced, cursor/selection retained | Required; forms, masked Player Setup password, engine console |
| W05 | List / `ListStyle` | `default`, `background`, `list-nobg` | Mixed; conchars font/selection, engine text colour | Required; profiles and dropdown internals |
| W06 | Dropdown / `SelectBoxStyle` | `default` | Mixed; includes resolved nested list and scroll styles | Required; forms and MD2 skin selector |
| W07 | Scroll pane / `ScrollPaneStyle` | `default`, `clean`, `list`, `background` | Mixed; vertical chrome replaced, horizontal retained | Required; forms and browser |
| W08 | Tree / `TreeStyle` | `default`, `background` | Engine only; no bundle field | Required; browser; node text uses separate LabelStyle |
| W09 | Split pane / `SplitPaneStyle` | `default-horizontal`, `default-vertical` | Engine only; no bundle field | Required horizontal split in browser; vertical is an extension |
| W10 | Button / `ButtonStyle` | `default`, `toggle`, `spinner-up`, `spinner-down` | Engine only | Required style class for browser tiles; stock variants are extensions |
| W11 | Browser tile | Local ButtonStyle; no idle background | Engine only | Required; labels, thumbnail/placeholder, selection |
| W12 | Checkbox / `CheckBoxStyle` | `default`, `radio` | Engine only | Extension; optional future replacement for W03 |
| W13 | Icon button / `ImageButtonStyle` | `default` | Engine only | Extension; current playback buttons use text |
| W14 | Icon and text button / `ImageTextButtonStyle` | `default` | Engine only | Extension |
| W15 | Slider / `SliderStyle` | `default-horizontal`, `default-vertical`, `curved` | Derived conchars 128–131; engine defaults when content is unavailable | Horizontal target for sample experiment; vertical/curved remain extensions; volume uses a 0.05 slider; other numeric options use TextField |
| W16 | Progress / `ProgressBarStyle` | `default-horizontal`, `default-vertical`, `curved` | Derived from resolved slider track/knob in both themes | Horizontal target for sample experiment; vertical/curved remain extensions |
| W17 | Tooltip / `TextTooltipStyle` | `default` | Engine only | Extension |
| W18 | Window / `WindowStyle` | `default`, `resize`, `border`, `dialog` | Engine only | Extension as a widget; its `window` drawable is required by panels |
| W19 | Touchpad / `TouchpadStyle` | `default` | Engine only | Extension |
| W20 | Console output | Custom widget reads `default` font and `font`, `warning`, `error` colours | Engine only | Required; wrapping, clipping, scrolling |
| W21 | Image and scene preview | Image/Widget/WidgetGroup implementations; content textures borrowed from host | Media is content, UI surrounds use engine skin | Required; images, sprites, MD2 and BSP |
| W22 | Layout containers | Table, Container, HorizontalGroup and other groups | Shared layout mechanisms | Required; no independent standard style; backgrounds are Drawables |

TextArea, multiline editing and spinner behaviour are not current requirements.
Password masking is already used by Player Setup with `*` as the mask character.
A `spinner` TextFieldStyle or arrow ButtonStyle does not provide a spinner
component by itself.

## Screen coverage

The composed screen cases below are **Planned**; the basic widget gallery is
available. Gallery and composition cases are
defined in [the sample specification](../../info/ui-skin-sample.md).

| ID | Existing screen or composition | Current theme policy | Sample case |
| --- | --- | --- | --- |
| S01 | Main menu | Resolved content style with engine fallback | Composition: menu over backdrop |
| S02 | Multiplayer and options navigation | Resolved content style | Composition: menu; shared action states |
| S03 | Profile editor | Resolved content style | Composition: list beside text form |
| S04 | Join Game | Resolved content style | Composition: address input, feedback, actions |
| S05 | Singleplayer and host setup | Resolved content style | Composition: dropdowns, fields, disabled Start |
| S06 | Player Setup | Resolved content style | Composition: long dropdown choices and editable fields |
| S07 | Options section | Resolved content style | Composition: boolean toggles, values, descriptions, scrolling |
| S08 | Console | Engine skin, ScreenViewport | Composition: severity text, panel, command input |
| S09 | Debug graphs | Engine text with locally defined metric colours, ScreenViewport | Composition: diagnostic labels and graph backdrop |
| S10 | Standalone viewer information panel | Engine skin, ScreenViewport | Composition: filename, metadata, hints, errors |
| S11 | Content Browser shell | Engine skin in standalone viewer | Composition: tree, splitter, tiles, path, status, Back |
| S12 | Image preview | Engine UI around borrowed texture | Composition: clipping, native dimensions, pan/zoom hint |
| S13 | Sound controls | Engine skin | Composition: duration and Play/Pause/Loop/Stop |
| S14 | Sprite and MD2 controls | Engine skin | Composition: frame info, transport buttons, skin dropdown |
| S15 | BSP and MD2 scene | Engine UI around scene rendering | Composition: contrasting light/dark preview backdrop |
| S16 | HUD and game overlays | Content font and numeric renderer | Compatibility area: normal/alternate text and numbers |
| S17 | Embedded Content Browser | Planned active-profile style with engine fallback | Same browser composition under resolved content theme |

The legacy Java client's direct picture/character UI provides compatibility
reference material; it does not consume the Scene2D skin. Standalone archive
selection must not change the host's UI theme.

## Sample coverage and consistency tracker

The implementation tables above describe what exists. This tracker records
which cases the sample exposes and what has been reviewed. Sample coverage starts
at `Planned`; use `Partial` for a group with some available cases, `Available`
when all its cases can be inspected and `Reviewed` after
checking its relevant states and usages. Review starts at `Open`; record findings
such as `Consistent`, `Intentional difference`, `Gap` or `Needs alignment` with
the specific IDs and usages involved. A screenshot alone does not establish
interactive support.

| Coverage group | Sample coverage | Consistency review | Evidence and decisions |
| --- | --- | --- | --- |
| C01–C08 palette and states | Planned | Open | Focus/feedback roles to inspect |
| F01–F02 UI typography | Partial | Open | F01 normal/long text available; role variants not separately shown |
| F03–F05 Quake text compatibility | Partial | Open | Normal conchars and unsupported text examples available; alternate bank/numbers remain planned |
| D01–D06 panels, button chrome, selection | Partial | Open | Button, checkbox and list states visible; console panel/raw drawable gallery remain planned |
| D10–D13 scrolling, dropdown, splitter | Partial | Open | Vertical scroll/dropdown specimens available; horizontal scroll and splitter remain planned |
| D16, D19–D21 input, tree, panels | Partial | Open | Inputs and thick/thin panel roles available; tree remains planned |
| W01–W11 required standard controls and tiles | Partial | Open | W01–W07 specimens available; W08–W11 tree/split/button variants/tiles remain planned |
| W20–W22 custom output, previews, layout | Planned | Open | Font metrics, padding and clipping |
| W15–W16; D07, D14 horizontal slider/progress | Available | Open | Conchars 128–131; live slider, linked progress, disabled slider; visual review pending |
| Two panel roles | Available | Open | Quake: thick 1–9, thin 18–26 with half-cell insets. Engine: distinct 3× panels; both visible in sample |
| W12–W14, W17–W19; D08–D09, D15, D17–D18 extensions | Planned | Open | Separate optional inspection area |
| S01–S09 menus and engine tools | Planned | Open | Both viewport policies |
| S10–S15 viewer and browser | Planned | Open | Shared resources and differences between usages |
| S16–S17 HUD compatibility and embedded host | Planned | Open | Compatibility area; embedding remains planned |

When a group contains different statuses, split the row or name the incomplete
IDs in the evidence column. For each style decision, record the role, affected
usages, resources to share, intentional differences and reason. Consistency means
equivalent roles follow understood rules; engine and Quake themes can express
those roles differently. Retaining the current appearance is a valid decision.

Visual variants can be compared in the sample when they help resolve a specific
question. Record the question and result against the relevant IDs; producing or
choosing a new design is not a completion condition for this inventory.

## Implementation references

- [Engine skin](uiskin.json), [atlas metadata](uiskin.atlas) and [asset guidance](README.md).
- [GameUiStyle and widget derivation](../../cake/core/src/main/kotlin/org/demoth/cake/ui/GameUiStyle.kt).
- [Content loading and fallback](../../cake/core/src/main/kotlin/org/demoth/cake/ui/IdTech2UiStyle.kt).
- [Conchars font and glyph mapping](../../cake/core/src/main/kotlin/org/demoth/cake/ui/ConcharsFontLoader.kt).
- [Content Browser](../../cake/core/src/main/kotlin/org/demoth/cake/ui/ContentBrowser.kt).
- [Menu stage responsibilities](../../cake/core/src/main/kotlin/org/demoth/cake/stages/README.md).
- [Console output](../../cake/core/src/main/kotlin/org/demoth/cake/stages/console/ConsoleOutputWidget.kt).
- [Viewer](../../cake/core/src/main/kotlin/org/demoth/cake/viewer/CakeViewer.kt).
