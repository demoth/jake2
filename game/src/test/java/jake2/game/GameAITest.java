package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.monsters.monsterinfo_t;
import jake2.qcommon.*;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarType;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameAITest {
    private GameEntity entity;
    private GameExportsImpl gameExports;
    private boolean meleeAttackExecuted;
    private boolean missileAttackExecuted;
    private float[] lastTraceStart;
    private float[] lastTraceEnd;
    private float[] lastMoveVector;  // Store M_walkmove's movement vector
    private float traceSuccessRate = 1.0f; // 1.0 means all traces succeed

    @Test
    void gameDeclarationsRejectUnsafeCountsAndPreserveSignedGravity() {
        GameCvars cvars = gameExports.gameCvars;
        assertTrue(cvars.skill.forceSet(3).getAccepted());
        assertFalse(cvars.skill.forceSet(4).getAccepted());
        assertEquals(3, cvars.skill.getTypedValue());
        assertTrue(cvars.flood_msgs.set(10).getAccepted());
        assertFalse(cvars.flood_msgs.set(11).getAccepted());
        assertFalse(cvars.flood_msgs.set(-1).getAccepted());
        assertEquals(10, cvars.flood_msgs.getTypedValue());
        assertFalse(cvars.flood_persecond.set(-1f).getAccepted());
        assertFalse(cvars.flood_waitdelay.set(Float.NaN).getAccepted());
        assertTrue(cvars.sv_gravity.set(-400.5f).getAccepted());
        assertEquals(-400.5f, cvars.sv_gravity.getTypedValue());
        var velocity = gameExports.gameImports.findCvar("sv_maxvelocity", Float.class);
        assertFalse(velocity.set(-1f).getAccepted());
        assertFalse(cvars.maxentities.forceSet(Defines.MAX_EDICTS + 1).getAccepted());
    }

    @Test
    void entityBudgetAlwaysAccommodatesClientAndBodySlots() {
        MockGameImports imports = new MockGameImports();
        imports.findCvar("maxclients", Integer.class).set(32);
        imports.cvar_set("maxentities", "10");
        GameExportsImpl game = new GameExportsImpl(imports);
        assertEquals(32 + GameDefines.BODY_QUEUE_SIZE + 1, game.game.maxentities);
        assertEquals(game.game.maxentities, game.g_edicts.length);
        assertEquals(32, game.game.clients.length);
    }

    @Test
    void normalPlayerAdmissionUsesPlayerPasswordRatherThanSpectatorPassword() {
        gameExports.gameCvars.password.set("players");
        gameExports.gameCvars.spectator_password.set("spectators");
        GameEntity player = gameExports.g_edicts[1];
        assertFalse(PlayerClient.ClientConnect(player, "\\password\\spectators", gameExports));
        assertFalse(PlayerClient.ClientConnect(player, "", gameExports));
        assertTrue(PlayerClient.ClientConnect(player, "\\password\\players", gameExports));
    }

    private class MockGameImports implements GameImports {
        private final Cvar cvars = new Cvar();

        MockGameImports() {
            cvars.register("deathmatch", CvarTypes.BOOLEAN, false);
            cvars.register("coop", CvarTypes.BOOLEAN, false);
            cvars.register("cheats", CvarTypes.BOOLEAN, false);
            cvars.register("dedicated", CvarTypes.BOOLEAN, false);
            cvars.register("maxclients", CvarTypes.INTEGER, 1);
            cvars.register("dmflags", CvarTypes.INTEGER, 0);
            cvars.register("fraglimit", CvarTypes.INTEGER, 0);
            cvars.register("timelimit", CvarTypes.FLOAT, 0f);
            cvars.register("password", CvarTypes.STRING, "");
            cvars.register("spectator_password", CvarTypes.STRING, "");
            cvars.register("needpass", CvarTypes.INTEGER, 0);
        }

        @Override
        public <T> cvar_t<T> registerCvar(String name, CvarType<T> type, T value, int flags, String description) {
            return cvars.register(name, type, value, flags, description);
        }

        @Override
        public <T> cvar_t<T> findCvar(String name, Class<T> type) {
            return cvars.find(name, type);
        }

        @Override
        public void dprintf(String s) {
            // Do nothing for tests
        }

        @Override
        public void bprintf(int printlevel, String s) {
            // Do nothing for tests
        }

        @Override
        public void cprintf(ServerEntity ent, int printlevel, String s) {
            // Do nothing for tests
        }

        @Override
        public void centerprintf(ServerEntity ent, String s) {
            // Do nothing for tests
        }

        @Override
        public void sound(ServerEntity ent, int channel, int soundindex, float volume, float attenuation, float timeofs) {
            // Do nothing for tests
        }

        @Override
        public void positioned_sound(float[] origin, ServerEntity ent, int channel, int soundinedex, float volume, float attenuation, float timeofs) {
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
        public void setmodel(ServerEntity ent, String name) {
            // Do nothing for tests
        }

        @Override
        public trace_t trace(float[] start, float[] mins, float[] maxs, float[] end, ServerEntity passent, int contentmask) {
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
            trace.ent = new GameEntity(0);  // Empty entity for collision
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
                    if (passent instanceof GameEntity) {
                        GameEntity ent = (GameEntity) passent;
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
        public void linkentity(ServerEntity ent) {
            // Do nothing for tests
        }

        @Override
        public void unlinkentity(ServerEntity ent) {
            // Do nothing for tests
        }

        @Override
        public int BoxEdicts(float[] mins, float[] maxs, ServerEntity[] list, int maxcount, int areatype) {
            return 0;
        }

        @Override
        public void Pmove(pmove_t pmove) {
            // Do nothing for tests
        }

        @Override
        public cvar_t cvar(String var_name, String value, int flags) {
            return cvars.Get(var_name, value, flags);
        }

        @Override
        public cvar_t cvar_set(String var_name, String value) {
            return cvars.Set(var_name, value);
        }

        @Override
        public cvar_t cvar_forceset(String var_name, String value) {
            return cvars.ForceSet(var_name, value);
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

    @BeforeEach
    public void setUp() {
        entity = new GameEntity(1);
        entity.s = new entity_state_t();
        entity.s.clear();

        // Initialize entity position and bounds
        entity.s.origin = new float[]{0, 0, 0};
        entity.s.angles = new float[3];  // Initialize angles array
        entity.mins = new float[]{-16, -16, -24};  // Standard monster bounds
        entity.maxs = new float[]{16, 16, 32};
        entity.size = new float[]{32, 32, 56};  // maxs - mins

        // Set up movement properties
        entity.groundentity = new GameEntity(0);  // Entity needs to be grounded for movement
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
            public boolean think(GameEntity self, GameExportsImpl gameExports) {
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
            public boolean think(GameEntity self, GameExportsImpl gameExports) {
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
        assertTrue(meleeAttackExecuted, "Melee attack should be executed when facing enemy");
        assertEquals(GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state,
            "Attack state should be changed to AS_STRAIGHT");
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
        assertFalse(meleeAttackExecuted, "Melee attack should not be executed when not facing enemy");
        assertNotEquals(GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state,
            "Attack state should not be changed when not facing enemy");
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
        assertTrue(missileAttackExecuted, "Missile attack should be executed when facing enemy");
        assertEquals(GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state,
            "Attack state should be changed to AS_STRAIGHT");
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
        assertFalse(missileAttackExecuted, "Missile attack should not be executed when not facing enemy");
        assertNotEquals(GameDefines.AS_STRAIGHT, entity.monsterinfo.attack_state,
            "Attack state should not be changed when not facing enemy");
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
