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

// Created on 02.01.2004 by RST.
// $Id: miptex_t.java,v 1.1 2004-07-07 19:59:34 hzi Exp $

package jake2.qcommon;


import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

public class miptex_t {
	//char		name[32];
	String		name="";
	
	int		width, height;
	
	//unsigned	offsets[MIPLEVELS];		// four mip maps stored
	int		offsets[] = new int[Defines.MIPLEVELS];		// four mip maps stored
	
	//char		animname[32];			// next frame in animation chain
	String 		animframe="";
	
	int			flags;
	int			contents;
	int			value;
}
