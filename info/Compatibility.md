# Content and compatibility

Jake2 and Cake build on the classic Quake II engine, protocol, and content
formats. Support for a format does not guarantee that every mod or asset using
it will work: content may depend on custom gameplay, engine extensions, or
resources from another installation.

## Multiplayer and demos

- Live networking uses classic Quake II **protocol 34**. Other clients and servers
  must support that protocol; extended or rerelease protocols are not supported.
- Cake records protocol-34 `.dm2` demos and plays classic protocol-26, 31, 32, and
  34 demos. Playback requires the corresponding maps, models, and sounds.
- Demo playback does not add support for older protocols to live networking.
- Extended/rerelease demo protocols and cinematic recordings are unsupported.

See [Networking](Networking.md) for the implementation and the
[Cake demo guide](../cake/README.md#demo-recording-and-playback) for commands.

## Content and packages

The content pipeline includes Quake II BSP maps, MD2 models, SP2 sprites,
PCX/WAL textures, TGA images, WAV sounds, and CIN cinematics. Support varies by
application and use: the standalone viewer previews resources without running
gameplay, and its BSP view shows static geometry rather than a playable level.

The shared filesystem reads loose files and `.pak` packages, plus ZIP containers
using `.pk2`, `.pk3`, `.pkz`, or `.zip` extensions. Supporting these containers does
not imply support for the games or formats commonly distributed in them.

See the [viewer guide](cake-viewer.md) for preview capabilities and limits,
the [asset pipeline](../cake/core/src/main/kotlin/org/demoth/cake/assets/README.md)
for loading and missing-resource behavior, and the [VFS guide](vfs.md) for
lookup order and package precedence.

## Mods and gameplay

Custom maps and asset replacements can work when they use supported formats,
available resources, and gameplay entities implemented by Jake2.

Jake2 does not load native gameplay modules such as `game.dll` or `game.so`.
Mods and mission packs requiring their own gameplay implementation need that
logic ported to the JVM. Placing their assets in a profile does not supply the
missing gameplay logic.

The `game` module contains the Quake II gameplay implementation. Its boundary
with the server is defined by `GameImports` and `GameExports`; see the
[architecture overview](Overview.md) for where to start making changes.

## Runtime and platforms

High-volume engine diagnostics use TRACE. The former logging cvars `showtrace`,
`showpackets`, `showdrop`, `sv_print_network`, `cl_debug_stufftext`, and
`r_bsp_batch_debug` have been retired. Use the `loglevel` cvar to control
diagnostic output. Use `set loglevel DEBUG` for DEBUG output previously enabled
by `developer 1`. Log verbosity defaults to INFO.
Use `set loglevel_network TRACE` for a process-wide network diagnostic override,
and `set loglevel_network GLOBAL` to restore the global threshold for Network. Client,
Common, Filesystem, Game, Render, and Server support the same override convention.
Interactive stdout uses ANSI severity colors when Java detects a terminal and
`TERM` is set to a value other than `dumb`. Pipes and files receive plain text.

Building requires JDK 25. JVM desktop distributions require Java 25 or newer;
`jpackage` application images bundle Java and are built for a particular OS and
architecture. Desktop applications also depend on compatible libGDX/LWJGL3
native libraries and a working graphics context. Java availability alone does
not guarantee desktop support on every platform.
