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

// Created on 27.11.2003 by RST.
// $Id: netadr_t.java,v 1.6 2005-10-26 12:37:58 cawe Exp $
package jake2.qcommon.network.transport

import java.net.InetAddress
import java.net.UnknownHostException

/** Parsed connection intent; local aliases are resolved against the owner's explicit local peer. */
sealed interface AddressTarget {
    data object Local : AddressTarget
    data class Host(val name: String, val port: Int) : AddressTarget {
        init { require(name.isNotEmpty() && port in 1..65535) }
    }
}

/** Address parsing creates no sockets. DNS resolution is explicit and injectable. */
object AddressResolver {
    /**
     * Parse a host, IPv4 host:port (e.g. 10.132.12.54:4312), or bracketed IPv6 with an optional port.
     * Returns null for invalid text; localhost/loopback without a port designate an in-process peer.
     */
    @JvmStatic fun parse(text: String, defaultPort: Int): AddressTarget? {
        require(defaultPort in 1..65535)
        if (text.equals("localhost", true) || text.equals("loopback", true)) return AddressTarget.Local
        if (text.isEmpty() || text.any { it.isWhitespace() || it.isISOControl() }) return null
        val host: String
        val portText: String?
        if (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 2) return null
            host = text.substring(1, end)
            if (':' !in host || '[' in host) return null
            val suffix = text.substring(end + 1)
            if (suffix.isNotEmpty() && !suffix.startsWith(":")) return null
            portText = if (suffix.isEmpty()) null else suffix.substring(1)
        } else {
            if ('[' in text || ']' in text) return null
            val colon = text.indexOf(':')
            if (colon >= 0 && colon == text.lastIndexOf(':')) {
                host = text.substring(0, colon)
                portText = text.substring(colon + 1)
            } else {
                host = text
                portText = null
            }
        }
        if (host.isEmpty()) return null
        val port = if (portText == null) defaultPort else portText.toIntOrNull()?.takeIf { it in 0..65535 } ?: return null
        // Preserve the legacy explicit-zero port fallback.
        return AddressTarget.Host(host, if (port == 0) defaultPort else port)
    }

    /** Resolve a parsed host without losing IPv6 scope identity; local connections never consult DNS. */
    fun resolve(target: AddressTarget, localPeer: NetworkAddress.Local,
                lookup: (String) -> InetAddress = InetAddress::getByName): NetworkAddress? = try {
        when (target) {
            AddressTarget.Local -> localPeer
            is AddressTarget.Host -> NetworkAddress.Udp(IpAddress.fromInetAddress(lookup(target.name)), target.port)
        }
    } catch (_: UnknownHostException) { null }

    @JvmStatic fun resolve(text: String, defaultPort: Int, localPeer: NetworkAddress.Local): NetworkAddress? =
        parse(text, defaultPort)?.let { resolve(it, localPeer) }

    /** UI validation uses the same parser and DNS policy, without needing a transport or local-link ID. */
    @JvmStatic fun isValid(text: String, defaultPort: Int): Boolean = when (val target = parse(text, defaultPort)) {
        null -> false
        AddressTarget.Local -> true
        is AddressTarget.Host -> try { InetAddress.getByName(target.name); true } catch (_: UnknownHostException) { false }
    }
}
