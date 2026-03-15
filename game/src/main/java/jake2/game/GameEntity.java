package jake2.game;

import jake2.game.adapters.*;
import jake2.game.character.BehaviorTree;
import jake2.game.character.GameCharacter;
import jake2.game.components.ThinkComponent;
import jake2.game.items.GameItem;
import jake2.game.monsters.monsterinfo_t;
import jake2.qcommon.ServerEntity;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.util.Lib;

import java.util.HashMap;
import java.util.Map;

public class GameEntity extends ServerEntity {
    public GameEntity(int i) {
        super(i);
    }

    private GamePlayerInfo client;

    public GameEntity enemy = null;
    public GameEntity oldenemy = null;
    public GameEntity chain = null;
    public GameEntity activator = null;
    public ServerEntity groundentity = null;
    public int groundentity_linkcount;
    // linked list
    // todo: move to a separate component
    public GameEntity teamchain = null;
    public GameEntity teammaster = null;
    public GameEntity goalentity = null;
    public GameEntity movetarget = null;
    public GameEntity target_ent = null;
    private GameEntity owner;

    /** can go in client only. */
    public GameEntity mynoise = null;
    public GameEntity mynoise2 = null;
    //================================
    public int movetype;

    public int flags;

    public String model = null;

    /** sv.time when the object was freed. */
    public float freetime;

    //
    // only used locally in game, not by server
    //
    public String message = null;

    public String classname = "";

    /**
     * a bit mask, containing various flags depending on the entity class (usually set in the map editor).
     * Can define different monster behavior,
      */
    public int spawnflags;

    public float timestamp;

    /** set in qe3, -1 = up, -2 = down */
    public float angle;

    /**
     * The name of another object (matching its targetname), which should be a target.
     * For example, a button's target can be a door
     */
    public String target = null;

    /**
     * Tame of the current object, used for targeting. If it is not null, usually it means something is targeting it.
     */
    public String targetname = null;

    public String killtarget = null;

    public String team = null;

    public String pathtarget = null;

    public String deathtarget = null;

    public String combattarget = null;


    // Movement related
    public float speed;
    public float accel;
    public float decel;
    public float[] movedir = { 0, 0, 0 }; //todo: split, move to MoveInfo & others
    public float[] pos1 = { 0, 0, 0 };//todo: split, move to MoveInfo & monster // todo: used by railgun mobs to shoot at players previous position
    public float[] pos2 = { 0, 0, 0 }; //todo: move to MoveInfo?
    public float[] velocity = { 0, 0, 0 };
    public float[] avelocity = { 0, 0, 0 };
    public float[] move_origin = { 0, 0, 0 }; //todo: move to turret
    public float[] move_angles = { 0, 0, 0 }; //todo: move to turret

    public int mass;

    public float air_finished;

    /** per entity gravity multiplier (1.0 is normal). */
    public float gravity;

    /** use for lowgrav artifact, flares. */

    public float yaw_speed;

    // degrees
    public float ideal_yaw;

    public ThinkComponent think = new ThinkComponent();

    public EntBlockedAdapter blocked = null;

    public EntTouchAdapter touch = null;

    /**
     * "Activate" function
     */
    public EntUseAdapter use = null;

    public EntPainAdapter pain = null;

    public EntDieAdapter die = null;

    /** Are all these legit? do we need more/less of them? */
    public float touch_debounce_time;

    public float pain_debounce_time;

    public float damage_debounce_time;

    /** Move to clientinfo. */
    public float fly_sound_debounce_time;

    public int health;
    /**
     * Type of damage. Also contains friendly fire bit flag. todo: split
     */
    public int meansOfDeath;

    public int max_health;

    public int gib_health;

    public int deadflag; // todo: switch to boolean

    public int show_hostile;

    public float powerarmor_time;

    /** target_changelevel. */
    public String map = null;

    /** Height above origin where eyesight is determined. */
    public int viewheight;

    public int takedamage;

    public int dmg;

    public int radius_dmg;

    public float dmg_radius;

    /** make this a spawntemp var? */
    public int sounds;

    public int count;


    public int noise_index;

    public int noise_index2;

    public float volume;

    public float attenuation;

    /** Timing variables. */
    public float wait;

    /** before firing targets... */
    public float delay;

    public float random;

    public float teleport_time;

    public int watertype;

    /**
     * <ul>
     *     <li>0 - not in the water</li>
     *     <li>1 - standing in water - at least 1 unit underwater</li>
     *     <li>2 - not fully submerged - at least 27 units underwater</li>
     *     <li>3 - fully submerged - at least 49 units underwater</li>
     * </ul>
     * Note: Player is 56 units high
     * <p/>
     * Slime and lava deal more dmg for higher waterlevel
     */
    public int waterlevel;


    /** move this to clientinfo? . */
    public int light_level;

    /** also used as areaportal number. */
    public int style;

    public GameItem item; // for bonus items

    /** common integrated data blocks. */

    public monsterinfo_t monsterinfo = new monsterinfo_t();

    public ExtraSpawnProperties st = new ExtraSpawnProperties();

    // fixme: make private
    public Map<String, Object> components = new HashMap<>();

    public GameCharacter character; // todo: move to a component
    public BehaviorTree controller; // todo: move to a component?

    // todo: replace with a constructor call?
    void G_InitEdict(int i) {
        inuse = true;
        classname = "noclass";
        gravity = 1.0f;
        //e.s.number= e - g_edicts;
        s = new entity_state_t(i);
        s.index = i;
        st = new ExtraSpawnProperties();
        index = i;
    }

    public boolean setField(String key, String value) {
        switch (key) {
            case "classname":
                classname = Lib.decodeBackslash(value);
                return true;

            case "model":
                model = Lib.decodeBackslash(value);
                return true;

            case "spawnflags":
                spawnflags = Lib.atoi(value);
                return true;

            case "speed":
                speed = Lib.atof(value);
                return true;

            case "accel":
                accel = Lib.atof(value);
                return true;

            case "decel":
                decel = Lib.atof(value);
                return true;

            case "target":
                target = Lib.decodeBackslash(value);
                return true;

            case "targetname":
                targetname = Lib.decodeBackslash(value);
                return true;

            case "pathtarget":
                pathtarget = Lib.decodeBackslash(value);
                return true;

            case "deathtarget":
                deathtarget = Lib.decodeBackslash(value);
                return true;

            case "killtarget":
                killtarget = Lib.decodeBackslash(value);
                return true;

            case "combattarget":
                combattarget = Lib.decodeBackslash(value);
                return true;

            case "message":
                message = Lib.decodeBackslash(value);
                return true;

            case "team":
                team = Lib.decodeBackslash(value);
                return true;

            case "wait":
                wait = Lib.atof(value);
                return true;

            case "delay":
                delay = Lib.atof(value);
                return true;

            case "random":
                random = Lib.atof(value);
                return true;

            case "move_origin":
                move_origin = Lib.atov(value);
                return true;

            case "move_angles":
                move_angles = Lib.atov(value);
                return true;

            case "style":
                style = Lib.atoi(value);
                return true;

            case "count":
                count = Lib.atoi(value);
                return true;

            case "health":
                health = Lib.atoi(value);
                return true;

            case "sounds":
                sounds = Lib.atoi(value);
                return true;

            case "light":
                return true;

            case "dmg":
                dmg = Lib.atoi(value);
                return true;

            case "mass":
                mass = Lib.atoi(value);
                return true;

            case "volume":
                volume = Lib.atof(value);
                return true;

            case "attenuation":
                attenuation = Lib.atof(value);
                return true;

            case "map":
                map = Lib.decodeBackslash(value);
                return true;

            case "origin":
                s.origin = Lib.atov(value);
                return true;

            case "angles":
                s.angles = Lib.atov(value);
                return true;

            case "angle":
                s.angles = new float[]{0, Lib.atof(value), 0};
                return true;

            default:
                //  if (key.equals("item")) {
                //      this field is set to spawn_temp
                //      so this line is never reachable
                //      todo log error
                return false;
        }
    }

    @Override
    public GameEntity getOwner() {
        return owner;
    }

    @Override
    public GamePlayerInfo getClient() {
        return client;
    }

    public void setClient(GamePlayerInfo client) {
        this.client = client;
    }

    public void setOwner(GameEntity owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return classname + "(" + super.index + ")";
    }
}
