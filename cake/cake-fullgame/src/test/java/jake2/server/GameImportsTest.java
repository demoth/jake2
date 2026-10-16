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
        Globals.server_state = previousServerState;
        EngineWriteRoot.setRoot(previousWriteRoot);
        Cvar.getInstance().clear();
    }

    @Test
    public void runEmptyInstance() {
        GameImportsImpl testInstance = new SV_MAIN().createGameInstance(new ChangeMapInfo("testbox", false, false), null);

        testInstance.SV_RunGameFrame(100);

        Assertions.assertEquals(1, testInstance.sv.framenum);
    }

    @Test
    public void runGame() {

        final SV_MAIN sv_main = new SV_MAIN();
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
