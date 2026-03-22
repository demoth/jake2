# Cake Downloads TODO

## Goal
- Make Cake usable on modded multiplayer servers that require custom maps and assets.
- Restore Quake II-compatible autodownload behavior without introducing Cake-specific protocol changes.
- Keep the first implementation compatible with vanilla Jake2 server support and future-friendly for R1Q2/Q2Pro-style HTTP extensions.

## Agreed Scope

### Phase 1
- Global download cache.
- Profile-local cvar switches.
- Vanilla UDP autodownload compatibility first.
- Strict filename validation.
- Download queue with temp file, resume, and rename.
- Refresh mounted content after successful download.

### Deferred
- HTTP download support.
- Cake-specific protocol extensions.
- Additional content categories beyond the vanilla set.
- Rich download UI beyond basic loading/progress feedback.

## Current Jake2 / Cake State
- Cake currently disables UDP download behavior in `cake/core/src/main/kotlin/org/demoth/cake/Cake.kt`:
  - `// no udp downloads anymore!!`
- Server-side UDP download support is still present:
  - `server/src/main/java/jake2/server/SV_MAIN.java`
  - `server/src/main/java/jake2/server/SV_USER.java`
- Shared network protocol pieces still exist:
  - `qcommon/src/main/java/jake2/qcommon/network/messages/client/StringCmdMessage.java`
  - `qcommon/src/main/java/jake2/qcommon/network/messages/server/DownloadMessage.java`
- Legacy Jake2 client still contains a full reference implementation:
  - `client/src/main/java/jake2/client/CL.java`
  - `client/src/main/java/jake2/client/CL_parse.java`
  - `client/src/main/java/jake2/client/Menu.java`

## Storage Decision

### Recommended: Global Download Cache
- Downloaded assets are shared content, not profile identity or preferences.
- Recommended shape:
  - `~/.cake/downloads/baseq2/...`
  - `~/.cake/downloads/xatrix/...`
  - `~/.cake/downloads/<mod>/...`

### Pros
- No duplicate downloads across profiles.
- Better fit for cache semantics.
- Easier testing across multiple profiles.
- Better disk usage.

### Cons
- Requires strict validation and namespacing.
- Less isolated than profile-local storage.
- Cleanup is not automatically tied to a single profile.

### Rejected For Phase 1: Per-Profile Download Storage
- Pros:
  - strong isolation
  - simpler cleanup per profile
- Cons:
  - duplicate downloads
  - unnecessary disk use
  - worse UX for multi-profile testing

## Config / Cvar Policy
- Download behavior is controlled by profile-local archived cvars.
- Downloaded content itself lives in the global cache.

### Phase 1 Cvars
- `allow_download`
- `allow_download_maps`
- `allow_download_models`
- `allow_download_players`
- `allow_download_sounds`

### Deferred Cvars
- `cl_http_downloads`
- `cl_nodownload_list`
- non-vanilla categories like:
  - `allow_download_pics`
  - `allow_download_textures`
  - `allow_download_textures_24bit`

## Reference Review

### Vanilla / Legacy Jake2
- Uses one-file-at-a-time UDP downloads.
- Supports temp file + resume + rename.
- Uses the classic `allow_download*` cvar set.
- Client references:
  - `client/src/main/java/jake2/client/CL.java`
  - `client/src/main/java/jake2/client/CL_parse.java`
- UI reference:
  - `client/src/main/java/jake2/client/Menu.java`

### Yamagi
- Keeps the classic `allow_download*` cvars.
- Adds:
  - `cl_http_downloads`
  - `cl_nodownload_list`
- Applies stricter path filtering before download.
- Uses HTTP when server support exists, with UDP fallback.
- References:
  - `../quake/quake2/yquake2/src/client/cl_download.c`
  - `../quake/quake2/yquake2/src/client/menu/menu.c`

### Q2Pro
- Uses a queue-based download manager.
- Supports HTTP plus UDP fallback.
- Adds extra categories beyond vanilla:
  - `allow_download_pics`
  - `allow_download_textures`
- Supports `allow_download == -1` as stronger disable semantics.
- References:
  - `../quake/quake2/q2pro/src/client/download.c`
  - `../quake/quake2/q2pro/src/client/http.c`
  - `../quake/quake2/q2pro/src/client/ui/q2pro.menu`

### KMQuake2
- Similar to Yamagi/Q2Pro.
- Adds more categories including `allow_download_textures_24bit`.
- Supports HTTP server advertisement.
- References:
  - `../quake/quake2/KMQuake2/client/cl_download.c`
  - `../quake/quake2/KMQuake2/client/cl_http.c`
  - `../quake/quake2/KMQuake2/ui/ui_mp_download.c`
  - `../quake/quake2/KMQuake2/server/sv_user.c`

### Quetoo
- Treats HTTP as the preferred path and UDP as fallback.
- Adds downloaded `.pk3` files to the search path immediately after completion.
- References:
  - `../quake/quake2/quetoo/src/client/cl_media.c`
  - `../quake/quake2/quetoo/src/client/cl_parse.c`
  - `../quake/quake2/quetoo/src/server/sv_client.c`

## Compatibility Constraint
- Java having an embedded HTTP client is not the hard part.
- The missing part in this repo is HTTP discovery/integration:
  - Jake2 server currently has UDP download support.
  - Jake2 server does not currently advertise an HTTP download URL like R1Q2/Q2Pro/Yamagi/KMQuake2.
- Because of that:
  - HTTP-first is not actually the smallest compatible implementation for public servers.
  - UDP-first is the smallest path that works with the current repo and protocol.

## Phase 1 Design

### 1. Download Manager
- Add a Cake-side download manager responsible for:
  - queuing missing files during precache
  - validating paths
  - opening/resuming temp files
  - writing incoming chunks
  - renaming completed files
  - reporting progress
  - triggering mount/content refresh

### 2. Transport
- Reuse the existing Quake II UDP download protocol:
  - client command: `download <path> [offset]`
  - server message: `svc_download`
  - continuation command after partial chunk: `nextdl`
- Design the manager so transport can be abstracted later:
  - `UdpDownloadTransport` first
  - `HttpDownloadTransport` later

### 3. Download Queue
- Queue missing precache assets instead of ad-hoc single-call flow.
- Phase 1 still downloads one file at a time on the wire.
- The queue exists so missing resources can be discovered first and then processed consistently.

### 4. Temp / Resume / Rename
- Match the proven legacy pattern:
  - download to `<name>.tmp`
  - if temp exists, resume from its current size
  - rename to final path on successful completion

### 5. Strict Filename Validation
- Reject:
  - `..`
  - absolute paths
  - drive letters / `:`
  - backslashes
  - leading `.` or `/`
  - dangerous native-library extensions like `.dll`, `.so`, `.dylib`
- Only allow expected content locations such as:
  - `maps/`
  - `models/`
  - `players/`
  - `sound/`

### 6. Category Gating
- Before enqueuing a file, check:
  - `allow_download`
  - matching category cvar derived from path prefix

### 7. Mounted Content Refresh
- After successful download, newly written files must become visible to the asset/VFS layer.
- This is especially important for:
  - maps
  - models
  - player assets
  - sounds
- The exact hook point still needs implementation design, but refresh is part of Phase 1, not an optional extra.

## Future HTTP Plan
- Do not invent Cake-specific protocol extensions unless R1Q2/Q2Pro-style extensions are explicitly rejected.
- Preferred future order:
  1. implement R1Q2/Q2Pro-style download URL discovery if compatible
  2. add `cl_http_downloads`
  3. prefer HTTP when advertised
  4. fall back to UDP

## Open Questions
- What is the cleanest refresh point for newly downloaded content in Cake:
  - remount/reindex VFS
  - targeted resolver refresh
  - explicit reload of stage-local caches
- Should `.pak` / `.pk3` downloads be allowed in phase 1, or should phase 1 be limited to loose files only?
- Where should minimal progress be surfaced first:
  - loading/precache screen
  - console
  - both

## Immediate Next Steps
1. Register the phase-1 download cvars in Cake startup.
2. Decide and implement the global download root helper.
3. Extract a Cake-side download manager skeleton with queue and validation.
4. Reuse the existing UDP protocol messages in Cake networking.
5. Integrate downloads into the current precache flow.
6. Add VFS/content refresh after completion.
