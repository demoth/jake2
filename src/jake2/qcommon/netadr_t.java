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
// $Id: netadr_t.java,v 1.4 2004-10-17 20:33:18 cawe Exp $
package jake2.qcommon;

import jake2.Defines;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class netadr_t {

    public int type;

    public int port;

    public byte ip[];

    InetAddress ia = null;

    public netadr_t() {
        this.type = Defines.NA_LOOPBACK;
        this.port = 0; // any
        try {
            this.ip = InetAddress.getByName("localhost").getAddress();
        } catch (UnknownHostException e) {
        }
    }

    public InetAddress getInetAddress() throws UnknownHostException {
        if (type == Defines.NA_BROADCAST) {
            ia = InetAddress.getByName("255.255.255.255");
        } else if (type == Defines.NA_LOOPBACK) {
            ia = InetAddress.getByName("localhost");
        } else if (ia == null) {
            ia = InetAddress.getByAddress(ip);
        }
        return ia;
    }

    public void set(netadr_t from) {
        type = from.type;
        port = from.port;
        ip[0] = from.ip[0];
        ip[1] = from.ip[1];
        ip[2] = from.ip[2];
        ip[3] = from.ip[3];
    }
}