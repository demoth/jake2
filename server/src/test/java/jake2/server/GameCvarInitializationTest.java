package jake2.server;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.Globals;
import jake2.qcommon.MainCommon;
import jake2.qcommon.ServerStates;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.server.save.ServerSaveJsonStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static jake2.qcommon.Defines.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks early config consumption, game-instance isolation and shared engine entries without map assets. */
class GameCvarInitializationTest {
    @Test
    void successorCopiesSettingsAndAppliesPendingWithoutChangingTheSource() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            cvars.Set("skill", "2");
            MainCommon.registerCvars(false);
            cvars.Get("maxclients", "1", CVAR_SERVERINFO | CVAR_LATCH);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl first = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, false));
            cvar_t skill = first.cvar("skill", "0", CVAR_LATCH);
            cvar_t gravity = first.cvar("sv_gravity", "800", 0);
            first.cvar_set("sv_gravity", "600");
            Globals.server_state = ServerStates.SS_GAME;
            first.cvar_set("skill", "3");

            GameImportsImpl next = new GameImportsImpl(server, new ChangeMapInfo("next.pcx", false, false), first);
            cvar_t nextSkill = next.cvar("skill", "0", CVAR_LATCH);
            cvar_t nextGravity = next.cvar("sv_gravity", "800", 0);
            assertNotSame(skill, nextSkill);
            assertEquals(3f, nextSkill.value);
            assertNull(nextSkill.latched_string);
            assertEquals(600f, nextGravity.value);
            assertEquals(2f, skill.value);
            assertEquals("3", skill.latched_string);
            next.cvar_forceset("skill", "1");
            next.cvar_set("sv_gravity", "400");
            assertEquals(2f, skill.value);
            assertEquals(600f, gravity.value);
            assertSame(first.cvar("deathmatch", "0", CVAR_LATCH), next.cvar("deathmatch", "0", CVAR_LATCH));
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void savedInputsOverrideTheSourceAndInitializeLocalDeclarations(@TempDir Path temp) throws Exception {
        Cvar cvars = Cvar.getInstance();
        Path previousRoot = EngineWriteRoot.path();
        cvars.clear();
        EngineWriteRoot.setRoot(temp);
        try {
            MainCommon.registerCvars(false);
            cvars.Get("maxclients", "1", CVAR_SERVERINFO | CVAR_LATCH);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl other = new GameImportsImpl(server, new ChangeMapInfo("other.pcx", false, false));
            cvar_t otherSkill = other.cvar("skill", "1", CVAR_LATCH);
            ServerSaveJsonStore.forWriteDir(temp.toString()).writeLatchedCvars("current", List.of(
                    new ServerSaveJsonStore.LatchedCvarSnapshot("skill", "2"),
                    new ServerSaveJsonStore.LatchedCvarSnapshot("deathmatch", "1")));

            GameImportsImpl loaded = new GameImportsImpl(server, new ChangeMapInfo("saved.pcx", false, true), other);
            cvar_t loadedSkill = loaded.cvar("skill", "0", CVAR_LATCH);
            assertEquals(2f, loadedSkill.value);
            assertNull(loadedSkill.latched_string);
            assertEquals(1f, otherSkill.value);
            assertFalse(cvars.isRegistered("skill"));
            assertSame(cvars.FindVar("deathmatch"), loaded.cvar("deathmatch", "0", CVAR_LATCH));
            assertEquals("1", cvars.VariableString("deathmatch"));
        } finally {
            EngineWriteRoot.setRoot(previousRoot);
            cvars.clear();
        }
    }

    @Test
    void gameDeclarationsConsumeEarlyInputsWithoutSharingTheirLiveState() {
        Cvar cvars = Cvar.getInstance();
        cvars.clear();
        try {
            cvars.Set("skill", "2");
            cvars.Set("maxspectators", "8");
            cvars.Set("spectator_password", "secret");
            MainCommon.registerCvars(false);
            cvars.Get("maxclients", "1", CVAR_SERVERINFO | CVAR_LATCH);
            SV_MAIN server = new SV_MAIN();
            // A picture transition constructs real game imports without loading a BSP or opening sockets.
            GameImportsImpl imports = new GameImportsImpl(server, new ChangeMapInfo("test.pcx", false, false));

            cvar_t skill = imports.cvar("skill", "0", CVAR_LATCH);
            cvar_t spectators = imports.cvar("maxspectators", "4", CVAR_SERVERINFO);
            cvar_t password = imports.cvar("spectator_password", "", CVAR_USERINFO);
            assertEquals("2", skill.string);
            assertEquals("8", spectators.string);
            assertEquals("secret", password.string);
            assertFalse(cvars.isRegistered("skill"));
            assertFalse(cvars.isRegistered("maxspectators"));
            assertFalse(cvars.isRegistered("spectator_password"));
            assertSame(skill, imports.cvar("skill", "0", CVAR_LATCH));

            GameImportsImpl other = new GameImportsImpl(server, new ChangeMapInfo("other.pcx", false, false));
            cvar_t otherSkill = other.cvar("skill", "0", CVAR_LATCH);
            cvar_t otherSpectators = other.cvar("maxspectators", "4", CVAR_SERVERINFO);
            assertNotSame(skill, otherSkill);
            assertEquals("0", otherSkill.string); // Startup input was consumed by the first owner.
            assertEquals("4", otherSpectators.string);

            assertSame(spectators, imports.cvar_set("maxspectators", "6"));
            assertEquals("6", spectators.string);
            assertEquals("4", otherSpectators.string);
            assertSame(skill, imports.cvar_forceset("skill", "1"));
            assertEquals("1", skill.string);
            assertEquals("0", otherSkill.string);
            other.cvar_set("maxspectators", "3");
            assertEquals("3", otherSpectators.string);
            assertEquals("6", spectators.string);

            cvar_t shared = imports.cvar("hostname", "ignored", CVAR_SERVERINFO);
            assertSame(cvars.FindVar("hostname"), shared);
            assertSame(shared, other.cvar("hostname", "ignored", CVAR_SERVERINFO));
            assertSame(shared, imports.cvar_set("hostname", "shared-host"));
            assertEquals("shared-host", cvars.VariableString("hostname"));
            assertSame(shared, other.cvar_forceset("hostname", "other-host"));
            assertEquals("other-host", shared.string);

            imports.cvar_set("game_custom", "7");
            assertEquals("7", imports.cvar("game_custom", "0", 0).string);
            assertEquals("0", other.cvar("game_custom", "0", 0).string);

            assertNotNull(cvars.FindVar("mapname"));
            cvars.FullSet("mapname", "test-map", CVAR_SERVERINFO | CVAR_NOSET);
            assertTrue(cvars.Serverinfo().contains("\\mapname\\test-map"));
        } finally {
            cvars.clear();
        }
    }
}
