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

// Created on 07.11.2003 by RST.
// $Id: EdictIterator.java,v 1.3 2005-02-06 18:55:16 salomo Exp $

package jake2.game;

/** Helps for iterating over the gedicts[] array. RST.*/

public class EdictIterator
{
	EdictIterator(int i)
	{
		this.i = i;
	}
	public edict_t o;
	int i;
}