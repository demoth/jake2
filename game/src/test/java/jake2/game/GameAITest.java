package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.monsters.monsterinfo_t;
import jake2.qcommon.*;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.ServerMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class GameAITest {
    private SubgameEntity entity;
    private GameExportsImpl gameExports;
    private boolean meleeAttackExecuted;
    private boolean missileAttackExecuted;
    private float[] lastTraceStart;
    private float[] lastTraceEnd;
    private float[] lastMoveVector;  // Store M_walkmove's movement vector
    private float traceSuccessRate = 1.0f; // 1.0 means all traces succeed

    private class MockGameImports implements GameImports {
        @Override
        public void dprintf(String s) {
            // Do nothing for tests
        }

        @Override
        public void bprintf(int printlevel, String s) {
            // Do nothing for tests
        }

        @Override
        public void cprintf(edict_t ent, int printlevel, String s) {
            // Do nothing for tests
        }

        @Override
        public void centerprintf(edict_t ent, String s) {
            // Do nothing for tests
        }

        @Override
        public void sound(edict_t ent, int channel, int soundindex, float volume, float attenuation, float timeofs) {
            // Do nothing for tests
        }

        @Override
        public void positioned_sound(float[] origin, edict_t ent, int channel, int soundinedex, float volume, float attenuation, float timeofs) {
            // Do nothing for tests
        }

        @Override
        public void configstring(int num, String string) {
            // Do nothing for tests
        }

        @Override
        public void error(String err) {
            // Do nothing for tests
        }

        @Override
        public void error(int level, String err) {
            // Do nothing for tests
        }

        @Override
        public int modelindex(String name) {
            return 0;
        }

        @Override
        public int soundindex(String name) {
            return 0;
        }

        @Override
        public int imageindex(String name) {
            return 0;
        }

        @Override
        public void setmodel(edict_t ent, String name) {
            // Do nothing for tests
        }

        @Override
        public trace_t trace(float[] start, float[] mins, float[] maxs, float[] end, edict_t passent, int contentmask) {
            System.out.println("[DEBUG_LOG] Trace from: (" + start[0] + "," + start[1] + "," + start[2] + 
                             ") to: (" + end[0] + "," + end[1] + "," + end[2] + ")");
            float dx = end[0] - start[0];
            float dy = end[1] - start[1];
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
            System.out.println("[DEBUG_LOG] Movement vector: dx=" + dx + ", dy=" + dy);
            System.out.println("[DEBUG_LOG] Movement angle: " + angle);

            // Store the movement for test verification
            System.arraycopy(start, 0, lastTraceStart, 0, 3);
            System.arraycopy(end, 0, lastTraceEnd, 0, 3);

            trace_t trace = new trace_t();
            trace.endpos = new float[3];
            trace.allsolid = false;
            trace.startsolid = false;
            trace.plane = new cplane_t();
            trace.plane.normal = new float[]{0, 0, 1};  // Pointing up
            trace.ent = new SubgameEntity(0);  // Empty entity for collision
            trace.ent.linkcount = 1;  // Needed by SV_movestep

            // Calculate movement vector relative to entity's origin
            float[] move = new float[3];
            for (int i = 0; i < 3; i++) {
                move[i] = end[i] - start[i];
            }

            // Get the movement vector from the trace coordinates
            float[] moveVector = {end[0] - start[0], end[1] - start[1], end[2] - start[2]};

            // Check if this is a horizontal movement
            if (Math.abs(moveVector[2]) < 0.1) {  // No significant vertical movement
                // Store the horizontal movement vector
                System.arraycopy(moveVector, 0, lastMoveVector, 0, 3);
                System.out.println("[DEBUG_LOG] Stored horizontal movement: (" + 
                    lastMoveVector[0] + "," + lastMoveVector[1] + "," + lastMoveVector[2] + ")");
            }

            // Check if this is a horizontal movement trace
            boolean isMovementTrace = Math.abs(lastMoveVector[0]) > 0.1 || Math.abs(lastMoveVector[1]) > 0.1;

            // For step checks, we move vertically
            boolean isStepCheck = Math.abs(end[2] - start[2]) > 0.1;

            System.out.println("[DEBUG_LOG] Movement vector: (" + 
                lastMoveVector[0] + "," + lastMoveVector[1] + "," + lastMoveVector[2] + "), " +
                "isMovementTrace=" + isMovementTrace + ", " +
                "isStepCheck=" + isStepCheck);

            if (isMovementTrace) {
                System.out.println("[DEBUG_LOG] Processing horizontal movement: move=(" + 
                    lastMoveVector[0] + "," + lastMoveVector[1] + "," + lastMoveVector[2] + ")");
                if (Math.random() < traceSuccessRate) {
                    trace.fraction = 1.0f;
                    System.arraycopy(end, 0, trace.endpos, 0, 3);
                    // Update entity position on successful movement
                    if (passent instanceof SubgameEntity) {
                        SubgameEntity ent = (SubgameEntity) passent;
                        System.arraycopy(end, 0, ent.s.origin, 0, 3);
                        System.out.println("[DEBUG_LOG] Updated entity position to: (" + 
                            ent.s.origin[0] + "," + ent.s.origin[1] + "," + ent.s.origin[2] + ")");
                    }
                    System.out.println("[DEBUG_LOG] Initial movement succeeded");
                } else {
                    trace.fraction = 0.0f;
                    System.arraycopy(start, 0, trace.endpos, 0, 3);
                    System.out.println("[DEBUG_LOG] Initial movement failed");
                }
            } else if (isStepCheck) {
                // For step checks, always succeed
                trace.fraction = 1.0f;
                System.arraycopy(end, 0, trace.endpos, 0, 3);
                System.out.println("[DEBUG_LOG] Step check succeeded");
            } else {
                // For bounds checks and subsequent traces, succeed
                trace.fraction = 1.0f;
                System.arraycopy(end, 0, trace.endpos, 0, 3);
                System.out.println("[DEBUG_LOG] Subsequent trace succeeded");
            }
            return trace;
        }

        @Override
        public boolean inPHS(float[] p1, float[] p2) {
            return true;
        }

        @Override
        public void SetAreaPortalState(int portalnum, boolean open) {
            // Do nothing for tests
        }

        @Override
        public boolean AreasConnected(int area1, int area2) {
            return true;
        }

        @Override
        public void linkentity(edict_t ent) {
            // Do nothing for tests
        }

        @Override
        public void unlinkentity(edict_t ent) {
            // Do nothing for tests
        }

        @Override
        public int BoxEdicts(float[] mins, float[] maxs, edict_t[] list, int maxcount, int areatype) {
            return 0;
        }

        @Override
        public void Pmove(pmove_t pmove) {
            // Do nothing for tests
        }

        @Override
        public cvar_t cvar(String var_name, String value, int flags) {
            return new cvar_t();
        }

        @Override
        public cvar_t cvar_set(String var_name, String value) {
            return new cvar_t();
        }

        @Override
        public cvar_t cvar_forceset(String var_name, String value) {
            return new cvar_t();
        }

        @Override
        public void AddCommandString(String text) {
            // Do nothing for tests
        }

        @Override
        public int getPointContents(float[] p) {
            return 0;
        }

        @Override
        public void multicastMessage(float[] origin, ServerMessage msg, MulticastTypes to) {
            // Do nothing for tests
        }

        @Override
        public void unicastMessage(int index, ServerMessage msg, boolean reliable) {
            // Do nothing for tests
        }
    }

    @Before
    public void setUp() {
        entity = new SubgameEntity(1);
        entity.s = new entity_state_t(null);
        entity.s.clear();

        // Initialize entity position and bounds
        entity.s.origin = new float[]{0, 0, 0};
        entity.s.angles = new float[3];  // Initialize angles array
        entity.mins = new float[]{-16, -16, -24};  // Standard monster bounds
        entity.maxs = new float[]{16, 16, 32};
        entity.size = new float[]{32, 32, 56};  // maxs - mins

        // Set up movement properties
        entity.groundentity = new SubgameEntity(0);  // Entity needs to be grounded for movement
        entity.flags = 0;  // Clear any flags that might interfere
        entity.movetype = GameDefines.MOVETYPE_STEP;  // Standard monster movement type
        entity.gravity = 1.0f;  // Normal gravity
        entity.velocity = new float[3];  // Initialize velocity
        entity.avelocity = new float[3];  // Initialize angular velocity
        entity.mass = 200;  // Standard monster mass
        entity.solid = Defines.SOLID_BBOX;  // Entity is solid
        entity.clipmask = Defines.MASK_MONSTERSOLID;  // Standard monster clipping
        entity.svflags = 0;  // No special flags
        entity.inuse = true;  // Entity is in use

        lastTraceStart = new float[3];
        lastTraceEnd = new float[3];
        lastMoveVector = new float[3];

        // Initialize movement vector to zero
        for (int i = 0; i < 3; i++) {
            lastMoveVector[i] = 0;
        }

        gameExports = new GameExportsImpl(new MockGameImports());
        gameExports.gameImports.linkentity(entity);  // Link entity to the world
        entity.monsterinfo = new monsterinfo_t();
        entity.monsterinfo.melee = new EntThinkAdapter() {
            @Override
            public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
                meleeAttackExecuted = true;
                return true;
            }

            @Override
            public String getID() {
                return "monster_melee_test";
            }
        };
        entity.monsterinfo.attack = new EntThinkAdapter() {
            @Override
            public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
                missileAttackExecuted = true;
                return true;
            }

            @Override
            public String getID() {
                return "monster_missile_test";
            }
        };
        meleeAttackExecuted = false;
        missileAttackExecuted = false;
    }

    @Test
    public void testAiRunMelee() {
        // Set up initial conditions
        gameExports.enemy_yaw = 90;
        entity.s.angles[Defines.YAW] = 90; // Already facing enemy
        entity.monsterinfo.attack_state = 0;

        // Execute melee attack
        GameAI.ai_run_melee(entity, gameExports);

        // Verify melee was executed and state was changed
        assertTrue("Melee attack should be executed when facing enemy", meleeAttackExecuted);
        assertEquals("Attack state should be changed to AS_STRAIGHT", 
            GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state);
    }

    @Test
    public void testAiRunMeleeNotFacing() {
        // Set up initial conditions
        gameExports.enemy_yaw = 90;
        entity.s.angles[Defines.YAW] = 0; // Not facing enemy
        entity.monsterinfo.attack_state = 0;

        // Execute melee attack
        GameAI.ai_run_melee(entity, gameExports);

        // Verify melee was not executed as we're not facing enemy
        assertFalse("Melee attack should not be executed when not facing enemy", meleeAttackExecuted);
        assertNotEquals("Attack state should not be changed when not facing enemy", 
            GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state);
    }

    @Test
    public void testAiRunMissile() {
        // Set up initial conditions
        gameExports.enemy_yaw = 90;
        entity.s.angles[Defines.YAW] = 90; // Facing enemy
        entity.monsterinfo.attack_state = 0;

        // Execute missile attack
        GameAI.ai_run_missile(entity, gameExports);

        // Verify missile was executed and state was changed
        assertTrue("Missile attack should be executed when facing enemy", missileAttackExecuted);
        assertEquals("Attack state should be changed to AS_STRAIGHT", 
            GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state);
    }

    @Test
    public void testAiRunMissileNotFacing() {
        // Set up initial conditions
        gameExports.enemy_yaw = 90;
        entity.s.angles[Defines.YAW] = 0; // Not facing enemy
        entity.monsterinfo.attack_state = 0;

        // Execute missile attack
        GameAI.ai_run_missile(entity, gameExports);

        // Verify missile was not executed as we're not facing enemy
        assertFalse("Missile attack should not be executed when not facing enemy", missileAttackExecuted);
        assertNotEquals("Attack state should not be changed when not facing enemy", 
            GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state);
    }

    @Test
    public void testFacingIdealExactly() {
        entity.s.angles[Defines.YAW] = 90;
        entity.ideal_yaw = 90;
        assertTrue(GameAI.FacingIdeal(entity));
    }

    @Test
    public void testFacingIdealWithinLimit() {
        entity.s.angles[Defines.YAW] = 90;
        entity.ideal_yaw = 120; // 30 degrees difference
        assertTrue(GameAI.FacingIdeal(entity));
    }

    @Test
    public void testNotFacingIdeal() {
        entity.s.angles[Defines.YAW] = 90;
        entity.ideal_yaw = 180; // 90 degrees difference
        assertFalse(GameAI.FacingIdeal(entity));
    }

    @Test
    public void testFacingIdealNearLimit() {
        entity.s.angles[Defines.YAW] = 0;
        entity.ideal_yaw = 44; // Just within 45 degrees
        assertTrue(GameAI.FacingIdeal(entity));
    }

    @Test
    public void testFacingIdealNearUpperLimit() {
        entity.s.angles[Defines.YAW] = 0;
        entity.ideal_yaw = 316; // Just within 315-360 range
        assertTrue(GameAI.FacingIdeal(entity));
    }
}
