# BSP Pipeline

This package loads Quake2 maps and renders their world and inline brush geometry.
File lookup and missing-asset policies belong to [shared asset loading](../README.md);
frame orchestration, entities, sky, and effects belong to the
[ingame runtime](../../stages/ingame/README.md).

## From File to Draw

```text
AssetManager request (maps/<name>.bsp)
  -> CakeFileResolver: locate BSP; BspLoader.getDependencies(): collect WAL textures
  -> qcommon Bsp: decode geometry, texinfo, leaves, and baked lighting
  -> BspLoader.load(): BspMapAsset
       world: stable surface/leaf records + chunked geometry + lightmap atlases
       inline: one libGDX Model per brush model, with per-face parts
  -> Game3dScreen.precache(): create world renderer and runtime controllers
       world: PVS/areabits mask -> BspWorldBatchRenderer
       inline: ModelInstance + texture/material controllers -> BspLightmapShader
```

`BspMapAsset.models` preserves BSP model indices: slot zero is an empty model;
worldspawn geometry lives in `BspWorldBatchData`. Inline brush models (`*1`, `*2`,
etc.) use the remaining slots, with part ids `inline_<modelIndex>_face_<faceIndex>`.
`BspWorldRenderData` retains stable surfaces, leaf-to-surface mappings, and texinfo
chains independently of draw grouping. Animated WAL dependencies include chain
frames even when no face references them directly.
`BspMapAsset.entities` and `bounds` retain the already-decoded entity dictionaries
and world model bounds, allowing preview camera placement without reparsing the map.

## World and Inline Rendering

`BspWorldVisibilityMaskTracker` finds the camera's collision leaf and combines its
potentially visible set (PVS) with server area bits. Invalid view clusters or absent
PVS data fall back to all-visible. The resulting mask selects world surfaces;
`BspWorldBatchRenderer` groups them by active texture, lightmap page, surface flags,
lightstyle signature, and mesh chunk, then submits opaque/warp and translucent
passes. Sky is drawn separately by the ingame runtime. Translucent world groups
currently follow batch order rather than a back-to-front surface sort.

Inline models use `ModelInstance` transforms and per-face materials.
`BspInlineTextureAnimationController` selects texinfo chain frames from the entity's
`resolvedFrame`; world animation advances every 500 ms. Both paths retain
`nexttexinfo` chain order. `BspInlineSurfaceMaterialController` updates flowing UVs,
transparency, and lightstyle weights. An inline model containing translucent parts
is classified into the entity alpha pass.

`SURF_TRANS33` and `SURF_TRANS66` use alpha 85/255 and 170/255 with depth writes
disabled. Flowing surfaces use U offset `-64 * frac(timeSeconds / 40)`.
World warp surfaces apply per-fragment UV distortion, with light scale 0.5 for
non-lava and 1.0 for lava. Both BSP draw paths currently disable face culling.

## Lighting and Shader Contracts

Eligible world and inline faces carry diffuse UVs and secondary lightmap UVs.
`BspLoader` packs lightmaps into shared atlas pages using a Q2PRO-style block
allocator (`LM_AllocBlock`), with four textures per page for the four BSP style
slots (`255` terminates unused slots). Shaders sum sampled slots using runtime
`CS_LIGHTS` weights. Transparent and warp faces are excluded from baked lightmaps;
inline non-lightmapped materials retain the diffuse/lightstyle fallback path.

World drawing uses `assets/shaders/bsp_world_batch.vert/.frag`; inline lightmapped
parts use `bsp_lightmap.vert/.frag` through the shared `CakeShaderProvider`.
Lightmapped brush shading adds per-fragment dynamic lights: `DynamicLightSystem`
supplies up to eight, ordered by descending radius. Shared `RenderTuningCvars`
provides `vid_gamma`, `gl3_intensity`, and `gl3_overbrightbits` controls.

Register `BspLightmapTexture*Attribute` types before building materials. libGDX
material copies can retain their type ids while copying them as base
`TextureAttribute`; shader lookups must query by id and cast to that base type.
Inline lightstyle weights travel through `ColorAttribute.Diffuse` RGBA channels.

`BspEntityLightSampler` supplies approximate lighting for MD2/sprite entities from
leaf-averaged baked style contributions plus dynamic lights. It is a cheap probe,
not a per-texel lightmap lookup; [MD2 shading](../md2/README.md) consumes its result.

## Ownership

`BspMapAsset.dispose()` releases generated models and atlas textures. WAL texture
dependencies belong to `AssetManager`. `BspWorldBatchRenderer` separately owns its
GPU meshes, white fallback texture, and shader program; the screen disposes it.
The shared shader provider owns and disposes `BspLightmapShader`.
