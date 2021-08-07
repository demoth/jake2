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

// Created on 14.01.2004 by RST.
// $Id: server_t.java,v 1.2 2004-09-22 19:22:12 salomo Exp $
package jake2.server;

import jake2.qcommon.Defines;
import jake2.qcommon.ServerStates;
import jake2.qcommon.cmodel_t;
import jake2.qcommon.entity_state_t;

import static jake2.qcommon.Defines.CS_NAME;

class server_t {

    server_t(ChangeMapInfo changeMapInfo) {
        models = new cmodel_t[Defines.MAX_MODELS];
        for (int n = 0; n < Defines.MAX_MODELS; n++) {
            models[n] = new cmodel_t();
        }

        for (int n = 0; n < Defines.MAX_EDICTS; n++) {
            baselines[n] = new entity_state_t(null);
        }

        loadgame = changeMapInfo.isLoadgame;
        isDemo = changeMapInfo.isDemo;
        name = changeMapInfo.mapName;
        fixedtime = 1000;
        state = ServerStates.SS_LOADING;
        // save name for levels that don't set message
        configstrings[CS_NAME] = changeMapInfo.mapName;

    }

    ServerStates state = ServerStates.SS_DEAD; // precache commands are only valid during load

    boolean isDemo; // running cinematics and demos for the local system
                         // only

    boolean loadgame; // client begins should reuse existing entity

    int fixedtime; // always sv.framenum * 100 msec

    int framenum;

    String name = ""; // map name, or cinematic name

    cmodel_t[] models;

    String[] configstrings = new String[Defines.MAX_CONFIGSTRINGS];

    entity_state_t[] baselines = new entity_state_t[Defines.MAX_EDICTS];
}
