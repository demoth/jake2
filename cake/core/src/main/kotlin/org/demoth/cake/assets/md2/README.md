# MD2 Pipeline

This package converts Quake2 MD2 frame models into libGDX geometry, materials, and
GPU animation data. Player model/skin selection belongs to
[configuration](../../config/README.md); file lookup and missing-asset policies
belong to [shared asset loading](../README.md).

## From File to Draw

```text
AssetManager request (model path or <skinPath>|<modelPath>)
  -> CakeFileResolver: locate the model file
  -> Md2Loader.getDependencies(): select skin texture paths
  -> qcommon Md2Model + buildVertexData(): decode frames, normals, and triangles
  -> Md2Loader.load(): static mesh + position/normal textures + material -> Md2Asset
  -> ModelInstance + Md2CustomData: entity frames, interpolation, skin and lighting
  -> Md2Shader: sample animation textures, interpolate positions, draw
```

`Md2Asset` contains the model, frame count and stored names, ordered skin textures, and bounds
covering decoded vertex positions across all frames. The viewer uses these bounds
for camera framing; ordinary mesh bounds cannot represent the VAT geometry,
whose mesh contains animation indices rather than position attributes. The model
viewer and transient model effects use the same loader/shader pipeline as gameplay
entities. The shared `CakeShaderProvider` dispatches MD2, BSP, and default shaders;
its cross-pipeline routing remains outside this package.

## Skin Dependencies and Player Variants

`Md2Loader` chooses skins in this order: a synthetic key's prefix; otherwise embedded
MD2 skin paths if enabled; otherwise an optional generated white 1-by-1 skin.
The viewer enables `useDefaultSkinIfMissing` for models with no selected skin
paths. That option does not recover arbitrary failures to load a referenced file.
`Md2Asset.skinNames` retains the resolved paths in texture order, or an empty list
for the generated fallback. For embedded skins, the viewer uses these names in
its selector and changes the preview instance's `skinIndex` without reloading assets.

Player previews disable embedded skins and use the white fallback for geometry
loading. `SceneResourcePreview` acquires the selected external PCX separately,
replaces only the instance's `Md2SkinTexturesAttribute`, then releases the old
texture reference. Model materials, geometry, camera, and animation remain intact.
Only one external skin is bound at a time, so the twelve-slot shader limit does
not limit the selectable player skins. Matching uses the current loose folder,
isolated archive, or profile entry snapshot; it does not change gameplay variant keys.

Player variants use keys such as
`players/male/grunt.pcx|players/male/tris.md2`. `PlayerConfiguration` selects the
variation from `CS_PLAYERSKINS`, applies legacy fallback/disguise rules, and builds
the key in `loadPlayerModelAsset()`. Each model/skin pair gets a distinct cache entry.
The trailing `.md2` preserves normal loader registration; the resolver strips the
skin prefix for file lookup, while the loader gives it priority over embedded skins.

Player entities (`modelindex == 255`) use shader skin index zero because the variant
loads exactly one skin. Non-player models can select embedded skins using replicated
`skinnum`. The material binds up to `MAX_MD2_SKIN_TEXTURES` skins (currently 12).
Legacy references are `CL_parse.LoadClientinfo` and `CL_ents.AddPacketEntities`.

## GPU Animation and Lighting

At load time, decoded frame positions and normals become floating-point
vertex-animation textures (VATs), indexed by vertex and frame. The static mesh
stores VAT indices and UVs, avoiding per-frame CPU updates to mesh positions.
`AnimationTextureAttribute` and `AnimationNormalTextureAttribute` bind those
textures; custom material attributes must be registered before construction.

`Md2CustomData` carries per-entity animation, skin, and lighting values. `Md2Shader`
interpolates two position frames. Directional lighting uses the current-frame normal
and a yaw-dependent shade vector rather than interpolating normals. Shell flags
produce a shader rim highlight; this approximates the legacy shell appearance.
Shader sources are `assets/shaders/md2.vert` and `md2.frag`.

Triangle strip/fan decoding normalises winding for conventional backface culling
(`GL_BACK`, counter-clockwise front faces); no per-model `GL_FRONT` override is needed.
World light sampling and effect flags are supplied by the ingame runtime.

## Ownership

`Md2Asset.dispose()` disposes the model, which owns its generated mesh, position and
normal VATs, and optional white skin. Skin dependencies belong to `AssetManager`.
Configuration and effect owners release their own asset references; shared assets
remain alive while another owner still references them.
