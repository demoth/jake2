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

// Created on 09.01.2004 by RST.

package jake2.game;

import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * There are many fields in the edict class. Some of them represent state - like 'health', 'damage' etc,
 * other represent behaviour - like 'think', 'touch', 'use' etc.
 * In c version these were function pointers.
 *
 * The purpose of all Adapter registration is to store and restore such behavioural edict fields.
 */
public abstract class SuperAdapter {

	/** Constructor, does the adapter registration. */
	public SuperAdapter() {
		register(this, getID());
	}

	/** Adapter registration. */
	private static void register(SuperAdapter sa, String id) {
		adapters.put(id, sa);
	}

	/** Adapter repository. */
	private static final Map<String, SuperAdapter> adapters = new HashMap<>();

	/**
	 * Returns the adapter from the repository given by its ID.
	 */
	public static SuperAdapter getFromID(String key) {
		return adapters.get(key);
	}

    /** Writes the Adapter-ID to the file. */
    static void writeAdapter(QuakeFile f, SuperAdapter a) throws IOException {
        f.writeInt(3988);
        if (a == null)
            f.writeString(null);
        else {
            String str = a.getID();
            if (str == null) {
            	// replace with log
            	// Com.DPrintf("writeAdapter: invalid Adapter id for " + a + "\n");
            }
            f.writeString(str);
        }
    }

	/** Reads the adapter id and returns the adapter. */
	static SuperAdapter readAdapter(QuakeFile f) throws IOException {
		if (f.readInt() != 3988) {
			// replace with log
			// Com.DPrintf("wrong read position: readadapter 3988 \n");
		}

		String id = f.readString();

		if (id == null) {
			// null adapter. :-)
			return null;
		}

		return getFromID(id);
	}

	/** Returns the Adapter-ID. */
	public abstract String getID();

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " '" + getID() + "'";
	}
}
