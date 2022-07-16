/*
 * qfiles.java
 * Copyright (C) 2003
 *
 * $Id: qfiles.java,v 1.6 2005-05-07 23:40:49 cawe Exp $
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
package jake2.qcommon.filesystem;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.lump_t;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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
		int hres, vres; // unsigned short
		public byte[] palette; //unsigned byte; size 48
		byte reserved;
		byte color_planes;
		int bytes_per_line; // unsigned short
		int palette_type; // unsigned short
		byte[] filler; // size 58
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
	
	TGA files are used for sky planes
	
	========================================================================
	*/
	public static class tga_t {
		
		// targa header
		public int id_length, colormap_type, image_type; // unsigned char
		int colormap_index, colormap_length; // unsigned short
		int colormap_size; // unsigned char
		int x_origin;
		int y_origin;
		public int width;
		public int height; // unsigned short
		public int pixel_size;
		int attributes; // unsigned char

		public ByteBuffer data; // (un)compressed data

		public tga_t(byte[] dataBytes) {
			this(ByteBuffer.wrap(dataBytes));
		}

		tga_t(ByteBuffer b) {
			// is stored as little endian
			b.order(ByteOrder.LITTLE_ENDIAN);

			// fill header
			id_length = b.get() & 0xFF;
			colormap_type = b.get() & 0xFF;
			image_type = b.get() & 0xFF;
			colormap_index = b.getShort() & 0xFFFF;
			colormap_length = b.getShort() & 0xFFFF;
			colormap_size = b.get() & 0xFF;
			x_origin = b.getShort() & 0xFFFF;
			y_origin = b.getShort() & 0xFFFF;
			width = b.getShort() & 0xFFFF;
			height = b.getShort() & 0xFFFF;
			pixel_size = b.get() & 0xFF;
			attributes = b.get() & 0xFF;

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
	
	/**
	 * daliasframe_t
	 * A frame in the MD2 model
	 */
	public static class Md2Frame {
		public float[] scale = {0, 0, 0}; // multiply byte verts by this
		public float[] translate = {0, 0, 0};    // then add this
		public String name; // frame name from grabbing (size 16)
		public int[] verts;

		public Md2Frame(ByteBuffer buffer, int num_xyz) {
			scale[0] = buffer.getFloat();
			scale[1] = buffer.getFloat();
			scale[2] = buffer.getFloat();
			translate[0] = buffer.getFloat();
			translate[1] = buffer.getFloat();
			translate[2] = buffer.getFloat();
			byte[] nameBuf = new byte[16];
			buffer.get(nameBuf);
			name = new String(nameBuf).trim();

			// vertices are all 8 bit, so no swapping needed
			verts = new int[num_xyz];
			for (int k = 0; k < num_xyz; k++) {
				verts[k] = buffer.getInt();
			}
		}
	}

	/**
	 * MD2 (quake2) model (previously named dmdl_t)
	 * Header section contains size information (how many vertices, frames, skins, etc)
	 * and byte offsets to the respective sections.
	 * Body section are the data arrays.
	 *
	 * The GL commands (glcmd) format:
	 * <ul>
	 *   <li> positive integer starts a tristrip command, followed by that many vertex structures.</li>
	 * 	 <li> negative integer starts a trifan command, followed by -x vertexes</li>
	 * 	 <li> zero indicates the end of the command list.</li>
	 * </ul>
	 * <p/>
	 * A vertex consists of a floating point s, a floating point and an integer vertex index.
	 */
	public static class Md2Model {

		// Header
		public int ident;
		public int version;
		public int num_skins;
		public int num_vertices;

		public int num_tris;
		public int num_glcmds; // dwords in strip/fan command list
		public int num_frames;

		// each skin is a MAX_SKINNAME string
		public int skinsOffset;
		public int firstFrameOffset;
		public int glCommandsOffset;
		int endOfFileOffset; // end of file

		// Body
		public String[] skinNames;
		public int[] glCmds;
		// all frames have vertex array of equal size (num_xyz)
		public Md2Frame[] frames;


		/**
		 * Reads md2 header
		 */
		public Md2Model(ByteBuffer b) {
			ident = b.getInt();
			version = b.getInt();

			b.getInt(); // skinwidth
			b.getInt(); // skinheight
			b.getInt(); // framesize: byte size of each frame

			num_skins = b.getInt();
			num_vertices = b.getInt();
			b.getInt(); //num_st: greater than num_xyz for seams, parsed into dstvert_t
			num_tris = b.getInt();
			num_glcmds = b.getInt(); // dwords in strip/fan command list
			num_frames = b.getInt();

			skinsOffset = b.getInt(); // each skin is a MAX_SKINNAME string
			b.getInt(); // offset from start until dstvert_t[]
			b.getInt(); // offset from start until dtriangles[]
			firstFrameOffset = b.getInt(); // offset for first frame
			glCommandsOffset = b.getInt();
			endOfFileOffset = b.getInt(); // end of file
		}

		public Md2Model(ByteBuffer b, String modelName) {
			this(b);
			validateMd2Header(modelName);
			loadBody(b);
		}

		/*
		 * new members for vertex array handling
		 */
		public FloatBuffer textureCoordBuf = null;
		public IntBuffer vertexIndexBuf = null;
		public int[] counts = null;
		public IntBuffer[] indexElements = null;

		/**
		 * Loads the bulk of the data
		 */
		private void loadBody(ByteBuffer buffer) {
			//
			//	   load the frames
			//
			frames = new qfiles.Md2Frame[num_frames];
			buffer.position(firstFrameOffset);
			for (int i = 0; i < num_frames; i++) {
				frames[i] = new qfiles.Md2Frame(buffer, num_vertices);
			}

			//
			// load the glcmds
			// STRIP or FAN
			glCmds = new int[num_glcmds];
			buffer.position(glCommandsOffset);
			for (int i = 0; i < num_glcmds; i++) {
				glCmds[i] = buffer.getInt();
			}

			skinNames = new String[num_skins];
			byte[] nameBuf = new byte[qfiles.MAX_SKINNAME];
			buffer.position(skinsOffset);
			for (int i = 0; i < num_skins; i++) {
				buffer.get(nameBuf);
				skinNames[i] = new String(nameBuf);
				int n = skinNames[i].indexOf('\0');
				if (n > -1) {
					skinNames[i] = skinNames[i].substring(0, n);
				}
			}
		}

		private void validateMd2Header(String modelName) {
			// todo: switch to exceptions instead
			if (ident != qfiles.IDALIASHEADER)
				Com.Error(Defines.ERR_DROP, "model " + modelName + " has wrong magic number " + version + ", expected: " + qfiles.IDALIASHEADER);
			if (version != ALIAS_VERSION)
				Com.Error(Defines.ERR_DROP, "model " + modelName + " has wrong version number " + version + ", expected: " + ALIAS_VERSION);
			if (num_vertices <= 0)
				Com.Error(Defines.ERR_DROP, "model " + modelName + " has no vertices");
			if (num_vertices > MAX_VERTS)
				Com.Error(Defines.ERR_DROP, "model " + modelName + " has too many vertices");
			if (num_frames <= 0)
				Com.Error(Defines.ERR_DROP, "model " + modelName + " has no frames");
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
		
		dsprframe_t(ByteBuffer b) {
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
		int ident;
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
		String animname; //	char	animname[32];
		public int flags;
		public int contents;
		public int value;

		public miptex_t(byte[] dataBytes) {
			this(ByteBuffer.wrap(dataBytes));
		}

		miptex_t(ByteBuffer b) {
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

	// =============================================================================

	public static class dheader_t {

		public dheader_t(ByteBuffer bb) {
			bb.order(ByteOrder.LITTLE_ENDIAN);
			this.ident = bb.getInt();
			this.version = bb.getInt();

			for (int n = 0; n < Defines.HEADER_LUMPS; n++)
				lumps[n] = new lump_t(bb.getInt(), bb.getInt());

		}

		int ident;
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


	// planes (x&~1) and (x&~1)+1 are always opposites
	public static class dplane_t {

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
	


	// note that edge 0 is never used, because negative edge nums are used for
	// counterclockwise use of the edge in a face
	
	public static class dedge_t {
		// unsigned short v[2];
		int v[] = { 0, 0 };
	}
	
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
		public int planenum; // facing out of the leaf

		public short texinfo;

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

		public int firstside;
		public int numsides;
		public int contents;
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
			portalnum = bb.getInt();
			otherarea = bb.getInt();
		}

		public int portalnum;
		public int otherarea;

		public static int SIZE = 8;
	}

	public static class darea_t {

		public darea_t(ByteBuffer bb) {

			bb.order(ByteOrder.LITTLE_ENDIAN);

			numareaportals = bb.getInt();
			firstareaportal = bb.getInt();

		}
		public int numareaportals;
		public int firstareaportal;

		public static int SIZE = 8;
	}

}
