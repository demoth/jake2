package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.monsters.monsterinfo_t;
import jake2.qcommon.Defines;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the facing threshold and when monsters may launch their selected attack. */
class GameAITest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void selectedAttackWaitsUntilTheMonsterIsFacingItsEnemy(boolean melee) {
        var game = new GameExportsImpl(new TestGameImports());
        var monster = new GameEntity(1);
        monster.monsterinfo = new monsterinfo_t();
        String attack = melee ? "melee" : "missile";
        var executed = new ArrayList<String>();
        monster.monsterinfo.melee = callback("melee", executed);
        monster.monsterinfo.attack = callback("missile", executed);
        int pending = melee ? GameDefines.AS_MELEE : GameDefines.AS_MISSILE;
        monster.monsterinfo.attack_state = pending;
        game.enemy_yaw = 90;

        runAttack(melee, monster, game);
        assertTrue(executed.isEmpty());
        assertEquals(pending, monster.monsterinfo.attack_state);

        monster.s.angles[Defines.YAW] = game.enemy_yaw;
        runAttack(melee, monster, game);
        assertEquals(List.of(attack), executed);
        assertEquals(GameDefines.AS_STRAIGHT, monster.monsterinfo.attack_state);
    }

    @Test
    void facingChecksTurnToleranceOnBothSidesOfZero() {
        var monster = new GameEntity(1);
        for (float yaw : new float[]{0, 44, 316}) {
            monster.s.angles[Defines.YAW] = yaw;
            assertTrue(GameAI.FacingIdeal(monster), "yaw " + yaw);
        }
        for (float yaw : new float[]{46, 314}) {
            monster.s.angles[Defines.YAW] = yaw;
            assertFalse(GameAI.FacingIdeal(monster), "yaw " + yaw);
        }
    }

    private static void runAttack(boolean melee, GameEntity monster, GameExportsImpl game) {
        if (melee) GameAI.ai_run_melee(monster, game);
        else GameAI.ai_run_missile(monster, game);
    }

    private static EntThinkAdapter callback(String attack, List<String> executed) {
        return new EntThinkAdapter() {
            @Override
            public boolean think(GameEntity self, GameExportsImpl game) {
                executed.add(attack);
                return true;
            }

            @Override
            public String getID() {
                return "test_" + attack;
            }
        };
    }
}
