/*
 * sfx_t.java
 * Copyright (C) 2004
 * 
 * $Id: sfx_t.java,v 1.3 2005-04-26 20:11:03 cawe Exp $
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

// Created on 28.11.2003 by RST.

package jake2.sound;

public class sfx_t {
	public String name;
	public int registration_sequence;
	public sfxcache_t cache;
	public String truename;
	
	// is used for AL buffers
	public int bufferId = -1;
	public boolean isCached = false;
	
	public void clear() {
		name = truename = null;
		cache = null;
		registration_sequence = 0;
		bufferId = -1; 
		isCached = false;
	}
}
