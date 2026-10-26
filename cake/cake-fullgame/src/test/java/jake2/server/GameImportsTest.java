package jake2.server;

import jake2.game.GameEntity;
import jake2.game.monsters.M_Gunner;
import jake2.qcommon.GameExports;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.MainCommon;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
import jake2.qcommon.Defines;
import jake2.qcommon.network.messages.server.ConfigStringMessage;
import jake2.qcommon.vfs.EngineVfs;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.qcommon.vfs.VfsConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GameImportsTest {

    @TempDir Path writeRoot;
    private Path previousWriteRoot;
    private SV_MAIN manager;
    private ServerStates previousServerState;

    @BeforeEach
    public void setup() {
        previousWriteRoot = EngineWriteRoot.path();
        previousServerState = Globals.server_state;
        Globals.server_state = ServerStates.SS_DEAD;
        Cvar.getInstance().clear();
        MainCommon.registerCvars(false);
        EngineVfs.configure(new VfsConfig(new File("src/test/resources").toPath(),
                "baseq2", null, true, false, false, List.of(), Set.of("pak")));
        EngineWriteRoot.setRoot(writeRoot);
    }

    @AfterEach
    public void cleanup() {
        if (manager != null) manager.close();
        Globals.server_state = previousServerState;
        EngineWriteRoot.setRoot(previousWriteRoot);
        Cvar.getInstance().clear();
    }

    @Test
    public void runEmptyInstance() {
        manager = new SV_MAIN();
        GameImportsImpl testInstance = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);

        testInstance.SV_RunGameFrame(100);

        Assertions.assertEquals(1, testInstance.frameNum);
    }

    @Test
    public void gamesKeepIndependentMapStateAndFrameCounters() {
        manager = new SV_MAIN();
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox2", false, false), null);

        Assertions.assertEquals("testbox", first.mapName);
        Assertions.assertEquals("testbox2", second.mapName);
        Assertions.assertNotEquals(first.name, second.name);
        Assertions.assertEquals(ServerStates.SS_LOADING, second.state);
        Assertions.assertNotSame(first.models, second.models);
        Assertions.assertNotSame(first.baselines[1], second.baselines[1]);
        String secondMapLabel = second.configstrings[jake2.qcommon.Defines.CS_NAME];

        first.configstrings[jake2.qcommon.Defines.CS_NAME] = "changed first map";
        first.baselines[1].modelindex = 7;
        first.state = ServerStates.SS_GAME;
        first.SV_RunGameFrame(100);

        Assertions.assertEquals(1, first.frameNum);
        Assertions.assertEquals(0, second.frameNum);
        Assertions.assertEquals(secondMapLabel, second.configstrings[jake2.qcommon.Defines.CS_NAME]);
        Assertions.assertEquals(0, second.baselines[1].modelindex);
        Assertions.assertEquals(ServerStates.SS_LOADING, second.state);
    }

    @Test
    public void resourceUpdatesStayWithTheirGameAndOnlySendAfterLoading() {
        manager = new SV_MAIN();
        Cvar.getInstance().find("maxclients", Integer.class).forceSet(2);
        manager.resetClients();
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox2", false, false), null);
        SV_MAIN.games.put(first.name, first);
        SV_MAIN.games.put(second.name, second);
        ClientNetworkInfo firstClient = manager.getClients().get(0);
        ClientNetworkInfo secondClient = manager.getClients().get(1);
        firstClient.gameName = first.name;
        secondClient.gameName = second.name;
        firstClient.state = secondClient.state = ClientStates.CS_CONNECTED;

        try {
            int firstIndex = first.modelindex("models/first.md2");
            int secondIndex = second.modelindex("models/second.md2");
            Assertions.assertEquals(firstIndex, first.modelindex("models/first.md2"));
            Assertions.assertEquals("models/first.md2", first.configstrings[Defines.CS_MODELS + firstIndex]);
            Assertions.assertEquals("models/second.md2", second.configstrings[Defines.CS_MODELS + secondIndex]);
            Assertions.assertTrue(firstClient.netchan.reliablePending.isEmpty());
            Assertions.assertTrue(secondClient.netchan.reliablePending.isEmpty());

            first.state = ServerStates.SS_GAME;
            int soundIndex = first.soundindex("sound/first.wav");
            // Reusing an index should not publish another resource update.
            Assertions.assertEquals(soundIndex, first.soundindex("sound/first.wav"));
            Assertions.assertEquals(1, firstClient.netchan.reliablePending.size());
            Assertions.assertEquals(new ConfigStringMessage(Defines.CS_SOUNDS + soundIndex, "sound/first.wav"),
                    firstClient.netchan.reliablePending.iterator().next());
            Assertions.assertTrue(secondClient.netchan.reliablePending.isEmpty());
        } finally {
            // These clients exercise message queues without establishing network channels.
            firstClient.state = secondClient.state = ClientStates.CS_FREE;
        }
    }

    @Test
    public void runGame() {

        final SV_MAIN sv_main = manager = new SV_MAIN();
        GameImportsImpl game = sv_main.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        final GameExports gameExports = game.gameExports;
        GameEntity gunner = null;
        GameEntity soldier = null;
        for (int i = 1; i < gameExports.getNumEdicts(); i++) {
            GameEntity e = (GameEntity) gameExports.getEdict(i);
            if ("monster_gunner".equals(e.classname)) {
                gunner = e;
            } else if ("monster_soldier".equals(e.classname)) {
                soldier = e;
            }
        }
        Assertions.assertNotNull(gunner);
        Assertions.assertNotNull(soldier);
        gunner.enemy = soldier;
        gunner.goalentity = soldier;
        gunner.monsterinfo.currentmove = M_Gunner.gunner_move_run;
        for (int i = 0; i < 100; i++) {
            gameExports.G_RunFrame();
            if (soldier.health <= 0) {
                System.out.println("soldier killed on frame: " + i);
                return;
            }
        }
        Assertions.fail("Soldier was not killed");
    }
}
