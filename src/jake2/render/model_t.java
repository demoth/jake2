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
// $Id: model_t.java,v 1.8 2004-01-06 02:06:44 cwei Exp $

package jake2.render;

import jake2.qcommon.*;
import jake2.*;
import jake2.game.*;

public class model_t {
	
	public String name = "";

	public int registration_sequence;

	// was enum modtype_t
	public int type;
	public int numframes;

	public int flags;

	//
	// volume occupied by the model graphics
	//		
	public float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };
	public float radius;

	//
	// solid volume for clipping 
	//
	public boolean clipbox;
	public float clipmins[] = { 0, 0, 0 }, clipmaxs[] = { 0, 0, 0 };

	//
	// brush model
	//
	public int firstmodelsurface, nummodelsurfaces;
	public int lightmap; // only for submodels

	public int numsubmodels;
	public mmodel_t submodels[];

	public int numplanes;
	public cplane_t planes[];

	public int numleafs; // number of visible leafs, not counting 0
	public mleaf_t leafs[];

	public int numvertexes;
	public mvertex_t vertexes[];

	public int numedges;
	public medge_t edges[];

	public int numnodes;
	public int firstnode;
	public mnode_t nodes[];

	public int numtexinfo;
	public mtexinfo_t texinfo[];

	public int numsurfaces;
	public msurface_t surfaces[];

	public int numsurfedges;
	public int surfedges[];

	public int nummarksurfaces;
	// was **marksurfaces;
	public msurface_t marksurfaces[];

	public qfiles.dvis_t vis;

	public byte lightdata[];

	// for alias models and skins
	// was image_t *skins[]; (array of pointers)
	public image_t skins[] = new image_t[Defines.MAX_MD2SKINS];

	public int extradatasize;

	// or whatever
	public Object extradata;
	
	public void clear() {
		name = "";
		extradata = null;
		registration_sequence = 0;
		// TODO and so on
	}
}
