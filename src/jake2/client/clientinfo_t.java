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
//$Id: clientinfo_t.java,v 1.3 2003-11-29 13:48:49 rst Exp $

package jake2.client;

import jake2.*;
import jake2.render.*;

public class clientinfo_t {
	String	name;
	String	cinfo;
	image_t skin;
	image_t icon;
	String iconname;;
	model_t model;
	model_t weaponmodel[] = new model_t[Defines.MAX_CLIENTWEAPONMODELS];
}
