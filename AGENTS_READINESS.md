# LLM Agent Repository Readiness Checklist - Jake2

This document tracks the readiness of the Jake2 repository for LLM agents to autonomously solve tasks. It adapts general AI-readiness guidelines for a game development project.

## 1. Documentation & Discovery
Agents need to quickly understand the project's purpose, structure, and domain-specific concepts.

- [x] **Project Overview**: `README.md` provides a high-level summary and project goals.
- [x] **Agent-Specific Instructions**: `AGENTS.md` exists with clear setup and execution commands.
- [x] **Architecture Map**: `info/Overview.md` explains the module interaction (`qcommon`, `game`, `server`, `cake`).
- [x] **Domain Documentation**: `info/` contains detailed docs on Quake 2 specific formats (BSP, MD2) and systems (AI, Networking).
- [x] **Contribution Guidelines**: `CONTRIBUTING.md` explains the development workflow.

## 2. Build & Environment
Agents must be able to reliably build and run the project in a restricted environment.

- [x] **Standard Build System**: Uses Gradle, which is well-supported by agents.
- [x] **Reproducible Build**: `./gradlew build` works out of the box (verified).
- [x] **Dependency Management**: All dependencies are managed via Gradle.
- [ ] **Containerization**: No `Dockerfile` or `.devcontainer` found to guarantee a consistent environment.
- [x] **Environment Requirements**: JDK 21 is clearly specified.

## 3. Testing & Verification
Feedback loops are crucial for agents to verify their changes.

- [x] **Automated Test Suite**: JUnit tests are present across modules.
- [x] **Headless Execution**: Tests can be run without a GUI via `./gradlew test` (verified).
- [x] **Continuous Integration**: GitHub Actions (`build.yml`) is configured to run on every PR.
- [ ] **Linting & Formatting**: No explicit linting tools (like Checkstyle or ktlint) are configured in the build script to enforce style.
- [ ] **Test Coverage**: While tests exist, coverage reports are not automatically generated or tracked.

## 4. Code Quality & Patterns
Consistency helps agents mimic existing styles and avoid regressions.

- [x] **Consistent Structure**: Standard Gradle multi-module layout.
- [x] **Naming Conventions**: Documented in `AGENTS.md`, including handling of legacy C names.
- [ ] **Type Safety**: Much of the legacy code uses primitive arrays (`float[]`) for vectors. Moving to more robust types (like `Vector3f`) is ongoing but not complete.
- [ ] **Self-Documenting Code**: Many legacy classes lack Javadoc/Kdoc.

## 5. Game Dev Specifics
Game projects have unique requirements like asset management and real-time constraints.

- [x] **Asset Loading Documentation**: Detailed in `info/BSP.md` and related files.
- [x] **External Data Requirements**: `README.md` clearly states the need for Quake 2 data files and how to provide them.
- [x] **Core Game Loop**: High-level structure is discussed in `info/Overview.md`.
- [ ] **Visual Verification**: No automated tools for verifying visual changes (rendering/UI) in a headless environment.

---

## Recommendations for Improvement
1. **Add a `.devcontainer`**: To provide a standardized development environment with all tools pre-installed.
2. **Integrate Linting**: Add `ktlint` or `Checkstyle` to `build.gradle` to ensure agents follow the preferred style automatically.
3. **Automate Coverage Reports**: Use Jacoco to provide feedback on test coverage for new changes.
4. **Mock Assets for Testing**: Provide a minimal set of mock/free assets for headless integration tests that don't require original Quake 2 data.
