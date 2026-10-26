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
    void gameWritesValidateSharedTypesAndRetainLatchSemantics() {
        Cvar cvars = Cvar.getInstance();
        var previousState = Globals.server_state;
        cvars.clear();
        try {
            cvars.setResult("maxclients", "3.5");
            cvars.setResult("timeout", "0.5");
            MainCommon.registerCvars(false);
            SV_MAIN server = new SV_MAIN();
            GameImportsImpl imports = new GameImportsImpl(server, new ChangeMapInfo("test.pcx", false, false));
            cvar_t<Integer> maxclients = cvars.find("maxclients", Integer.class);
            cvar_t<Boolean> deathmatch = cvars.find("deathmatch", Boolean.class);
            assertEquals(1, maxclients.getTypedValue());
            assertEquals(0.5f, server.timeout.getTypedValue());
            assertSame(deathmatch, imports.findCvar("deathmatch", Boolean.class));

            Globals.server_state = ServerStates.SS_GAME;
            imports.findCvar("deathmatch", Boolean.class).set(true);
            assertFalse(deathmatch.getTypedValue());
            assertEquals(Boolean.TRUE, deathmatch.getPendingValue());
            cvars.forceSetResult("deathmatch", "2");
            assertFalse(deathmatch.getTypedValue());
            assertEquals(Boolean.TRUE, deathmatch.getPendingValue());
            cvars.updateLatchedVars();
            assertTrue(deathmatch.getTypedValue());
            assertEquals("1", deathmatch.getRawString());
            imports.findCvar("maxclients", Integer.class).forceSet(0);
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
            cvars.setResult("skill", "2");
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
            assertSame(first.findCvar("deathmatch", Boolean.class), next.findCvar("deathmatch", Boolean.class));
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
            cvars.setResult("skill", "4");
            cvars.setResult("sv_gravity", "600.5");
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
            cvar_t<Integer> otherSkill = other.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 1, CVAR_LATCH, null);
            ServerSaveJsonStore.forWriteDir(temp.toString()).writeLatchedCvars("current", List.of(
                    new ServerSaveJsonStore.LatchedCvarSnapshot("skill", "2"),
                    new ServerSaveJsonStore.LatchedCvarSnapshot("deathmatch", "1")));

            GameImportsImpl loaded = new GameImportsImpl(server, new ChangeMapInfo("saved.pcx", false, true), other);
            cvar_t<Integer> loadedSkill = loaded.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, CVAR_LATCH, null);
            assertEquals(2, loadedSkill.getTypedValue());
            assertNull(loadedSkill.getRawPendingString());
            assertEquals(1, otherSkill.getTypedValue());
            assertFalse(cvars.isRegistered("skill"));
            assertSame(cvars.find("deathmatch"), loaded.findCvar("deathmatch", Boolean.class));
            assertEquals("1", cvars.find("deathmatch", Boolean.class).getRawString());
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
            cvars.setResult("skill", "2");
            cvars.setResult("maxspectators", "8");
            cvars.setResult("spectator_password", "secret");
            MainCommon.registerCvars(false);
            SV_MAIN server = new SV_MAIN();
            // A picture transition constructs real game imports without loading a BSP or opening sockets.
            GameImportsImpl imports = new GameImportsImpl(server, new ChangeMapInfo("test.pcx", false, false));

            cvar_t<Integer> skill = imports.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, CVAR_LATCH, null);
            cvar_t<Integer> spectators = imports.registerCvar("maxspectators", CvarTypes.INTEGER, 4, CVAR_SERVERINFO, null);
            cvar_t<String> password = imports.findCvar("spectator_password", String.class);
            assertEquals("2", skill.getRawString());
            assertEquals("8", spectators.getRawString());
            assertEquals("secret", password.getRawString());
            assertFalse(cvars.isRegistered("skill"));
            assertFalse(cvars.isRegistered("maxspectators"));
            assertSame(cvars.find("spectator_password", String.class), password);
            assertSame(skill, imports.findCvar("skill", Integer.class));

            GameImportsImpl other = new GameImportsImpl(server, new ChangeMapInfo("other.pcx", false, false));
            cvar_t<Integer> otherSkill = other.registerCvar("skill", new CvarTypes.ChoiceType<>(List.of(0, 1, 2, 3)), 0, CVAR_LATCH, null);
            cvar_t<Integer> otherSpectators = other.registerCvar("maxspectators", CvarTypes.INTEGER, 4, CVAR_SERVERINFO, null);
            assertNotSame(skill, otherSkill);
            assertEquals("0", otherSkill.getRawString()); // Startup input was consumed by the first owner.
            assertEquals("4", otherSpectators.getRawString());

            assertTrue(spectators.set(6).getAccepted());
            assertEquals("6", spectators.getRawString());
            assertEquals("4", otherSpectators.getRawString());
            assertTrue(skill.forceSet(1).getAccepted());
            assertEquals("1", skill.getRawString());
            assertEquals("0", otherSkill.getRawString());
            otherSpectators.set(3);
            assertEquals("3", otherSpectators.getRawString());
            assertEquals("6", spectators.getRawString());

            cvar_t<String> shared = imports.findCvar("hostname", String.class);
            assertSame(cvars.find("hostname"), shared);
            assertSame(shared, other.findCvar("hostname", String.class));
            shared.set("shared-host");
            assertEquals("shared-host", cvars.find("hostname", String.class).getTypedValue());
            other.findCvar("hostname", String.class).forceSet("other-host");
            assertEquals("other-host", shared.getRawString());

            var custom = imports.registerCvar("game_custom", CvarTypes.INTEGER, 0, 0, null);
            custom.set(7);
            assertEquals(7, custom.getTypedValue());
            assertEquals(0, other.registerCvar("game_custom", CvarTypes.INTEGER, 0, 0, null).getTypedValue());

            assertNotNull(cvars.find("mapname"));
            cvars.find("mapname", String.class).forceSet("test-map");
            assertTrue(cvars.Serverinfo().contains("\\mapname\\test-map"));
        } finally {
            cvars.clear();
        }
    }
}
