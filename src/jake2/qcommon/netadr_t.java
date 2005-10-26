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
package jake2.qcommon;

import jake2.Defines;
import jake2.sys.NET;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class netadr_t {

    public int type;

    public int port;

    public byte ip[];

    public netadr_t() {
        this.type = Defines.NA_LOOPBACK;
        this.port = 0; // any
        try {
        	// localhost / 127.0.0.1
            this.ip = InetAddress.getByName(null).getAddress();
        } catch (UnknownHostException e) {
        }
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        switch (type) {
        case Defines.NA_BROADCAST:
            return InetAddress.getByName("255.255.255.255");
        case Defines.NA_LOOPBACK:
        	// localhost / 127.0.0.1
            return InetAddress.getByName(null);
        case Defines.NA_IP:
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
        return (type == Defines.NA_LOOPBACK) ? "loopback" : NET
                .AdrToString(this);
    }
}