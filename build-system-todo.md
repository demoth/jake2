# Build System Upgrade TODO

## Context / Constraints (confirmed)
- Java 11 runtime compatibility is **not required**.
- Target should move to latest LTS (Java 21).
- Native compile validation should use:
  - `~/.sdkman/candidates/java/21.0.2-graalce`
- `client/` and `fullgame/` are archival; excluded from active build and not part of upgrade work.

## Findings
1. Legacy root cross-project configuration in `build.gradle` (`configure(subprojects)` + `apply plugin`) is brittle for long-term Gradle upgrades.
2. Active Cake scripts still use legacy Groovy `buildscript {}` / `apply plugin` patterns.
3. Java compatibility is pinned to 11 despite no longer needing Java 11 support.
4. Dependency/version management is split between `gradle.properties` and hardcoded module versions.
5. Root test dependency uses old JUnit 4.12.

## Plan (phased)
- P1. Raise Java baseline to 21 (toolchain + compile targets), keep build green.
- P2. Validate native compile path with Graal JDK 21.0.2 and document outcome.
- P3. Refresh critical baseline dependencies (starting with test stack), keep behavior stable.
- P4. Modernize build logic incrementally (reduce root cross-project mutation, centralize plugin/dependency versioning).
- P5. Keep archival modules untouched, but document boundaries in build configuration.

## Execution Log (one commit per step)
- [x] S1: Create `build-system-todo.md` with findings, constraints, and plan.
- [x] S2: Bump Java baseline/toolchain to 21 across active modules.
- [x] S3: Run and fix full build/test under Java 21 baseline.
- [x] S4: Validate Graal native compile using `~/.sdkman/candidates/java/21.0.2-graalce`.
- [x] S5: Upgrade baseline test dependency (JUnit 4.12 -> 4.13.2) and verify.
- [x] S6: Begin build-logic modernization pass 1 (safe incremental refactor, no behavior change).
- [x] S7: Centralize dependency repositories in `settings.gradle.kts` and remove duplicate project-level repo declarations.
- [x] S8: Replace root Kotlin plugin `buildscript classpath` usage with `plugins { ... apply false }`.
- [x] S9: Migrate `cake/lwjgl3` plugin setup from legacy `buildscript` classpath wiring to `plugins` DSL.
- [x] S10: Migrate `cake/engine-tools` from `apply plugin` to `plugins` DSL.
- [x] S11: Replace `configure(subprojects)` usage with a unified `subprojects` configuration block.
- [x] S12: Document archival module boundary via `includeArchivalModules` opt-in setting in `settings.gradle.kts`.
- [x] S13: Move remaining frequently-changed hardcoded versions into `gradle.properties`.
- [x] S14: Remove cross-project mutation from `cake/lwjgl3/nativeimage.gradle` and keep Graal helper dependencies module-local.
- [x] S15: Re-run clean build/native compile and group current warnings into actionable buckets.
- [x] S16: Remove invalid Graal `--add-exports` warnings by excluding outdated `gdx-svmhelper` native-image metadata.
- [x] S17: Resolve dedicated module deprecation compile note for intentional `longjmpException` handling.
- [x] S18: Resolve `qcommon` test deprecation compile note for intentional `Com.ParseHelp` legacy coverage.
- [x] S19: Remove `qcommon` unchecked warnings by generifying legacy raw `Vector` usage in formatting helpers.
- [x] S20: Resolve server module deprecation compile note for intentional legacy API usage.
- [x] S21: Resolve game module deprecation compile note for intentional `GameSVCmds` legacy usage.
- [x] S22: Resolve remaining qcommon module deprecation compile notes for intentional legacy API usage.
- [x] S23: Re-validate warning baseline after fixes (clean build warning-free; native compile limited to upstream LWJGL experimental metadata warnings).
- [x] S24: Accept remaining LWJGL native-image experimental metadata warnings as upstream noise (no local override).
- [x] S25: Start dependency catalog migration (introduce `libs.versions.toml`; migrate root JUnit and `game`/`maptools` external dependencies).
- [x] S26: Expand catalog usage to `cake/core` and `cake/engine-tools` dependencies.
- [x] S27: Complete active-module catalog rollout (`cake/lwjgl3` + nativeimage script), and remove now-unused version properties from `gradle.properties`.
- [x] S28: Complete JUnit5 migration Stage A (JUnit Platform + Jupiter + Vintage, keeping existing JUnit4 tests runnable).

## Notes from completed steps
- S4 native compile command that worked:
  - `JAVA_HOME=~/.sdkman/candidates/java/21.0.2-graalce GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce ./gradlew :cake:lwjgl3:nativeCompile`
- S4 emitted Graal native-image warnings from upstream metadata (experimental options and invalid `--add-exports` from `gdx-svmhelper`), but produced executable successfully.
- S6 modernization pass 1 replaced direct task-name configuration (`compileJava`, `compileKotlin`, `compileTestKotlin`) with typed task configuration (`tasks.withType(...)`) in root build logic.
- S7 added `dependencyResolutionManagement` and `pluginManagement` repository blocks in `settings.gradle.kts`, and removed duplicated dependency repository declarations from `build.gradle`.
- S8 migrated root Kotlin plugin declaration from legacy `buildscript/dependencies/classpath` to modern `plugins` DSL (`apply false`) and kept build green.
- S9 moved `cake/lwjgl3` plugin declarations (`application`, `org.beryx.runtime`, `org.graalvm.buildtools.native`) to `plugins` DSL, with conditional native plugin application retained for `enableGraalNative`.
- S10 migrated `cake/engine-tools/build.gradle` to use `plugins { id 'application' }` instead of legacy `apply plugin`.
- S11 removed legacy `configure(subprojects)` style in root build logic by consolidating shared configuration into one `subprojects` block.
- S12 made archival modules explicit and opt-in via `-PincludeArchivalModules=true`, while keeping default builds focused on active modules.
- S13 centralized `junitVersion`, `beryxRuntimePluginVersion`, and `graalNativeBuildToolsVersion` in `gradle.properties` and wired build scripts to use them.
- S14 removed `project(":...")` wrappers from `cake/lwjgl3/nativeimage.gradle` and moved `cake:core` Graal helper annotation dependency to `cake/core/build.gradle` behind `enableGraalNative`, preserving successful `build` and `:cake:lwjgl3:nativeCompile`.
- S15 warning baseline commands:
  - `./gradlew clean build --warning-mode all --console=plain`
  - `JAVA_HOME=~/.sdkman/candidates/java/21.0.2-graalce GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce ./gradlew :cake:lwjgl3:nativeCompile --warning-mode all --console=plain`
- S16 updated `cake/lwjgl3/nativeimage.gradle` to:
  - explicitly pass `-H:+UnlockExperimentalVMOptions`, and
  - exclude `META-INF/native-image/gdx-svmhelper/backend-lwjgl3/native-image.properties` via `--exclude-config`,
  which removed invalid `--add-exports org.graalvm.sdk/...` warnings while keeping native compile successful.
- S17 added `@SuppressWarnings("deprecation")` on `Jake2Dedicated.main` to silence intentional handling of deprecated `longjmpException`; re-run command:
  - `./gradlew :dedicated:compileJava --rerun-tasks --warning-mode all --console=plain`
- S18 added class-level `@SuppressWarnings("deprecation")` to `qcommon/src/test/java/jake2/qcommon/TestCOM.java` (tests intentionally cover deprecated parser API); re-run command:
  - `./gradlew :qcommon:compileTestJava --rerun-tasks --warning-mode all --console=plain`
- S19 updated `qcommon` formatting helpers (`Vargs`, `PrintfFormat`) to use generics instead of raw `Vector`, removing unchecked compiler warnings in `qcommon`; verification commands:
  - `./gradlew -I /tmp/javac-lint.init.gradle :qcommon:compileJava --rerun-tasks --warning-mode all --console=plain`
  - `./gradlew :qcommon:compileJava --rerun-tasks --warning-mode all --console=plain`
- S20 added `@SuppressWarnings("deprecation")` to `server` legacy integration classes (`GameImportsImpl`, `SV_MAIN`); verification command:
  - `./gradlew :server:compileJava --rerun-tasks --warning-mode all --console=plain`
- S21 added targeted `@SuppressWarnings("deprecation")` on legacy `GameSVCmds` call sites (`PlayerClient.ClientConnect`, `GameExportsImpl.ServerCommand`); verification command:
  - `./gradlew :game:compileJava --rerun-tasks --warning-mode all --console=plain`
- S22 added targeted `@SuppressWarnings("deprecation")` on qcommon legacy bridge classes (`Lib`, `CM`, `Com`, `MainCommon`, `Cmd`, `Cvar`); verification commands:
  - `./gradlew :qcommon:compileJava --rerun-tasks --warning-mode all --console=plain`
  - `./gradlew clean build --warning-mode all --console=plain`
- S23 final verification commands:
  - `./gradlew clean build --warning-mode all --console=plain` (no Java compile warnings emitted)
  - `JAVA_HOME=~/.sdkman/candidates/java/21.0.2-graalce GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce ./gradlew :cake:lwjgl3:nativeCompile --warning-mode all --console=plain` (remaining warnings are LWJGL-provided experimental metadata options only)
- S24 decision: keep LWJGL experimental metadata warnings as-is, because they are upstream-provided and do not block successful native compilation.
- S25 introduced `gradle/libs.versions.toml` and migrated:
  - root `testImplementation` (`junit:junit`) to `libs.junit4`,
  - `game` `commons-csv` to `libs.commonsCsv`,
  - `maptools` `logback-classic` to `libs.logbackClassic`;
  verification command:
  - `./gradlew build --warning-mode all --console=plain`
- S26 expanded catalog entries and migrated `cake/core` + `cake/engine-tools` from string coordinates to aliases where straightforward (kept classifier-based runtime dependencies unchanged for now); verification command:
  - `./gradlew build --warning-mode all --console=plain`
- S27 migrated remaining active-module dependency coordinates to catalog aliases (`cake/lwjgl3`, `cake/lwjgl3/nativeimage.gradle`, classifier-based runtime dependencies), then removed now-unused dependency version properties from `gradle.properties`; verification command:
  - `./gradlew build --warning-mode all --console=plain`
- S28 enabled `useJUnitPlatform()` for all subprojects, added explicit JUnit5/Jupiter/Vintage dependencies plus `junit-platform-launcher` in root shared test configuration, and verified mixed JUnit4/5 execution compatibility:
  - `./gradlew test --warning-mode all --console=plain`

## Current Warning Buckets
1. Java compiler deprecation notes:
   - Addressed in S22 for `qcommon`.
   - Addressed in S17 for `dedicated`.
   - Addressed in S18 for `qcommon` tests.
   - Addressed in S20 for `server`.
   - Addressed in S21 for `game`.
2. Java compiler unchecked/unsafe notes:
   - Addressed in S19 (`qcommon` unchecked notes no longer emitted in standard compile).
3. Graal native-image metadata warnings from dependencies:
   - Accepted as-is: experimental option warnings for `-H:JNIConfigurationResources` and `-H:ReflectionConfigurationResources` (from LWJGL metadata).
   - Addressed in S16: invalid `--add-exports org.graalvm.sdk/...` warning from `gdx-svmhelper` metadata.

## Current Status
- `./gradlew clean build --warning-mode all --console=plain` is warning-free for active Java/Kotlin compilation.
- Native compile remains successful; remaining LWJGL experimental metadata warnings are explicitly accepted.

## Next Wave Planning (requested)
### Feasibility and complexity
1. Dependency version catalog (`gradle/libs.versions.toml`)
   - Feasibility: High
   - Complexity: Medium
   - Notes: no existing catalog today; dependencies are spread across Groovy + Kotlin DSL build scripts and `gradle.properties`.
2. JUnit 5 migration
   - Feasibility: High (staged)
   - Complexity: High
   - Notes: test suite is heavily JUnit 4 today (72 test files total; 13 files use JUnit4-only constructs like `@Rule`/`TemporaryFolder`, `@Test(expected=...)`, `@BeforeClass`/`@AfterClass`).
3. Gradle wrapper major upgrade (to latest available line)
   - Feasibility: Medium
   - Complexity: High
   - Notes: wrapper is currently `8.14.3`; latest upstream line is `9.x`, but major upgrade risk is mainly plugin compatibility (Kotlin plugin and runtime/native-image plugins). This is explicitly optional and can be deferred by one major version cycle.

### Recommended order
1. N1: Introduce dependency catalog with no behavior change first.
2. N2: Migrate tests to JUnit 5 in two stages:
   - Stage A: enable JUnit Platform with Jupiter + Vintage so existing JUnit4 tests still run.
   - Stage B: migrate tests off JUnit4 APIs (`TemporaryFolder` -> `@TempDir`, `@Test(expected=...)` -> `assertThrows`, class lifecycle annotations), then drop Vintage + JUnit4 dependency.
3. N3: Optionally attempt Gradle major upgrade only after N1/N2 are stable, with a dedicated compatibility pass for Kotlin and build plugins.

### Next wave status
- [x] N1: Dependency catalog rollout is complete for active modules.
- [x] N2 Stage A: JUnit Platform + Jupiter + Vintage is enabled with tests green.

### Upcoming execution items (pending)
- [ ] N2 Stage B / N3: Migrate JUnit4-specific tests to pure JUnit5 and remove Vintage/JUnit4 dependencies.
- [ ] N4 (Optional): Run Gradle major-version upgrade spike (wrapper + plugin compatibility updates) and decide adopt/hold based on build + nativeCompile results.
