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
// $Id: entity_t.java,v 1.5 2004-02-13 22:04:00 rst Exp $

package jake2.client;


import jake2.render.*;

// ok!
public class entity_t {
	//reference
	public				model_t				model;			// opaque type outside refresh
	public float				angles[]={0,0,0};

	/*
	** most recent data
	*/
	public float				origin[]={0,0,0};		// also used as RF_BEAM's "from"
	public int				frame;			// also used as RF_BEAM's diameter

	/*
	** previous data for lerping
	*/
	public float				oldorigin[]={0,0,0};// also used as RF_BEAM's "to"
	public int				oldframe;

	/*
	** misc
	*/
	public float				backlerp;		// 0.0 = current, 1.0 = old
	public int				skinnum;		// also used as RF_BEAM's palette index

	public int				lightstyle;		// for flashing entities
	public float				alpha;			// ignore if RF_TRANSLUCENT isn't set

	// reference
	public image_t				skin;			// NULL for inline skin
	public int				flags;
}
