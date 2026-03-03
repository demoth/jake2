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

## Notes from completed steps
- S4 native compile command that worked:
  - `JAVA_HOME=~/.sdkman/candidates/java/21.0.2-graalce GRAALVM_HOME=~/.sdkman/candidates/java/21.0.2-graalce ./gradlew :cake:lwjgl3:nativeCompile`
- S4 emitted Graal native-image warnings from upstream metadata (experimental options and invalid `--add-exports` from `gdx-svmhelper`), but produced executable successfully.
- S6 modernization pass 1 replaced direct task-name configuration (`compileJava`, `compileKotlin`, `compileTestKotlin`) with typed task configuration (`tasks.withType(...)`) in root build logic.
- S7 added `dependencyResolutionManagement` and `pluginManagement` repository blocks in `settings.gradle.kts`, and removed duplicated dependency repository declarations from `build.gradle`.
- S8 migrated root Kotlin plugin declaration from legacy `buildscript/dependencies/classpath` to modern `plugins` DSL (`apply false`) and kept build green.
