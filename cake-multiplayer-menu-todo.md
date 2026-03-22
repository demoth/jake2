# Cake Multiplayer Menu TODO

The implemented multiplayer and player-setup screen architecture is now documented in:

- `cake/core/src/main/kotlin/org/demoth/cake/stages/README.md`

This file is intentionally reduced to active follow-up items only.

## Open Follow-Ups

- Decide whether `rate` belongs in `Player Setup` or a later options/settings surface.

- Only add player preview support if it stays isolated from:
  - menu-state routing
  - player setup validation
  - asset catalog discovery
