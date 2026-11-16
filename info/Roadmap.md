# Project direction and roadmap

Jake2 and Cake aim to keep their Quake II foundations usable on modern systems
while making the engine easier to understand, extend, and use for new content
and games. This document describes areas of interest rather than a release
schedule or a commitment to implement every idea.

## Existing foundations

These capabilities are already part of the project, rather than future roadmap items:

- A Kotlin/libGDX client with the LWJGL3 desktop backend and shader-based BSP
  and MD2 rendering.
- Separate remote-client, fullgame, and standalone viewer applications.
- An embedded server for singleplayer and hosting, plus a dedicated server application.
- Shared filesystem support for loose files, PAK, and ZIP-based packages.
- Content browsing and previews, BSP inspection tools, and asset-generation tools.
- Desktop JVM distributions, Java-bundled application images, and experimental
  GraalVM native builds.

See the [Cake guide](../cake/README.md), [VFS guide](vfs.md), and
[map tools guide](../maptools/README.md) for current behavior and limitations.

## Areas for further work

- Continue clarifying state ownership and lifecycle boundaries across engine,
  gameplay, and application code.
- Improve maintainability, diagnostics, and verification of networking,
  rendering, and content loading while preserving classic protocol compatibility.
- Make gameplay changes and content-development workflows easier to discover
  and document.
- Expand tooling and format support where they serve concrete development needs.
- Investigate additional content compatibility, including mission packs that
  require gameplay ports.

## Exploratory ideas

Ideas carried forward from earlier plans include:

- JVM scripting for gameplay behavior.
- Broader authoring tools, such as map and entity editors.
- Procedural map generation.
- Campaign experiments with quests, progression, trading, and randomized content.
- Clients for additional platforms, potentially using Kotlin Multiplatform.
- Investigating content from related engines and games.
