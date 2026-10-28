package jake2.server;

import jake2.qcommon.Globals;
import jake2.qcommon.MainCommon;
import jake2.qcommon.ServerStates;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.pmove_t;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.server.save.ServerSaveJsonStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static jake2.qcommon.Defines.CS_AIRACCEL;
import static jake2.qcommon.Defines.PM_FREEZE;
import static jake2.qcommon.exec.CvarSetResult.Status.QUEUED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AirAccelerationTest {
    @Test
    void onlyDeathmatchEnablesAirAccelerationAndEachInstanceKeepsItsSetting() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            MainCommon.registerCvars(false);
            ServerHost server = new ServerHost(TransportServerNetwork.standalone());
            var acceleration = cvars.find("sv_airaccelerate", Float.class);
            var deathmatch = cvars.find("deathmatch", Boolean.class);
            acceleration.forceSet(2.5f);
            GameImportsImpl singlePlayer = new GameImportsImpl(server, new ChangeMapInfo("single.pcx", false, false));
            deathmatch.forceSet(true);
            GameImportsImpl firstDeathmatch = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, false));
            acceleration.forceSet(7f);
            GameImportsImpl secondDeathmatch = new GameImportsImpl(server, new ChangeMapInfo("second.pcx", false, false));

            // Reuse one request to detect a parameter leaking between game instances.
            pmove_t movement = frozenMovement();
            assertMovementSetting(secondDeathmatch, movement, 7f);
            assertMovementSetting(singlePlayer, movement, 0f);
            assertMovementSetting(firstDeathmatch, movement, 2.5f);
            assertMovementSetting(secondDeathmatch, movement, 7f);
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void pendingChangesApplyToNewGamesWithoutChangingExistingGames() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            MainCommon.registerCvars(false);
            ServerHost server = new ServerHost(TransportServerNetwork.standalone());
            cvars.find("deathmatch", Boolean.class).forceSet(true);
            var acceleration = cvars.find("sv_airaccelerate", Float.class);
            acceleration.forceSet(1f);
            GameImportsImpl current = new GameImportsImpl(server, new ChangeMapInfo("current.pcx", false, false));
            Globals.server_state = ServerStates.SS_GAME;
            assertEquals(QUEUED, acceleration.set(4f).getStatus());
            GameImportsImpl beforeApply = new GameImportsImpl(server, new ChangeMapInfo("before.pcx", false, false));
            pmove_t movement = frozenMovement();
            assertMovementSetting(current, movement, 1f);
            assertMovementSetting(beforeApply, movement, 1f);

            cvars.updateLatchedVars();
            GameImportsImpl afterApply = new GameImportsImpl(server, new ChangeMapInfo("after.pcx", false, false));
            assertMovementSetting(afterApply, movement, 4f);
            assertMovementSetting(current, movement, 1f);
            assertMovementSetting(beforeApply, movement, 1f);
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void loadingCapturesRestoredSettingsBeforePublishingMovementConfiguration(@TempDir Path temp) throws Exception {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        Path previousRoot = EngineWriteRoot.path();
        cvars.clear();
        EngineWriteRoot.setRoot(temp);
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            MainCommon.registerCvars(false);
            ServerHost server = new ServerHost(TransportServerNetwork.standalone());
            ServerSaveJsonStore.forWriteDir(temp.toString()).writeLatchedCvars("current", List.of(
                    new ServerSaveJsonStore.LatchedCvarSnapshot("deathmatch", "1"),
                    new ServerSaveJsonStore.LatchedCvarSnapshot("sv_airaccelerate", "3.5")));
            GameImportsImpl loaded = new GameImportsImpl(server, new ChangeMapInfo("saved.pcx", false, true));
            assertMovementSetting(loaded, frozenMovement(), 3.5f);
        } finally {
            EngineWriteRoot.setRoot(previousRoot);
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void savesCaptureTheOwningGamesConfiguredValueRatherThanTheLatestGlobalValue(@TempDir Path temp) throws Exception {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        Path previousRoot = EngineWriteRoot.path();
        cvars.clear();
        EngineWriteRoot.setRoot(temp);
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            MainCommon.registerCvars(false);
            ServerHost server = new ServerHost(TransportServerNetwork.standalone());
            var deathmatch = cvars.find("deathmatch", Boolean.class);
            var acceleration = cvars.find("sv_airaccelerate", Float.class);
            deathmatch.forceSet(true);
            acceleration.forceSet(1f);
            GameImportsImpl first = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, false));
            acceleration.forceSet(7f);
            GameImportsImpl second = new GameImportsImpl(server, new ChangeMapInfo("second.pcx", false, false));
            var store = ServerSaveJsonStore.forWriteDir(temp.toString());
            first.writeLatchedCvars(store);
            GameImportsImpl loadedFirst = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, true));
            assertEquals(7f, acceleration.getTypedValue());
            assertEquals(1f, loadedFirst.findCvar("sv_airaccelerate", Float.class).getTypedValue());
            assertMovementSetting(loadedFirst, frozenMovement(), 1f);
            assertMovementSetting(second, frozenMovement(), 7f);

            deathmatch.forceSet(false);
            acceleration.forceSet(5f);
            GameImportsImpl singlePlayer = new GameImportsImpl(server, new ChangeMapInfo("single.pcx", false, false));
            acceleration.forceSet(9f);
            singlePlayer.writeLatchedCvars(store);
            GameImportsImpl loadedSinglePlayer = new GameImportsImpl(server, new ChangeMapInfo("single.pcx", false, true));
            assertEquals(9f, acceleration.getTypedValue());
            assertEquals(5f, loadedSinglePlayer.findCvar("sv_airaccelerate", Float.class).getTypedValue());
            assertMovementSetting(loadedSinglePlayer, frozenMovement(), 0f);
            assertMovementSetting(singlePlayer, frozenMovement(), 0f);
        } finally {
            EngineWriteRoot.setRoot(previousRoot);
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    private static pmove_t frozenMovement() {
        pmove_t movement = new pmove_t();
        movement.s.pm_type = PM_FREEZE;
        return movement;
    }

    private static void assertMovementSetting(GameImportsImpl game, pmove_t movement, float expected) {
        game.Pmove(movement);
        assertEquals(expected, movement.airAccelerate);
        assertEquals(Float.toString(expected), game.configstrings[CS_AIRACCEL]);
    }
}
