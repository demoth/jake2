package jake2.qcommon.network;

import jake2.qcommon.Defines;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;

/** Network configuration declarations; registering settings never creates transport resources. */
public final class NetworkSettings {
    private NetworkSettings() {}

    /**
     * Declare protected network settings after startup config has supplied early values.
     */
    public static void registerCvars() {
        Cvar cvars = Cvar.getInstance();
        cvars.register("port", new CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_SERVER, Defines.CVAR_NOSET, "Server UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent");
        cvars.register("ip", CvarTypes.STRING, "localhost", Defines.CVAR_NOSET, "Local bind address; empty or localhost binds all interfaces");
        cvars.register("clientport", new CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_CLIENT, Defines.CVAR_NOSET, "Client UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent");
    }

}
