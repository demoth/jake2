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

// Created on 19.08.2006 by RST.

// $Id: Model.java,v 1.2 2006-10-31 14:00:23 salomo Exp $

package jake2.render.common;


import jake2.Defines;
import jake2.client.VID;
import jake2.game.cplane_t;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.nio.*;
import java.util.Arrays;
import java.util.Vector;

/**
 * model loading and caching.
 */
public abstract class Model extends Image {

	protected model_t loadmodel;

	protected int modfilelen;

	protected byte[] mod_novis = new byte[Defines.MAX_MAP_LEAFS / 8];

	protected static final int MAX_MOD_KNOWN = 512;

	protected model_t[] mod_known = new model_t[MAX_MOD_KNOWN];

	protected int mod_numknown;

	// the inline * models from the current map are kept seperate
	protected model_t[] mod_inline = new model_t[MAX_MOD_KNOWN];
	
		/**
	 * Mod_PointInLeaf
	 */
	protected mleaf_t Mod_PointInLeaf(float[] p, model_t model) {
		mnode_t node;
		float d;
		cplane_t plane;

		if (model == null || model.nodes == null)
			Com.Error(Defines.ERR_DROP, "Mod_PointInLeaf: bad model");

		node = model.nodes[0]; // root node
		while (true) {
			if (node.contents != -1)
				return (mleaf_t) node;

			plane = node.plane;
			d = Math3D.DotProduct(p, plane.normal) - plane.dist;
			if (d > 0)
				node = node.children[0];
			else
				node = node.children[1];
		}
		// never reached
	}

	protected byte[] decompressed = new byte[Defines.MAX_MAP_LEAFS / 8];

	protected byte[] model_visibility = new byte[Defines.MAX_MAP_VISIBILITY];

	/*
	=================== 
	Mod_DecompressVis
	===================
	 */
	protected byte[] Mod_DecompressVis(byte[] in, int offset, model_t model) {
		int c;
		byte[] out;
		int outp, inp;
		int row;

		row = (model.vis.numclusters + 7) >> 3;
		out = decompressed;
		outp = 0;
		inp = offset;

		if (in == null) { // no vis info, so make all visible
			while (row != 0) {
				out[outp++] = (byte) 0xFF;
				row--;
			}
			return decompressed;
		}

		do {
			if (in[inp] != 0) {
				out[outp++] = in[inp++];
				continue;
			}

			c = in[inp + 1] & 0xFF;
			inp += 2;
			while (c != 0) {
				out[outp++] = 0;
				c--;
			}
		} while (outp < row);

		return decompressed;
	}

	/**
	 * Mod_ClusterPVS
	 */
	protected byte[] Mod_ClusterPVS(int cluster, model_t model) {
		if (cluster == -1 || model.vis == null)
			return mod_novis;

		return Mod_DecompressVis(model_visibility, model.vis.bitofs[cluster][Defines.DVIS_PVS], model);
	}

	/**
	 * Mod_Modellist_f
	 */
	protected void Mod_Modellist_f() {
		int i;
		model_t mod;
		int total;

		total = 0;
		VID.Printf(Defines.PRINT_ALL, "Loaded models:\n");
		for (i = 0; i < mod_numknown; i++) {
			mod = mod_known[i];
			if (mod.name.length() == 0)
				continue;

			VID.Printf(Defines.PRINT_ALL, "%8i : %s\n", new Vargs(2).add(mod.extradatasize).add(mod.name));
			total += mod.extradatasize;
		}
		VID.Printf(Defines.PRINT_ALL, "Total resident: " + total + '\n');
	}

	/**
	 * Mod_Init.
	 */
	protected void Mod_Init() {
		// init mod_known
		for (int i = 0; i < MAX_MOD_KNOWN; i++) {
			mod_known[i] = new model_t();
		}
		Arrays.fill(mod_novis, (byte) 0xff);
	}

	protected byte[] fileBuffer;

	/**
	 * Mod_ForName. 
	 * 
	 * Loads in a model for the given name.
	 */
	protected model_t Mod_ForName(String name, boolean crash) {
		model_t mod = null;
		int i;

		if (name == null || name.length() == 0)
			Com.Error(Defines.ERR_DROP, "Mod_ForName: NULL name");

		//
		// inline models are grabbed only from worldmodel
		//
		if (name.charAt(0) == '*') {
			i = Integer.parseInt(name.substring(1));
			if (i < 1 || r_worldmodel == null || i >= r_worldmodel.numsubmodels)
				Com.Error(Defines.ERR_DROP, "bad inline model number");
			return mod_inline[i];
		}

		//
		// search the currently loaded models
		//
		for (i = 0; i < mod_numknown; i++) {
			mod = mod_known[i];

			if (mod.name.length() == 0)
				continue;
			if (mod.name.equals(name))
				return mod;
		}

		//
		// find a free model slot spot
		//
		for (i = 0; i < mod_numknown; i++) {
			mod = mod_known[i];

			if (mod.name.length() == 0)
				break; // free spot
		}
		if (i == mod_numknown) {
			if (mod_numknown == MAX_MOD_KNOWN)
				Com.Error(Defines.ERR_DROP, "mod_numknown == MAX_MOD_KNOWN");
			mod_numknown++;
			mod = mod_known[i];
		}

		mod.name = name;

		//
		// load the file
		//
		fileBuffer = FS.LoadFile(name);

		if (fileBuffer == null) {
			if (crash)
				Com.Error(Defines.ERR_DROP, "Mod_NumForName: " + mod.name
						+ " not found");

			mod.name = "";
			return null;
		}

		modfilelen = fileBuffer.length;

		loadmodel = mod;

		//
		// fill it in
		//
		ByteBuffer bb = ByteBuffer.wrap(fileBuffer);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// call the apropriate loader

		bb.mark();
		int ident = bb.getInt();

		bb.reset();

		switch (ident) {
		case qfiles.IDALIASHEADER:
			Mod_LoadAliasModel(mod, bb);
			break;
		case qfiles.IDSPRITEHEADER:
			Mod_LoadSpriteModel(mod, bb);
			break;
		case qfiles.IDBSPHEADER:
			Mod_LoadBrushModel(mod, bb);
			break;
		default:
			Com.Error(Defines.ERR_DROP, "Mod_NumForName: unknown fileid for " + mod.name);
			break;
		}

		this.fileBuffer = null; // free it for garbage collection
		return mod;
	}

	/*
	 * ===============================================================================
	 * 
	 * BRUSHMODEL LOADING
	 * 
	 * ===============================================================================
	 */

	protected byte[] mod_base;

	/**
	 * Mod_LoadLighting.
	 */
	protected void Mod_LoadLighting(lump_t l) {
		if (l.filelen == 0) {
			loadmodel.lightdata = null;
			return;
		}
		loadmodel.lightdata = new byte[l.filelen];
		System.arraycopy(mod_base, l.fileofs, loadmodel.lightdata, 0, l.filelen);
	}

	/**
	 * Mod_LoadVisibility.
	 */
	protected void Mod_LoadVisibility(lump_t l) {

		if (l.filelen == 0) {
			loadmodel.vis = null;
			return;
		}

		System.arraycopy(mod_base, l.fileofs, model_visibility, 0, l.filelen);

		ByteBuffer bb = ByteBuffer.wrap(model_visibility, 0, l.filelen);

		loadmodel.vis = new qfiles.dvis_t(bb.order(ByteOrder.LITTLE_ENDIAN));

	}

	/**
	 * Mod_LoadVertexes.
	 */
	protected void Mod_LoadVertexes(lump_t l) {
		mvertex_t[] vertexes;
		int i, count;

		if ((l.filelen % mvertex_t.DISK_SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in " + loadmodel.name);

		count = l.filelen / mvertex_t.DISK_SIZE;

		vertexes = new mvertex_t[count];

		loadmodel.vertexes = vertexes;
		loadmodel.numvertexes = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			vertexes[i] = new mvertex_t(bb);
		}
	}

	/**
	 * RadiusFromBounds
	 */
	protected float RadiusFromBounds(float[] mins, float[] maxs) {
		float[] corner = { 0, 0, 0 };

		for (int i = 0; i < 3; i++) {
			corner[i] = Math.abs(mins[i]) > Math.abs(maxs[i]) ? Math.abs(mins[i]) : Math.abs(maxs[i]);
		}
		return Math3D.VectorLength(corner);
	}
	
	/**
	 * Mod_LoadSubmodels.
	 */
	protected void Mod_LoadSubmodels(lump_t l) {

		if ((l.filelen % qfiles.dmodel_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in " + loadmodel.name);

		int i, j;

		int count = l.filelen / qfiles.dmodel_t.SIZE;
		// out = Hunk_Alloc ( count*sizeof(*out));
		mmodel_t out;
		mmodel_t[] outs = new mmodel_t[count];
		for (i = 0; i < count; i++) {
			outs[i] = new mmodel_t();
		}

		loadmodel.submodels = outs;
		loadmodel.numsubmodels = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		qfiles.dmodel_t in;

		for (i = 0; i < count; i++) {
			in = new qfiles.dmodel_t(bb);
			out = outs[i];
			for (j = 0; j < 3; j++) { // spread the mins / maxs by a
				// pixel
				out.mins[j] = in.mins[j] - 1;
				out.maxs[j] = in.maxs[j] + 1;
				out.origin[j] = in.origin[j];
			}
			out.radius = RadiusFromBounds(out.mins, out.maxs);
			out.headnode = in.headnode;
			out.firstface = in.firstface;
			out.numfaces = in.numfaces;
		}
	}

	/*
	 * ================= Mod_LoadEdges =================
	 */
	protected void Mod_LoadEdges(lump_t l) {
		medge_t[] edges;
		int i, count;

		if ((l.filelen % medge_t.DISK_SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		count = l.filelen / medge_t.DISK_SIZE;
		// out = Hunk_Alloc ( (count + 1) * sizeof(*out));
		edges = new medge_t[count + 1];

		loadmodel.edges = edges;
		loadmodel.numedges = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			edges[i] = new medge_t(bb);
		}
	}

	/*
	 * ================= Mod_LoadTexinfo =================
	 */
	protected void Mod_LoadTexinfo(lump_t l) {
		texinfo_t in;
		mtexinfo_t[] out;
		mtexinfo_t step;
		int i, j, count;
		int next;
		String name;

		if ((l.filelen % texinfo_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in " + loadmodel.name);

		count = l.filelen / texinfo_t.SIZE;
		// out = Hunk_Alloc ( count*sizeof(*out));
		out = new mtexinfo_t[count];
		for (i = 0; i < count; i++) {
			out[i] = new mtexinfo_t();
		}

		loadmodel.texinfo = out;
		loadmodel.numtexinfo = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {

			in = new texinfo_t(bb);
			out[i].vecs = in.vecs;
			out[i].flags = in.flags;
			next = in.nexttexinfo;
			if (next > 0)
				out[i].next = loadmodel.texinfo[next];
			else
				out[i].next = null;

			name = "textures/" + in.texture + ".wal";

			out[i].image = GL_FindImage(name, Base.it_wall);
			if (out[i].image == null) {
				VID.Printf(Defines.PRINT_ALL, "Couldn't load " + name + '\n');
				out[i].image = r_notexture;
			}
		}

		// count animation frames
		for (i = 0; i < count; i++) {
			out[i].numframes = 1;
			for (step = out[i].next; (step != null) && (step != out[i]); step = step.next)
				out[i].numframes++;
		}
	}

	/*
	================
	CalcSurfaceExtents

	Fills in s.texturemins[] and s.extents[]
	================
	 */
	protected void CalcSurfaceExtents(msurface_t s) {
		float[] mins = { 0, 0 };
		float[] maxs = { 0, 0 };
		float val;

		int j, e;
		mvertex_t v;
		int[] bmins = { 0, 0 };
		int[] bmaxs = { 0, 0 };

		mins[0] = mins[1] = 999999;
		maxs[0] = maxs[1] = -99999;

		mtexinfo_t tex = s.texinfo;

		for (int i = 0; i < s.numedges; i++) {
			e = loadmodel.surfedges[s.firstedge + i];
			if (e >= 0)
				v = loadmodel.vertexes[loadmodel.edges[e].v[0]];
			else
				v = loadmodel.vertexes[loadmodel.edges[-e].v[1]];

			for (j = 0; j < 2; j++) {
				val = v.position[0] * tex.vecs[j][0] + v.position[1]
						* tex.vecs[j][1] + v.position[2] * tex.vecs[j][2]
						+ tex.vecs[j][3];
				if (val < mins[j])
					mins[j] = val;
				if (val > maxs[j])
					maxs[j] = val;
			}
		}

		for (int i = 0; i < 2; i++) {
			bmins[i] = (int) Math.floor(mins[i] / 16);
			bmaxs[i] = (int) Math.ceil(maxs[i] / 16);

			s.texturemins[i] = (short) (bmins[i] * 16);
			s.extents[i] = (short) ((bmaxs[i] - bmins[i]) * 16);

		}
	}

	/*
	 * ================= Mod_LoadFaces =================
	 */
	protected void Mod_LoadFaces(lump_t l) {

		int i, surfnum;
		int planenum, side;
		int ti;

		if ((l.filelen % qfiles.dface_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		int count = l.filelen / qfiles.dface_t.SIZE;
		// out = Hunk_Alloc ( count*sizeof(*out));
		msurface_t[] outs = new msurface_t[count];
		for (i = 0; i < count; i++) {
			outs[i] = new msurface_t();
		}

		loadmodel.surfaces = outs;
		loadmodel.numsurfaces = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		currentmodel = loadmodel;

		GL_BeginBuildingLightmaps(loadmodel);

		qfiles.dface_t in;
		msurface_t out;

		for (surfnum = 0; surfnum < count; surfnum++) {
			in = new qfiles.dface_t(bb);
			out = outs[surfnum];
			out.firstedge = in.firstedge;
			out.numedges = in.numedges;
			out.flags = 0;
			out.polys = null;

			planenum = in.planenum;
			side = in.side;
			if (side != 0)
				out.flags |= Defines.SURF_PLANEBACK;

			out.plane = loadmodel.planes[planenum];

			ti = in.texinfo;
			if (ti < 0 || ti >= loadmodel.numtexinfo)
				Com.Error(Defines.ERR_DROP,
						"MOD_LoadBmodel: bad texinfo number");

			out.texinfo = loadmodel.texinfo[ti];

			CalcSurfaceExtents(out);

			// lighting info

			for (i = 0; i < Defines.MAXLIGHTMAPS; i++)
				out.styles[i] = in.styles[i];

			i = in.lightofs;
			if (i == -1)
				out.samples = null;
			else {
				ByteBuffer pointer = ByteBuffer.wrap(loadmodel.lightdata);
				pointer.position(i);
				pointer = pointer.slice();
				pointer.mark();
				out.samples = pointer; // subarray
			}

			// set the drawing flags

			if ((out.texinfo.flags & Defines.SURF_WARP) != 0) {
				out.flags |= Defines.SURF_DRAWTURB;
				for (i = 0; i < 2; i++) {
					out.extents[i] = 16384;
					out.texturemins[i] = -8192;
				}
				GL_SubdivideSurface(out); // cut up polygon for warps
			}

			// create lightmaps and polygons
			if ((out.texinfo.flags & (Defines.SURF_SKY | Defines.SURF_TRANS33
					| Defines.SURF_TRANS66 | Defines.SURF_WARP)) == 0)
				GL_CreateSurfaceLightmap(out);

			if ((out.texinfo.flags & Defines.SURF_WARP) == 0)
				GL_BuildPolygonFromSurface(out);

		}
		GL_EndBuildingLightmaps();
	}

	/*
	 * ================= Mod_SetParent =================
	 */
	protected void Mod_SetParent(mnode_t node, mnode_t parent) {
		node.parent = parent;
		if (node.contents != -1)
			return;
		Mod_SetParent(node.children[0], node);
		Mod_SetParent(node.children[1], node);
	}

	/*
	 * ================= Mod_LoadNodes =================
	 */
	protected void Mod_LoadNodes(lump_t l) {
		int i, j, count, p;
		qfiles.dnode_t in;
		mnode_t[] out;

		if ((l.filelen % qfiles.dnode_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		count = l.filelen / qfiles.dnode_t.SIZE;
		// out = Hunk_Alloc ( count*sizeof(*out));
		out = new mnode_t[count];

		loadmodel.nodes = out;
		loadmodel.numnodes = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// initialize the tree array
		for (i = 0; i < count; i++)
			out[i] = new mnode_t(); // do first before linking

		// fill and link the nodes
		for (i = 0; i < count; i++) {
			in = new qfiles.dnode_t(bb);
			for (j = 0; j < 3; j++) {
				out[i].mins[j] = in.mins[j];
				out[i].maxs[j] = in.maxs[j];
			}

			p = in.planenum;
			out[i].plane = loadmodel.planes[p];

			out[i].firstsurface = in.firstface;
			out[i].numsurfaces = in.numfaces;
			out[i].contents = -1; // differentiate from leafs

			for (j = 0; j < 2; j++) {
				p = in.children[j];
				if (p >= 0)
					out[i].children[j] = loadmodel.nodes[p];
				else
					out[i].children[j] = loadmodel.leafs[-1 - p]; // mleaf_t
				// extends
				// mnode_t
			}
		}

		Mod_SetParent(loadmodel.nodes[0], null); // sets nodes and leafs
	}

	/*
	 * ================= Mod_LoadLeafs =================
	 */
	protected void Mod_LoadLeafs(lump_t l) {
		qfiles.dleaf_t in;
		mleaf_t[] out;
		int i, j, count, p;

		if ((l.filelen % qfiles.dleaf_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		count = l.filelen / qfiles.dleaf_t.SIZE;
		// out = Hunk_Alloc ( count*sizeof(*out));
		out = new mleaf_t[count];

		loadmodel.leafs = out;
		loadmodel.numleafs = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			in = new qfiles.dleaf_t(bb);
			out[i] = new mleaf_t();
			for (j = 0; j < 3; j++) {
				out[i].mins[j] = in.mins[j];
				out[i].maxs[j] = in.maxs[j];

			}

			out[i].contents = in.contents;
			out[i].cluster = in.cluster;
			out[i].area = in.area;

			out[i].setMarkSurface(in.firstleafface, loadmodel.marksurfaces);
			out[i].nummarksurfaces = in.numleaffaces;
		}
	}

	/*
	 * ================= Mod_LoadMarksurfaces =================
	 */
	protected void Mod_LoadMarksurfaces(lump_t l) {
		int i, j, count;

		msurface_t[] out;

		if ((l.filelen % Defines.SIZE_OF_SHORT) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);
		count = l.filelen / Defines.SIZE_OF_SHORT;
		// out = Hunk_Alloc ( count*sizeof(*out));
		out = new msurface_t[count];

		loadmodel.marksurfaces = out;
		loadmodel.nummarksurfaces = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			j = bb.getShort();
			if (j < 0 || j >= loadmodel.numsurfaces)
				Com.Error(Defines.ERR_DROP,
						"Mod_ParseMarksurfaces: bad surface number");

			out[i] = loadmodel.surfaces[j];
		}
	}

	/*
	 * ================= Mod_LoadSurfedges =================
	 */
	protected void Mod_LoadSurfedges(lump_t l) {
		int i, count;
		int[] offsets;

		if ((l.filelen % Defines.SIZE_OF_INT) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		count = l.filelen / Defines.SIZE_OF_INT;
		if (count < 1 || count >= Defines.MAX_MAP_SURFEDGES)
			Com.Error(Defines.ERR_DROP,
					"MOD_LoadBmodel: bad surfedges count in " + loadmodel.name
							+ ": " + count);

		offsets = new int[count];

		loadmodel.surfedges = offsets;
		loadmodel.numsurfedges = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++)
			offsets[i] = bb.getInt();
	}

	/*
	 * ================= Mod_LoadPlanes =================
	 */
	protected void Mod_LoadPlanes(lump_t l) {
		int i, j;
		cplane_t[] out;
		qfiles.dplane_t in;
		int count;
		int bits;

		if ((l.filelen % qfiles.dplane_t.SIZE) != 0)
			Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size in "
					+ loadmodel.name);

		count = l.filelen / qfiles.dplane_t.SIZE;
		// out = Hunk_Alloc ( count*2*sizeof(*out));
		out = new cplane_t[count * 2];
		for (i = 0; i < count; i++) {
			out[i] = new cplane_t();
		}

		loadmodel.planes = out;
		loadmodel.numplanes = count;

		ByteBuffer bb = ByteBuffer.wrap(mod_base, l.fileofs, l.filelen);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		for (i = 0; i < count; i++) {
			bits = 0;
			in = new qfiles.dplane_t(bb);
			for (j = 0; j < 3; j++) {
				out[i].normal[j] = in.normal[j];
				if (out[i].normal[j] < 0)
					bits |= (1 << j);
			}

			out[i].dist = in.dist;
			out[i].type = (byte) in.type;
			out[i].signbits = (byte) bits;
		}
	}

	/*
	 * ================= Mod_LoadBrushModel =================
	 */
	protected void Mod_LoadBrushModel(model_t mod, ByteBuffer buffer) {
		int i;
		qfiles.dheader_t header;
		mmodel_t bm;

		loadmodel.type = Base.mod_brush;
		if (loadmodel != mod_known[0])
			Com.Error(Defines.ERR_DROP, "Loaded a brush model after the world");

		header = new qfiles.dheader_t(buffer);

		i = header.version;
		if (i != Defines.BSPVERSION)
			Com.Error(Defines.ERR_DROP, "Mod_LoadBrushModel: " + mod.name
					+ " has wrong version number (" + i + " should be "
					+ Defines.BSPVERSION + ")");

		mod_base = fileBuffer; // (byte *)header;

		// load into heap
		Mod_LoadVertexes(header.lumps[Defines.LUMP_VERTEXES]); // ok
		Mod_LoadEdges(header.lumps[Defines.LUMP_EDGES]); // ok
		Mod_LoadSurfedges(header.lumps[Defines.LUMP_SURFEDGES]); // ok
		Mod_LoadLighting(header.lumps[Defines.LUMP_LIGHTING]); // ok
		Mod_LoadPlanes(header.lumps[Defines.LUMP_PLANES]); // ok
		Mod_LoadTexinfo(header.lumps[Defines.LUMP_TEXINFO]); // ok
		Mod_LoadFaces(header.lumps[Defines.LUMP_FACES]); // ok
		Mod_LoadMarksurfaces(header.lumps[Defines.LUMP_LEAFFACES]);
		Mod_LoadVisibility(header.lumps[Defines.LUMP_VISIBILITY]); // ok
		Mod_LoadLeafs(header.lumps[Defines.LUMP_LEAFS]); // ok
		Mod_LoadNodes(header.lumps[Defines.LUMP_NODES]); // ok
		Mod_LoadSubmodels(header.lumps[Defines.LUMP_MODELS]);
		mod.numframes = 2; // regular and alternate animation

		//
		// set up the submodels
		//
		model_t starmod;

		for (i = 0; i < mod.numsubmodels; i++) {

			bm = mod.submodels[i];
			starmod = mod_inline[i] = loadmodel.copy();

			starmod.firstmodelsurface = bm.firstface;
			starmod.nummodelsurfaces = bm.numfaces;
			starmod.firstnode = bm.headnode;
			if (starmod.firstnode >= loadmodel.numnodes)
				Com.Error(Defines.ERR_DROP, "Inline model " + i
						+ " has bad firstnode");

			Math3D.VectorCopy(bm.maxs, starmod.maxs);
			Math3D.VectorCopy(bm.mins, starmod.mins);
			starmod.radius = bm.radius;

			if (i == 0)
				loadmodel = starmod.copy();

			starmod.numleafs = bm.visleafs;
		}
	}

	/*
	 * ==============================================================================
	 * 
	 * ALIAS MODELS
	 * 
	 * ==============================================================================
	 */

	/**
	 * Mod_LoadAliasModel.
	 */
	protected void Mod_LoadAliasModel(model_t mod, ByteBuffer buffer) {
		int i, j;
		qfiles.dmdl_t pheader;
		qfiles.dstvert_t[] poutst;
		qfiles.dtriangle_t[] pouttri;
		qfiles.daliasframe_t[] poutframe;
		int[] poutcmd;

		pheader = new qfiles.dmdl_t(buffer);

		if (pheader.version != qfiles.ALIAS_VERSION)
			Com.Error(Defines.ERR_DROP,
					"%s has wrong version number (%i should be %i)", new Vargs(
							3).add(mod.name).add(pheader.version).add(
							qfiles.ALIAS_VERSION));

		if (pheader.skinheight > Base.MAX_LBM_HEIGHT)
			Com.Error(Defines.ERR_DROP, "model " + mod.name
					+ " has a skin taller than " + Base.MAX_LBM_HEIGHT);

		if (pheader.num_xyz <= 0)
			Com.Error(Defines.ERR_DROP, "model " + mod.name
					+ " has no vertices");

		if (pheader.num_xyz > qfiles.MAX_VERTS)
			Com.Error(Defines.ERR_DROP, "model " + mod.name
					+ " has too many vertices");

		if (pheader.num_st <= 0)
			Com.Error(Defines.ERR_DROP, "model " + mod.name
					+ " has no st vertices");

		if (pheader.num_tris <= 0)
			Com.Error(Defines.ERR_DROP, "model " + mod.name
					+ " has no triangles");

		if (pheader.num_frames <= 0)
			Com.Error(Defines.ERR_DROP, "model " + mod.name + " has no frames");

		//
		// load base s and t vertices (not used in gl version)
		//
		poutst = new qfiles.dstvert_t[pheader.num_st];
		buffer.position(pheader.ofs_st);
		for (i = 0; i < pheader.num_st; i++) {
			poutst[i] = new qfiles.dstvert_t(buffer);
		}

		//
		// load triangle lists
		//
		pouttri = new qfiles.dtriangle_t[pheader.num_tris];
		buffer.position(pheader.ofs_tris);
		for (i = 0; i < pheader.num_tris; i++) {
			pouttri[i] = new qfiles.dtriangle_t(buffer);
		}

		//
		// load the frames
		//
		poutframe = new qfiles.daliasframe_t[pheader.num_frames];
		buffer.position(pheader.ofs_frames);
		for (i = 0; i < pheader.num_frames; i++) {
			poutframe[i] = new qfiles.daliasframe_t(buffer);
			// verts are all 8 bit, so no swapping needed
			poutframe[i].verts = new int[pheader.num_xyz];
			for (int k = 0; k < pheader.num_xyz; k++) {
				poutframe[i].verts[k] = buffer.getInt();
			}
		}

		mod.type = Base.mod_alias;

		//
		// load the glcmds
		//
		poutcmd = new int[pheader.num_glcmds];
		buffer.position(pheader.ofs_glcmds);
		for (i = 0; i < pheader.num_glcmds; i++)
			poutcmd[i] = buffer.getInt(); // LittleLong (pincmd[i]);

		// register all skins
		String[] skinNames = new String[pheader.num_skins];
		byte[] nameBuf = new byte[qfiles.MAX_SKINNAME];
		buffer.position(pheader.ofs_skins);
		for (i = 0; i < pheader.num_skins; i++) {
			buffer.get(nameBuf);
			skinNames[i] = new String(nameBuf);
			int n = skinNames[i].indexOf('\0');
			if (n > -1) {
				skinNames[i] = skinNames[i].substring(0, n);
			}
			mod.skins[i] = GL_FindImage(skinNames[i], Base.it_skin);
		}

		// set the model arrays
		pheader.skinNames = skinNames; // skin names
		pheader.stVerts = poutst; // textur koordinaten
		pheader.triAngles = pouttri; // dreiecke
		pheader.glCmds = poutcmd; // STRIP or FAN
		pheader.aliasFrames = poutframe; // frames mit vertex array

		mod.extradata = pheader;

		mod.mins[0] = -32;
		mod.mins[1] = -32;
		mod.mins[2] = -32;
		mod.maxs[0] = 32;
		mod.maxs[1] = 32;
		mod.maxs[2] = 32;

		if (fastjogl)
			precompileGLCmds(pheader);
	}

	boolean fastjogl = true;
	
	/*
	 * ==============================================================================
	 * 
	 * SPRITE MODELS
	 * 
	 * ==============================================================================
	 */

	/*
	 * ================= Mod_LoadSpriteModel =================
	 */
	protected void Mod_LoadSpriteModel(model_t mod, ByteBuffer buffer) {
		qfiles.dsprite_t sprout = new qfiles.dsprite_t(buffer);

		if (sprout.version != qfiles.SPRITE_VERSION)
			Com.Error(Defines.ERR_DROP,
					"%s has wrong version number (%i should be %i)", new Vargs(
							3).add(mod.name).add(sprout.version).add(
							qfiles.SPRITE_VERSION));

		if (sprout.numframes > qfiles.MAX_MD2SKINS)
			Com.Error(Defines.ERR_DROP, "%s has too many frames (%i > %i)",
					new Vargs(3).add(mod.name).add(sprout.numframes).add(
							qfiles.MAX_MD2SKINS));

		for (int i = 0; i < sprout.numframes; i++) {
			mod.skins[i] = GL_FindImage(sprout.frames[i].name, Base.it_sprite);
		}

		mod.type = Base.mod_sprite;
		mod.extradata = sprout;
	}

	// =============================================================================

	/*
	 * @@@@@@@@@@@@@@@@@@@@@ 
	 * R_BeginRegistration
	 * 
	 * Specifies the model that will be used as the world 
	 * @@@@@@@@@@@@@@@@@@@@@
	 */
	protected void R_BeginRegistration(String model) {
		
		if (fastjogl)
			resetModelArrays();
		
		Polygon.reset();

		cvar_t flushmap;

		registration_sequence++;
		r_oldviewcluster = -1; // force markleafs

		String fullname = "maps/" + model + ".bsp";

		// explicitly free the old map if different
		// this guarantees that mod_known[0] is the world map
		flushmap = Cvar.Get("flushmap", "0", 0);
		if (!mod_known[0].name.equals(fullname) || flushmap.value != 0.0f)
			Mod_Free(mod_known[0]);
		r_worldmodel = Mod_ForName(fullname, true);

		r_viewcluster = -1;
	}

	/*
	 * @@@@@@@@@@@@@@@@@@@@@ R_RegisterModel
	 * 
	 * @@@@@@@@@@@@@@@@@@@@@
	 */
	protected model_t R_RegisterModel(String name) {
		model_t mod = null;
		int i;
		qfiles.dsprite_t sprout;
		qfiles.dmdl_t pheader;

		mod = Mod_ForName(name, false);
		if (mod != null) {
			mod.registration_sequence = registration_sequence;

			// register any images used by the models
			if (mod.type == Base.mod_sprite) {
				sprout = (qfiles.dsprite_t) mod.extradata;
				for (i = 0; i < sprout.numframes; i++)
					mod.skins[i] = GL_FindImage(sprout.frames[i].name,
							Base.it_sprite);
			} else if (mod.type == Base.mod_alias) {
				pheader = (qfiles.dmdl_t) mod.extradata;
				for (i = 0; i < pheader.num_skins; i++)
					mod.skins[i] = GL_FindImage(pheader.skinNames[i],
							Base.it_skin);
				// PGM
				mod.numframes = pheader.num_frames;
				// PGM
			} else if (mod.type == Base.mod_brush) {
				for (i = 0; i < mod.numtexinfo; i++)
					mod.texinfo[i].image.registration_sequence = registration_sequence;
			}
		}
		return mod;
	}

	/*
	 * @@@@@@@@@@@@@@@@@@@@@ R_EndRegistration
	 * 
	 * @@@@@@@@@@@@@@@@@@@@@
	 */
	protected void R_EndRegistration() {
		model_t mod;

		for (int i = 0; i < mod_numknown; i++) {
			mod = mod_known[i];
			if (mod.name.length() == 0)
				continue;
			if (mod.registration_sequence != registration_sequence) { // don't
				// need
				// this
				// model
				Mod_Free(mod);
			} else {
				// precompile AliasModels
				if (fastjogl)
					if (mod.type == Base.mod_alias)
						precompileGLCmds((qfiles.dmdl_t) mod.extradata);
			}
		}
		GL_FreeUnusedImages();

		// modelMemoryUsage();
	}

	// =============================================================================

	/*
	 * ================ Mod_Free ================
	 */
	protected void Mod_Free(model_t mod) {
		mod.clear();
	}

	/*
	 * ================ Mod_FreeAll ================
	 */
	protected void Mod_FreeAll() {
		for (int i = 0; i < mod_numknown; i++) {
			if (mod_known[i].extradata != null)
				Mod_Free(mod_known[i]);
		}
	}

	/*
	 * new functions for vertex array handling
	 */
	protected static final int MODEL_BUFFER_SIZE = 50000;

	protected static FloatBuffer globalModelTextureCoordBuf = 
		newFloatBuffer(MODEL_BUFFER_SIZE * 2);

	protected static IntBuffer globalModelVertexIndexBuf = 
		newIntBuffer(MODEL_BUFFER_SIZE);

	protected void precompileGLCmds(qfiles.dmdl_t model) {
		model.textureCoordBuf = globalModelTextureCoordBuf.slice();
		model.vertexIndexBuf = globalModelVertexIndexBuf.slice();
		Vector tmp = new Vector();

		int count = 0;
		int[] order = model.glCmds;
		int orderIndex = 0;
		while (true) {
			// get the vertex count and primitive type
			count = order[orderIndex++];
			if (count == 0)
				break; // done

			tmp.addElement(new Integer(count));

			if (count < 0) {
				count = -count;
				// gl.glBegin (GL.GL_TRIANGLE_FAN);
			} else {
				// gl.glBegin (GL.GL_TRIANGLE_STRIP);
			}

			do {
				// texture coordinates come from the draw list
				globalModelTextureCoordBuf.put(Float
						.intBitsToFloat(order[orderIndex + 0]));
				globalModelTextureCoordBuf.put(Float
						.intBitsToFloat(order[orderIndex + 1]));
				globalModelVertexIndexBuf.put(order[orderIndex + 2]);

				orderIndex += 3;
			} while (--count != 0);
		}

		int size = tmp.size();

		model.counts = new int[size];
		model.indexElements = new IntBuffer[size];

		count = 0;
		int pos = 0;
		for (int i = 0; i < model.counts.length; i++) {
			count = ((Integer) tmp.get(i)).intValue();
			model.counts[i] = count;

			count = (count < 0) ? -count : count;
			model.vertexIndexBuf.position(pos);
			model.indexElements[i] = model.vertexIndexBuf.slice();
			model.indexElements[i].limit(count);
			pos += count;
		}
	}

	protected static void resetModelArrays() {
		globalModelTextureCoordBuf.rewind();
		globalModelVertexIndexBuf.rewind();
	}

	protected static void modelMemoryUsage() {
		System.out.println("AliasModels: globalVertexBuffer size "
				+ globalModelVertexIndexBuf.position());
	}
}
