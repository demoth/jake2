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

// Created on 20.11.2003 by RST.
// $Id: mmodel_t.java,v 1.3 2004-01-20 16:15:41 cwei Exp $

package jake2.render;

public class mmodel_t
{
	public float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };
	public float[] origin = { 0, 0, 0 }; // for sounds or lights
	public float radius;
	public int headnode;
	public int visleafs; // not including the solid leaf 0
	public int firstface, numfaces;
}
