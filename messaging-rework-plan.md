# Messaging rework plan

Contract and research: [messaging-rework.md](messaging-rework.md).
Work through phases in order; resolve local decisions in the affected phase.
Checkboxes track implementation and verification, not agreement alone.

## 1. Com contract (source attribution deferred after review)

- [x] Add `Print`, `Info`, `Debug`, `Warn`, and reporting-only `Error` entry points.
- [x] Carry PRINT / INFO / DEBUG / WARN / ERROR through the console sink.
- [x] Ensure a terminal newline by default; avoid duplicating an existing one.
- [x] Support `endLine = false` only on Print for exact fragments; preserve multiline text and blank lines.
- [x] Provide convenient Java/Kotlin overloads without ambiguous boolean/format arguments.
- Debug class attribution deferred after review; no source parameters or metadata plumbing in this slice.
- [x] Keep the existing developer gate, including the legacy formatter check.
- [x] Remove `debugContext` and `_debugContext`.
- [x] Verify boundaries, level preservation, and developer-off behavior in qcommon tests.

Decision during implementation:

- [x] Use `Debug(String)`. Revisit lazy construction if the migration exposes a costly diagnostic, and assess class attribution separately.
- [x] Deprecate every legacy Printf/Println/DPrintf/dprintln and Error(code, ...) overload with migration guidance.

Validation (2026-11-18): `:qcommon:test` — 555 tests, 1 skipped, no failures;
`:cake:core:test --tests org.demoth.cake.stages.console.ConsoleBufferTest` — 3 passed;
`:cake:cake-fullgame:compileTestKotlin` passed. Full build, GUI smoke tests, and native
build were not run. DEBUG temporarily uses the existing main text color until slice 5.

## 2. Reporting-only errors

- [x] Keep `Com.Error(message)` as the reporting API.
- [x] Use it at the server transition result boundary for failed map, load, and continuation operations.
- [x] Preserve recovery and the deprecated `Error(code, ...)` behavior.
- [x] Verify the reporting API and transition recovery with focused tests (`ComMessagingTest`, `HostSessionPreparationTest`).

Deferred: cause overload, developer-mode stack traces, and broader handler migration.

## 3. Redirect safety

- [x] Guarantee redirect cleanup after command execution and flush failures.
- [x] Preserve the command failure when cleanup also fails.
- [x] Reject overlapping captures and null arguments. Scope cleanup to the command call.
- [x] Prevent callback/transport logging from recursively entering the same capture.
- [x] Split oversized output within encoded packet limits, including framing overhead.
- [x] Preserve plain-text, capture-only behavior and exact fragment reconstruction.
- [x] Redact the password from rcon request logging.
- [x] Test command/flush failures, reentry, overlap, empty output, packet boundaries, and suppression of local sinks.

Validation: `./gradlew :qcommon:test :server:test` passed.

## 4. Call-site and legacy API migration

- [x] Classify existing Com calls by purpose: Print for user output, Info for sparse lifecycle events, Debug for diagnostics, Warn/Error for failures.
- [x] Migrate qcommon command output, including `help` and `cvarlist`.
- [x] Migrate server command output and lifecycle diagnostics, including `status`.
- [x] Assemble table rows before printing where practical; use `endLine = false` for remaining fragments.
- [x] Migrate Cake diagnostics and gameplay/center-print console echoes.
- [x] Preserve remote text chunk boundaries; do not add newlines per received rcon chunk.
- [x] Review game `dprintf` callers by purpose; use Com directly for game diagnostics.
- [x] Preserve GameImports player-directed delivery, HUD behavior, and recipient selection.
- [x] Retain `PRINT_LOW/MEDIUM/HIGH/CHAT`, wire values, and `msg` filtering; all local echoes use Print.
- [x] Remove `PRINTF`-style integer routing: migrate the command echo, then remove `Printf(int, ...)` and `PRINT_ALL/DEVELOPER/ALERT`.
- [x] Remove obsolete `Printf` / `Println` / `DPrintf` / `dprintln` wrappers after their callers migrate.
- [x] Replace `Vargs` uses in Com, CM, MainCommon, and Lib; delete the class and imports.
- [x] Check formatter conversions and add regression coverage for vector widths, precision, and literal percent handling.

Decision during implementation:

- [x] Use preformatted text and `String.format(Locale.ROOT, ...)` for the remaining numeric formats. Convert `%i` to `%d` and remove `PrintfFormat`.

### TRACE follow-up

- [x] Use TRACE for repeatable command, network, collision, and rendering diagnostics.
- [x] Remove the dedicated log-only cvars and the boolean Debug overload.
- [x] Defer TRACE message construction until it is enabled.
- [x] Enable TRACE through the typed `loglevel` cvar. The default is INFO.

## 5. Log threshold and console colors

- [x] Use the `loglevel` cvar for ERROR, WARN, INFO, DEBUG, and TRACE with INFO as the default.
- [x] Apply the threshold to new diagnostic messages before routing. Keep PRINT always on.
- [x] Keep developer features such as automation independent of log verbosity.
- [ ] Render Warn yellow, Error red, Debug and Trace muted/readable, and Info/Print in the main text color.
- [ ] Connect a level control in the console view to the existing cvar.
- [ ] Manually inspect colors and the console control.

## 6. SLF4J/logback terminal backend

- [ ] Replace Com's direct stdout write after redirect and console routing.
- [ ] Add the SLF4J facade at the shared boundary; include logback/configuration in affected application distributions.
- [ ] Preserve Print identity without adding a custom logging severity; caller source attribution remains deferred.
- [ ] Keep GUI/redirect delivery independent of terminal logger thresholds.
- [ ] Honor Com newline/fragments; avoid duplicate terminators and per-fragment line breaks.
- [ ] Add terminal colors/reset; keep sink text and redirect/network payloads free of renderer-added ANSI escapes.
- [ ] Preserve stdout/stderr routing unless a concrete change is agreed.
- [ ] Verify terminal filtering, colors, fragments, plain pipe/file output, and packaging.

Decisions during implementation:

- [ ] Choose Print's terminal mapping and fragment handling with minimal backend customization.
- [ ] Establish color activation for terminal, IDE, Gradle, Windows, packaged, and native launches; add an override only if needed.

## 7. Integrated checks and documentation

- [ ] Run focused qcommon/server/Cake checks as each phase changes behavior.
- [ ] Run `./gradlew build` after integration.
- [ ] Smoke-test singleplayer game/HUD echoes and player `msg` filtering.
- [ ] Smoke-test remote command output, long rcon replies, and output after failed commands.
- [ ] Smoke-test dedicated stdout and Cake console controls.
- [ ] Record untested launch environments and native-build limitations.
- [ ] Update the console guide and compatibility guide for actual supported behavior changes.
- [ ] Resolve relevant Com warning/logging TODOs only when implemented; retain remaining limitations.
- [ ] Record local decisions in existing guides and update this checklist.

## Deferred

- Engine-wide exception/recovery redesign and full legacy error-control migration.
- Logger-first diagnostic APIs and custom GUI/redirect appenders.
- Early startup replay, file logging, async logging, and nested/per-thread redirects.
- Separate gameplay-versus-command Print filtering.
- Repository-wide migration of non-Com logging and machine-readable tool output.

- Debug class attribution and source metadata (deferred during slice 1 review).
