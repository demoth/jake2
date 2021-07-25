package jake2.server;

import java.util.List;

public interface ServerUserCommand {
    void execute(List<String> args, GameImportsImpl gameImports, client_t client);
}
