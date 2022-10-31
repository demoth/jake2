/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 11.11.2003 by RST.

package jake2.game.monsters;

import jake2.game.adapters.AIAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.SuperAdapter;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;

/**
 * Represents a single time frame of a monster.
 * Combined into {@link mmove_t} to form a state
 */
public class mframe_t {
	/**
	 *
	 * @param ai - Function responsible for the intention of the actor - what to do (next)?
	 * @param dist - Entity will be moved this amount of units along the direction of view this frame (can be negative)
	 * @param think - a callback which is invoked this frame - like playing a sound or firing
	 */
	public mframe_t(AIAdapter ai, float dist, EntThinkAdapter think) {
        this.ai = ai;
        this.dist = dist;
        this.think = think;
    }

    public mframe_t() {
    }

    public AIAdapter ai;
    public float dist;

    public EntThinkAdapter think;

    public void write(QuakeFile f) throws IOException {
        SuperAdapter.writeAdapter(f, ai);
        f.writeFloat(dist);
        SuperAdapter.writeAdapter(f, think);
    }

    public void read(QuakeFile f) throws IOException {
        ai = (AIAdapter) SuperAdapter.readAdapter(f);
        dist = f.readFloat();
        think = (EntThinkAdapter) SuperAdapter.readAdapter(f);
    }
}
