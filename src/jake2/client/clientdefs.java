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

package jake2.client;

public class clientdefs {

	public static final int MAX_PARSE_ENTITIES = 1024; 
	public static final int MAX_CLIENTWEAPONMODELS	= 20;

	public static int CMD_BACKUP = 64; // allow a lot of command backups for very fast systems	
	

	public static final int ca_uninitialized = 0; 	
	public static final int ca_disconnected = 1;
	public static final int ca_connecting = 2;	
	public static final int ca_connected = 3; 	
	public static final int ca_active = 4; 
}
