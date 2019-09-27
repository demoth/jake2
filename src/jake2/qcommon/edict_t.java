/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 04.11.2003 by RST.

package jake2.qcommon;

import jake2.game.EntBlockedAdapter;
import jake2.game.EntDieAdapter;
import jake2.game.EntPainAdapter;
import jake2.game.EntThinkAdapter;
import jake2.game.EntTouchAdapter;
import jake2.game.EntUseAdapter;
import jake2.game.gitem_t;
import jake2.game.monsterinfo_t;
import jake2.game.moveinfo_t;

public class edict_t {

    /** Constructor. */
    public edict_t(int i) {
        s.number = i;
        index = i;
    }

    /** Used during level loading. */
    public void cleararealinks() {
        area = new link_t(this);
    }

    /** Integrated entity state. */
    public entity_state_t s = new entity_state_t(this);

    public boolean inuse;

    public int linkcount;

    /**
     * FIXME: move these fields to a server private sv_entity_t. linked to a
     * division node or leaf.
     */
    public link_t area = new link_t(this);

    /** if -1, use headnode instead. */
    public int num_clusters;

    public int clusternums[] = new int[Defines.MAX_ENT_CLUSTERS];

    /** unused if num_clusters != -1. */
    public int headnode;

    public int areanum, areanum2;

    //================================

    /** SVF_NOCLIENT, SVF_DEADMONSTER, SVF_MONSTER, etc. */
    public int svflags;

    public float[] mins = { 0, 0, 0 };

    public float[] maxs = { 0, 0, 0 };

    public float[] absmin = { 0, 0, 0 };

    public float[] absmax = { 0, 0, 0 };

    public float[] size = { 0, 0, 0 };

    public int solid;

    public int clipmask;

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

    public GameClient client;

    /** Introduced by rst. */
    public int index;

    public edict_t getOwner() {
        throw new IllegalStateException("edict_t.getOwner() should not be called");
    }


    /////////////////////////////////////////////////


}