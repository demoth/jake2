/*
 * sizebuf_t.java
 * Copyright (C) 2003
 * 
 * $Id: sizebuf_t.java,v 1.1 2004-07-07 19:59:34 hzi Exp $
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
package jake2.qcommon;

import java.util.Arrays;

/**
 * sizebuf_t
 */
public final class sizebuf_t {
	public boolean allowoverflow = false;
	public boolean overflowed = false;
	public byte[] data = null;
	public int maxsize = 0;
	public int cursize = 0;
	public int readcount = 0;
	
	public void clear()
	{
		if (data!=null)		
			Arrays.fill(data,(byte)0);
		cursize = 0;
		overflowed = false;
	}
}
