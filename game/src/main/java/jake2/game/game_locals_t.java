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

// Created on 31.10.2003 by RST.

package jake2.game;

import jake2.qcommon.Defines;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;
import java.util.Date;

/**
 * this structure is left intact through an entire game;
 * it should be initialized at dll load time, and read/written to
 * the server.ssv file for savegames
 */
public class game_locals_t {
    public String helpmessage1 = "";

    public String helpmessage2 = "";

    /**
     * flash F1 icon if non 0, play sound, and increment only if 1, 2, or 3
     */
    public int helpchanged;

    public GamePlayerInfo[] clients = new GamePlayerInfo[Defines.MAX_CLIENTS];

    // can't store spawnpoint in level, because
    // it would get overwritten by the savegame restore
    public String spawnpoint = ""; // needed for coop respawns

    // store latched cvars here that we want to get at often
    public int maxclients;

    public int maxentities;

    // cross level triggers
    public int serverflags;

    public boolean autosaved;

    /** Reads the game locals from a file. */
    public void load(QuakeFile f) throws IOException {
        String date = f.readString();

        helpmessage1 = f.readString();
        helpmessage2 = f.readString();

        helpchanged = f.readInt();
        // GamePlayerInfo*

        spawnpoint = f.readString();
        maxclients = f.readInt();
        maxentities = f.readInt();
        serverflags = f.readInt();
        /*num_items = */
        f.readInt();
        autosaved = f.readInt() != 0;
    }

    /** Writes the game locals to a file. */
    public void write(QuakeFile f) throws IOException {
        f.writeString(new Date().toString());

        f.writeString(helpmessage1);
        f.writeString(helpmessage2);

        f.writeInt(helpchanged);
        // GamePlayerInfo*

        f.writeString(spawnpoint);
        f.writeInt(maxclients);
        f.writeInt(maxentities);
        f.writeInt(serverflags);
        //f.writeInt(num_items);
        f.writeInt(autosaved ? 1 : 0);
    }
}
