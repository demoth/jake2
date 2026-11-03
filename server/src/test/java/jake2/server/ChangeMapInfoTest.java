package jake2.server;

import jake2.qcommon.Globals;
import jake2.qcommon.MainCommon;
import jake2.qcommon.ServerStates;
import jake2.qcommon.exec.Cvar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static jake2.qcommon.Defines.CS_MODELS;
import static org.junit.jupiter.api.Assertions.*;

class ChangeMapInfoTest {
    private static final List<String> STILL_EXTENSIONS = List.of("pcx", "tga", "png", "jpg", "jpeg");

    @Test
    void stillMapsKeepPathAndTransitionSyntaxWhileSelectingPicturePresentation() {
        for (String extension : STILL_EXTENSIONS) {
            for (String suffix : List.of(extension, extension.toUpperCase(Locale.ROOT))) {
                ChangeMapInfo info = new ChangeMapInfo("*pics/Ending." + suffix + "$entry+base1", false, false);
                assertEquals(ServerStates.SS_PIC, info.state);
                assertEquals("pics/Ending." + suffix, info.mapName);
                assertEquals("entry", info.spawnPoint);
                assertEquals("base1", info.nextServer);
                assertTrue(info.newUnit);
            }
        }
        assertEquals(ServerStates.SS_CINEMATIC, new ChangeMapInfo("INTRO.CIN+base1", false, false).state);
        assertEquals(ServerStates.SS_DEMO, new ChangeMapInfo("DEMO.DM2", true, false).state);
        assertThrows(IllegalStateException.class, () -> new ChangeMapInfo("DEMO.DM2", false, false));
        assertEquals(ServerStates.SS_GAME, new ChangeMapInfo("base1", false, false).state);
        assertEquals(ServerStates.SS_GAME, new ChangeMapInfo("ending.png.bsp", false, false).state);
    }

    @Test
    void stillGamesAvoidBspLoadingAndKeepTerminalAndCooperativeContinuationRules() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            MainCommon.registerCvars(false);
            try (ServerHost host = new ServerHost(TransportServerNetwork.standalone())) {
                for (String extension : STILL_EXTENSIONS) {
                    ChangeMapInfo info = new ChangeMapInfo("ending." + extension + "+base1", false, false);
                    GameImportsImpl game = new GameImportsImpl(host, info);
                    // No BSP is needed to allocate picture games, just as with legacy PCX endings.
                    assertNull(game.configstrings[CS_MODELS + 1]);
                    game.state = info.state;
                    int spawnCount = game.spawncount;
                    assertNull(game.takeNextMap(), "Single-player picture remains a terminal end screen");
                    assertEquals(spawnCount, game.spawncount);
                    game.findCvar("coop", Boolean.class).forceSet(true);
                    assertEquals("base1", game.takeNextMap());
                    assertNotEquals(spawnCount, game.spawncount);
                    assertNull(game.takeNextMap(), "Continuation is consumed only once");
                }
            }
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }
}
