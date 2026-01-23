# Repository Guidelines

## Project Structure & Module Organization

- Root Gradle build with multiple modules
    - `qcommon/` - Common module with classes, functions and utilities
    - `game/` - Game logic related code (monsters, weapons, etc.) - server side
    - `server/` - Server-side game logic like networking and server state
    - `dedicated/` - Thin wrapper around the server module
    - `cake/` -  New client implementation (libgdx based)
    - `client/` - (Deprecated) Old Client-side code (for reference)
    - `fullgame/` - (Deprecated) Wrapper around all modules
    - `maptools/` - WIP - Map editing tools and utilities
- Source code lives under each module’s `src/main/java` or `src/main/kotlin`.
- Tests live under `src/test/java` or `src/test/kotlin` in each module.
- Shared assets and game data live in `assets/`, `resources/`, and module resources (for example
  `game/src/main/resources`).

## Build, Test, and Development Commands
- `./gradlew build` builds all enabled modules and runs tests.
- `./gradlew test` runs the unit test suites only.
- `./gradlew run` launches the Cake client (see `cake/README.md`).
- `./gradlew distZip` assembles a distributable archive.
- IDE run targets: `Jake2Dedicated.main` for the server, `Lwjgl3GameLauncher.main` for the Cake client.

## Coding Style & Naming Conventions
- Follow standard Java/Kotlin conventions; 4-space indentation and default IntelliJ IDEA formatting are expected.
- The codebase mixes legacy C-inspired naming; keep existing patterns in touched files for consistency.
- When renaming critical functions, keep the old C name in a brief comment for traceability.
- Use `*Test` suffixes for test classes (for example `Vector3fTest.kt`).

## Testing Guidelines
- Primary test framework is JUnit 4 (see root `build.gradle`); Kotlin tests live alongside Java tests.
- Run all tests with `./gradlew test` or target a module with `./gradlew :qcommon:test`.
- Test classes should be in the same package as the module they verify.
- Keep tests deterministic and colocated with the module they verify.

## Commit & Pull Request Guidelines
- Commit messages follow Git Flow/Conventional style (`feat:`, `fix:`, `chore:`), sometimes with github issue refs (e.g. `#143`).
- Keep commits scoped to one topic; describe the intention(most important), behavior changes, and impacts.
- PRs should include a short summary, testing notes, and link related issues; add screenshots for UI/visual changes.

## Configuration Tips
- Jake2 requires original Quake2 data files. If auto-detection fails, pass a base dir: `+set basedir "/path/to/Quake 2"`.
- JDK 11 is required; newer JVMs may cause LWJGL issues on Linux.
