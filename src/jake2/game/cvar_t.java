/*
 * cvar_t.java
 * Copyright (C) 2003
 * 
 * $Id: cvar_t.java,v 1.2 2004-07-08 15:58:44 hzi Exp $
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
package jake2.game;

/**
 * cvar_t implements the struct cvar_t of the C version
 */
public final class cvar_t
{
	public String name;
	public String string;
	public String latched_string;
	public int flags = 0;
	public boolean modified = false;
	public float value = 0.0f;
	public cvar_t next = null;
}
