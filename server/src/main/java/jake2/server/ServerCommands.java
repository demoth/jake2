package jake2.server;

import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Command;

import java.util.LinkedHashMap;
import java.util.Map;

/** Command registrations owned by a server/session, including replacements during map changes. */
final class ServerCommands implements AutoCloseable {
    private final Map<String, Cmd.cmd_function_t> previous = new LinkedHashMap<>();
    private final Map<String, Cmd.cmd_function_t> installed = new LinkedHashMap<>();

    void add(String name, Command command) {
        add(name, false, command);
    }

    void add(String name, boolean replace, Command command) {
        Cmd.cmd_function_t before = Cmd.FindCommand(name);
        Cmd.AddCommand(name, replace, command);
        Cmd.cmd_function_t after = Cmd.FindCommand(name);
        if (after != null && after.function == command) {
            if (!previous.containsKey(name)) previous.put(name, before);
            installed.put(name, after);
        }
    }

    @Override
    public void close() {
        installed.forEach((name, registration) -> {
            // A later owner may already have replaced our command.
            if (Cmd.FindCommand(name) == registration) {
                Cmd.RemoveCommand(name);
                Cmd.cmd_function_t original = previous.get(name);
                if (original != null)
                    Cmd.AddCommand(name, original.description, original.function);
            }
        });
        installed.clear();
        previous.clear();
    }
}
