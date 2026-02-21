# Cake UI Skin Assets

## Overview
This folder owns runtime skin assets used by Scene2D:
- `uiskin.json` (style/resource map),
- `uiskin.atlas` + `uiskin.png` (drawables/nine-patch metadata),
- bitmap fonts (`share-tech-mono*.fnt/.png`).

It does not own stage/layout logic (see `cake/core/.../stages`).

## Key Resources
- `console-panel` (`TintedDrawable`) - console frame drawable.
- `buttonUp`/`buttonOver`/`buttonDown` (`TintedDrawable`) - primary button chrome.
- `chamfer-panel` (`uiskin.atlas`) - chamfer nine-patch base region.

## Invariants
- Skin class tags are short names (`ButtonStyle`, `Color`, ...), not fully-qualified class names.
- Resource names referenced from Kotlin stages must remain stable.
- Bitmap font files must exist at paths referenced by `BitmapFont` entries.

## Decision Log
Newest first.

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
- Should console panel use a dedicated atlas region (with split/pad tuned for console text) rather than sharing `chamfer-panel`?
- Should skin validation be automated in CI (e.g., duplicate-resource and missing-reference checks)?
