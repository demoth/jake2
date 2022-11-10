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
package jake2.game.adapters

import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.qcommon.filesystem.QuakeFile

/**
 * There are many fields in the [SubgameEntity] class. Some of them represent state - like 'health', 'damage' etc,
 * other represent behaviour - like 'think', 'touch', 'use' etc.
 * In c version these were function pointers.
 *
 *
 * The purpose of all Adapter registration is to store and restore such behavioural edict fields.
 */
abstract class SuperAdapter {
    /** Constructor, does the adapter registration.  */
    init {
        register(iD, this)
    }

    /**
     * Returns the Adapter-ID.
     */
    abstract val iD: String

    override fun toString(): String {
        return "${this.javaClass.simpleName} '$iD'"
    }

    override fun equals(obj: Any?): Boolean {
        return if (obj is SuperAdapter) {
            iD == obj.iD
        } else false
    }

    companion object {
        /** Adapter registration.  */
        fun register(id: String?, sa: SuperAdapter): String {
            if (id == null)
                return "null"
            adapters[id] = sa
            return id
        }

        fun registerThink(id: String, think: (self: SubgameEntity, gameExports: GameExportsImpl) -> Boolean): EntThinkAdapter {
            val adapter = object : EntThinkAdapter() {
                override fun think(self: SubgameEntity, gameExports: GameExportsImpl): Boolean {
                    return think.invoke(self, gameExports)
                }

                override val iD: String = id
            }
            register(id, adapter)
            return adapter
        }

        /** Adapter repository.  */
        private val adapters: MutableMap<String, SuperAdapter> = HashMap()

        /**
         * Returns the adapter from the repository given by its ID.
         */
        @JvmStatic
        fun getFromID(key: String): SuperAdapter? {
            return adapters[key]
        }

        /** Writes the Adapter-ID to the file.  */
        @JvmStatic
        fun writeAdapter(f: QuakeFile, a: SuperAdapter?) {
            f.writeInt(3988)
            if (a == null)
                f.writeString(null)
            else {
                f.writeString(a.iD)
            }
        }

        /** Reads the adapter id and returns the adapter.  */
        @JvmStatic
        fun readAdapter(f: QuakeFile): SuperAdapter? {
            if (f.readInt() != 3988) {
                // replace with log
                // Com.DPrintf("wrong read position: readadapter 3988 \n");
            }
            val id = f.readString() ?: return null
            return getFromID(id)
        }
    }
}
