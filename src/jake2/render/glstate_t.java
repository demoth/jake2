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
// $Id: glstate_t.java,v 1.4 2004-01-22 15:44:40 cwei Exp $

package jake2.render;

public class glstate_t 
{
	public float inverse_intensity;
	public boolean fullscreen;

	public int prev_mode;

	public byte d_16to8table[];

	public int lightmap_textures;

	public int currenttextures[]= {0,0};
	public int currenttmu;

	public float camera_separation;
	public boolean stereo_enabled;

	public byte originalRedGammaTable[]= new byte [256];
	public byte originalGreenGammaTable[]= new byte [256];
	public byte originalBlueGammaTable[]= new byte [256];

}
