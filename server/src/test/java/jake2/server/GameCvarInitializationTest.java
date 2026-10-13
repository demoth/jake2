package jake2.server;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
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
    void legacyGameWritesValidateSharedTypesAndRetainLatchSemantics() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            cvars.Set("maxclients", "3.5");
            cvars.Set("timeout", "0.5");
            MainCommon.registerCvars(false);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl imports = new GameImportsImpl(server, new ChangeMapInfo("test.pcx", false, false));
            cvar_t<Integer> maxclients = cvars.find("maxclients", Integer.class);
            cvar_t<Boolean> deathmatch = cvars.find("deathmatch", Boolean.class);
            assertEquals(1, maxclients.getTypedValue());
            assertEquals(0.5f, SV_MAIN.timeout.getTypedValue());
            assertSame(deathmatch, imports.cvar("deathmatch", "0", CVAR_LATCH));

            Globals.server_state = ServerStates.SS_GAME;
            imports.cvar_set("deathmatch", "true");
            assertFalse(deathmatch.getTypedValue());
            assertEquals(Boolean.TRUE, deathmatch.getPendingValue());
            imports.cvar_forceset("deathmatch", "2");
            assertFalse(deathmatch.getTypedValue());
            assertEquals(Boolean.TRUE, deathmatch.getPendingValue());
            cvars.updateLatchedVars();
            assertTrue(deathmatch.getTypedValue());
            assertEquals("1", deathmatch.getRawString());
            imports.cvar_forceset("maxclients", "0");
            assertEquals(1, maxclients.getTypedValue());
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void successorCopiesSettingsAndAppliesPendingWithoutChangingTheSource() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            cvars.Set("skill", "2");
            MainCommon.registerCvars(false);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl first = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, false));
            var skill = first.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, CVAR_LATCH, null);
            var gravity = first.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null);
            gravity.set(600f);
            Globals.server_state = ServerStates.SS_GAME;
            skill.set(3);

            GameImportsImpl next = new GameImportsImpl(server, new ChangeMapInfo("next.pcx", false, false), first);
            var nextSkill = next.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, CVAR_LATCH, null);
            var nextGravity = next.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null);
            assertNotSame(skill, nextSkill);
            assertEquals(3, nextSkill.getTypedValue());
            assertNull(nextSkill.getPendingValue());
            assertEquals(600f, nextGravity.getTypedValue());
            assertEquals(2, skill.getTypedValue());
            assertEquals(3, skill.getPendingValue());
            nextSkill.forceSet(1);
            nextGravity.set(400f);
            assertEquals(2, skill.getTypedValue());
            assertEquals(600f, gravity.getTypedValue());
            assertSame(first.cvar("deathmatch", "0", CVAR_LATCH), next.cvar("deathmatch", "0", CVAR_LATCH));
        } finally {
            Globals.server_state = previousState;
            cvars.clear();
        }
    }

    @Test
    void typedDeclarationsRejectInvalidEarlyInputAndRemainIndependent() {
        Cvar cvars = Cvar.getInstance();
        cvars.clear();
        try {
            cvars.Set("skill", "4");
            cvars.Set("sv_gravity", "600.5");
            MainCommon.registerCvars(false);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl first = new GameImportsImpl(server, new ChangeMapInfo("first.pcx", false, false));
            GameImportsImpl second = new GameImportsImpl(server, new ChangeMapInfo("second.pcx", false, false));
            var skillType = new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3));
            var firstSkill = first.registerCvar("skill", skillType, 0, CVAR_LATCH, null);
            var firstGravity = first.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null);
            var secondSkill = second.registerCvar("skill", skillType, 0, CVAR_LATCH, null);
            var secondGravity = second.registerCvar("sv_gravity", CvarTypes.FLOAT, 800f, 0, null);

            assertEquals(0, firstSkill.getTypedValue()); // Rejected choice falls back to the declaration default.
            assertEquals(600.5f, firstGravity.getTypedValue());
            assertEquals(800f, secondGravity.getTypedValue()); // Early input is consumed only once.
            assertNotSame(firstSkill, secondSkill);
            assertNotSame(firstGravity, secondGravity);
            assertSame(firstSkill, first.findCvar("skill", Integer.class));
            assertSame(firstGravity, first.findCvar("sv_gravity", Float.class));
            firstSkill.forceSet(2);
            firstGravity.set(-200f);
            assertEquals(0, secondSkill.getTypedValue());
            assertEquals(800f, secondGravity.getTypedValue());
            assertFalse(cvars.isRegistered("skill"));
            assertFalse(cvars.isRegistered("sv_gravity"));
        } finally {
            cvars.clear();
        }
    }

    @Test
    void explicitLocalRegistrationShadowsGlobalWithoutChangingIt() {
        Cvar cvars = Cvar.getInstance();
        cvars.clear();
        try {
            MainCommon.registerCvars(false);
            var globalSetting = cvars.register("test_setting", CvarTypes.STRING, "client-secret", CVAR_USERINFO | CVAR_ARCHIVE);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl imports = new GameImportsImpl(server, new ChangeMapInfo("test.pcx", false, false));
            assertSame(globalSetting, imports.findCvar("test_setting", String.class));
            var localSetting = imports.registerCvar("test_setting", CvarTypes.STRING, "", CVAR_USERINFO, null);
            assertNotSame(globalSetting, localSetting);
            assertEquals("", localSetting.getTypedValue());
            assertSame(localSetting, imports.findCvar("test_setting", String.class));
            localSetting.set("game-secret");
            assertEquals("client-secret", globalSetting.getTypedValue());
            assertEquals("game-secret", localSetting.getTypedValue());
            assertEquals(CVAR_USERINFO | CVAR_ARCHIVE, globalSetting.flags);
            assertEquals(CVAR_USERINFO, localSetting.flags);
            assertSame(cvars.find("hostname", String.class), imports.findCvar("hostname", String.class));
            assertThrows(IllegalArgumentException.class, () -> imports.findCvar("test_setting", Boolean.class));
        } finally {
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
            assertSame(cvars.find("spectator_password", String.class), password);
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
