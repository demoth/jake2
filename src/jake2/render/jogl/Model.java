/*
 * Model.java
 * Copyright (C) 2003
 *
 * $Id: Model.java,v 1.8 2004-01-14 21:30:00 cwei Exp $
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
package jake2.render.jogl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import jake2.Defines;
import jake2.game.cplane_t;
import jake2.game.cvar_t;
import jake2.qcommon.qfiles;
import jake2.render.mleaf_t;
import jake2.render.mnode_t;
import jake2.render.model_t;
import jake2.util.Math3D;
import jake2.util.Vargs;

/**
 * Model
 *  
 * @author cwei
 */
public abstract class Model extends Image {
	
////	   models.c -- model loading and caching
//
//	#include "gl_local.h"
//
	model_t	loadmodel;
	int modfilelen;
//
//	void Mod_LoadSpriteModel (model_t *mod, void *buffer);
//	void Mod_LoadBrushModel (model_t *mod, void *buffer);
//	void Mod_LoadAliasModel (model_t *mod, void *buffer);
//	model_t *Mod_LoadModel (model_t *mod, qboolean crash);
//
	byte[] mod_novis = new byte[Defines.MAX_MAP_LEAFS/8];

	static final int MAX_MOD_KNOWN = 512;
	model_t[] mod_known = new model_t[MAX_MOD_KNOWN];
	int mod_numknown;

	// the inline * models from the current map are kept seperate
	model_t[] mod_inline = new model_t[MAX_MOD_KNOWN];

	/*
	===============
	Mod_PointInLeaf
	===============
	*/
	mleaf_t Mod_PointInLeaf(float[] p, model_t model)
	{
		mnode_t node;
		float	d;
		cplane_t plane;
	
		if (model == null || model.nodes == null)
			ri.Sys_Error (Defines.ERR_DROP, "Mod_PointInLeaf: bad model");

		node = model.nodes[0];
		while (true)
		{
			if (node.contents != -1)
				return (mleaf_t)node;
			plane = node.plane;
			d = Math3D.DotProduct(p, plane.normal) - plane.dist;
			if (d > 0)
				node = node.children[0];
			else
				node = node.children[1];
		}
		// never reached
	}

	/*
	===================
	Mod_DecompressVis
	===================
	*/
//	byte *Mod_DecompressVis (byte *in, model_t *model)
//	{
//		static byte	decompressed[MAX_MAP_LEAFS/8];
//		int		c;
//		byte	*out;
//		int		row;
//
//		row = (model->vis->numclusters+7)>>3;	
//		out = decompressed;
//
//		if (!in)
//		{	// no vis info, so make all visible
//			while (row)
//			{
//				*out++ = 0xff;
//				row--;
//			}
//			return decompressed;		
//		}
//
//		do
//		{
//			if (*in)
//			{
//				*out++ = *in++;
//				continue;
//			}
//	
//			c = in[1];
//			in += 2;
//			while (c)
//			{
//				*out++ = 0;
//				c--;
//			}
//		} while (out - decompressed < row);
//	
//		return decompressed;
//	}
//
//	/*
//	==============
//	Mod_ClusterPVS
//	==============
//	*/
//	byte *Mod_ClusterPVS (int cluster, model_t *model)
//	{
//		if (cluster == -1 || !model->vis)
//			return mod_novis;
//		return Mod_DecompressVis ( (byte *)model->vis + model->vis->bitofs[cluster][DVIS_PVS],
//			model);
//	}
//
//
//	  ===============================================================================

	/*
	================
	Mod_Modellist_f
	================
	*/
	void Mod_Modellist_f()
	{
//		int		i;
//		model_t	*mod;
//		int		total;
//
//		total = 0;
//		ri.Con_Printf (PRINT_ALL,"Loaded models:\n");
//		for (i=0, mod=mod_known ; i < mod_numknown ; i++, mod++)
//		{
//			if (!mod->name[0])
//				continue;
//			ri.Con_Printf (PRINT_ALL, "%8i : %s\n",mod->extradatasize, mod->name);
//			total += mod->extradatasize;
//		}
//		ri.Con_Printf (PRINT_ALL, "Total resident: %i\n", total);
	}

	/*
	===============
	Mod_Init
	===============
	*/
	void Mod_Init()
	{
		// init mod_known
		for (int i=0; i < MAX_MOD_KNOWN; i++) {
			mod_known[i] = new model_t();
		}
		Arrays.fill(mod_novis, (byte)0xff);
	}



	/*
	==================
	Mod_ForName

	Loads in a model for the given name
	==================
	*/
	model_t Mod_ForName(String name, boolean crash)
	{
		model_t mod = null;
		int		i;
	
		if (name == null || name.length() == 0)
			ri.Sys_Error(Defines.ERR_DROP, "Mod_ForName: NULL name");
		
		//
		// inline models are grabbed only from worldmodel
		//
		if (name.charAt(0) == '*')
		{
			i = Integer.parseInt(name.substring(1));
			if (i < 1 || r_worldmodel == null || i >= r_worldmodel.numsubmodels)
				ri.Sys_Error (Defines.ERR_DROP, "bad inline model number");
			return mod_inline[i];
		}

		//
		// search the currently loaded models
		//
		for (i=0; i<mod_numknown ; i++)
		{
			mod = mod_known[i];
			
			if (mod.name == "")
				continue;
			if (mod.name.equals(name) )
				return mod;
		}
	
		//
		// find a free model slot spot
		//
		for (i=0; i<mod_numknown ; i++)
		{
			mod = mod_known[i];

			if (mod.name == "")
				break;	// free spot
		}
		if (i == mod_numknown)
		{
			if (mod_numknown == MAX_MOD_KNOWN)
				ri.Sys_Error (Defines.ERR_DROP, "mod_numknown == MAX_MOD_KNOWN");
			mod_numknown++;
			mod = mod_known[i];
		}

		mod.name = name; 
	
		//
		// load the file
		//
		byte[] buf = ri.FS_LoadFile(name);

		if (buf == null)
		{
			if (crash)
				ri.Sys_Error(Defines.ERR_DROP, "Mod_NumForName: " + mod.name + " not found");

			mod.name = "";
			return null;
		}

		modfilelen = buf.length;
	
		loadmodel = mod;

		//
		// fill it in
		//
		ByteBuffer bb = ByteBuffer.wrap(buf);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		// call the apropriate loader
	
		bb.mark();
		int ident = bb.getInt();
		
		bb.reset();
		
		switch (ident)
		{
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
			ri.Sys_Error(Defines.ERR_DROP,"Mod_NumForName: unknown fileid for " + mod.name);
			break;
		}

		return mod;
	}

	/*
	===============================================================================

						BRUSHMODEL LOADING

	===============================================================================
	*/

//	byte	*mod_base;


//	/*
//	=================
//	Mod_LoadLighting
//	=================
//	*/
//	void Mod_LoadLighting (lump_t *l)
//	{
//		if (!l->filelen)
//		{
//			loadmodel->lightdata = NULL;
//			return;
//		}
//		loadmodel->lightdata = Hunk_Alloc ( l->filelen);	
//		memcpy (loadmodel->lightdata, mod_base + l->fileofs, l->filelen);
//	}
//
//
//	/*
//	=================
//	Mod_LoadVisibility
//	=================
//	*/
//	void Mod_LoadVisibility (lump_t *l)
//	{
//		int		i;
//
//		if (!l->filelen)
//		{
//			loadmodel->vis = NULL;
//			return;
//		}
//		loadmodel->vis = Hunk_Alloc ( l->filelen);	
//		memcpy (loadmodel->vis, mod_base + l->fileofs, l->filelen);
//
//		loadmodel->vis->numclusters = LittleLong (loadmodel->vis->numclusters);
//		for (i=0 ; i<loadmodel->vis->numclusters ; i++)
//		{
//			loadmodel->vis->bitofs[i][0] = LittleLong (loadmodel->vis->bitofs[i][0]);
//			loadmodel->vis->bitofs[i][1] = LittleLong (loadmodel->vis->bitofs[i][1]);
//		}
//	}
//
//
//	/*
//	=================
//	Mod_LoadVertexes
//	=================
//	*/
//	void Mod_LoadVertexes (lump_t *l)
//	{
//		dvertex_t	*in;
//		mvertex_t	*out;
//		int			i, count;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->vertexes = out;
//		loadmodel->numvertexes = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			out->position[0] = LittleFloat (in->point[0]);
//			out->position[1] = LittleFloat (in->point[1]);
//			out->position[2] = LittleFloat (in->point[2]);
//		}
//	}
//
//	/*
//	=================
//	RadiusFromBounds
//	=================
//	*/
//	float RadiusFromBounds (vec3_t mins, vec3_t maxs)
//	{
//		int		i;
//		vec3_t	corner;
//
//		for (i=0 ; i<3 ; i++)
//		{
//			corner[i] = fabs(mins[i]) > fabs(maxs[i]) ? fabs(mins[i]) : fabs(maxs[i]);
//		}
//
//		return VectorLength (corner);
//	}
//
//
//	/*
//	=================
//	Mod_LoadSubmodels
//	=================
//	*/
//	void Mod_LoadSubmodels (lump_t *l)
//	{
//		dmodel_t	*in;
//		mmodel_t	*out;
//		int			i, j, count;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->submodels = out;
//		loadmodel->numsubmodels = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			for (j=0 ; j<3 ; j++)
//			{	// spread the mins / maxs by a pixel
//				out->mins[j] = LittleFloat (in->mins[j]) - 1;
//				out->maxs[j] = LittleFloat (in->maxs[j]) + 1;
//				out->origin[j] = LittleFloat (in->origin[j]);
//			}
//			out->radius = RadiusFromBounds (out->mins, out->maxs);
//			out->headnode = LittleLong (in->headnode);
//			out->firstface = LittleLong (in->firstface);
//			out->numfaces = LittleLong (in->numfaces);
//		}
//	}
//
//	/*
//	=================
//	Mod_LoadEdges
//	=================
//	*/
//	void Mod_LoadEdges (lump_t *l)
//	{
//		dedge_t *in;
//		medge_t *out;
//		int 	i, count;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( (count + 1) * sizeof(*out));	
//
//		loadmodel->edges = out;
//		loadmodel->numedges = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			out->v[0] = (unsigned short)LittleShort(in->v[0]);
//			out->v[1] = (unsigned short)LittleShort(in->v[1]);
//		}
//	}
//
//	/*
//	=================
//	Mod_LoadTexinfo
//	=================
//	*/
//	void Mod_LoadTexinfo (lump_t *l)
//	{
//		texinfo_t *in;
//		mtexinfo_t *out, *step;
//		int 	i, j, count;
//		char	name[MAX_QPATH];
//		int		next;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->texinfo = out;
//		loadmodel->numtexinfo = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			for (j=0 ; j<8 ; j++)
//				out->vecs[0][j] = LittleFloat (in->vecs[0][j]);
//
//			out->flags = LittleLong (in->flags);
//			next = LittleLong (in->nexttexinfo);
//			if (next > 0)
//				out->next = loadmodel->texinfo + next;
//			else
//				out->next = NULL;
//			Com_sprintf (name, sizeof(name), "textures/%s.wal", in->texture);
//
//			out->image = GL_FindImage (name, it_wall);
//			if (!out->image)
//			{
//				ri.Con_Printf (PRINT_ALL, "Couldn't load %s\n", name);
//				out->image = r_notexture;
//			}
//		}
//
//		// count animation frames
//		for (i=0 ; i<count ; i++)
//		{
//			out = &loadmodel->texinfo[i];
//			out->numframes = 1;
//			for (step = out->next ; step && step != out ; step=step->next)
//				out->numframes++;
//		}
//	}
//
//	/*
//	================
//	CalcSurfaceExtents
//
//	Fills in s->texturemins[] and s->extents[]
//	================
//	*/
//	void CalcSurfaceExtents (msurface_t *s)
//	{
//		float	mins[2], maxs[2], val;
//		int		i,j, e;
//		mvertex_t	*v;
//		mtexinfo_t	*tex;
//		int		bmins[2], bmaxs[2];
//
//		mins[0] = mins[1] = 999999;
//		maxs[0] = maxs[1] = -99999;
//
//		tex = s->texinfo;
//	
//		for (i=0 ; i<s->numedges ; i++)
//		{
//			e = loadmodel->surfedges[s->firstedge+i];
//			if (e >= 0)
//				v = &loadmodel->vertexes[loadmodel->edges[e].v[0]];
//			else
//				v = &loadmodel->vertexes[loadmodel->edges[-e].v[1]];
//		
//			for (j=0 ; j<2 ; j++)
//			{
//				val = v->position[0] * tex->vecs[j][0] + 
//					v->position[1] * tex->vecs[j][1] +
//					v->position[2] * tex->vecs[j][2] +
//					tex->vecs[j][3];
//				if (val < mins[j])
//					mins[j] = val;
//				if (val > maxs[j])
//					maxs[j] = val;
//			}
//		}
//
//		for (i=0 ; i<2 ; i++)
//		{	
//			bmins[i] = floor(mins[i]/16);
//			bmaxs[i] = ceil(maxs[i]/16);
//
//			s->texturemins[i] = bmins[i] * 16;
//			s->extents[i] = (bmaxs[i] - bmins[i]) * 16;
//
////			if ( !(tex->flags & TEX_SPECIAL) && s->extents[i] > 512 /* 256 */ )
////				ri.Sys_Error (ERR_DROP, "Bad surface extents");
//		}
//	}
//
//
//	void GL_BuildPolygonFromSurface(msurface_t *fa);
//	void GL_CreateSurfaceLightmap (msurface_t *surf);
//	void GL_EndBuildingLightmaps (void);
//	void GL_BeginBuildingLightmaps (model_t *m);
//
//	/*
//	=================
//	Mod_LoadFaces
//	=================
//	*/
//	void Mod_LoadFaces (lump_t *l)
//	{
//		dface_t		*in;
//		msurface_t 	*out;
//		int			i, count, surfnum;
//		int			planenum, side;
//		int			ti;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->surfaces = out;
//		loadmodel->numsurfaces = count;
//
//		currentmodel = loadmodel;
//
//		GL_BeginBuildingLightmaps (loadmodel);
//
//		for ( surfnum=0 ; surfnum<count ; surfnum++, in++, out++)
//		{
//			out->firstedge = LittleLong(in->firstedge);
//			out->numedges = LittleShort(in->numedges);		
//			out->flags = 0;
//			out->polys = NULL;
//
//			planenum = LittleShort(in->planenum);
//			side = LittleShort(in->side);
//			if (side)
//				out->flags |= SURF_PLANEBACK;			
//
//			out->plane = loadmodel->planes + planenum;
//
//			ti = LittleShort (in->texinfo);
//			if (ti < 0 || ti >= loadmodel->numtexinfo)
//				ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: bad texinfo number");
//			out->texinfo = loadmodel->texinfo + ti;
//
//			CalcSurfaceExtents (out);
//				
//		// lighting info
//
//			for (i=0 ; i<MAXLIGHTMAPS ; i++)
//				out->styles[i] = in->styles[i];
//			i = LittleLong(in->lightofs);
//			if (i == -1)
//				out->samples = NULL;
//			else
//				out->samples = loadmodel->lightdata + i;
//		
//		// set the drawing flags
//		
//			if (out->texinfo->flags & SURF_WARP)
//			{
//				out->flags |= SURF_DRAWTURB;
//				for (i=0 ; i<2 ; i++)
//				{
//					out->extents[i] = 16384;
//					out->texturemins[i] = -8192;
//				}
//				GL_SubdivideSurface (out);	// cut up polygon for warps
//			}
//
//			// create lightmaps and polygons
//			if ( !(out->texinfo->flags & (SURF_SKY|SURF_TRANS33|SURF_TRANS66|SURF_WARP) ) )
//				GL_CreateSurfaceLightmap (out);
//
//			if (! (out->texinfo->flags & SURF_WARP) ) 
//				GL_BuildPolygonFromSurface(out);
//
//		}
//
//		GL_EndBuildingLightmaps ();
//	}
//
//
//	/*
//	=================
//	Mod_SetParent
//	=================
//	*/
//	void Mod_SetParent (mnode_t *node, mnode_t *parent)
//	{
//		node->parent = parent;
//		if (node->contents != -1)
//			return;
//		Mod_SetParent (node->children[0], node);
//		Mod_SetParent (node->children[1], node);
//	}
//
//	/*
//	=================
//	Mod_LoadNodes
//	=================
//	*/
//	void Mod_LoadNodes (lump_t *l)
//	{
//		int			i, j, count, p;
//		dnode_t		*in;
//		mnode_t 	*out;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->nodes = out;
//		loadmodel->numnodes = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			for (j=0 ; j<3 ; j++)
//			{
//				out->minmaxs[j] = LittleShort (in->mins[j]);
//				out->minmaxs[3+j] = LittleShort (in->maxs[j]);
//			}
//	
//			p = LittleLong(in->planenum);
//			out->plane = loadmodel->planes + p;
//
//			out->firstsurface = LittleShort (in->firstface);
//			out->numsurfaces = LittleShort (in->numfaces);
//			out->contents = -1;	// differentiate from leafs
//
//			for (j=0 ; j<2 ; j++)
//			{
//				p = LittleLong (in->children[j]);
//				if (p >= 0)
//					out->children[j] = loadmodel->nodes + p;
//				else
//					out->children[j] = (mnode_t *)(loadmodel->leafs + (-1 - p));
//			}
//		}
//	
//		Mod_SetParent (loadmodel->nodes, NULL);	// sets nodes and leafs
//	}
//
//	/*
//	=================
//	Mod_LoadLeafs
//	=================
//	*/
//	void Mod_LoadLeafs (lump_t *l)
//	{
//		dleaf_t 	*in;
//		mleaf_t 	*out;
//		int			i, j, count, p;
////		glpoly_t	*poly;
//
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->leafs = out;
//		loadmodel->numleafs = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			for (j=0 ; j<3 ; j++)
//			{
//				out->minmaxs[j] = LittleShort (in->mins[j]);
//				out->minmaxs[3+j] = LittleShort (in->maxs[j]);
//			}
//
//			p = LittleLong(in->contents);
//			out->contents = p;
//
//			out->cluster = LittleShort(in->cluster);
//			out->area = LittleShort(in->area);
//
//			out->firstmarksurface = loadmodel->marksurfaces +
//				LittleShort(in->firstleafface);
//			out->nummarksurfaces = LittleShort(in->numleaffaces);
//		
//			// gl underwater warp
//	#if 0
//			if (out->contents & (CONTENTS_WATER|CONTENTS_SLIME|CONTENTS_LAVA|CONTENTS_THINWATER) )
//			{
//				for (j=0 ; j<out->nummarksurfaces ; j++)
//				{
//					out->firstmarksurface[j]->flags |= SURF_UNDERWATER;
//					for (poly = out->firstmarksurface[j]->polys ; poly ; poly=poly->next)
//						poly->flags |= SURF_UNDERWATER;
//				}
//			}
//	#endif
//		}	
//	}


//	/*
//	=================
//	Mod_LoadMarksurfaces
//	=================
//	*/
//	void Mod_LoadMarksurfaces (lump_t *l)
//	{	
//		int		i, j, count;
//		short		*in;
//		msurface_t **out;
//	
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->marksurfaces = out;
//		loadmodel->nummarksurfaces = count;
//
//		for ( i=0 ; i<count ; i++)
//		{
//			j = LittleShort(in[i]);
//			if (j < 0 ||  j >= loadmodel->numsurfaces)
//				ri.Sys_Error (ERR_DROP, "Mod_ParseMarksurfaces: bad surface number");
//			out[i] = loadmodel->surfaces + j;
//		}
//	}


//	/*
//	=================
//	Mod_LoadSurfedges
//	=================
//	*/
//	void Mod_LoadSurfedges (lump_t *l)
//	{	
//		int		i, count;
//		int		*in, *out;
//	
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		if (count < 1 || count >= MAX_MAP_SURFEDGES)
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: bad surfedges count in %s: %i",
//			loadmodel->name, count);
//
//		out = Hunk_Alloc ( count*sizeof(*out));	
//
//		loadmodel->surfedges = out;
//		loadmodel->numsurfedges = count;
//
//		for ( i=0 ; i<count ; i++)
//			out[i] = LittleLong (in[i]);
//	}


//	/*
//	=================
//	Mod_LoadPlanes
//	=================
//	*/
//	void Mod_LoadPlanes (lump_t *l)
//	{
//		int			i, j;
//		cplane_t	*out;
//		dplane_t 	*in;
//		int			count;
//		int			bits;
//	
//		in = (void *)(mod_base + l->fileofs);
//		if (l->filelen % sizeof(*in))
//			ri.Sys_Error (ERR_DROP, "MOD_LoadBmodel: funny lump size in %s",loadmodel->name);
//		count = l->filelen / sizeof(*in);
//		out = Hunk_Alloc ( count*2*sizeof(*out));	
//	
//		loadmodel->planes = out;
//		loadmodel->numplanes = count;
//
//		for ( i=0 ; i<count ; i++, in++, out++)
//		{
//			bits = 0;
//			for (j=0 ; j<3 ; j++)
//			{
//				out->normal[j] = LittleFloat (in->normal[j]);
//				if (out->normal[j] < 0)
//					bits |= 1<<j;
//			}
//
//			out->dist = LittleFloat (in->dist);
//			out->type = LittleLong (in->type);
//			out->signbits = bits;
//		}
//	}

	/*
	=================
	Mod_LoadBrushModel
	=================
	*/
	void Mod_LoadBrushModel(model_t mod, ByteBuffer buffer)
	{
//		int			i;
//		dheader_t	*header;
//		mmodel_t 	*bm;
//	
//		loadmodel->type = mod_brush;
//		if (loadmodel != mod_known)
//			ri.Sys_Error (ERR_DROP, "Loaded a brush model after the world");
//
//		header = (dheader_t *)buffer;
//
//		i = LittleLong (header->version);
//		if (i != BSPVERSION)
//			ri.Sys_Error (ERR_DROP, "Mod_LoadBrushModel: %s has wrong version number (%i should be %i)", mod->name, i, BSPVERSION);
//
////	   swap all the lumps
//		mod_base = (byte *)header;
//
//		for (i=0 ; i<sizeof(dheader_t)/4 ; i++)
//			((int *)header)[i] = LittleLong ( ((int *)header)[i]);
//
////	   load into heap
//	
//		Mod_LoadVertexes (&header->lumps[LUMP_VERTEXES]);
//		Mod_LoadEdges (&header->lumps[LUMP_EDGES]);
//		Mod_LoadSurfedges (&header->lumps[LUMP_SURFEDGES]);
//		Mod_LoadLighting (&header->lumps[LUMP_LIGHTING]);
//		Mod_LoadPlanes (&header->lumps[LUMP_PLANES]);
//		Mod_LoadTexinfo (&header->lumps[LUMP_TEXINFO]);
//		Mod_LoadFaces (&header->lumps[LUMP_FACES]);
//		Mod_LoadMarksurfaces (&header->lumps[LUMP_LEAFFACES]);
//		Mod_LoadVisibility (&header->lumps[LUMP_VISIBILITY]);
//		Mod_LoadLeafs (&header->lumps[LUMP_LEAFS]);
//		Mod_LoadNodes (&header->lumps[LUMP_NODES]);
//		Mod_LoadSubmodels (&header->lumps[LUMP_MODELS]);
//		mod->numframes = 2;		// regular and alternate animation
//	
////
////	   set up the submodels
////
//		for (i=0 ; i<mod->numsubmodels ; i++)
//		{
//			model_t	*starmod;
//
//			bm = &mod->submodels[i];
//			starmod = &mod_inline[i];
//
//			*starmod = *loadmodel;
//		
//			starmod->firstmodelsurface = bm->firstface;
//			starmod->nummodelsurfaces = bm->numfaces;
//			starmod->firstnode = bm->headnode;
//			if (starmod->firstnode >= loadmodel->numnodes)
//				ri.Sys_Error (ERR_DROP, "Inline model %i has bad firstnode", i);
//
//			VectorCopy (bm->maxs, starmod->maxs);
//			VectorCopy (bm->mins, starmod->mins);
//			starmod->radius = bm->radius;
//	
//			if (i == 0)
//				*loadmodel = *starmod;
//
//			starmod->numleafs = bm->visleafs;
//		}
	}

	/*
	==============================================================================

	ALIAS MODELS

	==============================================================================
	*/

	/*
	=================
	Mod_LoadAliasModel
	=================
	*/
	void Mod_LoadAliasModel (model_t mod, ByteBuffer buffer)
	{
		int i, j;
		qfiles.dmdl_t pheader;
		qfiles.dstvert_t[] poutst;
		qfiles.dtriangle_t[] pouttri;
		qfiles.daliasframe_t[] poutframe;
		int[] poutcmd;

		pheader = new qfiles.dmdl_t(buffer);

		if (pheader.version != qfiles.ALIAS_VERSION)
			ri.Sys_Error(Defines.ERR_DROP, "%s has wrong version number (%i should be %i)",
					 new Vargs(3).add(mod.name).add(pheader.version).add(qfiles.ALIAS_VERSION));

		if (pheader.skinheight > MAX_LBM_HEIGHT)
			ri.Sys_Error(Defines.ERR_DROP, "model "+ mod.name +" has a skin taller than " + MAX_LBM_HEIGHT);

		if (pheader.num_xyz <= 0)
			ri.Sys_Error(Defines.ERR_DROP, "model " + mod.name + " has no vertices");

		if (pheader.num_xyz > qfiles.MAX_VERTS)
			ri.Sys_Error(Defines.ERR_DROP, "model " + mod.name +" has too many vertices");

		if (pheader.num_st <= 0)
			ri.Sys_Error(Defines.ERR_DROP, "model " + mod.name + " has no st vertices");

		if (pheader.num_tris <= 0)
			ri.Sys_Error(Defines.ERR_DROP, "model " + mod.name + " has no triangles");

		if (pheader.num_frames <= 0)
			ri.Sys_Error(Defines.ERR_DROP, "model " + mod.name + " has no frames");

		//
		// load base s and t vertices (not used in gl version)
		//
		poutst = new qfiles.dstvert_t[pheader.num_st]; 
		buffer.position(pheader.ofs_st);
		for (i=0 ; i<pheader.num_st ; i++)
		{
			poutst[i] = new qfiles.dstvert_t(buffer);
		} 

		//
		//	   load triangle lists
		//
		pouttri = new qfiles.dtriangle_t[pheader.num_tris];
		buffer.position(pheader.ofs_tris);
		for (i=0 ; i<pheader.num_tris ; i++)
		{
			pouttri[i] = new qfiles.dtriangle_t(buffer);
		}

		//
		//	   load the frames
		//
		poutframe = new qfiles.daliasframe_t[pheader.num_frames];
		buffer.position(pheader.ofs_frames);
		for (i=0 ; i<pheader.num_frames ; i++)
		{
			poutframe[i] = new qfiles.daliasframe_t(buffer);
			// verts are all 8 bit, so no swapping needed
			poutframe[i].verts = new qfiles.dtrivertx_t[pheader.num_xyz];
			for (int k=0; k < pheader.num_xyz; k++) {
				poutframe[i].verts[k] = new qfiles.dtrivertx_t(buffer);	
			}
		}

		mod.type = mod_alias;

		//
		// load the glcmds
		//
		poutcmd = new int[pheader.num_glcmds];
		buffer.position(pheader.ofs_glcmds);
		for (i=0 ; i<pheader.num_glcmds ; i++)
			poutcmd[i] = buffer.getInt(); // LittleLong (pincmd[i]);

		// register all skins
		String[] skinNames = new String[pheader.num_skins];
		byte[] nameBuf = new byte[qfiles.MAX_SKINNAME];
		buffer.position(pheader.ofs_skins);
		for (i=0 ; i<pheader.num_skins ; i++)
		{
			buffer.get(nameBuf);
			skinNames[i] = new String(nameBuf).trim();
			mod.skins[i] = GL_FindImage(skinNames[i], it_skin);
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
	}

	/*
	==============================================================================

	SPRITE MODELS

	==============================================================================
	*/

	/*
	=================
	Mod_LoadSpriteModel
	=================
	*/
	void Mod_LoadSpriteModel(model_t mod, ByteBuffer buffer)
	{
		qfiles.dsprite_t sprout = new qfiles.dsprite_t(buffer);
		
		if (sprout.version != qfiles.SPRITE_VERSION)
			ri.Sys_Error(Defines.ERR_DROP, "%s has wrong version number (%i should be %i)",
				new Vargs(3).add(mod.name).add(sprout.version).add(qfiles.SPRITE_VERSION));

		if (sprout.numframes > qfiles.MAX_MD2SKINS)
			ri.Sys_Error(Defines.ERR_DROP, "%s has too many frames (%i > %i)",
				new Vargs(3).add(mod.name).add(sprout.numframes).add(qfiles.MAX_MD2SKINS));

		for (int i=0 ; i<sprout.numframes ; i++)
		{
			mod.skins[i] = GL_FindImage(sprout.frames[i].name,	it_sprite);
		}

		mod.type = mod_sprite;
		mod.extradata = sprout;
	}

//	  =============================================================================

	/*
	@@@@@@@@@@@@@@@@@@@@@
	R_BeginRegistration

	Specifies the model that will be used as the world
	@@@@@@@@@@@@@@@@@@@@@
	*/
	protected void R_BeginRegistration(String model)
	{
		cvar_t flushmap;

		registration_sequence++;
		r_oldviewcluster = -1;		// force markleafs

		String fullname = "maps/" + model + ".bsp";

		// explicitly free the old map if different
		// this guarantees that mod_known[0] is the world map
		flushmap = ri.Cvar_Get("flushmap", "0", 0);
		if ( !mod_known[0].name.equals(fullname) || flushmap.value != 0.0f)
			Mod_Free(mod_known[0]);
		r_worldmodel = Mod_ForName(fullname, true);

		r_viewcluster = -1;
	}


	/*
	@@@@@@@@@@@@@@@@@@@@@
	R_RegisterModel

	@@@@@@@@@@@@@@@@@@@@@
	*/
	protected model_t R_RegisterModel(String name)
	{
		model_t	mod = null;
		int		i;
		qfiles.dsprite_t sprout;
		qfiles.dmdl_t pheader;

		mod = Mod_ForName(name, false);
		if (mod != null)
		{
			mod.registration_sequence = registration_sequence;

			// register any images used by the models
			if (mod.type == mod_sprite)
			{
				sprout = (qfiles.dsprite_t)mod.extradata;
				for (i=0 ; i<sprout.numframes ; i++)
					mod.skins[i] = GL_FindImage(sprout.frames[i].name, it_sprite);
			}
			else if (mod.type == mod_alias)
			{
				pheader = (qfiles.dmdl_t)mod.extradata;
				for (i=0 ; i<pheader.num_skins ; i++)
					mod.skins[i] = GL_FindImage(pheader.skinNames[i], it_skin);
				// PGM
				mod.numframes = pheader.num_frames;
				// PGM
			}
			else if (mod.type == mod_brush)
			{
				for (i=0 ; i<mod.numtexinfo ; i++)
					mod.texinfo[i].image.registration_sequence = registration_sequence;
			}
		}
		return mod;
	}


	/*
	@@@@@@@@@@@@@@@@@@@@@
	R_EndRegistration

	@@@@@@@@@@@@@@@@@@@@@
	*/
	protected void R_EndRegistration()
	{
		model_t	mod;

		for (int i=0; i<mod_numknown ; i++)
		{
			mod = mod_known[i];
			if (mod.name == "")
				continue;
			if (mod.registration_sequence != registration_sequence)
			{	// don't need this model
				// Mod_Free(mod);
				// TODO clear model_t or new model_t()
				mod_known[i] = new model_t();
			}
		}

		GL_FreeUnusedImages();
	}


//	  =============================================================================


	/*
	================
	Mod_Free
	================
	*/
	void Mod_Free (model_t mod)
	{
		// Hunk_Free (mod->extradata);
		// memset (mod, 0, sizeof(*mod));
		mod.clear();
		
	}

	/*
	================
	Mod_FreeAll
	================
	*/
	void Mod_FreeAll()
	{
		for (int i=0 ; i<mod_numknown ; i++)
		{
			if (mod_known[i].extradata != null)
				Mod_Free(mod_known[i]);
		}
	}


}
