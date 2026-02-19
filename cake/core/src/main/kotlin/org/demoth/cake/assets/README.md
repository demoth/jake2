# Assets Package

## Overview
This package owns runtime asset resolving/loading for the Cake client (maps, models, textures, sounds) and shader-specific model data.

It does **not** own gameplay selection rules (for example which player model/skin to pick from `CS_PLAYERSKINS`) - that logic lives in `org.demoth.cake.GameConfiguration`.

## Key Types
- `CakeFileResolver` - resolves logical asset names to actual files (classpath/internal/mod/baseq2), including synthetic player MD2 variant keys.
- `Md2Loader` / `Md2Asset` - loads MD2 geometry into VAT-ready `Model` + resolved skins.
- `Md2Shader` / `Md2SkinTexturesAttribute` - runtime MD2 frame interpolation + skin selection on GPU.
- `BspLoader`, `Sp2Loader`, texture/sound loaders - format-specific loaders used by `AssetManager`.

## Data / Control Flow
`GameConfiguration.getPlayerModel(skinnum, renderFx)`
-> resolve legacy model/skin fallback from `CS_PLAYERSKINS`
-> compose synthetic MD2 key: `<skinPath>|<modelPath>`
-> `AssetManager.load(Md2Asset)`
-> `CakeFileResolver.resolve(...)` resolves only trailing `<modelPath>`
-> `Md2Loader.getDependencies(...)` extracts `<skinPath>` from key and loads texture dependency
-> `Md2Loader.load(...)` builds MD2 model/material and returns `Md2Asset`
-> `ClientEntityManager` assigns model for `modelindex == 255` and sets `skinIndex = 0`.

Legacy counterparts:
- `client/CL_parse.LoadClientinfo` (model/skin fallback)
- `client/CL_ents.AddPacketEntities` (`modelindex == 255`, `skinnum & 0xFF`)

## Invariants
- Player MD2 variant key format is exactly: `<skinPath>|<modelPath>`.
- Variant key must end with `.md2` (`<modelPath>` suffix) so standard MD2 loader mapping applies.
- `CakeFileResolver` ignores the `<skinPath>|` prefix for file lookup.
- `Md2Loader` gives synthetic key skin prefix priority over embedded MD2 skins.
- For player entities (`modelindex == 255`), runtime shader `skinIndex` is forced to `0` because only one skin texture is loaded for that variant.

## Decision Log

### Decision: Encode player model variants as `<skinPath>|<modelPath>` asset keys
- **Context:** AssetManager caches by asset path. Multiple players can share model path but require different skins.
- **Options Considered:**
  - Query-style key (`model?skin=...`)
  - Prefix-style key (`skin|model`)
  - Separate per-instance material mutation only
- **Chosen Option & Rationale:** `skin|model`. Keeps `.md2` suffix for existing loader registration, avoids model path duplication, and keeps key readable.
- **Consequences:** Resolver and MD2 loader must parse synthetic keys consistently.
- **Status:** accepted
- **Definition of Done:** Two players using same model with different skins render distinct skins while sharing standard MD2 loader registration.

### Decision: Derive skin dependency from synthetic key inside `Md2Loader`
- **Context:** After switching to `skin|model`, passing external skin as loader parameter became redundant.
- **Options Considered:**
  - Keep explicit `externalSkinPath` parameter
  - Parse from key in loader
- **Chosen Option & Rationale:** Parse from key in loader. Single source of truth and less parameter plumbing.
- **Consequences:** Loader behavior now depends on stable key format contract.
- **Status:** accepted
- **Definition of Done:** Player model load path in `GameConfiguration` uses `tryAcquireAsset<Md2Asset>(variantKey)` with no external skin parameter.

### Decision: Keep model-viewer-specific fallback mode in `Md2Loader.Parameters`
- **Context:** Viewer often opens standalone MD2 without valid skin dependencies.
- **Options Considered:**
  - Keep embedded skin loading only
  - Keep embedded skin loading + fallback to default skin when missing
- **Chosen Option & Rationale:** Keep embedded skin loading enabled and enable fallback (`useDefaultSkinIfMissing`) for assets with no embedded skins (common for player models).
- **Consequences:** Loader has one extra mode branch; gameplay path remains unaffected.
- **Status:** accepted
- **Definition of Done:** Model viewer renders MD2 with embedded skins when present, and still renders MD2 with no embedded skins using default fallback texture.

### Decision: Mirror legacy player fallback/disguise behavior in `GameConfiguration`
- **Context:** Cake previously used hardcoded `male/grunt`, breaking multiplayer correctness.
- **Options Considered:**
  - Keep default hardcoded model
  - Implement legacy fallback order and disguise handling
- **Chosen Option & Rationale:** Implement legacy-compatible fallback/disguise flow to match old client semantics.
- **Consequences:** More lookup logic, but predictable behavior and compatibility.
- **Status:** accepted
- **Definition of Done:** In multiplayer, remote player names/icons/models match selected model+skin and update when `CS_PLAYERSKINS` changes.

### Decision: Normalize MD2 triangle winding at decode time and keep conventional OpenGL backface culling
- **Context:** Legacy alias-model path effectively relied on `GL_FRONT` culling. In Cake's shader pipeline this caused confusion and mixed behavior when culling policy was handled at runtime per material.
- **Options Considered:**
  - Keep legacy-style runtime culling override (`GL_FRONT`) for MD2 materials
  - Normalize winding in MD2 decode and keep conventional OpenGL culling (`GL_BACK`, CCW front faces)
- **Chosen Option & Rationale:** Normalize winding during `buildVertexData(...)` and remove per-model cull overrides. This keeps culling policy simple and predictable across model pipelines.
- **Consequences:** `qcommon` MD2 decode output is now Cake-oriented rather than a raw mirror of legacy immediate-mode winding.
- **Status:** accepted
- **Definition of Done:** MD2 entities render with correct outward faces using default backface culling, with no MD2-specific `GL_FRONT` cull attribute in `Md2Loader`.

## Quirks & Workarounds
- **What:** Synthetic variant key uses `|` separator.
  - **Why:** Needed to carry both skin and model in one AssetManager key while preserving `.md2` suffix.
  - **How to work with it:** Always build keys via `GameConfiguration.playerModelVariantAssetPath(...)`; do not handcraft elsewhere.
  - **Removal plan:** Remove if/when per-instance material management replaces path-key variation.

- **What:** Player entities force shader `skinIndex = 0`.
  - **Why:** Player MD2 variants are loaded with exactly one skin texture.
  - **How to work with it:** Non-player MD2 can still use replicated `skinnum` for multi-skin models.
  - **Removal plan:** Revisit when player path switches to geometry-shared + per-instance material swapping.

## How to Extend
1. If adding another synthetic key format, update both:
   - `CakeFileResolver.resolve(...)` key-to-path mapping
   - `Md2Loader.resolveDependencySkinPaths(...)` key-to-skin mapping
2. Keep `.md2` as trailing segment for MD2 variants unless loader registration strategy changes.
3. For new player visual rules, update `GameConfiguration.getPlayerModel(...)` and document legacy parity impact.
4. For viewer-only behavior, prefer `Md2Loader.Parameters` flags over game-path conditionals.

## Open Question
- Should synthetic key parsing be centralized in a single helper shared by resolver and loader to prevent format drift?
