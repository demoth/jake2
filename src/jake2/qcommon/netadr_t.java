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

// Created on 27.11.2003 by RST.
// $Id: netadr_t.java,v 1.4 2004-02-05 21:32:40 rst Exp $

package jake2.qcommon;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class netadr_t {
	 

	public int type;
	public int port;
	public byte ip[] = {1,2,3,4};
	//public byte ipx[] = new byte[10];
	
	InetAddress ia = null;
	
	public InetAddress getInetAddress() throws UnknownHostException 
	{
		if (ia == null)
			ia = InetAddress.getByAddress(ip);

		return ia;
	}	
}
