package jake2.qcommon;

import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.Netchan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonCvarsTest {
    private final Cvar cvars = Cvar.getInstance();

    @BeforeEach
    void setUp() {
        cvars.clear();
        MainCommon.registerCvars(false);
    }

    @AfterEach
    void tearDown() {
        cvars.clear();
    }

    @Test
    void timingUsesNativeValuesAndRejectsInvalidInputs() {
        assertEquals(20, MainCommon.adjustTime(20));
        cvars.Set("timescale", "0.5");
        assertEquals(10, MainCommon.adjustTime(20));
        cvars.Set("timescale", "NaN");
        assertEquals(10, MainCommon.adjustTime(20));
        cvars.Set("fixedtime", "7");
        cvars.Set("fixedtime", "1.5");
        assertEquals(7, MainCommon.adjustTime(20));
        cvars.Set("fixedtime", "0");
        cvars.Set("timescale", "0");
        assertEquals(20, MainCommon.adjustTime(20));
        assertEquals("", cvars.find("rcon_password", String.class).getTypedValue());
    }

    @Test
    void protectedNetworkDeclarationsConsumeStartupConfig() {
        Cvar.Init();
        Cbuf.AddText("set ip 127.0.0.1\nset port 28000\nset clientport -1\nset qport 65535\n");
        Cbuf.Execute();
        Netchan.Netchan_Init();
        assertEquals("127.0.0.1", cvars.find("ip", String.class).getTypedValue());
        assertEquals(28000, cvars.find("port", Integer.class).getTypedValue());
        assertEquals(-1, cvars.find("clientport", Integer.class).getTypedValue());
        assertEquals(65535, Netchan.qport.getTypedValue());
        cvars.Set("port", "29000");
        assertEquals(28000, cvars.find("port", Integer.class).getTypedValue());
        cvars.ForceSet("qport", "65536");
        assertEquals(65535, Netchan.qport.getTypedValue());
        Netchan.Netchan_Init();
        assertEquals(65535, Netchan.qport.getTypedValue());
        cvars.Set("sv_print_network", "true");
        assertTrue(Netchan.printNetwork.getTypedValue());
    }
}
