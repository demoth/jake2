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
package jake2.qcommon.network;

import jake2.qcommon.Com;
import jake2.qcommon.util.Lib;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static jake2.qcommon.network.NET.net_local_adr;

public class netadr_t {

    public NetAddrType type;

    public int port;

    public byte[] ip;

    /**
     * @param s String address, like 10.132.12.54:4312
     * @return null if the string s is not a valid address
     */
    public static netadr_t fromString(String s, int defaultPort) {
        if (s.equalsIgnoreCase("localhost") || s.equalsIgnoreCase("loopback")) {
            return net_local_adr;
        } else {
            try {
                netadr_t result = new netadr_t();
                String[] address = s.split(":");
                InetAddress ia = InetAddress.getByName(address[0]);
                result.ip = ia.getAddress();
                result.type = NetAddrType.NA_IP;
                if (address.length == 2)
                    result.port = Lib.atoi(address[1]);
                if (result.port == 0)
                    result.port = defaultPort;
                return result;
            } catch (Exception e) {
                Com.Println(e.getMessage());
                return null;
            }
        }
    }

    public netadr_t() {
        this.type = NetAddrType.NA_LOOPBACK;
        this.port = 0; // any
        try {
        	// localhost / 127.0.0.1
            this.ip = InetAddress.getByName(null).getAddress();
        } catch (UnknownHostException e) {
        }
    }

    /**
     * Seems to return true, if the address is is on 127.0.0.1.
     */
    public boolean IsLocalAddress() {
        return this.equals(net_local_adr);
    }

    public boolean compareIp(Object obj) {
        if (!(obj instanceof netadr_t))
            return false;
        netadr_t other = (netadr_t) obj;

        return (this.ip[0] == other.ip[0]
                && this.ip[1] == other.ip[1]
                && this.ip[2] == other.ip[2]
                && this.ip[3] == other.ip[3]
                && this.port == other.port);
    }
    /**
     * Compares ip address without the port.
     * Returns true for a LOOPBACK type - assuming that singleplayer client is the first one
     */
    public boolean CompareBaseAdr(netadr_t other) {
        if (this.type != other.type)
            return false;

        if (this.type == NetAddrType.NA_LOOPBACK)
            return true;

        if (this.type == NetAddrType.NA_IP) {
            return this.ip[0] == other.ip[0]
                    && this.ip[1] == other.ip[1]
                    && this.ip[2] == other.ip[2]
                    && this.ip[3] == other.ip[3];
        }
        return false;
    }


    public InetAddress getInetAddress() throws UnknownHostException {
        switch (type) {
        case NA_BROADCAST:
            return InetAddress.getByName("255.255.255.255");
        case NA_LOOPBACK:
        	// localhost / 127.0.0.1
            return InetAddress.getByName(null);
        case NA_IP:
            return InetAddress.getByAddress(ip);
        default:
            return null;
        }
    }

    public void set(netadr_t from) {
        type = from.type;
        port = from.port;
        ip[0] = from.ip[0];
        ip[1] = from.ip[1];
        ip[2] = from.ip[2];
        ip[3] = from.ip[3];
    }

    public String toString() {
        return new StringBuilder()
                .append(ip[0] & 0xFF).append('.')
                .append(ip[1] & 0xFF).append('.')
                .append(ip[2] & 0xFF).append('.')
                .append(ip[3] & 0xFF).append(':')
                .append(port).toString();

    }
}