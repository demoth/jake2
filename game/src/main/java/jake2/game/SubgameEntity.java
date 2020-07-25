package jake2.game;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.edict_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;

import static jake2.qcommon.Defines.ERR_FATAL;

public class SubgameEntity extends edict_t {
    public SubgameEntity(int i) {
        super(i);
    }

    private gclient_t client;

    public SubgameEntity enemy = null;
    public SubgameEntity oldenemy = null;
    public SubgameEntity chain = null;
    public SubgameEntity activator = null;
    public edict_t groundentity = null;
    public int groundentity_linkcount;
    public SubgameEntity teamchain = null;
    public SubgameEntity teammaster = null;
    public SubgameEntity goalentity = null;
    public SubgameEntity movetarget = null;
    public SubgameEntity target_ent = null;
    private SubgameEntity owner;

    /** can go in client only. */
    public SubgameEntity mynoise = null;
    public SubgameEntity mynoise2 = null;
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

    public int spawnflags;

    public float timestamp;

    /** set in qe3, -1 = up, -2 = down */
    public float angle;

    public String target = null;

    public String targetname = null;

    public String killtarget = null;

    public String team = null;

    public String pathtarget = null;

    public String deathtarget = null;

    public String combattarget = null;


    public float speed, accel, decel;

    public float[] movedir = { 0, 0, 0 };

    public float[] pos1 = { 0, 0, 0 };

    public float[] pos2 = { 0, 0, 0 };

    public float[] velocity = { 0, 0, 0 };

    public float[] avelocity = { 0, 0, 0 };

    public int mass;

    public float air_finished;

    /** per entity gravity multiplier (1.0 is normal). */
    public float gravity;

    /** use for lowgrav artifact, flares. */

    public float yaw_speed;

    public float ideal_yaw;

    public float nextthink;

    public EntThinkAdapter prethink = null;

    public EntThinkAdapter think = null;

    public EntBlockedAdapter blocked = null;

    public EntTouchAdapter touch = null;

    public EntUseAdapter use = null;

    public EntPainAdapter pain = null;

    public EntDieAdapter die = null;

    /** Are all these legit? do we need more/less of them? */
    public float touch_debounce_time;

    public float pain_debounce_time;

    public float damage_debounce_time;

    /** Move to clientinfo. */
    public float fly_sound_debounce_time;

    public float last_move_time;

    public int health;

    public int max_health;

    public int gib_health;

    public int deadflag;

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

    public int waterlevel;

    public float[] move_origin = { 0, 0, 0 };

    public float[] move_angles = { 0, 0, 0 };

    /** move this to clientinfo? . */
    public int light_level;

    /** also used as areaportal number. */
    public int style;

    public gitem_t item; // for bonus items

    /** common integrated data blocks. */
    public moveinfo_t moveinfo = new moveinfo_t();

    public monsterinfo_t monsterinfo = new monsterinfo_t();




    public boolean setField(String key, String value) {
        if (key.equals("classname")) {
            classname = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("model")) {
            model = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("spawnflags")) {
            spawnflags = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("speed")) {
            speed = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("accel")) {
            accel = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("decel")) {
            decel = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("target")) {
            target = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("targetname")) {
            targetname = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("pathtarget")) {
            pathtarget = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("deathtarget")) {
            deathtarget = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),
        if (key.equals("killtarget")) {
            killtarget = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("combattarget")) {
            combattarget = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("message")) {
            message = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("team")) {
            team = Lib.ED_NewString(value);
            Com.dprintln("Monster Team:" + team);
            return true;
        } // F_LSTRING),

        if (key.equals("wait")) {
            wait = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("delay")) {
            delay = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("random")) {
            random = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("move_origin")) {
            move_origin = Lib.atov(value);
            return true;
        } // F_VECTOR),

        if (key.equals("move_angles")) {
            move_angles = Lib.atov(value);
            return true;
        } // F_VECTOR),

        if (key.equals("style")) {
            style = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("count")) {
            count = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("health")) {
            health = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("sounds")) {
            sounds = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("light")) {
            return true;
        } // F_IGNORE),

        if (key.equals("dmg")) {
            dmg = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("mass")) {
            mass = Lib.atoi(value);
            return true;
        } // F_INT),

        if (key.equals("volume")) {
            volume = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("attenuation")) {
            attenuation = Lib.atof(value);
            return true;
        } // F_FLOAT),

        if (key.equals("map")) {
            map = Lib.ED_NewString(value);
            return true;
        } // F_LSTRING),

        if (key.equals("origin")) {
            s.origin = Lib.atov(value);
            return true;
        } // F_VECTOR),

        if (key.equals("angles")) {
            s.angles = Lib.atov(value);
            return true;
        } // F_VECTOR),

        if (key.equals("angle")) {
            s.angles = new float[] { 0, Lib.atof(value), 0 };
            return true;
        } // F_ANGLEHACK),

        if (key.equals("item")) {
            Com.Error(ERR_FATAL, "ent.set(\"item\") called.");
            return true;
        } // F_ITEM)

        return false;
    }

    /** Writes the entity to the file. */
    public void write(QuakeFile f) throws IOException {

        s.write(f);
        f.writeBoolean(inuse);
        f.writeInt(linkcount);
        f.writeInt(num_clusters);

        f.writeInt(9999);

        if (clusternums == null)
            f.writeInt(-1);
        else {
            f.writeInt(Defines.MAX_ENT_CLUSTERS);
            for (int n = 0; n < Defines.MAX_ENT_CLUSTERS; n++)
                f.writeInt(clusternums[n]);

        }
        f.writeInt(headnode);
        f.writeInt(areanum);
        f.writeInt(areanum2);
        f.writeInt(svflags);
        f.writeVector(mins);
        f.writeVector(maxs);
        f.writeVector(absmin);
        f.writeVector(absmax);
        f.writeVector(size);
        f.writeInt(solid);
        f.writeInt(clipmask);

        f.writeInt(movetype);
        f.writeInt(flags);

        f.writeString(model);
        f.writeFloat(freetime);
        f.writeString(message);
        f.writeString(classname);
        f.writeInt(spawnflags);
        f.writeFloat(timestamp);

        f.writeFloat(angle);

        f.writeString(target);
        f.writeString(targetname);
        f.writeString(killtarget);
        f.writeString(team);
        f.writeString(pathtarget);
        f.writeString(deathtarget);
        f.writeString(combattarget);

        f.writeEdictRef(target_ent);

        f.writeFloat(speed);
        f.writeFloat(accel);
        f.writeFloat(decel);

        f.writeVector(movedir);

        f.writeVector(pos1);
        f.writeVector(pos2);

        f.writeVector(velocity);
        f.writeVector(avelocity);

        f.writeInt(mass);
        f.writeFloat(air_finished);

        f.writeFloat(gravity);

        f.writeEdictRef(goalentity);
        f.writeEdictRef(movetarget);

        f.writeFloat(yaw_speed);
        f.writeFloat(ideal_yaw);

        f.writeFloat(nextthink);

        SuperAdapter.writeAdapter(f, prethink);
        SuperAdapter.writeAdapter(f, think);
        SuperAdapter.writeAdapter(f, blocked);
        SuperAdapter.writeAdapter(f, touch);
        SuperAdapter.writeAdapter(f, use);
        SuperAdapter.writeAdapter(f, pain);
        SuperAdapter.writeAdapter(f, die);

        f.writeFloat(touch_debounce_time);
        f.writeFloat(pain_debounce_time);
        f.writeFloat(damage_debounce_time);

        f.writeFloat(fly_sound_debounce_time);
        f.writeFloat(last_move_time);

        f.writeInt(health);
        f.writeInt(max_health);

        f.writeInt(gib_health);
        f.writeInt(deadflag);
        f.writeInt(show_hostile);

        f.writeFloat(powerarmor_time);

        f.writeString(map);

        f.writeInt(viewheight);
        f.writeInt(takedamage);
        f.writeInt(dmg);
        f.writeInt(radius_dmg);
        f.writeFloat(dmg_radius);

        f.writeInt(sounds);
        f.writeInt(count);

        f.writeEdictRef(chain);
        f.writeEdictRef(enemy);
        f.writeEdictRef(oldenemy);
        f.writeEdictRef(activator);
        f.writeEdictRef(groundentity);
        f.writeInt(groundentity_linkcount);
        f.writeEdictRef(teamchain);
        f.writeEdictRef(teammaster);

        f.writeEdictRef(mynoise);
        f.writeEdictRef(mynoise2);

        f.writeInt(noise_index);
        f.writeInt(noise_index2);

        f.writeFloat(volume);
        f.writeFloat(attenuation);
        f.writeFloat(wait);
        f.writeFloat(delay);
        f.writeFloat(random);

        f.writeFloat(teleport_time);

        f.writeInt(watertype);
        f.writeInt(waterlevel);
        f.writeVector(move_origin);
        f.writeVector(move_angles);

        f.writeInt(light_level);
        f.writeInt(style);

        GameItems.writeItem(f, item);

        moveinfo.write(f);
        monsterinfo.write(f);
        if (getClient() == null)
            f.writeInt(-1);
        else
            f.writeInt(getClient().getIndex());

        f.writeEdictRef(getOwner());

        // rst's checker :-)
        f.writeInt(9876);
    }

    /** Reads the entity from the file. */
    public void read(QuakeFile f, edict_t[] g_edicts, gclient_t[] clients, GameExportsImpl gameExports) throws IOException {
        s.read(f, g_edicts);
        inuse = f.readBoolean();
        linkcount = f.readInt();
        num_clusters = f.readInt();

        if (f.readInt() != 9999)
            new Throwable("wrong read pos!").printStackTrace();

        int len = f.readInt();

        if (len == -1)
            clusternums = null;
        else {
            clusternums = new int[Defines.MAX_ENT_CLUSTERS];
            for (int n = 0; n < Defines.MAX_ENT_CLUSTERS; n++)
                clusternums[n] = f.readInt();
        }

        headnode = f.readInt();
        areanum = f.readInt();
        areanum2 = f.readInt();
        svflags = f.readInt();
        mins = f.readVector();
        maxs = f.readVector();
        absmin = f.readVector();
        absmax = f.readVector();
        size = f.readVector();
        solid = f.readInt();
        clipmask = f.readInt();

        movetype = f.readInt();
        flags = f.readInt();

        model = f.readString();
        freetime = f.readFloat();
        message = f.readString();
        classname = f.readString();
        spawnflags = f.readInt();
        timestamp = f.readFloat();

        angle = f.readFloat();

        target = f.readString();
        targetname = f.readString();
        killtarget = f.readString();
        team = f.readString();
        pathtarget = f.readString();
        deathtarget = f.readString();
        combattarget = f.readString();

        target_ent = (SubgameEntity) f.readEdictRef(g_edicts);

        speed = f.readFloat();
        accel = f.readFloat();
        decel = f.readFloat();

        movedir = f.readVector();

        pos1 = f.readVector();
        pos2 = f.readVector();

        velocity = f.readVector();
        avelocity = f.readVector();

        mass = f.readInt();
        air_finished = f.readFloat();

        gravity = f.readFloat();

        goalentity = (SubgameEntity) f.readEdictRef(g_edicts);
        movetarget = (SubgameEntity) f.readEdictRef(g_edicts);

        yaw_speed = f.readFloat();
        ideal_yaw = f.readFloat();

        nextthink = f.readFloat();

        prethink = (EntThinkAdapter) SuperAdapter.readAdapter(f);
        think = (EntThinkAdapter) SuperAdapter.readAdapter(f);
        blocked = (EntBlockedAdapter) SuperAdapter.readAdapter(f);

        touch = (EntTouchAdapter) SuperAdapter.readAdapter(f);
        use = (EntUseAdapter) SuperAdapter.readAdapter(f);
        pain = (EntPainAdapter) SuperAdapter.readAdapter(f);
        die = (EntDieAdapter) SuperAdapter.readAdapter(f);

        touch_debounce_time = f.readFloat();
        pain_debounce_time = f.readFloat();
        damage_debounce_time = f.readFloat();

        fly_sound_debounce_time = f.readFloat();
        last_move_time = f.readFloat();

        health = f.readInt();
        max_health = f.readInt();

        gib_health = f.readInt();
        deadflag = f.readInt();
        show_hostile = f.readInt();

        powerarmor_time = f.readFloat();

        map = f.readString();

        viewheight = f.readInt();
        takedamage = f.readInt();
        dmg = f.readInt();
        radius_dmg = f.readInt();
        dmg_radius = f.readFloat();

        sounds = f.readInt();
        count = f.readInt();

        chain = (SubgameEntity) f.readEdictRef(g_edicts);
        enemy = (SubgameEntity) f.readEdictRef(g_edicts);

        oldenemy = (SubgameEntity) f.readEdictRef(g_edicts);
        activator = (SubgameEntity) f.readEdictRef(g_edicts);
        groundentity = (SubgameEntity) f.readEdictRef(g_edicts);

        groundentity_linkcount = f.readInt();
        teamchain = (SubgameEntity) f.readEdictRef(g_edicts);
        teammaster = (SubgameEntity) f.readEdictRef(g_edicts);

        mynoise = (SubgameEntity) f.readEdictRef(g_edicts);
        mynoise2 = (SubgameEntity) f.readEdictRef(g_edicts);

        noise_index = f.readInt();
        noise_index2 = f.readInt();

        volume = f.readFloat();
        attenuation = f.readFloat();
        wait = f.readFloat();
        delay = f.readFloat();
        random = f.readFloat();

        teleport_time = f.readFloat();

        watertype = f.readInt();
        waterlevel = f.readInt();
        move_origin = f.readVector();
        move_angles = f.readVector();

        light_level = f.readInt();
        style = f.readInt();

        item = GameItems.readItem(f, gameExports);

        moveinfo.read(f);
        monsterinfo.read(f);

        int ndx = f.readInt();
        if (ndx == -1)
            setClient(null);
        else
            setClient(clients[ndx]);

        setOwner((SubgameEntity) f.readEdictRef(g_edicts));

        // rst's checker :-)
        if (f.readInt() != 9876)
            System.err.println("ent load check failed for num " + index);
    }

    @Override
    public SubgameEntity getOwner() {
        return owner;
    }

    @Override
    public gclient_t getClient() {
        return client;
    }

    public void setClient(gclient_t client) {
        this.client = client;
    }

    public void setOwner(SubgameEntity owner) {
        this.owner = owner;
    }
}
