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
// Created on 20.11.2003 by RST
// $Id: field_t.java,v 1.4 2004-02-29 00:51:04 rst Exp $

package jake2.game;

public class field_t
{
	public field_t(String name, int type, int flags)
	{
		this.name = name;
		this.type = type;
		this.flags = flags;
	}

	public field_t(String name, int type)
	{

		this.name = name;
		this.type = type;
		flags = 0;
	}

	public String name;
	public int type;
	public int flags;
}
