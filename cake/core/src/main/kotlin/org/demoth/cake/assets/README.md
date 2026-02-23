# Assets Package

## Overview
This package owns runtime asset resolving/loading for the Cake client (maps, models, textures, sounds) and shader-specific model data.

It does **not** own gameplay selection rules (for example which player model/skin to pick from `CS_PLAYERSKINS`) - that logic lives in `org.demoth.cake.GameConfiguration`.

## Key Types
- `CakeFileResolver` - resolves logical asset names to actual files (classpath/internal/mod/baseq2), including synthetic player MD2 variant keys.
- `Md2Loader` / `Md2Asset` - loads MD2 geometry into VAT-ready `Model` + resolved skins.
- `Md2Shader` / `Md2SkinTexturesAttribute` - runtime MD2 frame interpolation + skin selection on GPU.
- `AnimationTextureAttribute` / `AnimationNormalTextureAttribute` - position/normal VAT bindings for MD2 shader path.
- `BspLightmapShader` / `BspLightmapTexture*Attribute` - per-texel BSP lightmap sampling (`UV2`) with up to 4 lightstyle slots per face.
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

## Terminology Alignment (Quake2 vs libGDX)
- Quake2 BSP **model 0** (worldspawn geometry) maps to one libGDX `Model` produced by `BspLoader`.
- Quake2 **inline brush model** (`*1`, `*2`, ...) maps to additional libGDX `Model` objects in the same BSP asset.
- Quake2 **render entity** maps to libGDX `ModelInstance`.
- Quake2 world **surface/face** maps to one libGDX `NodePart` mesh part for model 0 (`meshPart.id = surface_<faceIndex>`).
- Quake2 BSP **texinfo** maps to `BspWorldTextureInfoRecord`; texture animation follows `nexttexinfo` chain.
- Quake2 **leaf/cluster/area** visibility metadata maps to `BspWorldLeafRecord` and drives per-surface `NodePart.enabled`.

For world rendering specifically:
- `Game3dScreen.precache()` creates one `levelEntity.modelInstance` from world `Model` (model 0).
- `BspWorldVisibilityController` toggles world `NodePart.enabled`.
- `BspWorldTextureAnimationController` swaps world `NodePart` diffuse textures by texinfo animation frame.
- `BspWorldSurfaceMaterialController` applies `SURF_FLOWING`, surface transparency flags, and per-slot lightstyle weights for world lightmaps.
- `BspLightmapShader` multiplies world diffuse albedo by baked lightmap texels sampled from UV2.

For inline brush models specifically:
- `BspLoader` emits stable inline part ids by face (`inline_<modelIndex>_face_<faceIndex>`).
- `BspInlineTextureAnimationController` updates inline `NodePart` diffuse textures.
- `BspInlineSurfaceMaterialController` applies flowing/transparency/lightstyle material state per inline part.
- Inline animation frame source is entity-local (`ClientEntity.resolvedFrame`), not global time.

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

### Decision: Carry MD2 per-frame normals through a dedicated normal VAT
- **Context:** MD2 lighting tint alone could not reproduce alias directional response and left models visually flatter than legacy/Yamagi.
- **Options Considered:**
  - Keep tint-only MD2 lighting
  - Decode normals and compute directional term on CPU per vertex every frame
  - Decode normals once and upload a second VAT texture consumed in shader
- **Chosen Option & Rationale:** Resolve `lightnormalindex` via `Globals.bytedirs` at decode time and upload a normal VAT (`AnimationNormalTextureAttribute`). Shader interpolates per-frame normals and applies directional dot with yaw-derived shade vector. This keeps runtime work on GPU and matches Cake's VAT pipeline.
- **Consequences:** MD2 materials now bind two VAT textures (position + normal) and incur one extra texture fetch path in shader.
- **Status:** accepted
- **Definition of Done:** MD2 vertex data includes `vertexNormals`; runtime binds normal VAT; fragment shading visibly reacts to entity yaw and normal orientation.

### Decision: Add optional legacy MD2 shadedots mode (`r_md2_legacy_shadedots`)
- **Context:** Continuous Lambert shading improves smoothness, but some maps/scenes are compared against legacy alias look that uses quantized shadedot response.
- **Options Considered:**
  - Keep continuous mode only
  - Always force quantized legacy shadedots mode
  - Add runtime toggle for legacy shadedots mode
- **Chosen Option & Rationale:** Runtime toggle. Default remains smooth VAT interpolation, while parity/debug sessions can enable quantized legacy-style response.
- **Consequences:** MD2 shader has one extra uniform branch and render output depends on runtime cvar.
- **Status:** accepted
- **Definition of Done:** With `r_md2_legacy_shadedots 1`, MD2 shading uses 16 yaw buckets and `dot(currentFrameNormal, shadeVector) + 1` response.

### Decision: Keep explicit world surface/leaf runtime records in `BspMapAsset`
- **Context:** Grouping world BSP faces by texture into coarse model parts made PVS/areabits, lightmaps, and transparent/animated surface passes difficult to implement incrementally.
- **Options Considered:**
  - Keep texture-grouped world-only model and derive visibility later with ad-hoc scans
  - Store stable per-surface records and leaf->surface mapping at load time
- **Chosen Option & Rationale:** Store `BspWorldRenderData` (`surfaces`, `leaves`) inside `BspMapAsset`, and emit world model mesh parts per face. This preserves identity needed for upcoming visibility and lighting phases while keeping current rendering path functional.
- **Consequences:** More world mesh parts/draw records, but much clearer runtime structure and lower coupling for next phases.
- **Status:** accepted
- **Definition of Done:** Runtime can enumerate world faces by stable indices and map visible leaves/clusters to exact surface sets without reconstructing topology from grouped texture batches.

### Decision: Drive world texture animation from texinfo `nexttexinfo` chains at runtime
- **Context:** Legacy world animation picks texture frame by walking texinfo chains (`R_TextureAnimation`) using a global 2 Hz frame counter, but Cake originally bound only the base face texture.
- **Options Considered:**
  - Bake one static texture per surface at load time
  - Resolve texinfo chains at runtime and swap diffuse textures per surface
- **Chosen Option & Rationale:** Runtime chain resolution + per-surface texture swap. This matches legacy timing behavior and keeps animated texture support independent from upcoming lightmap/transparency refactors.
- **Consequences:** World texture dependencies must include chain frames that are not directly referenced by faces.
- **Status:** accepted
- **Definition of Done:** World monitor/button-style animated textures advance over time using texinfo chain order and legacy cadence (`time * 2` equivalent).

### Decision: Drive inline brush-model texture animation from entity frame
- **Context:** Legacy brush-model path (`R_DrawBrushModel`) resolves animated texinfo frame using `currententity.frame`, while world path uses global renderer time.
- **Options Considered:**
  - Reuse world global-time animation logic for inline models
  - Use entity-local frame index for inline models
- **Chosen Option & Rationale:** Use entity-local frame (`ClientEntity.resolvedFrame`) for inline model texinfo animation to match legacy semantics and preserve per-entity frame control.
- **Consequences:** Inline controller must run from entity render path and receive resolved per-entity frame values.
- **Status:** accepted
- **Definition of Done:** Animated textures on inline brush entities follow per-entity frame progression and no longer depend on global world animation time.

### Decision: Apply BSP `SURF_*` material flags at runtime on per-surface/per-part materials
- **Context:** After switching BSP runtime to stable surface/part identities, world and inline materials need idTech2 flag semantics (`SURF_FLOWING`, `SURF_TRANS33`, `SURF_TRANS66`) without re-baking geometry.
- **Options Considered:**
  - Encode all effects into custom shaders immediately
  - Apply effects in runtime material controller layer
- **Chosen Option & Rationale:** Runtime material controllers. It keeps behavior changes incremental, easy to reason about, and consistent for world + inline surfaces.
- **Consequences:** Brush entities must avoid unconditional material reset paths that would erase per-surface blend/depth/UV state.
- **Status:** accepted
- **Definition of Done:** Flowing and transparent BSP surfaces are driven by `SURF_*` flags in both world and inline brush-model paths.

### Decision: Use UV2 + per-surface lightmap textures for world BSP lighting
- **Context:** Surface-average modulation removed fullbright rendering, but produced visibly coarse lighting on large faces.
- **Options Considered:**
  - Keep surface-average modulation only
  - Add UV2 lightmap sampling for world surfaces with a dedicated brush-surface shader
- **Chosen Option & Rationale:** UV2 + texture sampling for world surfaces. This restores per-texel baked shadow detail and matches legacy brush-lighting semantics more closely.
- **Consequences:** World model now carries secondary UVs and generated lightmap textures. Faces with multiple BSP style slots keep one texture per slot (up to 4) and combine them in shader using runtime `CS_LIGHTS` weights.
- **Status:** accepted
- **Definition of Done:** World BSP surfaces sample baked lightmap texels in shader space (not per-surface averages) and remain compatible with flowing/transparency runtime material control.

### Decision: Blend all BSP face lightstyle slots (up to 4) in world lightmap shader
- **Context:** `target_lightramp` and similar gameplay updates can animate any style slot referenced by a face, not only slot 0.
- **Options Considered:**
  - Keep one sampled lightmap texture per face and scale by primary style only
  - Load one lightmap texture per style slot and blend in shader using runtime style weights
- **Chosen Option & Rationale:** Per-slot textures + shader blend. This preserves UV2 per-texel detail and restores animated lightstyle behavior for non-primary slots.
- **Consequences:** More generated textures and texture-unit usage on world surfaces with multi-style lightmaps.
- **Status:** accepted
- **Definition of Done:** Triggered `CS_LIGHTS` updates (for example from `target_lightramp`) visibly affect world surfaces that reference non-primary style slots.

### Decision: Use per-face UV2 lightmaps for inline BSP entities
- **Context:** Legacy brush rendering lights both world and inline brush faces through the same lightmapped path (`R_DrawBrushModel` -> `GL_RenderLightmappedPoly`), while Cake inline lighting was still per-part aggregate modulation.
- **Options Considered:**
  - Keep inline per-part aggregate modulation
  - Split inline models into per-face mesh parts and apply UV2 lightmaps for eligible faces
- **Chosen Option & Rationale:** Per-face inline parts + UV2 lightmaps for non-`SURF_TRANS*` and non-`SURF_WARP` faces. This restores parity for doors/platforms/func_* lighting and makes inline lightstyle updates (`CS_LIGHTS`) behave like world faces.
- **Consequences:** More inline mesh parts/materials and generated lightmap textures; texture animation/material controllers continue to work by mesh part id.
- **Status:** accepted
- **Definition of Done:** Inline brush entities show per-texel baked lighting on eligible faces and react to `target_lightramp` updates through style-slot weighting.

### Decision: Apply Yamagi-style brightness controls in Cake brush/MD2 shaders
- **Context:** World and inline lightmaps looked darker than Yamagi defaults because Cake path had no gamma/intensity/overbright controls.
- **Options Considered:**
  - Keep fixed shader response and tune textures offline
  - Add runtime controls aligned with Yamagi (`vid_gamma`, `gl3_intensity`, `gl3_overbrightbits`)
- **Chosen Option & Rationale:** Add runtime controls and feed both `BspLightmapShader` and `Md2Shader` from shared cvars. This keeps parity tuning data-driven and comparable to Yamagi.
- **Consequences:** Shader output now depends on cvar state; screenshot/visual diffs require recording active cvar values.
- **Status:** accepted
- **Definition of Done:** Changing `vid_gamma` / `gl3_intensity` / `gl3_overbrightbits` immediately affects brush + MD2 brightness without reloading assets.

### Decision: Keep dynamic-light application on brush surfaces in shader (up to 8 strongest lights)
- **Context:** Legacy/Yamagi dynamic lights affect lightmapped world/inline surfaces each frame.
- **Options Considered:**
  - CPU-side per-surface modulation only
  - Shader-side per-fragment additive dynamic lights
- **Chosen Option & Rationale:** Shader-side additive lights in `BspLightmapShader` with a capped upload budget for predictable cost.
- **Consequences:** Very dense dynamic-light scenes are approximated by strongest lights only.
- **Status:** accepted
- **Definition of Done:** Muzzle/explosion/`EF_*` dynamic lights visibly affect world and inline lightmapped faces in gameplay.

## Quirks & Workarounds
- **What:** Synthetic variant key uses `|` separator.
  - **Why:** Needed to carry both skin and model in one AssetManager key while preserving `.md2` suffix.
  - **How to work with it:** Always build keys via `GameConfiguration.playerModelVariantAssetPath(...)`; do not handcraft elsewhere.
  - **Removal plan:** Remove if/when per-instance material management replaces path-key variation.

- **What:** Player entities force shader `skinIndex = 0`.
  - **Why:** Player MD2 variants are loaded with exactly one skin texture.
  - **How to work with it:** Non-player MD2 can still use replicated `skinnum` for multi-skin models.
  - **Removal plan:** Revisit when player path switches to geometry-shared + per-instance material swapping.

- **What:** Custom BSP lightmap texture attributes require explicit registration.
  - **Why:** libGDX `TextureAttribute` validates type against static `Mask`; unregistered custom aliases throw `Invalid type specified`.
  - **How to work with it:** Keep `BspLightmapTexture*Attribute.init()` calls before creating BSP materials in `BspLoader.load(...)`.
  - **Removal plan:** None planned unless lightmap attributes are replaced with a non-`TextureAttribute` material path.

- **What:** Material copies may store custom lightmap attribute ids as base `TextureAttribute`.
  - **Why:** `ModelInstance` material copy path can lose concrete subtype while keeping attribute type id.
  - **How to work with it:** In shader render path, query by custom type id but cast to base `TextureAttribute`.
  - **Removal plan:** Revisit if upstream libGDX copy semantics change.

- **What:** BSP lightmap shader forces `GL_NONE` culling for world faces.
- **Why:** BSP brush winding/cull expectations differ across assets; forcing backface culling dropped valid world geometry in tested maps.
- **Legacy counterpart:** `client/render/fast/Main.R_SetupGL` (`glCullFace(GL_FRONT)`), plus per-surface side tests in `Surf.R_RecursiveWorldNode` and `Surf.R_DrawInlineBModel`.
- **Difference:** Legacy relies on fixed winding + planeback tests; Cake currently disables culling for BSP shader path.
  - **How to work with it:** Keep cull policy in `BspLightmapShader` unless world BSP winding is normalized end-to-end.
  - **Removal plan:** Re-evaluate after a dedicated BSP winding normalization effort.

- **What:** BSP face lightstyles are handled as 4 fixed slots.
  - **Why:** BSP/Quake2 format is fixed-width (`MAXLIGHTMAPS = 4`) with `255` as terminator for unused slots.
  - **Legacy counterpart:** `qcommon/Defines.MAXLIGHTMAPS` and loops in `client/render/fast/Light.R_BuildLightMap`.
  - **Difference:** Same slot count contract; Cake stores per-slot textures for world shader and encodes weights as RGBA.

- **What:** Transparency (`SURF_TRANS33/SURF_TRANS66`) is applied by material mutation.
  - **Why:** Cake uses per-surface `NodePart` materials instead of immediate-mode alpha surface chain.
  - **Cake counterpart:** `BspSurfaceMaterialController.applySurfaceTransparency`.
  - **Legacy counterpart:** enqueue in `Surf.R_RecursiveWorldNode` / `Surf.R_DrawInlineBModel`, render in `Surf.R_DrawAlphaSurfaces`.
  - **Difference:** Legacy had dedicated alpha pass; Cake sets `BlendingAttribute` and disables depth writes per material.

- **What:** Flowing surfaces use time-based U offset.
  - **Why:** Preserve classic scroll cadence.
  - **Cake counterpart:** `BspSurfaceMaterialController.applySurfaceFlowing`.
  - **Legacy counterpart:** `client/render/fast/Surf.DrawGLFlowingPoly`.
  - **Difference:** Same formula (`-64 * frac(time/40)`), but applied via texture attribute offset instead of immediate-mode vertex texcoord rewrite.

- **What:** Lightstyle application keeps a fallback branch for non-lightmapped BSP faces.
- **Why:** Legacy excludes `SURF_TRANS33`, `SURF_TRANS66`, and `SURF_WARP` from lightmap sampling.
- **Cake counterpart:** `BspSurfaceMaterialController.applySurfaceLightstyles`.
- **Legacy counterpart:** `client/render/fast/Light.R_BuildLightMap` + `Surf.GL_RenderLightmappedPoly`.
- **Difference:** World and inline eligible faces now use per-slot UV2 lightmaps; non-lightmapped faces use diffuse-only material path.

- **What:** MD2 per-entity lighting is sampled from leaf-averaged baked style data, then adjusted by dynamic lights.
  - **Why:** Keep MD2 lighting coupled to world lightstyles/dlights while preserving VAT-friendly alias shading.
  - **Legacy counterpart:** `client/render/fast/Mesh.R_DrawAliasModel`, `../yquake2/src/client/refresh/gl3/gl3_mesh.c` (`GL3_LightPoint` + alias shading).
  - **Difference:** Default Cake mode uses continuous normal-dot shading from interpolated normal VAT. Optional `r_md2_legacy_shadedots 1` switches to quantized shadedot response (`SHADEDOT_QUANT = 16`) and current-frame normal usage.
  - **How to work with it:** Use default mode for smoother animation; enable legacy mode when validating against classic alias-shading look.
  - **Removal plan:** Remove toggle only if one mode is chosen as permanent visual target.

## How to Extend
1. If adding another synthetic key format, update both:
   - `CakeFileResolver.resolve(...)` key-to-path mapping
   - `Md2Loader.resolveDependencySkinPaths(...)` key-to-skin mapping
2. Keep `.md2` as trailing segment for MD2 variants unless loader registration strategy changes.
3. For new player visual rules, update `GameConfiguration.getPlayerModel(...)` and document legacy parity impact.
4. For viewer-only behavior, prefer `Md2Loader.Parameters` flags over game-path conditionals.

## Open Question
- Should synthetic key parsing be centralized in a single helper shared by resolver and loader to prevent format drift?
