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

import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.qcommon.cplane_t
import jake2.qcommon.csurface_t
import java.util.concurrent.ConcurrentHashMap

/**
 * There are many fields in the [GameEntity] class. Some of them represent state - like 'health', 'damage' etc,
 * other represent behaviour - like 'think', 'touch', 'use' etc.
 * In c version these were function pointers.
 *
 *
 * The purpose of all Adapter registration is to store and restore such behavioural edict fields.
 */
abstract class SuperAdapter(val id: String) {
    init {
        require(id.isNotBlank()) { "Callback ID must not be blank" }
    }

    override fun toString(): String {
        return "${this.javaClass.simpleName} '$id'"
    }

    override fun equals(other: Any?): Boolean {
        return if (other is SuperAdapter) {
            id == other.id
        } else false
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        private val adapters = ConcurrentHashMap<String, SuperAdapter>()

        @JvmStatic
        fun <T : SuperAdapter> register(adapter: T): T {
            check(adapters.putIfAbsent(adapter.id, adapter) == null) {
                "Duplicate callback ID: ${adapter.id}"
            }
            return adapter
        }

        fun registerThink(id: String, think: (self: GameEntity, game: GameExportsImpl) -> Boolean): EntThinkAdapter {
            return register(object : EntThinkAdapter(id) {
                override fun think(self: GameEntity, game: GameExportsImpl): Boolean {
                    return think.invoke(self, game)
                }
            })
        }

        fun registerBlocked(id: String, block: (self: GameEntity, obstacle: GameEntity, game: GameExportsImpl ) -> Unit): EntBlockedAdapter {
            return register(object : EntBlockedAdapter(id) {
                override fun blocked(self: GameEntity, obstacle: GameEntity, game: GameExportsImpl) {
                    block.invoke(self, obstacle, game)
                }
            })
        }

        fun registerUse(id: String, use: (self: GameEntity, other: GameEntity?, activator: GameEntity?, game: GameExportsImpl) -> Unit): EntUseAdapter {
            return register(object : EntUseAdapter(id) {
                override fun use(
                    self: GameEntity,
                    other: GameEntity?,
                    activator: GameEntity?,
                    gameExports: GameExportsImpl
                ) {
                    use.invoke(self, other, activator, gameExports)
                }
            })
        }

        fun registerTouch(id: String, touch: (self: GameEntity, other: GameEntity, plane: cplane_t, surf: csurface_t?, game: GameExportsImpl) -> Unit): EntTouchAdapter {
            return register(object : EntTouchAdapter(id) {
                override fun touch(
                    self: GameEntity,
                    other: GameEntity,
                    plane: cplane_t,
                    surf: csurface_t?,
                    game: GameExportsImpl
                ) {
                    touch.invoke(self, other, plane, surf, game)
                }
            })
        }

        fun registerDie(id:String, die: (self: GameEntity, inflictor: GameEntity?, attacker: GameEntity?, damage: Int, point: FloatArray?, game: GameExportsImpl) -> Unit): EntDieAdapter {
            return register(object : EntDieAdapter(id) {
                override fun die(
                    self: GameEntity,
                    inflictor: GameEntity?,
                    attacker: GameEntity?,
                    damage: Int,
                    point: FloatArray?,
                    gameExports: GameExportsImpl
                ) {
                    die.invoke(self, inflictor, attacker, damage, point, gameExports)
                }
            })
        }

        /**
         * Returns the adapter from the repository given by its ID.
         */
        @JvmStatic
        fun getFromID(key: String): SuperAdapter? {
            return adapters[key]
        }

        fun think(s: String): EntThinkAdapter {
            val adapter = adapters[s]
            if (adapter is EntThinkAdapter)
                return adapter
            else
                throw IllegalStateException("No such think adapter: $s")
        }
    }
}
