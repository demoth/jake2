/*
 * qfiles.java
 * Copyright (C) 2003
 *
 * $Id: qfiles.java,v 1.11 2004-01-28 15:00:19 cwei Exp $
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

/**
 * qfiles
 * 
 * @author cwei
 */
public class qfiles {
	//
	// qfiles.h: quake file formats
	// This file must be identical in the quake and utils directories
	//

	/*
	========================================================================
	
	The .pak files are just a linear collapse of a directory tree
	
	========================================================================
	*/

	//	#define IDPAKHEADER		(('K'<<24)+('C'<<16)+('A'<<8)+'P')

	// gibts schon in FS
	/*
		typedef struct
		{
			char	name[56];
			int		filepos, filelen;
		} dpackfile_t;
	
		typedef struct
		{
			int		ident;		// == IDPAKHEADER
			int		dirofs;
			int		dirlen;
		} dpackheader_t;
	*/

	//	#define	MAX_FILES_IN_PACK	4096

	/*
	========================================================================
	
	PCX files are used for as many images as possible
	
	========================================================================
	*/
	public static class pcx_t {

		// size of byte arrays
		static final int PALETTE_SIZE = 48;
		static final int FILLER_SIZE = 58;

		public byte manufacturer;
		public byte version;
		public byte encoding;
		public byte bits_per_pixel;
		public int xmin, ymin, xmax, ymax; // unsigned short
		public int hres, vres; // unsigned short
		public byte[] palette; //unsigned byte; size 48
		public byte reserved;
		public byte color_planes;
		public int bytes_per_line; // unsigned short
		public int palette_type; // unsigned short
		public byte[] filler; // size 58
		public ByteBuffer data; //unbounded data

		public pcx_t(byte[] dataBytes) {
			this(ByteBuffer.wrap(dataBytes));
		}

		public pcx_t(ByteBuffer b) {
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);

			// fill header
			manufacturer = b.get();
			version = b.get();
			encoding = b.get();
			bits_per_pixel = b.get();
			xmin = b.getShort() & 0xffff;
			ymin = b.getShort() & 0xffff;
			xmax = b.getShort() & 0xffff;
			ymax = b.getShort() & 0xffff;
			hres = b.getShort() & 0xffff;
			vres = b.getShort() & 0xffff;
			b.get(palette = new byte[PALETTE_SIZE]);
			reserved = b.get();
			color_planes = b.get();
			bytes_per_line = b.getShort() & 0xffff;
			palette_type = b.getShort() & 0xffff;
			b.get(filler = new byte[FILLER_SIZE]);

			// fill data
			data = b.slice();
		}
	}

	/*
	========================================================================
	
	.MD2 triangle model file format
	
	========================================================================
	*/
	
	public static final int IDALIASHEADER =	(('2'<<24)+('P'<<16)+('D'<<8)+'I');
	public static final int ALIAS_VERSION = 8;
	
	public static final int MAX_TRIANGLES = 4096;
	public static final int MAX_VERTS = 2048;
	public static final int MAX_FRAMES = 512;
	public static final int MAX_MD2SKINS = 32;
	public static final int MAX_SKINNAME = 64;
	
	public static class dstvert_t {
		public short s;
		public short t;
		
		public dstvert_t(ByteBuffer b) {
			s = b.getShort();
			t = b.getShort();
		}
	}

	public static class dtriangle_t {
		public short index_xyz[] = { 0, 0, 0 };
		public short index_st[] = { 0, 0, 0 };
		
		public dtriangle_t(ByteBuffer b) {
			index_xyz[0] = b.getShort();
			index_xyz[1] = b.getShort();
			index_xyz[2] = b.getShort();
			
			index_st[0] = b.getShort();
			index_st[1] = b.getShort();
			index_st[2] = b.getShort();
		}
	}

	public static class dtrivertx_t {
		public int v[] = { 0, 0, 0 }; //  byte 0..255 scaled byte to fit in frame mins/maxs
		public int lightnormalindex; // byte 0 .. 255;
		
		public dtrivertx_t(ByteBuffer b) {
			v[0] = b.get() & 0xff; // unsigned byte
			v[1] = b.get() & 0xff; // unsigned byte
			v[2] = b.get() & 0xff; // unsigned byte
			lightnormalindex = b.get() & 0xff; // unsigned byte
		}
	}

	public static final int DTRIVERTX_V0 =  0;
	public static final int DTRIVERTX_V1 = 1;
	public static final int DTRIVERTX_V2 = 2;
	public static final int DTRIVERTX_LNI = 3;
	public static final int DTRIVERTX_SIZE = 4;
	
	public static class  daliasframe_t {
		public float[] scale = {0, 0, 0}; // multiply byte verts by this
		public float[] translate = {0, 0, 0};	// then add this
		public String name; // frame name from grabbing (size 16)
		public dtrivertx_t[] verts;	// variable sized
		
		public daliasframe_t(ByteBuffer b) {
			scale[0] = b.getFloat();	scale[1] = b.getFloat();	scale[2] = b.getFloat();
			translate[0] = b.getFloat(); translate[1] = b.getFloat(); translate[2] = b.getFloat();
			byte[] nameBuf = new byte[16];
			b.get(nameBuf);
			name = new String(nameBuf).trim();
		}
	}
	
	//	   the glcmd format:
	//	   a positive integer starts a tristrip command, followed by that many
	//	   vertex structures.
	//	   a negative integer starts a trifan command, followed by -x vertexes
	//	   a zero indicates the end of the command list.
	//	   a vertex consists of a floating point s, a floating point t,
	//	   and an integer vertex index.
	
	public static class dmdl_t {
		public int ident;
		public int version;

		public int skinwidth;
		public int skinheight;
		public int framesize; // byte size of each frame

		public int num_skins;
		public int num_xyz;
		public int num_st; // greater than num_xyz for seams
		public int num_tris;
		public int num_glcmds; // dwords in strip/fan command list
		public int num_frames;

		public int ofs_skins; // each skin is a MAX_SKINNAME string
		public int ofs_st; // byte offset from start for stverts
		public int ofs_tris; // offset for dtriangles
		public int ofs_frames; // offset for first frame
		public int ofs_glcmds;
		public int ofs_end; // end of file
		
		// wird extra gebraucht
		public String[] skinNames;
		public dstvert_t[] stVerts;
		public dtriangle_t[] triAngles;
		public int[] glCmds;
		public daliasframe_t[] aliasFrames;
		
		
		public dmdl_t(ByteBuffer b) {
			ident = b.getInt();
			version = b.getInt();

			skinwidth = b.getInt();
			skinheight = b.getInt();
			framesize = b.getInt(); // byte size of each frame

			num_skins = b.getInt();
			num_xyz = b.getInt();
			num_st = b.getInt(); // greater than num_xyz for seams
			num_tris = b.getInt();
			num_glcmds = b.getInt(); // dwords in strip/fan command list
			num_frames = b.getInt();

			ofs_skins = b.getInt(); // each skin is a MAX_SKINNAME string
			ofs_st = b.getInt(); // byte offset from start for stverts
			ofs_tris = b.getInt(); // offset for dtriangles
			ofs_frames = b.getInt(); // offset for first frame
			ofs_glcmds = b.getInt();
			ofs_end = b.getInt(); // end of file
		}
	}
	
	/*
	========================================================================
	
	.SP2 sprite file format
	
	========================================================================
	*/
	// little-endian "IDS2"
	public static final int IDSPRITEHEADER = (('2'<<24)+('S'<<16)+('D'<<8)+'I');
	public static final int SPRITE_VERSION = 2;

	public static class dsprframe_t {
		public int width, height;
		public int origin_x, origin_y; // raster coordinates inside pic
		public String name; // name of pcx file (MAX_SKINNAME)
		
		public dsprframe_t(ByteBuffer b) {
			width = b.getInt();
			height = b.getInt();
			origin_x = b.getInt();
			origin_y = b.getInt();
			
			byte[] nameBuf = new byte[MAX_SKINNAME];
			b.get(nameBuf);
			name = new String(nameBuf).trim();
		}
	}

	public static class dsprite_t {
		public int ident;
		public int version;
		public int numframes;
		public dsprframe_t frames[]; // variable sized
		
		public dsprite_t(ByteBuffer b) {
			ident = b.getInt();
			version = b.getInt();
			numframes = b.getInt();
			
			frames = new dsprframe_t[numframes];
			for (int i=0; i < numframes; i++) {
				frames[i] = new dsprframe_t(b);	
			}
		}
	}
	
	/*
	==============================================================================
	
	  .WAL texture file format
	
	==============================================================================
	*/
	public static class miptex_t {

		static final int MIPLEVELS = 4;
		static final int NAME_SIZE = 32;

		public String name; // char name[32];
		public int width, height;
		public int[] offsets = new int[MIPLEVELS]; // 4 mip maps stored
		// next frame in animation chain
		public String animname; //	char	animname[32];
		public int flags;
		public int contents;
		public int value;

		public miptex_t(byte[] dataBytes) {
			this(ByteBuffer.wrap(dataBytes));
		}

		public miptex_t(ByteBuffer b) {
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);

			byte[] nameBuf = new byte[NAME_SIZE];
			// fill header
			b.get(nameBuf);
			name = new String(nameBuf).trim();
			width = b.getInt();
			height = b.getInt();
			offsets[0] = b.getInt();
			offsets[1] = b.getInt();
			offsets[2] = b.getInt();
			offsets[3] = b.getInt();
			b.get(nameBuf);
			animname = new String(nameBuf).trim();
			flags = b.getInt();
			contents = b.getInt();
			value = b.getInt();
		}

	}
	
	/*
	==============================================================================
	
	  .BSP file format
	
	==============================================================================
	*/

	public static final int IDBSPHEADER = (('P'<<24)+('S'<<16)+('B'<<8)+'I');
	// little-endian "IBSP"

	//	#define BSPVERSION	38

	// upper design bounds
	// leaffaces, leafbrushes, planes, and verts are still bounded by
	// 16 bit short limits
	//	#define	MAX_MAP_MODELS		1024
	//	#define	MAX_MAP_BRUSHES		8192
	//	#define	MAX_MAP_ENTITIES	2048
	//	#define	MAX_MAP_ENTSTRING	0x40000
	//	#define	MAX_MAP_TEXINFO		8192

	//	#define	MAX_MAP_AREAS		256
	//	#define	MAX_MAP_AREAPORTALS	1024
	//	#define	MAX_MAP_PLANES		65536
	//	#define	MAX_MAP_NODES		65536
	//	#define	MAX_MAP_BRUSHSIDES	65536
	//	#define	MAX_MAP_LEAFS		65536
	//	#define	MAX_MAP_VERTS		65536
	//	#define	MAX_MAP_FACES		65536
	//	#define	MAX_MAP_LEAFFACES	65536
	//	#define	MAX_MAP_LEAFBRUSHES 65536
	//	#define	MAX_MAP_PORTALS		65536
	//	#define	MAX_MAP_EDGES		128000
	//	#define	MAX_MAP_SURFEDGES	256000
	//	#define	MAX_MAP_LIGHTING	0x200000
	//	#define	MAX_MAP_VISIBILITY	0x100000

	// key / value pair sizes

	//	#define	MAX_KEY		32
	//	#define	MAX_VALUE	1024

	// =============================================================================

	// gibts schon in qcommon
	/*
		typedef struct
		{
			int		fileofs, filelen;
		} lump_t;
	*/

	//	#define	LUMP_ENTITIES		0
	//	#define	LUMP_PLANES			1
	//	#define	LUMP_VERTEXES		2
	//	#define	LUMP_VISIBILITY		3
	//	#define	LUMP_NODES			4
	//	#define	LUMP_TEXINFO		5
	//	#define	LUMP_FACES			6
	//	#define	LUMP_LIGHTING		7
	//	#define	LUMP_LEAFS			8
	//	#define	LUMP_LEAFFACES		9
	//	#define	LUMP_LEAFBRUSHES	10
	//	#define	LUMP_EDGES			11
	//	#define	LUMP_SURFEDGES		12
	//	#define	LUMP_MODELS			13
	//	#define	LUMP_BRUSHES		14
	//	#define	LUMP_BRUSHSIDES		15
	//	#define	LUMP_POP			16
	//	#define	LUMP_AREAS			17
	//	#define	LUMP_AREAPORTALS	18
	//	#define	HEADER_LUMPS		19

	public static class dheader_t {

		public dheader_t(ByteBuffer bb) {

			this.ident = bb.getInt();
			this.version = bb.getInt();

			for (int n = 0; n < Defines.HEADER_LUMPS; n++)
				lumps[n] = new lump_t(bb.getInt(), bb.getInt());

		}

		public int ident;
		public int version;
		public lump_t lumps[] = new lump_t[Defines.HEADER_LUMPS];
	}

	public static class dmodel_t {

		public dmodel_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);

			for (int j = 0; j < 3; j++)
				mins[j] = bb.getFloat();

			for (int j = 0; j < 3; j++)
				maxs[j] = bb.getFloat();

			for (int j = 0; j < 3; j++)
				origin[j] = bb.getFloat();

			headnode = bb.getInt();
			firstface = bb.getInt();
			numfaces = bb.getInt();
		}
		public float mins[] = { 0, 0, 0 };
		public float maxs[] = { 0, 0, 0 };
		public float origin[] = { 0, 0, 0 }; // for sounds or lights
		public int headnode;
		public int firstface, numfaces; // submodels just draw faces
		// without walking the bsp tree

		public static int SIZE = 3 * 4 + 3 * 4 + 3 * 4 + 4 + 8;
	}
	
	public static class dvertex_t {
		
		public static final int SIZE = 3 * 4; // 3 mal 32 bit float 
		
		public float[] point = { 0, 0, 0 };
		
		public dvertex_t(ByteBuffer b) {
			point[0] = b.getFloat();
			point[1] = b.getFloat();
			point[2] = b.getFloat();
		}
	}

	// 0-2 are axial planes
	//	#define	PLANE_X			0
	//	#define	PLANE_Y			1
	//	#define	PLANE_Z			2

	// 3-5 are non-axial planes snapped to the nearest
	// #define	PLANE_ANYX		3
	// #define	PLANE_ANYY		4
	// #define	PLANE_ANYZ		5

	// planes (x&~1) and (x&~1)+1 are always opposites

	public static class dplane_t {

		// planes (x&~1) and (x&~1)+1 are always opposites

		public dplane_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);

			normal[0] = (bb.getFloat());
			normal[1] = (bb.getFloat());
			normal[2] = (bb.getFloat());

			dist = (bb.getFloat());
			type = (bb.getInt());
		}

		public float normal[] = { 0, 0, 0 };
		public float dist;
		public int type; // PLANE_X - PLANE_ANYZ ?remove? trivial to regenerate

		public static final int SIZE = 3 * 4 + 4 + 4;
	}

	//
	// contents flags are seperate bits
	// a given brush can contribute multiple content bits
	// multiple brushes can be in a single leaf
	//
	// these definitions also need to be in q_shared.h!
	//
	// lower bits are stronger, and will eat weaker brushes completely
	//	#define	CONTENTS_SOLID			1		// an eye is never valid in a solid
	//	#define	CONTENTS_WINDOW			2		// translucent, but not watery
	//	#define	CONTENTS_AUX			4
	//	#define	CONTENTS_LAVA			8
	//	#define	CONTENTS_SLIME			16
	//	#define	CONTENTS_WATER			32
	//	#define	CONTENTS_MIST			64
	//	#define	LAST_VISIBLE_CONTENTS	64
	//
	// remaining contents are non-visible, and don't eat brushes
	//
	//	#define	CONTENTS_AREAPORTAL		0x8000
	//
	//	#define	CONTENTS_PLAYERCLIP		0x10000
	//	#define	CONTENTS_MONSTERCLIP	0x20000
	//
	// currents can be added to any other contents, and may be mixed
	//	#define	CONTENTS_CURRENT_0		0x40000
	//	#define	CONTENTS_CURRENT_90		0x80000
	//	#define	CONTENTS_CURRENT_180	0x100000
	//	#define	CONTENTS_CURRENT_270	0x200000
	//	#define	CONTENTS_CURRENT_UP		0x400000
	//	#define	CONTENTS_CURRENT_DOWN	0x800000
	//
	//	#define	CONTENTS_ORIGIN			0x1000000	// removed before bsping an entity
	//
	//	#define	CONTENTS_MONSTER		0x2000000	// should never be on a brush, only in game
	//	#define	CONTENTS_DEADMONSTER	0x4000000
	//	#define	CONTENTS_DETAIL			0x8000000	// brushes to be added after vis leafs
	//	#define	CONTENTS_TRANSLUCENT	0x10000000	// auto set if any surface has trans
	//	#define	CONTENTS_LADDER			0x20000000
	//
	//	#define	SURF_LIGHT		0x1		// value will hold the light strength
	//
	//	#define	SURF_SLICK		0x2		// effects game physics
	//
	//	#define	SURF_SKY		0x4		// don't draw, but add to skybox
	//	#define	SURF_WARP		0x8		// turbulent water warp
	//	#define	SURF_TRANS33	0x10
	//	#define	SURF_TRANS66	0x20
	//	#define	SURF_FLOWING	0x40	// scroll towards angle
	//	#define	SURF_NODRAW		0x80	// don't bother referencing the texture

	public static class dnode_t {

		public dnode_t(ByteBuffer bb) {

			bb.order(ByteOrder.LITTLE_ENDIAN);
			planenum = bb.getInt();

			children[0] = bb.getInt();
			children[1] = bb.getInt();

			for (int j = 0; j < 3; j++)
				mins[j] = bb.getShort();

			for (int j = 0; j < 3; j++)
				maxs[j] = bb.getShort();

			firstface = bb.getShort() & 0xffff;
			numfaces = bb.getShort() & 0xffff;

		}

		public int planenum;
		public int children[] = { 0, 0 };
		// negative numbers are -(leafs+1), not nodes
		public short mins[] = { 0, 0, 0 }; // for frustom culling
		public short maxs[] = { 0, 0, 0 };

		/*
		unsigned short	firstface;
		unsigned short	numfaces;	// counting both sides
		*/

		public int firstface;
		public int numfaces;

		public static int SIZE = 4 + 8 + 6 + 6 + 2 + 2; // counting both sides
	}
	
	// gibts schon in qcommon
	/*
	 	public static class texinfo_t
		{
			public float[][] vecs = new float[2][4]; // [s/t][xyz offset]
			public int flags; // miptex flags + overrides
			public int value; // light emission, etc
			public String texture;
			// char texture[32];	// texture name (textures/*.wal)
			public int nexttexinfo; // for animations, -1 = end of chain
		}
	*/

	// note that edge 0 is never used, because negative edge nums are used for
	// counterclockwise use of the edge in a face
	
	public static class dedge_t {
		// unsigned short v[2];
		int v[] = { 0, 0 };
	}

	//	#define	MAXLIGHTMAPS	4
	
	public static class dface_t {
		
		public static final int SIZE =
				4 * Defines.SIZE_OF_SHORT
			+	2 * Defines.SIZE_OF_INT
			+	Defines.MAXLIGHTMAPS;

		//unsigned short	planenum;
		public int planenum;
		public short side;

		public int firstedge; // we must support > 64k edges
		public short numedges;
		public short texinfo;

		// lighting info
		public byte styles[] = new byte[Defines.MAXLIGHTMAPS];
		public int lightofs; // start of [numstyles*surfsize] samples
		
		public dface_t(ByteBuffer b) {
			planenum = b.getShort() & 0xFFFF;
			side = b.getShort();
			firstedge = b.getInt();
			numedges = b.getShort();
			texinfo = b.getShort();
			b.get(styles);
			lightofs = b.getInt();
		}
		
	}

	public static class dleaf_t {

		public dleaf_t(byte[] cmod_base, int i, int j) {
			this(ByteBuffer.wrap(cmod_base, i, j).order(ByteOrder.LITTLE_ENDIAN));
		}

		public dleaf_t(ByteBuffer bb) {
			contents = bb.getInt();
			cluster = bb.getShort();
			area = bb.getShort();

			mins[0] = bb.getShort();
			mins[1] = bb.getShort();
			mins[2] = bb.getShort();

			maxs[0] = bb.getShort();
			maxs[1] = bb.getShort();
			maxs[2] = bb.getShort();

			firstleafface = bb.getShort() & 0xffff;
			numleaffaces = bb.getShort() & 0xffff;

			firstleafbrush = bb.getShort() & 0xffff;
			numleafbrushes = bb.getShort() & 0xffff;
		}

		public static final int SIZE = 4 + 8 * 2 + 4 * 2;

		public int contents; // OR of all brushes (not needed?)

		public short cluster;
		public short area;

		public short mins[] = { 0, 0, 0 }; // for frustum culling
		public short maxs[] = { 0, 0, 0 };

		public int firstleafface; // unsigned short
		public int numleaffaces; // unsigned short

		public int firstleafbrush; // unsigned short
		public int numleafbrushes; // unsigned short
	}
	
	public static class dbrushside_t {

		public dbrushside_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);

			planenum = bb.getShort() & 0xffff;
			texinfo = bb.getShort();
		}

		//unsigned short planenum;
		int planenum; // facing out of the leaf

		short texinfo;

		public static int SIZE = 4;
	}
	
	public static class dbrush_t {

		public dbrush_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
			firstside = bb.getInt();
			numsides = bb.getInt();
			contents = bb.getInt();
		}

		public static int SIZE = 3 * 4;

		int firstside;
		int numsides;
		int contents;
	}

	//	#define	ANGLE_UP	-1
	//	#define	ANGLE_DOWN	-2

	// the visibility lump consists of a header with a count, then
	// byte offsets for the PVS and PHS of each cluster, then the raw
	// compressed bit vectors
	// #define	DVIS_PVS	0
	// #define	DVIS_PHS	1

	public static class dvis_t {

		public dvis_t(ByteBuffer bb) {
			numclusters = bb.getInt();
			bitofs = new int[numclusters][2];

			for (int i = 0; i < numclusters; i++) {
				bitofs[i][0] = bb.getInt();
				bitofs[i][1] = bb.getInt();
			}
		}

		public int numclusters;
		public int bitofs[][] = new int[8][2]; // bitofs[numclusters][2]	
	}
	
	// each area has a list of portals that lead into other areas
	// when portals are closed, other areas may not be visible or
	// hearable even if the vis info says that it should be
	
	public static class dareaportal_t {

		public dareaportal_t() {
		}

		public dareaportal_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);

			portalnum = bb.getShort();
			otherarea = bb.getShort();
		}

		int portalnum;
		int otherarea;

		public static int SIZE = 8;
	}

	public static class darea_t {

		public darea_t(ByteBuffer bb) {

			bb.order(ByteOrder.LITTLE_ENDIAN);

			numareaportals = bb.getInt();
			firstareaportal = bb.getInt();

		}
		int numareaportals;
		int firstareaportal;

		public static int SIZE = 8;
	}

}