/*
 * NetChannel.java
 * Copyright (C) 2003
 */
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
package jake2.qcommon.network

import jake2.qcommon.Defines
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.sys.Timer
import java.util.function.IntSupplier

/** Live configuration for one network owner; reading settings never creates transport resources. */
interface NetworkSettings {
    val bindAddress: String
    val serverPort: Int
    val clientPort: Int
    val qport: Int
    val logPackets: Boolean
    val logDrops: Boolean
    val logMessages: Boolean

    companion object {
        /** Declare protected settings after startup config has supplied early values. */
        @JvmStatic @JvmOverloads
        fun registerCvars(cvars: Cvar = Cvar.getInstance(), nextQport: IntSupplier = IntSupplier { Timer.Milliseconds() }): NetworkSettings {
            cvars.register("port", CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_SERVER, Defines.CVAR_NOSET,
                "Server UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent")
            cvars.register("ip", CvarTypes.STRING, "localhost", Defines.CVAR_NOSET,
                "Local bind address; empty or localhost binds all interfaces")
            cvars.register("clientport", CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_CLIENT, Defines.CVAR_NOSET,
                "Client UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent")
            cvars.register("showpackets", CvarTypes.BOOLEAN, false, 0, "Log packet sizes, sequences and acknowledgements")
            cvars.register("showdrop", CvarTypes.BOOLEAN, false, 0, "Log dropped or out-of-order packets")
            cvars.register("sv_print_network", CvarTypes.BOOLEAN, false, 0, "Log decoded incoming network messages")
            // Reinitializing networks retains the process's original random qport declaration.
            if (cvars.isRegistered("qport")) cvars.find("qport", Int::class.javaObjectType)
            else cvars.register("qport", CvarTypes.IntegerType(0, 65535),
                nextQport.asInt and 0xffff, Defines.CVAR_NOSET,
                "Client connection identifier for NAT port changes; not a UDP socket port")
            return CvarNetworkSettings(cvars)
        }
    }
}

/**
 * Cvar-backed settings at the engine composition boundary. Construction does not declare settings:
 * an application may create its network owners before startup config is loaded and registered.
 * Resolve through the supplied registry on each read, so live edits and registry resets cannot leave
 * a network holding stale Cvar handles. Unregistered diagnostics remain disabled during startup.
 */
class CvarNetworkSettings(private val cvars: Cvar) : NetworkSettings {
    override val bindAddress: String get() = cvars.find("ip", String::class.java).typedValue
    override val serverPort: Int get() = cvars.find("port", Int::class.javaObjectType).typedValue
    override val clientPort: Int get() = cvars.find("clientport", Int::class.javaObjectType).typedValue
    override val qport: Int get() = cvars.find("qport", Int::class.javaObjectType).typedValue
    override val logPackets: Boolean get() = flag("showpackets")
    override val logDrops: Boolean get() = flag("showdrop")
    override val logMessages: Boolean get() = flag("sv_print_network")
    private fun flag(name: String): Boolean = cvars.isRegistered(name) && cvars.find(name, Boolean::class.javaObjectType).typedValue
}
