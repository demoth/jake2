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

package jake2.render;

import jake2.qcommon.*;
import jake2.game.*;

public class model_t {
	String			name;

	int			registration_sequence;

	// was enum modtype_t
	int	type;
	int			numframes;
	
	int			flags;

//
// volume occupied by the model graphics
//		
	float []		mins={0,0,0}, maxs={0,0,0};
	float			radius;

//
// solid volume for clipping 
//
	boolean	clipbox;
	float 		clipmins[]={0,0,0}, clipmaxs[]={0,0,0};

//
// brush model
//
	int			firstmodelsurface, nummodelsurfaces;
	int			lightmap;		// only for submodels

	int			numsubmodels;
	mmodel_t	submodels[];

	int			numplanes;
	cplane_t	planes[];

	int			numleafs;		// number of visible leafs, not counting 0
	mleaf_t		leafs[];

	int			numvertexes;
	mvertex_t	vertexes[];

	int			numedges;
	medge_t		edges[];

	int			numnodes;
	int			firstnode;
	mnode_t		nodes[];

	int			numtexinfo;
	mtexinfo_t	texinfo[];

	int			numsurfaces;
	msurface_t	surfaces[];

	int			numsurfedges;
	int			surfedges[];

	int			nummarksurfaces;
	// was **marksurfaces;
	msurface_t	marksurfaces[];

	dvis_t		vis;

	byte		lightdata[];

	// for alias models and skins
	// was image_t *skins[]; (array of pointers)
	image_t		skins[]= new image_t[qcommondefs.MAX_MD2SKINS];

	int		extradatasize;
	
	// or whatever
	byte		extradata[];
}
