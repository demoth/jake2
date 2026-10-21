This package is responsible for internal command execution (like when you type into the console), cvar and alias support.

Current metadata support:

- commands may carry an optional description through `Cmd.AddCommand(...)`
- cvars declare their type, default, flags and optional description through
  `Cvar.register(name, type, defaultValue, flags, description)` on a registry instance

Cake uses that metadata to render richer console completion output.
