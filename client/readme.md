# Legacy Jake2 client

This module is deprecated, excluded from the current Gradle build, and kept for
reference only. Its sources may reference retired engine APIs. Use the
`jake2-legacy` branch to build the original client; it is no longer maintained.

## Why Cake replaced it

The original client used LWJGL2 and OpenGL immediate-mode rendering. Moving to
LWJGL3 and a modern shader pipeline required substantial changes. Cake uses
libGDX to provide rendering, input, audio, and UI infrastructure, replacing the
legacy client implementation while preserving Quake II network compatibility.

See the [Cake guide](../cake/README.md) for the current applications.
