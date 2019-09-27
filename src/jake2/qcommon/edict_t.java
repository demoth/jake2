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


    /** Introduced by rst. */
    public int index;

    public edict_t getOwner() {
        throw new IllegalStateException("edict_t.getOwner() should not be called");
    }

    public GameClient getClient() {
        throw new IllegalStateException("edict_t.getClient() should not be called");
    }

    /////////////////////////////////////////////////


}