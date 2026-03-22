This package is responsible for internal command execution (like when you type into the console), cvar and alias support.

Current metadata support:

- commands may carry an optional description through `Cmd.AddCommand(...)`
- cvars may carry descriptions and options metadata through `Cvar.Get(...)`

Cake uses that metadata to render richer console completion output.
