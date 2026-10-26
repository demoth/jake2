package jake2.server;

import jake2.game.GameEntity;
import jake2.game.monsters.M_Gunner;
import jake2.qcommon.GameExports;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.MainCommon;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
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
