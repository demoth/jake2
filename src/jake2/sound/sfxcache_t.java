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

// Created on 28.11.2003 by RST.
// $Id: sfxcache_t.java,v 1.1 2004-07-08 20:56:49 hzi Exp $

package jake2.sound;

public class sfxcache_t {
	public int length;
	public int loopstart;
	public int speed;			// not needed, because converted on load?
	public int width;
	public int stereo;
	public byte data[];		// variable sized
	
	public sfxcache_t(int size) {
		data = new byte[size];
	}
}
