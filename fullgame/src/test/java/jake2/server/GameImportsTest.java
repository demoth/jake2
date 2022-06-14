package jake2.server;

import jake2.game.SubgameEntity;
import jake2.game.monsters.M_Gunner;
import jake2.qcommon.GameExports;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class GameImportsTest {

    @Before
    public void setup() {
        Cvar.getInstance().ForceSet("basedir", new File("src/test/resources").getAbsoluteFile().toString());
        FS.InitFilesystem();
    }

    @Test
    public void runEmptyInstance() {
        GameImportsImpl testInstance = new SV_MAIN().createGameInstance(new ChangeMapInfo("testbox", false, false));

        testInstance.SV_RunGameFrame(100);

        Assert.assertEquals(1, testInstance.sv.framenum);
    }

    @Test
    public void runGame() {

        final SV_MAIN sv_main = new SV_MAIN();
        GameImportsImpl game = sv_main.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        final GameExports gameExports = game.gameExports;
        SubgameEntity gunner = null;
        SubgameEntity soldier = null;
        for (int i = 1; i < gameExports.getNumEdicts(); i++) {
            SubgameEntity e = (SubgameEntity) gameExports.getEdict(i);
            if ("monster_gunner".equals(e.classname)) {
                gunner = e;
            } else if ("monster_soldier".equals(e.classname)) {
                soldier = e;
            }
        }
        Assert.assertNotNull(gunner);
        Assert.assertNotNull(soldier);
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
        Assert.fail("Soldier was not killed");
    }
}
