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
// $Id: CM.java,v 1.3 2004-01-04 02:07:45 rst Exp $

package jake2.qcommon;

import java.util.Arrays;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

public class CM extends PlayerHud {

	public static class cnode_t {
		cplane_t plane;
		int children[] = { 0, 0 }; // negative numbers are leafs
	}

	public static class cbrushside_t {
		cplane_t plane;
		mapsurface_t surface;
	}

	public static class cleaf_t {
		int contents;
		int cluster;
		int area;
		//unsigned short	firstleafbrush;
		//unsigned short	numleafbrushes;

		short firstleafbrush;
		short numleafbrushes;
	}

	public static class cbrush_t {
		int contents;
		int numsides;
		int firstbrushside;
		int checkcount; // to avoid repeated testings
	}

	public static class carea_t {
		int numareaportals;
		int firstareaportal;
		int floodnum; // if two areas have equal floodnums, they are connected
		int floodvalid;
	}

	static int checkcount;

	static String map_name = "";

	static int numbrushsides;
	static cbrushside_t map_brushsides[] = new cbrushside_t[MAX_MAP_BRUSHSIDES];

	static int numtexinfo;
	static mapsurface_t map_surfaces[] = new mapsurface_t[MAX_MAP_TEXINFO];

	static int numplanes;
	static cplane_t map_planes[] = new cplane_t[MAX_MAP_PLANES + 6]; // extra for box hull

	static int numnodes;
	static cnode_t map_nodes[] = new cnode_t[MAX_MAP_NODES + 6]; // extra for box hull

	static int numleafs = 1; // allow leaf funcs to be called without a map
	static cleaf_t map_leafs[] = new cleaf_t[MAX_MAP_LEAFS];
	static int emptyleaf, solidleaf;

	static int numleafbrushes;
	//static unsigned short	map_leafbrushes[MAX_MAP_LEAFBRUSHES];
	static int map_leafbrushes[] = new int[MAX_MAP_LEAFBRUSHES];
	static int numcmodels;
	static cmodel_t map_cmodels[] = new cmodel_t[MAX_MAP_MODELS];

	static int numbrushes;
	static cbrush_t map_brushes[] = new cbrush_t[MAX_MAP_BRUSHES];

	static int numvisibility;
	//static byte map_visibility[] = new byte[MAX_MAP_VISIBILITY];

	//static dvis_t		*map_vis = (dvis_t *)map_visibility;
	// main visibility data. rst
	static dvis_t map_vis;

	static int numentitychars;
	//static char		map_entitystring[MAX_MAP_ENTSTRING];
	static String map_entitystring;

	static int numareas = 1;
	static carea_t map_areas[] = new carea_t[MAX_MAP_AREAS];

	static int numareaportals;
	static dareaportal_t map_areaportals[] = new dareaportal_t[MAX_MAP_AREAPORTALS];

	static int numclusters = 1;

	static mapsurface_t nullsurface;

	static int floodvalid;

	static boolean portalopen[] = new boolean[MAX_MAP_AREAPORTALS];

	static cvar_t map_noareas;

	static int c_pointcontents;
	static int c_traces, c_brush_traces;

	/*
	===============================================================================
	
						MAP LOADING
	
	===============================================================================
	*/

	static byte cmod_base[];

	static int checksum;
	static int last_checksum;
	/*
	==================
	CM_LoadMap
	
	Loads in the map and all submodels
	==================
	*/
	static cmodel_t CM_LoadMap(String name, boolean clientload) {
		Com.DPrintf("CM_LoadMap...\n");
		byte buf[];
		int i;
		dheader_t header;
		int length;

		map_noareas = Cvar.Get("map_noareas", "0", 0);

		if (0 == strcmp(map_name, name) && (clientload || 0 == Cvar.VariableValue("flushmap"))) {
			checksum = last_checksum;
			if (!clientload) {
				//memset(portalopen, 0, sizeof(portalopen));
				Arrays.fill(portalopen, false);
				
				
				FloodAreaConnections();
			}
			return map_cmodels[0]; // still have the right version
		}

		// free old stuff
		numplanes = 0;
		numnodes = 0;
		numleafs = 0;
		numcmodels = 0;
		numvisibility = 0;
		numentitychars = 0;
		map_entitystring = "";
		map_name = "";

		if (name == null || name.length() == 0) {
			numleafs = 1;
			numclusters = 1;
			numareas = 1;
			checksum = 0;
			return map_cmodels[0]; // cinematic servers won't have anything at all
		}

		//
		// load the file
		//
		buf = FS.LoadFile(name);

		if (buf == null)
			Com.Error(ERR_DROP, "Couldn't load " + name);

		length = buf.length;

		ByteBuffer bbuf = ByteBuffer.wrap(buf);

		//last_checksum = LittleLong(Com.BlockChecksum(buf, length));
		checksum = last_checksum;

		header = new dheader_t(bbuf.slice());

		/* already done.
		for (i = 0; i < sizeof(dheader_t) / 4; i++)
			 ((int *) & header)[i] = LittleLong(((int *) & header)[i]);
		*/

		if (header.version != BSPVERSION)
			Com.Error(
				ERR_DROP,
				"CMod_LoadBrushModel: " + name + " has wrong version number (" + header.version + " should be " + BSPVERSION + ")");

		cmod_base = buf;

		// load into heap
		CMod_LoadSurfaces(header.lumps[LUMP_TEXINFO]);
		CMod_LoadLeafs(header.lumps[LUMP_LEAFS]);
		CMod_LoadLeafBrushes(header.lumps[LUMP_LEAFBRUSHES]);
		CMod_LoadPlanes(header.lumps[LUMP_PLANES]);
		CMod_LoadBrushes(header.lumps[LUMP_BRUSHES]);
		CMod_LoadBrushSides(header.lumps[LUMP_BRUSHSIDES]);
		CMod_LoadSubmodels(header.lumps[LUMP_MODELS]);

		CMod_LoadNodes(header.lumps[LUMP_NODES]);
		CMod_LoadAreas(header.lumps[LUMP_AREAS]);
		CMod_LoadAreaPortals(header.lumps[LUMP_AREAPORTALS]);
		CMod_LoadVisibility(header.lumps[LUMP_VISIBILITY]);
		CMod_LoadEntityString(header.lumps[LUMP_ENTITIES]);
		 
		FS.FreeFile(buf);
		
		
		//TODO:port next
		//CM_InitBoxHull();
		
		//memset(portalopen, 0, sizeof(portalopen));
		Arrays.fill(portalopen, false);
		
		FloodAreaConnections();
		
		map_name=name;
		
		System.out.println("ok!");
		return map_cmodels[0];
	}

	/*
	=================
	CMod_LoadSubmodels
	=================
	*/
	static void CMod_LoadSubmodels(lump_t l) {
		Com.DPrintf("CMod_LoadSubmodels...\n");
		dmodel_t in;
		cmodel_t out;
		int i, j, count;

		//in = (cmod_base + l.fileofs);

		if ((l.filelen % dmodel_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / dmodel_t.SIZE;

		if (count < 1)
			Com.Error(ERR_DROP, "Map with no models");
		if (count > MAX_MAP_MODELS)
			Com.Error(ERR_DROP, "Map has too many models");

		numcmodels = count;

		for (i = 0; i < count; i++) {
			in = new dmodel_t(ByteBuffer.wrap(cmod_base, i * dmodel_t.SIZE + l.fileofs, dmodel_t.SIZE));
			out = map_cmodels[i];
			if (out == null)
				out = map_cmodels[i] = new cmodel_t();

			for (j = 0; j < 3; j++) { // spread the mins / maxs by a pixel
				out.mins[j] = in.mins[j] - 1;
				out.maxs[j] = in.maxs[j] + 1;
				out.origin[j] = in.origin[j];
			}
			out.headnode = in.headnode;
		}
	}

	/*
	=================
	CMod_LoadSurfaces
	=================
	*/
	static void CMod_LoadSurfaces(lump_t l) {
		Com.DPrintf("CMod_LoadSurfaces...\n");
		texinfo_t in;
		mapsurface_t out;
		int i, count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % texinfo_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / texinfo_t.SIZE;
		if (count < 1)
			Com.Error(ERR_DROP, "Map with no surfaces");
		if (count > MAX_MAP_TEXINFO)
			Com.Error(ERR_DROP, "Map has too many surfaces");

		numtexinfo = count;

		for (i = 0; i < count; i++) {

			out = map_surfaces[i] = new mapsurface_t();
			in = new texinfo_t(cmod_base, l.fileofs + i * texinfo_t.SIZE, texinfo_t.SIZE);

			/*
			strncpy(out.c.name, in.texture, sizeof(out.c.name) - 1);
			strncpy(out.rname, in.texture, sizeof(out.rname) - 1);
						
			out.c.flags = LittleLong(in.flags);
			out.c.value = LittleLong(in.value);
			*/

			out.c.name = in.texture;
			out.rname = in.texture;
			out.c.flags = in.flags;
			out.c.value = in.value;
		}
	}

	/*
	=================
	CMod_LoadNodes
	
	=================
	*/
	static void CMod_LoadNodes(lump_t l) {
		Com.DPrintf("CMod_LoadNodes...\n");
		dnode_t in;
		int child;
		cnode_t out;
		int i, j, count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % dnode_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size:" + l.fileofs + "," + dnode_t.SIZE);
		count = l.filelen / dnode_t.SIZE;

		if (count < 1)
			Com.Error(ERR_DROP, "Map has no nodes");
		if (count > MAX_MAP_NODES)
			Com.Error(ERR_DROP, "Map has too many nodes");

		numnodes = count;

		for (i = 0; i < count; i++) {
			in = new dnode_t(ByteBuffer.wrap(cmod_base, dnode_t.SIZE * i + l.fileofs, dnode_t.SIZE));
			out = map_nodes[i];
			if (out == null)
				out = map_nodes[i] = new cnode_t();

			out.plane = map_planes[in.planenum];
			for (j = 0; j < 2; j++) {
				child = in.children[j];
				out.children[j] = child;
			}
		}

	}

	/*
	=================
	CMod_LoadBrushes
	
	=================
	*/
	static void CMod_LoadBrushes(lump_t l) {
		Com.DPrintf("CMod_LoadBrushes...\n");
		dbrush_t in;
		cbrush_t out;
		int i, count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % dbrush_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / dbrush_t.SIZE;

		if (count > MAX_MAP_BRUSHES)
			Com.Error(ERR_DROP, "Map has too many brushes");

		numbrushes = count;

		for (i = 0; i < count; i++) {
			in = new dbrush_t(ByteBuffer.wrap(cmod_base, i * dbrush_t.SIZE + l.fileofs, dbrush_t.SIZE));

			out = map_brushes[i];
			if (out == null)
				out = map_brushes[i] = new cbrush_t();

			out.firstbrushside = in.firstside;
			out.numsides = in.numsides;
			out.contents = in.contents;
		}

	}

	/*
	=================
	CMod_LoadLeafs
	=================
	*/
	static void CMod_LoadLeafs(lump_t l) {
		Com.DPrintf("CMod_LoadLeafs...\n");
		int i;
		cleaf_t out;
		dleaf_t in;
		int count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % dleaf_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / dleaf_t.SIZE;

		if (count < 1)
			Com.Error(ERR_DROP, "Map with no leafs");

		// need to save space for box planes
		if (count > MAX_MAP_PLANES)
			Com.Error(ERR_DROP, "Map has too many planes");

		numleafs = count;
		numclusters = 0;

		for (i = 0; i < count; i++) {

			in = new dleaf_t(cmod_base, i * dleaf_t.SIZE + l.fileofs, dleaf_t.SIZE);
			out = map_leafs[i] = new cleaf_t();

			out.contents = in.contents;
			out.cluster = in.cluster;
			out.area = in.area;
			out.firstleafbrush = (short) in.firstleafbrush;
			out.numleafbrushes = (short) in.numleafbrushes;

			if (out.cluster >= numclusters)
				numclusters = out.cluster + 1;
		}

		if (map_leafs[0].contents != CONTENTS_SOLID)
			Com.Error(ERR_DROP, "Map leaf 0 is not CONTENTS_SOLID");

		solidleaf = 0;
		emptyleaf = -1;

		for (i = 1; i < numleafs; i++) {
			if (map_leafs[i].contents == 0) {
				emptyleaf = i;
				break;
			}
		}

		if (emptyleaf == -1)
			Com.Error(ERR_DROP, "Map does not have an empty leaf");
	}

	/*
	=================
	CMod_LoadPlanes
	=================
	*/
	static void CMod_LoadPlanes(lump_t l) {
		Com.DPrintf("CMod_LoadPlanes...\n");
		int i, j;
		cplane_t out;
		dplane_t in;
		int count;
		int bits;

		//in = (void *) (cmod_base + l.fileofs);

		if ((l.filelen % dplane_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / dplane_t.SIZE;

		if (count < 1)
			Com.Error(ERR_DROP, "Map with no planes");

		// need to save space for box planes

		if (count > MAX_MAP_PLANES)
			Com.Error(ERR_DROP, "Map has too many planes");

		numplanes = count;

		for (i = 0; i < count; i++) {
			in = new dplane_t(ByteBuffer.wrap(cmod_base, i * dplane_t.SIZE + l.fileofs, dplane_t.SIZE));

			out = map_planes[i];
			if (out == null)
				out = map_planes[i] = new cplane_t();

			bits = 0;
			for (j = 0; j < 3; j++) {
				out.normal[j] = in.normal[j];

				if (out.normal[j] < 0)
					bits |= 1 << j;
			}

			out.dist = in.dist;
			out.type = (byte) in.type;
			out.signbits = (byte) bits;
		}
	}

	/*
	=================
	CMod_LoadLeafBrushes
	=================
	*/
	static void CMod_LoadLeafBrushes(lump_t l) {
		Com.DPrintf("CMod_LoadLeafBrushes...\n");
		int i;
		int out[];
		short in[];
		int count;

		//in = (void *) (cmod_base + l.fileofs);

		if ((l.filelen % 2) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / 2;

		if (count < 1)
			Com.Error(ERR_DROP, "Map with no planes");

		// need to save space for box planes
		if (count > MAX_MAP_LEAFBRUSHES)
			Com.Error(ERR_DROP, "Map has too many leafbrushes");

		out = map_leafbrushes;
		numleafbrushes = count;

		ByteBuffer bb = ByteBuffer.wrap(cmod_base, l.fileofs, count * 2).order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			out[i] = bb.getShort();
		}
	}

	/*
	=================
	CMod_LoadBrushSides
	=================
	*/
	static void CMod_LoadBrushSides(lump_t l) {
		Com.DPrintf("CMod_LoadBrushSides...\n");
		int i, j;
		cbrushside_t out;
		dbrushside_t in;
		int count;
		int num;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % dbrushside_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");
		count = l.filelen / dbrushside_t.SIZE;

		// need to save space for box planes
		if (count > MAX_MAP_BRUSHSIDES)
			Com.Error(ERR_DROP, "Map has too many planes");

		numbrushsides = count;

		for (i = 0; i < count; i++) {

			in = new dbrushside_t(ByteBuffer.wrap(cmod_base, i * dbrushside_t.SIZE + l.fileofs, dbrushside_t.SIZE));

			out = map_brushsides[i];
			if (out == null)
				out = map_brushsides[i] = new cbrushside_t();
				
			num = in.planenum;
			System.out.println("planenum= " + in.planenum);
			System.out.println("texinfo= " + in.texinfo);
			
			out.plane = map_planes[num];
			
			j = in.texinfo;

			if (j >= numtexinfo)
				Com.Error(ERR_DROP, "Bad brushside texinfo");
				
			//TODO: RST says: some mysterious happens here, even in the original code ???, texinfo is -1!!!
			if (j==-1)
				Com.DPrintf("RST says: some mysterious happens here, even in the original code ???, texinfo is -1!!!");
			else
				out.surface = map_surfaces[j];
		}
	}

	/*
	=================
	CMod_LoadAreas
	=================
	*/
	static void CMod_LoadAreas(lump_t l) {
		Com.DPrintf("CMod_LoadAreas...\n");
		int i;
		carea_t out;
		darea_t in;
		int count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % darea_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");

		count = l.filelen / darea_t.SIZE;

		if (count > MAX_MAP_AREAS)
			Com.Error(ERR_DROP, "Map has too many areas");

		numareas = count;

		for (i = 0; i < count; i++) {

			in = new darea_t(ByteBuffer.wrap(cmod_base, i * darea_t.SIZE + l.fileofs, darea_t.SIZE));
			out = map_areas[i];
			if (out == null)
				out = map_areas[i] = new carea_t();

			out.numareaportals = in.numareaportals;
			out.firstareaportal = in.firstareaportal;
			out.floodvalid = 0;
			out.floodnum = 0;
		}
	}

	/*
	=================
	CMod_LoadAreaPortals
	=================
	*/
	static void CMod_LoadAreaPortals(lump_t l) {
		Com.DPrintf("CMod_LoadAreaPortals...\n");
		int i;
		dareaportal_t out;
		dareaportal_t in;
		int count;

		//in = (void *) (cmod_base + l.fileofs);
		if ((l.filelen % dareaportal_t.SIZE) != 0)
			Com.Error(ERR_DROP, "MOD_LoadBmodel: funny lump size");
		count = l.filelen / dareaportal_t.SIZE;

		if (count > MAX_MAP_AREAS)
			Com.Error(ERR_DROP, "Map has too many areas");

		numareaportals = count;

		for (i = 0; i < count; i++) {
			in = new dareaportal_t(ByteBuffer.wrap(cmod_base, i * dareaportal_t.SIZE + l.fileofs, dareaportal_t.SIZE));
			// dont loose data. rst
			out = map_areaportals[i];
			if (out == null)
				out = map_areaportals[i] = new dareaportal_t();
			
	
			out.portalnum = in.portalnum;
			out.otherarea = in.otherarea;
		}
	}

	/*
	=================
	CMod_LoadVisibility
	=================
	*/
	static void CMod_LoadVisibility(lump_t l) {
		Com.DPrintf("CMod_LoadVisibility...\n");
		int i;

		numvisibility = l.filelen;
		if (l.filelen > MAX_MAP_VISIBILITY)
			Com.Error(ERR_DROP, "Map has too large visibility lump");

		//memcpy(map_visibility, cmod_base + l.fileofs, l.filelen);
		ByteBuffer bb = ByteBuffer.wrap(cmod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		map_vis = new dvis_t(bb);

		/* done 
		map_vis.numclusters = LittleLong(map_vis.numclusters);
		for (i = 0; i < map_vis.numclusters; i++) {
			map_vis.bitofs[i][0] = LittleLong(map_vis.bitofs[i][0]);
			map_vis.bitofs[i][1] = LittleLong(map_vis.bitofs[i][1]);
		}
		*/
	}

	/*
	=================
	CMod_LoadEntityString
	=================
	*/
	static void CMod_LoadEntityString(lump_t l) {
		Com.DPrintf("CMod_LoadEntityString...\n");

		numentitychars = l.filelen;

		if (l.filelen > MAX_MAP_ENTSTRING)
			Com.Error(ERR_DROP, "Map has too large entity lump");

		int x = 0;
		for (; x < l.filelen && cmod_base[x + l.fileofs] != 0; x++);

		map_entitystring = new String(cmod_base, l.fileofs, x);
		//memcpy(map_entitystring, cmod_base + l.fileofs, l.filelen);
	}
	//
	//
	//	/*
	//	==================
	//	CM_InlineModel
	//	==================
	//	*/
	//	cmodel_t * CM_InlineModel(char * name) {
	//		int num;
	//
	//		if (!name || name[0] != '*')
	//			Com.Error(ERR_DROP, "CM_InlineModel: bad name");
	//		num = atoi(name + 1);
	//		if (num < 1 || num >= numcmodels)
	//			Com.Error(ERR_DROP, "CM_InlineModel: bad number");
	//
	//		return & map_cmodels[num];
	//	}
	//
	//	int CM_NumClusters(void) {
	//		return numclusters;
	//	}
	//
	//	int CM_NumInlineModels(void) {
	//		return numcmodels;
	//	}
	//
	//	char * CM_EntityString(void) {
	//		return map_entitystring;
	//	}
	//
	//	int CM_LeafContents(int leafnum) {
	//		if (leafnum < 0 || leafnum >= numleafs)
	//			Com.Error(ERR_DROP, "CM_LeafContents: bad number");
	//		return map_leafs[leafnum].contents;
	//	}
	//
	//	int CM_LeafCluster(int leafnum) {
	//		if (leafnum < 0 || leafnum >= numleafs)
	//			Com.Error(ERR_DROP, "CM_LeafCluster: bad number");
	//		return map_leafs[leafnum].cluster;
	//	}
	//
	//	int CM_LeafArea(int leafnum) {
	//		if (leafnum < 0 || leafnum >= numleafs)
	//			Com.Error(ERR_DROP, "CM_LeafArea: bad number");
	//		return map_leafs[leafnum].area;
	//	}
	//
	//	//=======================================================================
	//
	//	cplane_t * box_planes;
	//	int box_headnode;
	//	cbrush_t * box_brush;
	//	cleaf_t * box_leaf;
	//
	//	/*
	//	===================
	//	CM_InitBoxHull
	//	
	//	Set up the planes and nodes so that the six floats of a bounding box
	//	can just be stored out and get a proper clipping hull structure.
	//	===================
	//	*/
	//	static void CM_InitBoxHull(void) {
	//		int i;
	//		int side;
	//		cnode_t * c;
	//		cplane_t * p;
	//		cbrushside_t * s;
	//
	//		box_headnode = numnodes;
	//		box_planes = & map_planes[numplanes];
	//		if (numnodes + 6 > MAX_MAP_NODES
	//			|| numbrushes + 1 > MAX_MAP_BRUSHES
	//			|| numleafbrushes + 1 > MAX_MAP_LEAFBRUSHES
	//			|| numbrushsides + 6 > MAX_MAP_BRUSHSIDES
	//			|| numplanes + 12 > MAX_MAP_PLANES)
	//			Com.Error(ERR_DROP, "Not enough room for box tree");
	//
	//		box_brush = & map_brushes[numbrushes];
	//		box_brush.numsides = 6;
	//		box_brush.firstbrushside = numbrushsides;
	//		box_brush.contents = CONTENTS_MONSTER;
	//
	//		box_leaf = & map_leafs[numleafs];
	//		box_leaf.contents = CONTENTS_MONSTER;
	//		box_leaf.firstleafbrush = numleafbrushes;
	//		box_leaf.numleafbrushes = 1;
	//
	//		map_leafbrushes[numleafbrushes] = numbrushes;
	//
	//		for (i = 0; i < 6; i++) {
	//			side = i & 1;
	//
	//			// brush sides
	//			s = & map_brushsides[numbrushsides + i];
	//			s.plane = map_planes + (numplanes + i * 2 + side);
	//			s.surface = & nullsurface;
	//
	//			// nodes
	//			c = & map_nodes[box_headnode + i];
	//			c.plane = map_planes + (numplanes + i * 2);
	//			c.children[side] = -1 - emptyleaf;
	//			if (i != 5)
	//				c.children[side ^ 1] = box_headnode + i + 1;
	//			else
	//				c.children[side ^ 1] = -1 - numleafs;
	//
	//			// planes
	//			p = & box_planes[i * 2];
	//			p.type = i >> 1;
	//			p.signbits = 0;
	//			VectorClear(p.normal);
	//			p.normal[i >> 1] = 1;
	//
	//			p = & box_planes[i * 2 + 1];
	//			p.type = 3 + (i >> 1);
	//			p.signbits = 0;
	//			VectorClear(p.normal);
	//			p.normal[i >> 1] = -1;
	//		}
	//	}
	//
	//	/*
	//	===================
	//	CM_HeadnodeForBox
	//	
	//	To keep everything totally uniform, bounding boxes are turned into small
	//	BSP trees instead of being compared directly.
	//	===================
	//	*/
	//	int CM_HeadnodeForBox(vec3_t mins, vec3_t maxs) {
	//		box_planes[0].dist = maxs[0];
	//		box_planes[1].dist = -maxs[0];
	//		box_planes[2].dist = mins[0];
	//		box_planes[3].dist = -mins[0];
	//		box_planes[4].dist = maxs[1];
	//		box_planes[5].dist = -maxs[1];
	//		box_planes[6].dist = mins[1];
	//		box_planes[7].dist = -mins[1];
	//		box_planes[8].dist = maxs[2];
	//		box_planes[9].dist = -maxs[2];
	//		box_planes[10].dist = mins[2];
	//		box_planes[11].dist = -mins[2];
	//
	//		return box_headnode;
	//	}
	//
	//	/*
	//	==================
	//	CM_PointLeafnum_r
	//	
	//	==================
	//	*/
	//	int CM_PointLeafnum_r(vec3_t p, int num) {
	//		float d;
	//		cnode_t * node;
	//		cplane_t * plane;
	//
	//		while (num >= 0) {
	//			node = map_nodes + num;
	//			plane = node.plane;
	//
	//			if (plane.type < 3)
	//				d = p[plane.type] - plane.dist;
	//			else
	//				d = DotProduct(plane.normal, p) - plane.dist;
	//			if (d < 0)
	//				num = node.children[1];
	//			else
	//				num = node.children[0];
	//		}
	//
	//		c_pointcontents++; // optimize counter
	//
	//		return -1 - num;
	//	}
	//
	//	int CM_PointLeafnum(vec3_t p) {
	//		if (!numplanes)
	//			return 0; // sound may call this without map loaded
	//		return CM_PointLeafnum_r(p, 0);
	//	}
	//
	//	/*
	//	=============
	//	CM_BoxLeafnums
	//	
	//	Fills in a list of all the leafs touched
	//	=============
	//	*/
	//	int leaf_count, leaf_maxcount;
	//	int * leaf_list;
	//	float * leaf_mins, * leaf_maxs;
	//	int leaf_topnode;
	//
	//	static void CM_BoxLeafnums_r(int nodenum) {
	//		cplane_t * plane;
	//		cnode_t * node;
	//		int s;
	//
	//		while (1) {
	//			if (nodenum < 0) {
	//				if (leaf_count >= leaf_maxcount) {
	//					//				Com_Printf ("CM_BoxLeafnums_r: overflow\n");
	//					return;
	//				}
	//				leaf_list[leaf_count++] = -1 - nodenum;
	//				return;
	//			}
	//
	//			node = & map_nodes[nodenum];
	//			plane = node.plane;
	//			//		s = BoxOnPlaneSide (leaf_mins, leaf_maxs, plane);
	//			s = BOX_ON_PLANE_SIDE(leaf_mins, leaf_maxs, plane);
	//			if (s == 1)
	//				nodenum = node.children[0];
	//			else if (s == 2)
	//				nodenum = node.children[1];
	//			else { // go down both
	//				if (leaf_topnode == -1)
	//					leaf_topnode = nodenum;
	//				CM_BoxLeafnums_r(node.children[0]);
	//				nodenum = node.children[1];
	//			}
	//
	//		}
	//	}
	//
	//	int CM_BoxLeafnums_headnode(vec3_t mins, vec3_t maxs, int * list, int listsize, int headnode, int * topnode) {
	//		leaf_list = list;
	//		leaf_count = 0;
	//		leaf_maxcount = listsize;
	//		leaf_mins = mins;
	//		leaf_maxs = maxs;
	//
	//		leaf_topnode = -1;
	//
	//		CM_BoxLeafnums_r(headnode);
	//
	//		if (topnode)
	//			* topnode = leaf_topnode;
	//
	//		return leaf_count;
	//	}
	//
	//	int CM_BoxLeafnums(vec3_t mins, vec3_t maxs, int * list, int listsize, int * topnode) {
	//		return CM_BoxLeafnums_headnode(mins, maxs, list, listsize, map_cmodels[0].headnode, topnode);
	//	}
	//
	//	/*
	//	==================
	//	CM_PointContents
	//	
	//	==================
	//	*/
	//	int CM_PointContents(vec3_t p, int headnode) {
	//		int l;
	//
	//		if (!numnodes) // map not loaded
	//			return 0;
	//
	//		l = CM_PointLeafnum_r(p, headnode);
	//
	//		return map_leafs[l].contents;
	//	}
	//
	//	/*
	//	==================
	//	CM_TransformedPointContents
	//	
	//	Handles offseting and rotation of the end points for moving and
	//	rotating entities
	//	==================
	//	*/
	//	int CM_TransformedPointContents(vec3_t p, int headnode, vec3_t origin, vec3_t angles) {
	//		vec3_t p_l;
	//		vec3_t temp;
	//		vec3_t forward, right, up;
	//		int l;
	//
	//		// subtract origin offset
	//		VectorSubtract(p, origin, p_l);
	//
	//		// rotate start and end into the models frame of reference
	//		if (headnode != box_headnode && (angles[0] || angles[1] || angles[2])) {
	//			AngleVectors(angles, forward, right, up);
	//
	//			VectorCopy(p_l, temp);
	//			p_l[0] = DotProduct(temp, forward);
	//			p_l[1] = -DotProduct(temp, right);
	//			p_l[2] = DotProduct(temp, up);
	//		}
	//
	//		l = CM_PointLeafnum_r(p_l, headnode);
	//
	//		return map_leafs[l].contents;
	//	}
	//
	//	/*
	//	===============================================================================
	//	
	//	BOX TRACING
	//	
	//	===============================================================================
	//	*/
	//
	//	// 1/32 epsilon to keep floating point happy
	//	# define DIST_EPSILON(0.03125) vec3_t trace_start, trace_end;
	//	vec3_t trace_mins, trace_maxs;
	//	vec3_t trace_extents;
	//
	//	trace_t trace_trace;
	//	int trace_contents;
	//	boolean trace_ispoint; // optimized case
	//
	//	/*
	//	================
	//	CM_ClipBoxToBrush
	//	================
	//	*/
	//	static void CM_ClipBoxToBrush(vec3_t mins, vec3_t maxs, vec3_t p1, vec3_t p2, trace_t * trace, cbrush_t * brush) {
	//		int i, j;
	//		cplane_t * plane, * clipplane;
	//		float dist;
	//		float enterfrac, leavefrac;
	//		vec3_t ofs;
	//		float d1, d2;
	//		boolean getout, startout;
	//		float f;
	//		cbrushside_t * side, * leadside;
	//
	//		enterfrac = -1;
	//		leavefrac = 1;
	//		clipplane = NULL;
	//
	//		if (!brush.numsides)
	//			return;
	//
	//		c_brush_traces++;
	//
	//		getout = false;
	//		startout = false;
	//		leadside = NULL;
	//
	//		for (i = 0; i < brush.numsides; i++) {
	//			side = & map_brushsides[brush.firstbrushside + i];
	//			plane = side.plane;
	//
	//			// FIXME: special case for axial
	//
	//			if (!trace_ispoint) { // general box case
	//
	//				// push the plane out apropriately for mins/maxs
	//
	//				// FIXME: use signbits into 8 way lookup for each mins/maxs
	//				for (j = 0; j < 3; j++) {
	//					if (plane.normal[j] < 0)
	//						ofs[j] = maxs[j];
	//					else
	//						ofs[j] = mins[j];
	//				}
	//				dist = DotProduct(ofs, plane.normal);
	//				dist = plane.dist - dist;
	//			}
	//			else { // special point case
	//				dist = plane.dist;
	//			}
	//
	//			d1 = DotProduct(p1, plane.normal) - dist;
	//			d2 = DotProduct(p2, plane.normal) - dist;
	//
	//			if (d2 > 0)
	//				getout = true; // endpoint is not in solid
	//			if (d1 > 0)
	//				startout = true;
	//
	//			// if completely in front of face, no intersection
	//			if (d1 > 0 && d2 >= d1)
	//				return;
	//
	//			if (d1 <= 0 && d2 <= 0)
	//				continue;
	//
	//			// crosses face
	//			if (d1 > d2) { // enter
	//				f = (d1 - DIST_EPSILON) / (d1 - d2);
	//				if (f > enterfrac) {
	//					enterfrac = f;
	//					clipplane = plane;
	//					leadside = side;
	//				}
	//			}
	//			else { // leave
	//				f = (d1 + DIST_EPSILON) / (d1 - d2);
	//				if (f < leavefrac)
	//					leavefrac = f;
	//			}
	//		}
	//
	//		if (!startout) { // original point was inside brush
	//			trace.startsolid = true;
	//			if (!getout)
	//				trace.allsolid = true;
	//			return;
	//		}
	//		if (enterfrac < leavefrac) {
	//			if (enterfrac > -1 && enterfrac < trace.fraction) {
	//				if (enterfrac < 0)
	//					enterfrac = 0;
	//				trace.fraction = enterfrac;
	//				trace.plane = * clipplane;
	//				trace.surface = & (leadside.surface.c);
	//				trace.contents = brush.contents;
	//			}
	//		}
	//	}
	//
	//	/*
	//	================
	//	CM_TestBoxInBrush
	//	================
	//	*/
	//	static void CM_TestBoxInBrush(vec3_t mins, vec3_t maxs, vec3_t p1, trace_t * trace, cbrush_t * brush) {
	//		int i, j;
	//		cplane_t * plane;
	//		float dist;
	//		vec3_t ofs;
	//		float d1;
	//		cbrushside_t * side;
	//
	//		if (!brush.numsides)
	//			return;
	//
	//		for (i = 0; i < brush.numsides; i++) {
	//			side = & map_brushsides[brush.firstbrushside + i];
	//			plane = side.plane;
	//
	//			// FIXME: special case for axial
	//
	//			// general box case
	//
	//			// push the plane out apropriately for mins/maxs
	//
	//			// FIXME: use signbits into 8 way lookup for each mins/maxs
	//			for (j = 0; j < 3; j++) {
	//				if (plane.normal[j] < 0)
	//					ofs[j] = maxs[j];
	//				else
	//					ofs[j] = mins[j];
	//			}
	//			dist = DotProduct(ofs, plane.normal);
	//			dist = plane.dist - dist;
	//
	//			d1 = DotProduct(p1, plane.normal) - dist;
	//
	//			// if completely in front of face, no intersection
	//			if (d1 > 0)
	//				return;
	//
	//		}
	//
	//		// inside this brush
	//		trace.startsolid = trace.allsolid = true;
	//		trace.fraction = 0;
	//		trace.contents = brush.contents;
	//	}
	//
	//	/*
	//	================
	//	CM_TraceToLeaf
	//	================
	//	*/
	//	static void CM_TraceToLeaf(int leafnum) {
	//		int k;
	//		int brushnum;
	//		cleaf_t * leaf;
	//		cbrush_t * b;
	//
	//		leaf = & map_leafs[leafnum];
	//		if (!(leaf.contents & trace_contents))
	//			return;
	//		// trace line against all brushes in the leaf
	//		for (k = 0; k < leaf.numleafbrushes; k++) {
	//			brushnum = map_leafbrushes[leaf.firstleafbrush + k];
	//			b = & map_brushes[brushnum];
	//			if (b.checkcount == checkcount)
	//				continue; // already checked this brush in another leaf
	//			b.checkcount = checkcount;
	//
	//			if (!(b.contents & trace_contents))
	//				continue;
	//			CM_ClipBoxToBrush(trace_mins, trace_maxs, trace_start, trace_end, & trace_trace, b);
	//			if (!trace_trace.fraction)
	//				return;
	//		}
	//
	//	}
	//
	//	/*
	//	================
	//	CM_TestInLeaf
	//	================
	//	*/
	//	static void CM_TestInLeaf(int leafnum) {
	//		int k;
	//		int brushnum;
	//		cleaf_t * leaf;
	//		cbrush_t * b;
	//
	//		leaf = & map_leafs[leafnum];
	//		if (!(leaf.contents & trace_contents))
	//			return;
	//		// trace line against all brushes in the leaf
	//		for (k = 0; k < leaf.numleafbrushes; k++) {
	//			brushnum = map_leafbrushes[leaf.firstleafbrush + k];
	//			b = & map_brushes[brushnum];
	//			if (b.checkcount == checkcount)
	//				continue; // already checked this brush in another leaf
	//			b.checkcount = checkcount;
	//
	//			if (!(b.contents & trace_contents))
	//				continue;
	//			CM_TestBoxInBrush(trace_mins, trace_maxs, trace_start, & trace_trace, b);
	//			if (!trace_trace.fraction)
	//				return;
	//		}
	//
	//	}
	//
	//	/*
	//	==================
	//	CM_RecursiveHullCheck
	//	
	//	==================
	//	*/
	//	static void CM_RecursiveHullCheck(int num, float p1f, float p2f, vec3_t p1, vec3_t p2) {
	//		cnode_t * node;
	//		cplane_t * plane;
	//		float t1, t2, offset;
	//		float frac, frac2;
	//		float idist;
	//		int i;
	//		vec3_t mid;
	//		int side;
	//		float midf;
	//
	//		if (trace_trace.fraction <= p1f)
	//			return; // already hit something nearer
	//
	//		// if < 0, we are in a leaf node
	//		if (num < 0) {
	//			CM_TraceToLeaf(-1 - num);
	//			return;
	//		}
	//
	//		//
	//		// find the point distances to the seperating plane
	//		// and the offset for the size of the box
	//		//
	//		node = map_nodes + num;
	//		plane = node.plane;
	//
	//		if (plane.type < 3) {
	//			t1 = p1[plane.type] - plane.dist;
	//			t2 = p2[plane.type] - plane.dist;
	//			offset = trace_extents[plane.type];
	//		}
	//		else {
	//			t1 = DotProduct(plane.normal, p1) - plane.dist;
	//			t2 = DotProduct(plane.normal, p2) - plane.dist;
	//			if (trace_ispoint)
	//				offset = 0;
	//			else
	//				offset =
	//					fabs(trace_extents[0] * plane.normal[0])
	//						+ fabs(trace_extents[1] * plane.normal[1])
	//						+ fabs(trace_extents[2] * plane.normal[2]);
	//		}
	//
	//		# if 0 CM_RecursiveHullCheck(node.children[0], p1f, p2f, p1, p2);
	//		CM_RecursiveHullCheck(node.children[1], p1f, p2f, p1, p2);
	//		return;
	//		# endif
	//
	//		// see which sides we need to consider
	//		if (t1 >= offset && t2 >= offset) {
	//			CM_RecursiveHullCheck(node.children[0], p1f, p2f, p1, p2);
	//			return;
	//		}
	//		if (t1 < -offset && t2 < -offset) {
	//			CM_RecursiveHullCheck(node.children[1], p1f, p2f, p1, p2);
	//			return;
	//		}
	//
	//		// put the crosspoint DIST_EPSILON pixels on the near side
	//		if (t1 < t2) {
	//			idist = 1.0 / (t1 - t2);
	//			side = 1;
	//			frac2 = (t1 + offset + DIST_EPSILON) * idist;
	//			frac = (t1 - offset + DIST_EPSILON) * idist;
	//		}
	//		else if (t1 > t2) {
	//			idist = 1.0 / (t1 - t2);
	//			side = 0;
	//			frac2 = (t1 - offset - DIST_EPSILON) * idist;
	//			frac = (t1 + offset + DIST_EPSILON) * idist;
	//		}
	//		else {
	//			side = 0;
	//			frac = 1;
	//			frac2 = 0;
	//		}
	//
	//		// move up to the node
	//		if (frac < 0)
	//			frac = 0;
	//		if (frac > 1)
	//			frac = 1;
	//
	//		midf = p1f + (p2f - p1f) * frac;
	//		for (i = 0; i < 3; i++)
	//			mid[i] = p1[i] + frac * (p2[i] - p1[i]);
	//
	//		CM_RecursiveHullCheck(node.children[side], p1f, midf, p1, mid);
	//
	//		// go past the node
	//		if (frac2 < 0)
	//			frac2 = 0;
	//		if (frac2 > 1)
	//			frac2 = 1;
	//
	//		midf = p1f + (p2f - p1f) * frac2;
	//		for (i = 0; i < 3; i++)
	//			mid[i] = p1[i] + frac2 * (p2[i] - p1[i]);
	//
	//		CM_RecursiveHullCheck(node.children[side ^ 1], midf, p2f, mid, p2);
	//	}
	//
	//	//======================================================================
	//
	//	/*
	//	==================
	//	CM_BoxTrace
	//	==================
	//	*/
	//	trace_t CM_BoxTrace(vec3_t start, vec3_t end, vec3_t mins, vec3_t maxs, int headnode, int brushmask) {
	//		int i;
	//
	//		checkcount++; // for multi-check avoidance
	//
	//		c_traces++; // for statistics, may be zeroed
	//
	//		// fill in a default trace
	//		memset(& trace_trace, 0, sizeof(trace_trace));
	//		trace_trace.fraction = 1;
	//		trace_trace.surface = & (nullsurface.c);
	//
	//		if (!numnodes) // map not loaded
	//			return trace_trace;
	//
	//		trace_contents = brushmask;
	//		VectorCopy(start, trace_start);
	//		VectorCopy(end, trace_end);
	//		VectorCopy(mins, trace_mins);
	//		VectorCopy(maxs, trace_maxs);
	//
	//		//
	//		// check for position test special case
	//		//
	//		if (start[0] == end[0] && start[1] == end[1] && start[2] == end[2]) {
	//			int leafs[1024];
	//			int i, numleafs;
	//			vec3_t c1, c2;
	//			int topnode;
	//
	//			VectorAdd(start, mins, c1);
	//			VectorAdd(start, maxs, c2);
	//			for (i = 0; i < 3; i++) {
	//				c1[i] -= 1;
	//				c2[i] += 1;
	//			}
	//
	//			numleafs = CM_BoxLeafnums_headnode(c1, c2, leafs, 1024, headnode, & topnode);
	//			for (i = 0; i < numleafs; i++) {
	//				CM_TestInLeaf(leafs[i]);
	//				if (trace_trace.allsolid)
	//					break;
	//			}
	//			VectorCopy(start, trace_trace.endpos);
	//			return trace_trace;
	//		}
	//
	//		//
	//		// check for point special case
	//		//
	//		if (mins[0] == 0 && mins[1] == 0 && mins[2] == 0 && maxs[0] == 0 && maxs[1] == 0 && maxs[2] == 0) {
	//			trace_ispoint = true;
	//			VectorClear(trace_extents);
	//		}
	//		else {
	//			trace_ispoint = false;
	//			trace_extents[0] = -mins[0] > maxs[0] ? -mins[0] : maxs[0];
	//			trace_extents[1] = -mins[1] > maxs[1] ? -mins[1] : maxs[1];
	//			trace_extents[2] = -mins[2] > maxs[2] ? -mins[2] : maxs[2];
	//		}
	//
	//		//
	//		// general sweeping through world
	//		//
	//		CM_RecursiveHullCheck(headnode, 0, 1, start, end);
	//
	//		if (trace_trace.fraction == 1) {
	//			VectorCopy(end, trace_trace.endpos);
	//		}
	//		else {
	//			for (i = 0; i < 3; i++)
	//				trace_trace.endpos[i] = start[i] + trace_trace.fraction * (end[i] - start[i]);
	//		}
	//		return trace_trace;
	//	}
	//
	//	/*
	//	==================
	//	CM_TransformedBoxTrace
	//	
	//	Handles offseting and rotation of the end points for moving and
	//	rotating entities
	//	==================
	//	*/
	//	# ifdef _WIN32 # pragma optimize("", off) # endif 
	//trace_t CM_TransformedBoxTrace(
	//		vec3_t start,
	//		vec3_t end,
	//		vec3_t mins,
	//		vec3_t maxs,
	//		int headnode,
	//		int brushmask,
	//		vec3_t origin,
	//		vec3_t angles) {
	//		trace_t trace;
	//		vec3_t start_l, end_l;
	//		vec3_t a;
	//		vec3_t forward, right, up;
	//		vec3_t temp;
	//		boolean rotated;
	//
	//		// subtract origin offset
	//		VectorSubtract(start, origin, start_l);
	//		VectorSubtract(end, origin, end_l);
	//
	//		// rotate start and end into the models frame of reference
	//		if (headnode != box_headnode && (angles[0] || angles[1] || angles[2]))
	//			rotated = true;
	//		else
	//			rotated = false;
	//
	//		if (rotated) {
	//			AngleVectors(angles, forward, right, up);
	//
	//			VectorCopy(start_l, temp);
	//			start_l[0] = DotProduct(temp, forward);
	//			start_l[1] = -DotProduct(temp, right);
	//			start_l[2] = DotProduct(temp, up);
	//
	//			VectorCopy(end_l, temp);
	//			end_l[0] = DotProduct(temp, forward);
	//			end_l[1] = -DotProduct(temp, right);
	//			end_l[2] = DotProduct(temp, up);
	//		}
	//
	//		// sweep the box through the model
	//		trace = CM_BoxTrace(start_l, end_l, mins, maxs, headnode, brushmask);
	//
	//		if (rotated && trace.fraction != 1.0) {
	//			// FIXME: figure out how to do this with existing angles
	//			VectorNegate(angles, a);
	//			AngleVectors(a, forward, right, up);
	//
	//			VectorCopy(trace.plane.normal, temp);
	//			trace.plane.normal[0] = DotProduct(temp, forward);
	//			trace.plane.normal[1] = -DotProduct(temp, right);
	//			trace.plane.normal[2] = DotProduct(temp, up);
	//		}
	//
	//		trace.endpos[0] = start[0] + trace.fraction * (end[0] - start[0]);
	//		trace.endpos[1] = start[1] + trace.fraction * (end[1] - start[1]);
	//		trace.endpos[2] = start[2] + trace.fraction * (end[2] - start[2]);
	//
	//		return trace;
	//	}
	//
	//	# ifdef _WIN32 # pragma optimize("", on) # endif
	//
	//	/*
	//	===============================================================================
	//	
	//	PVS / PHS
	//	
	//	===============================================================================
	//	*/
	//
	//	/*
	//	===================
	//	CM_DecompressVis
	//	===================
	//	*/
	//	static void CM_DecompressVis(byte * in, byte * out) {
	//		int c;
	//		byte * out_p;
	//		int row;
	//
	//		row = (numclusters + 7) >> 3;
	//		out_p = out;
	//
	//		if (!in || !numvisibility) { // no vis info, so make all visible
	//			while (row) {
	//				* out_p++ = 0xff;
	//				row--;
	//			}
	//			return;
	//		}
	//
	//		do {
	//			if (* in) {
	//				* out_p++ = * in++;
	//				continue;
	//			}
	//
	//			c = in[1];
	//			in += 2;
	//			if ((out_p - out) + c > row) {
	//				c = row - (out_p - out);
	//				Com_DPrintf("warning: Vis decompression overrun\n");
	//			}
	//				while (c) {
	//					* out_p++ = 0;
	//					c--;
	//				}
	//		}
	//		while (out_p - out < row);
	//	}
	//
	//	byte pvsrow[MAX_MAP_LEAFS / 8];
	//	byte phsrow[MAX_MAP_LEAFS / 8];
	//
	//	byte * CM_ClusterPVS(int cluster) {
	//		if (cluster == -1)
	//			memset(pvsrow, 0, (numclusters + 7) >> 3);
	//		else
	//			CM_DecompressVis(map_visibility + map_vis.bitofs[cluster][DVIS_PVS], pvsrow);
	//		return pvsrow;
	//	}
	//
	//	byte * CM_ClusterPHS(int cluster) {
	//		if (cluster == -1)
	//			memset(phsrow, 0, (numclusters + 7) >> 3);
	//		else
	//			CM_DecompressVis(map_visibility + map_vis.bitofs[cluster][DVIS_PHS], phsrow);
	//		return phsrow;
	//	}
	//
	//	/*
	//	===============================================================================
	//	
	//	AREAPORTALS
	//	
	//	===============================================================================
	//	*/
	//
		static void FloodArea_r(carea_t area, int floodnum) {
			Com.DPrintf("FloodArea_r("+ floodnum + ")...\n");
			int i;
			dareaportal_t p;
	
			if (area.floodvalid == floodvalid) {
				if (area.floodnum == floodnum)
					return;
				Com.Error(ERR_DROP, "FloodArea_r: reflooded");
			}
	
			area.floodnum = floodnum;
			area.floodvalid = floodvalid;
			
			for (i = 0; i < area.numareaportals; i++) {
				p = map_areaportals[area.firstareaportal + i];
				if (portalopen[p.portalnum])
					FloodArea_r( map_areas[p.otherarea], floodnum);
			}
		}
	//
		/*
		====================
		FloodAreaConnections
		
		
		====================
		*/
		static void FloodAreaConnections() {
			Com.DPrintf("FloodAreaConnections...\n");
			
			int i;
			carea_t  area;
			int floodnum;
	
			// all current floods are now invalid
			floodvalid++;
			floodnum = 0;
	
			// area 0 is not used
			for (i = 1; i < numareas; i++) {
				
				area = map_areas[i];
				
				if (area.floodvalid == floodvalid)
					continue; // already flooded into
				floodnum++;
				FloodArea_r(area, floodnum);
			}
	
		}
	
		static void CM_SetAreaPortalState(int portalnum, boolean open) {
			if (portalnum > numareaportals)
				Com.Error(ERR_DROP, "areaportal > numareaportals");
	
			portalopen[portalnum] = open;
			FloodAreaConnections();
		}
	
		boolean CM_AreasConnected(int area1, int area2) {
			if (map_noareas.value!=0)
				return true;
	
			if (area1 > numareas || area2 > numareas)
				Com.Error(ERR_DROP, "area > numareas");
	
			if (map_areas[area1].floodnum == map_areas[area2].floodnum)
				return true;
			return false;
		}
	//
	//	/*
	//	=================
	//	CM_WriteAreaBits
	//	
	//	Writes a length byte followed by a bit vector of all the areas
	//	that area in the same flood as the area parameter
	//	
	//	This is used by the client refreshes to cull visibility
	//	=================
	//	*/
	//	int CM_WriteAreaBits(byte * buffer, int area) {
	//		int i;
	//		int floodnum;
	//		int bytes;
	//
	//		bytes = (numareas + 7) >> 3;
	//
	//		if (map_noareas.value) { // for debugging, send everything
	//			memset(buffer, 255, bytes);
	//		}
	//		else {
	//			memset(buffer, 0, bytes);
	//
	//			floodnum = map_areas[area].floodnum;
	//			for (i = 0; i < numareas; i++) {
	//				if (map_areas[i].floodnum == floodnum || !area)
	//					buffer[i >> 3] |= 1 << (i & 7);
	//			}
	//		}
	//
	//		return bytes;
	//	}
	//
	//	/*
	//	===================
	//	CM_WritePortalState
	//	
	//	Writes the portal state to a savegame file
	//	===================
	//	*/
	//	static void CM_WritePortalState(FILE * f) {
	//		fwrite(portalopen, sizeof(portalopen), 1, f);
	//	}
	//
	//	/*
	//	===================
	//	CM_ReadPortalState
	//	
	//	Reads the portal state from a savegame file
	//	and recalculates the area connections
	//	===================
	//	*/
	//	static void CM_ReadPortalState(FILE * f) {
	//		FS_Read(portalopen, sizeof(portalopen), f);
	//		FloodAreaConnections();
	//	}
	//
	//	/*
	//	=============
	//	CM_HeadnodeVisible
	//	
	//	Returns true if any leaf under headnode has a cluster that
	//	is potentially visible
	//	=============
	//	*/
	//	boolean CM_HeadnodeVisible(int nodenum, byte visbits[]) {
	//		int leafnum;
	//		int cluster;
	//		cnode_t node;
	//
	//		if (nodenum < 0) {
	//			leafnum = -1 - nodenum;
	//			cluster = map_leafs[leafnum].cluster;
	//			if (cluster == -1)
	//				return false;
	//			if (visbits[cluster >> 3] & (1 << (cluster & 7)))
	//				return true;
	//			return false;
	//		}
	//
	//		node = & map_nodes[nodenum];
	//		if (CM_HeadnodeVisible(node.children[0], visbits))
	//			return true;
	//		return CM_HeadnodeVisible(node.children[1], visbits);
	//	}
	//
}
