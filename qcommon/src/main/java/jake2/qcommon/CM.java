/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 02.01.2004 by RST.
package jake2.qcommon;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.filesystem.BspHeader;
import jake2.qcommon.filesystem.BspLump;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.filesystem.qfiles;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;
import jake2.qcommon.util.Vargs;
import jake2.qcommon.util.Vec3Cache;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;

public class CM {
    public CM() {
        for (int n = 0; n < Defines.MAX_MAP_BRUSHSIDES; n++)
            map_brushsides[n] = new cbrushside_t();
        for (int n = 0; n < Defines.MAX_MAP_TEXINFO; n++)
            map_surfaces[n] = new mapsurface_t();
        for (int n = 0; n < Defines.MAX_MAP_PLANES + 6; n++)
            map_planes[n] = new cplane_t();
        for (int n = 0; n < Defines.MAX_MAP_NODES + 6; n++)
            map_nodes[n] = new cnode_t();
        for (int n = 0; n < Defines.MAX_MAP_LEAFS; n++)
            map_leafs[n] = new cleaf_t();
        for (int n = 0; n < Defines.MAX_MAP_MODELS; n++)
            map_cmodels[n] = new cmodel_t();
        for (int n = 0; n < Defines.MAX_MAP_BRUSHES; n++)
            map_brushes[n] = new cbrush_t();
        for (int n = 0; n < Defines.MAX_MAP_AREAS; n++)
            map_areas[n] = new carea_t();
        for (int n = 0; n < Defines.MAX_MAP_AREAPORTALS; n++)
            map_areaportals[n] = new qfiles.dareaportal_t();

    }

    public static class cnode_t {
        cplane_t plane; // ptr

        int children[] = { 0, 0 }; // negative numbers are leafs
    }

    public static class cbrushside_t {
        cplane_t plane; // ptr

        mapsurface_t surface; // ptr
    }

    public static class cleaf_t {
        int contents;

        int cluster;

        int area;

        // was unsigned short, but is ok (rst)
        short firstleafbrush;

        // was unsigned short, but is ok (rst)
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

    int checkcount;

    String map_name = "";

    public int numbrushsides;

    cbrushside_t[] map_brushsides = new cbrushside_t[Defines.MAX_MAP_BRUSHSIDES];

    public int numtexinfo;

    public mapsurface_t[] map_surfaces = new mapsurface_t[Defines.MAX_MAP_TEXINFO];

    public int numplanes;

    /** Extra for box hull ( +6) */
    cplane_t[] map_planes = new cplane_t[Defines.MAX_MAP_PLANES + 6];

    public int numnodes;

    /** Extra for box hull ( +6) */
    cnode_t[] map_nodes = new cnode_t[Defines.MAX_MAP_NODES + 6];

    public int numleafs = 1; // allow leaf funcs to be called without a map

    cleaf_t[] map_leafs = new cleaf_t[Defines.MAX_MAP_LEAFS];
    int emptyleaf;
    int solidleaf;

    public int numleafbrushes;

    public int[] map_leafbrushes = new int[Defines.MAX_MAP_LEAFBRUSHES];

    public int numcmodels;

    public cmodel_t[] map_cmodels = new cmodel_t[Defines.MAX_MAP_MODELS];

    public int numbrushes;

    public cbrush_t[] map_brushes = new cbrush_t[Defines.MAX_MAP_BRUSHES];

    public int numvisibility;

    public byte[] map_visibility = new byte[Defines.MAX_MAP_VISIBILITY];

    /** Main visibility data. */
    public qfiles.dvis_t map_vis = new qfiles.dvis_t(ByteBuffer.wrap(map_visibility));

    public int numentitychars;

    public String map_entitystring;

    public int numareas = 1;

    public carea_t[] map_areas = new carea_t[Defines.MAX_MAP_AREAS];

    public int numareaportals;

    public qfiles.dareaportal_t map_areaportals[] = new qfiles.dareaportal_t[Defines.MAX_MAP_AREAPORTALS];


    public int numclusters = 1;

    public mapsurface_t nullsurface = new mapsurface_t();

    public int floodvalid;

    public boolean[] portalopen = new boolean[Defines.MAX_MAP_AREAPORTALS];

    public cvar_t map_noareas;

    public byte[] cmod_base;

    public int checksum;

    public int last_checksum;

    public cmodel_t CM_LoadMapFile(byte[] buf, String name, int[] checksum) {

        if (buf == null)
            Com.Error(Defines.ERR_DROP, "Couldn't load " + name);

        int length = buf.length;

        ByteBuffer bbuf = ByteBuffer.wrap(buf);

        last_checksum = MD4.Com_BlockChecksum(buf, length);
        checksum[0] = last_checksum;

        BspHeader header = new BspHeader(bbuf.slice());

        if (header.version != Defines.BSPVERSION)
            Com.Error(Defines.ERR_DROP, "CMod_LoadBrushModel: " + name
                    + " has wrong version number (" + header.version
                    + " should be " + Defines.BSPVERSION + ")");

        cmod_base = buf;

        // load into heap
        CMod_LoadSurfaces(header.lumps[Defines.LUMP_TEXINFO]); // ok
        CMod_LoadLeafs(header.lumps[Defines.LUMP_LEAFS]);
        CMod_LoadLeafBrushes(header.lumps[Defines.LUMP_LEAFBRUSHES]);
        CMod_LoadPlanes(header.lumps[Defines.LUMP_PLANES]);
        CMod_LoadBrushes(header.lumps[Defines.LUMP_BRUSHES]);
        CMod_LoadBrushSides(header.lumps[Defines.LUMP_BRUSHSIDES]);
        CMod_LoadSubmodels(header.lumps[Defines.LUMP_MODELS]);

        CMod_LoadNodes(header.lumps[Defines.LUMP_NODES]);
        CMod_LoadAreas(header.lumps[Defines.LUMP_AREAS]);
        CMod_LoadAreaPortals(header.lumps[Defines.LUMP_AREAPORTALS]);
        CMod_LoadVisibility(header.lumps[Defines.LUMP_VISIBILITY]);
        CMod_LoadEntityString(header.lumps[Defines.LUMP_ENTITIES]);

        CM_InitBoxHull();

        Arrays.fill(portalopen, false);

        FloodAreaConnections();

        map_name = name;

        return map_cmodels[0];
    }

    /**
     * Loads in the map and all submodels.
     */
    public cmodel_t CM_LoadMap(String name, boolean clientload, int[] checksum) {
        Com.DPrintf("CM_LoadMap(" + name + ")...\n");

        map_noareas = Cvar.getInstance().Get("map_noareas", "0", 0);

        if (map_name.equals(name)
                && (clientload || 0 == Cvar.getInstance().VariableValue("flushmap"))) {

            checksum[0] = last_checksum;

            if (!clientload) {
                Arrays.fill(portalopen, false);
                FloodAreaConnections();
            }
            return map_cmodels[0]; // still have the right version
        }

        // free old stuff
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
            checksum[0] = 0;
            return map_cmodels[0];
            // cinematic servers won't have anything at all
        }

        //
        // load the file
        //
        byte[] buf = FS.LoadFile(name);

        return CM_LoadMapFile(buf, name, checksum);
    }

    /** Loads Submodels. */
    public void CMod_LoadSubmodels(BspLump l) {
        Com.DPrintf("CMod_LoadSubmodels()\n");
        qfiles.dmodel_t in;
        cmodel_t out;
        int i, j, count;

        if ((l.length % qfiles.dmodel_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "CMod_LoadBmodel: funny lump size");

        count = l.length / qfiles.dmodel_t.SIZE;

        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map with no models");
        if (count > Defines.MAX_MAP_MODELS)
            Com.Error(Defines.ERR_DROP, "Map has too many models");

        Com.DPrintf(" numcmodels=" + count + "\n");
        numcmodels = count;

        if (debugloadmap) {
            Com.DPrintf("submodles(headnode, <origin>, <mins>, <maxs>)\n");
        }
        for (i = 0; i < count; i++) {
            in = new qfiles.dmodel_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.dmodel_t.SIZE + l.offset, qfiles.dmodel_t.SIZE));
            out = map_cmodels[i];

            for (j = 0; j < 3; j++) { // spread the mins / maxs by a pixel
                out.mins[j] = in.mins[j] - 1;
                out.maxs[j] = in.maxs[j] + 1;
                out.origin[j] = in.origin[j];
            }
            out.headnode = in.headnode;
            if (debugloadmap) {
                Com.DPrintf(
                	"|%6i|%8.2f|%8.2f|%8.2f|  %8.2f|%8.2f|%8.2f|   %8.2f|%8.2f|%8.2f|\n",
                        new Vargs().add(out.headnode)
                        .add(out.origin[0]).add(out.origin[1]).add(out.origin[2])
                        .add(out.mins[0]).add(out.mins[1]).add(out.mins[2])
                        .add(out.maxs[0]).add(out.maxs[1]).add(out.maxs[2]));
            }
        }
    }

    boolean debugloadmap = false;

    /** Loads surfaces. */
    public void CMod_LoadSurfaces(BspLump l) {
        Com.DPrintf("CMod_LoadSurfaces()\n");
        texinfo_t in;
        mapsurface_t out;
        int i, count;

        if ((l.length % texinfo_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        count = l.length / texinfo_t.SIZE;
        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map with no surfaces");
        if (count > Defines.MAX_MAP_TEXINFO)
            Com.Error(Defines.ERR_DROP, "Map has too many surfaces");

        numtexinfo = count;
        Com.DPrintf(" numtexinfo=" + count + "\n");
        if (debugloadmap)
            Com.DPrintf("surfaces:\n");

        for (i = 0; i < count; i++) {
            out = map_surfaces[i] = new mapsurface_t();
            in = new texinfo_t(cmod_base, l.offset + i * texinfo_t.SIZE,
                    texinfo_t.SIZE);

            out.c.name = in.texture;
            out.rname = in.texture;
            out.c.flags = in.flags;
            out.c.value = in.value;

            if (debugloadmap) {
                Com.DPrintf("|%20s|%20s|%6i|%6i|\n", new Vargs()
                        .add(out.c.name).add(out.rname).add(out.c.value).add(
                                out.c.flags));
            }

        }
    }

    /** Loads nodes. */
    public void CMod_LoadNodes(BspLump l) {
        Com.DPrintf("CMod_LoadNodes()\n");
        qfiles.dnode_t in;
        int child;
        cnode_t out;
        int i, j, count;

        if ((l.length % qfiles.dnode_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size:"
                    + l.offset + "," + qfiles.dnode_t.SIZE);
        count = l.length / qfiles.dnode_t.SIZE;

        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map has no nodes");
        if (count > Defines.MAX_MAP_NODES)
            Com.Error(Defines.ERR_DROP, "Map has too many nodes");

        numnodes = count;
        Com.DPrintf(" numnodes=" + count + "\n");

        if (debugloadmap) {
            Com.DPrintf("nodes(planenum, child[0], child[1])\n");
        }

        for (i = 0; i < count; i++) {
            in = new qfiles.dnode_t(ByteBuffer.wrap(cmod_base,
                    qfiles.dnode_t.SIZE * i + l.offset, qfiles.dnode_t.SIZE));
            out = map_nodes[i];

            out.plane = map_planes[in.planenum];
            for (j = 0; j < 2; j++) {
                child = in.children[j];
                out.children[j] = child;
            }
            if (debugloadmap) {
                Com.DPrintf("|%6i| %6i| %6i|\n", new Vargs().add(in.planenum)
                        .add(out.children[0]).add(out.children[1]));
            }
        }
    }

    /** Loads brushes.*/
    public void CMod_LoadBrushes(BspLump l) {
        Com.DPrintf("CMod_LoadBrushes()\n");
        qfiles.dbrush_t in;
        cbrush_t out;
        int i, count;

        if ((l.length % qfiles.dbrush_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        count = l.length / qfiles.dbrush_t.SIZE;

        if (count > Defines.MAX_MAP_BRUSHES)
            Com.Error(Defines.ERR_DROP, "Map has too many brushes");

        numbrushes = count;
        Com.DPrintf(" numbrushes=" + count + "\n");
        if (debugloadmap) {
            Com.DPrintf("brushes:(firstbrushside, numsides, contents)\n");
        }
        for (i = 0; i < count; i++) {
            in = new qfiles.dbrush_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.dbrush_t.SIZE + l.offset, qfiles.dbrush_t.SIZE));
            out = map_brushes[i];
            out.firstbrushside = in.firstside;
            out.numsides = in.numsides;
            out.contents = in.contents;

            if (debugloadmap) {
                Com.DPrintf("| %6i| %6i| %8X|\n", new Vargs().add(
                	out.firstbrushside).add(out.numsides).add(
                        out.contents));
            }
        }
    }

    /** Loads leafs.   */
    public void CMod_LoadLeafs(BspLump l) {
        Com.DPrintf("CMod_LoadLeafs()\n");
        int i;
        cleaf_t out;
        qfiles.dleaf_t in;
        int count;

        if ((l.length % qfiles.dleaf_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        count = l.length / qfiles.dleaf_t.SIZE;

        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map with no leafs");

        // need to save space for box planes
        if (count > Defines.MAX_MAP_PLANES)
            Com.Error(Defines.ERR_DROP, "Map has too many planes");

        Com.DPrintf(" numleafes=" + count + "\n");

        numleafs = count;
        numclusters = 0;
        if (debugloadmap)
            Com.DPrintf("cleaf-list:(contents, cluster, area, firstleafbrush, numleafbrushes)\n");
        for (i = 0; i < count; i++) {
            in = new qfiles.dleaf_t(cmod_base, i * qfiles.dleaf_t.SIZE
                    + l.offset, qfiles.dleaf_t.SIZE);

            out = map_leafs[i];

            out.contents = in.contents;
            out.cluster = in.cluster;
            out.area = in.area;
            out.firstleafbrush = (short) in.firstleafbrush;
            out.numleafbrushes = (short) in.numleafbrushes;

            if (out.cluster >= numclusters)
                numclusters = out.cluster + 1;

            if (debugloadmap) {
                Com.DPrintf("|%8x|%6i|%6i|%6i|\n", new Vargs()
                        .add(out.contents).add(out.cluster).add(out.area).add(
                                out.firstleafbrush).add(out.numleafbrushes));
            }

        }

        Com.DPrintf(" numclusters=" + numclusters + "\n");

        if (map_leafs[0].contents != Defines.CONTENTS_SOLID)
            Com.Error(Defines.ERR_DROP, "Map leaf 0 is not CONTENTS_SOLID");

        solidleaf = 0;
        emptyleaf = -1;

        for (i = 1; i < numleafs; i++) {
            if (map_leafs[i].contents == 0) {
                emptyleaf = i;
                break;
            }
        }

        if (emptyleaf == -1)
            Com.Error(Defines.ERR_DROP, "Map does not have an empty leaf");
    }

    /** Loads planes. */
    public void CMod_LoadPlanes(BspLump l) {
        Com.DPrintf("CMod_LoadPlanes()\n");
        int i, j;
        cplane_t out;
        qfiles.dplane_t in;
        int count;
        int bits;

        if ((l.length % qfiles.dplane_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        count = l.length / qfiles.dplane_t.SIZE;

        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map with no planes");

        // need to save space for box planes
        if (count > Defines.MAX_MAP_PLANES)
            Com.Error(Defines.ERR_DROP, "Map has too many planes");

        Com.DPrintf(" numplanes=" + count + "\n");

        numplanes = count;
        if (debugloadmap) {
            Com
                    .DPrintf("cplanes(normal[0],normal[1],normal[2], dist, type, signbits)\n");
        }

        for (i = 0; i < count; i++) {
            in = new qfiles.dplane_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.dplane_t.SIZE + l.offset, qfiles.dplane_t.SIZE));

            out = map_planes[i];

            bits = 0;
            for (j = 0; j < 3; j++) {
                out.normal[j] = in.normal[j];

                if (out.normal[j] < 0)
                    bits |= 1 << j;
            }

            out.dist = in.dist;
            out.type = (byte) in.type;
            out.signbits = (byte) bits;

            if (debugloadmap) {
                Com.DPrintf("|%6.2f|%6.2f|%6.2f| %10.2f|%3i| %1i|\n",
                        new Vargs().add(out.normal[0]).add(out.normal[1]).add(
                                out.normal[2]).add(out.dist).add(out.type).add(
                                out.signbits));
            }
        }
    }

    /** Loads leaf brushes. */
    public void CMod_LoadLeafBrushes(BspLump l) {
        Com.DPrintf("CMod_LoadLeafBrushes()\n");

        if ((l.length % 2) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        int count = l.length / 2;

        Com.DPrintf(" numbrushes=" + count + "\n");

        if (count < 1)
            Com.Error(Defines.ERR_DROP, "Map with no planes");

        // need to save space for box planes
        if (count > Defines.MAX_MAP_LEAFBRUSHES)
            Com.Error(Defines.ERR_DROP, "Map has too many leafbrushes");

        int[] out = map_leafbrushes;
        numleafbrushes = count;

        ByteBuffer bb = ByteBuffer.wrap(cmod_base, l.offset, count * 2).order(
                ByteOrder.LITTLE_ENDIAN);

        if (debugloadmap) {
            Com.DPrintf("map_brushes:\n");
        }

        for (int i = 0; i < count; i++) {
            out[i] = bb.getShort();
            if (debugloadmap) {
                Com.DPrintf("|%6i|%6i|\n", new Vargs().add(i).add(out[i]));
            }
        }
    }

    /** Loads brush sides. */
    public void CMod_LoadBrushSides(BspLump l) {
        Com.DPrintf("CMod_LoadBrushSides()\n");
        int i, j;
        cbrushside_t out;
        qfiles.dbrushside_t in;
        int count;
        int num;

        if ((l.length % qfiles.dbrushside_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");
        count = l.length / qfiles.dbrushside_t.SIZE;

        // need to save space for box planes
        if (count > Defines.MAX_MAP_BRUSHSIDES)
            Com.Error(Defines.ERR_DROP, "Map has too many planes");

        numbrushsides = count;

        Com.DPrintf(" numbrushsides=" + count + "\n");

        if (debugloadmap) {
            Com.DPrintf("brushside(planenum, surfacenum):\n");
        }
        for (i = 0; i < count; i++) {

            in = new qfiles.dbrushside_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.dbrushside_t.SIZE + l.offset,
                    qfiles.dbrushside_t.SIZE));

            out = map_brushsides[i];

            num = in.planenum;

            out.plane = map_planes[num]; // pointer

            j = in.texinfo;

            if (j >= numtexinfo)
                Com.Error(Defines.ERR_DROP, "Bad brushside texinfo");

            // java specific handling of -1
            if (j == -1)
                out.surface = new mapsurface_t(); // just for safety
            else
                out.surface = map_surfaces[j];

            if (debugloadmap) {
                Com.DPrintf("| %6i| %6i|\n", new Vargs().add(num).add(j));
            }
        }
    }

    /** Loads areas. */
    public void CMod_LoadAreas(BspLump l) {
        Com.DPrintf("CMod_LoadAreas()\n");
        int i;
        carea_t out;
        qfiles.darea_t in;
        int count;

        if ((l.length % qfiles.darea_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");

        count = l.length / qfiles.darea_t.SIZE;

        if (count > Defines.MAX_MAP_AREAS)
            Com.Error(Defines.ERR_DROP, "Map has too many areas");

        Com.DPrintf(" numareas=" + count + "\n");
        numareas = count;

        if (debugloadmap) {
            Com.DPrintf("areas(numportals, firstportal)\n");
        }

        for (i = 0; i < count; i++) {

            in = new qfiles.darea_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.darea_t.SIZE + l.offset, qfiles.darea_t.SIZE));
            out = map_areas[i];

            out.numareaportals = in.numareaportals;
            out.firstareaportal = in.firstareaportal;
            out.floodvalid = 0;
            out.floodnum = 0;
            if (debugloadmap) {
                Com.DPrintf("| %6i| %6i|\n", new Vargs()
                        .add(out.numareaportals).add(out.firstareaportal));
            }
        }
    }

    /** Loads area portals. */
    public void CMod_LoadAreaPortals(BspLump l) {
        Com.DPrintf("CMod_LoadAreaPortals()\n");
        int i;
        qfiles.dareaportal_t out;
        qfiles.dareaportal_t in;
        int count;

        if ((l.length % qfiles.dareaportal_t.SIZE) != 0)
            Com.Error(Defines.ERR_DROP, "MOD_LoadBmodel: funny lump size");
        count = l.length / qfiles.dareaportal_t.SIZE;

        if (count > Defines.MAX_MAP_AREAS)
            Com.Error(Defines.ERR_DROP, "Map has too many areas");

        numareaportals = count;
        Com.DPrintf(" numareaportals=" + count + "\n");
        if (debugloadmap) {
            Com.DPrintf("areaportals(portalnum, otherarea)\n");
        }
        for (i = 0; i < count; i++) {
            in = new qfiles.dareaportal_t(ByteBuffer.wrap(cmod_base, i
                    * qfiles.dareaportal_t.SIZE + l.offset,
                    qfiles.dareaportal_t.SIZE));

            out = map_areaportals[i];

            out.portalnum = in.portalnum;
            out.otherarea = in.otherarea;

            if (debugloadmap) {
                Com.DPrintf("|%6i|%6i|\n", new Vargs().add(out.portalnum).add(
                        out.otherarea));
            }
        }
    }

    /** Loads visibility data. */
    public void CMod_LoadVisibility(BspLump l) {
        Com.DPrintf("CMod_LoadVisibility()\n");

        numvisibility = l.length;

        Com.DPrintf(" numvisibility=" + numvisibility + "\n");

        if (l.length > Defines.MAX_MAP_VISIBILITY)
            Com.Error(Defines.ERR_DROP, "Map has too large visibility lump");

        System.arraycopy(cmod_base, l.offset, map_visibility, 0, l.length);

        ByteBuffer bb = ByteBuffer.wrap(map_visibility, 0, l.length);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        map_vis = new qfiles.dvis_t(bb);

    }

    /** Loads entity strings. */
    public void CMod_LoadEntityString(BspLump l) {
        Com.DPrintf("CMod_LoadEntityString()\n");

        numentitychars = l.length;

        if (l.length > Defines.MAX_MAP_ENTSTRING)
            Com.Error(Defines.ERR_DROP, "Map has too large entity lump");

        int x = 0;
        for (; x < l.length && cmod_base[x + l.offset] != 0; x++);

        map_entitystring = new String(cmod_base, l.offset, x).trim();
        Com.dprintln("entitystring=" + map_entitystring.length() + 
                " bytes, [" + map_entitystring.substring(0, Math.min (
                        map_entitystring.length(), 15)) + "...]" );
    }

    /** Returns the model with a given id "*" + <number> */
    public cmodel_t InlineModel(String name) {
        if (name == null || name.charAt(0) != '*')
            Com.Error(Defines.ERR_DROP, "CM_InlineModel: bad name");

        int num = Lib.atoi(name.substring(1));

        if (num < 1 || num >= numcmodels)
            Com.Error(Defines.ERR_DROP, "CM_InlineModel: bad number");

        return map_cmodels[num];
    }

    public int CM_NumClusters() {
        return numclusters;
    }

    public int CM_NumInlineModels() {
        return numcmodels;
    }

    public String CM_EntityString() {
        return map_entitystring;
    }

    public int CM_LeafContents(int leafnum) {
        if (leafnum < 0 || leafnum >= numleafs)
            Com.Error(Defines.ERR_DROP, "CM_LeafContents: bad number");
        return map_leafs[leafnum].contents;
    }

    public int CM_LeafCluster(int leafnum) {
        if (leafnum < 0 || leafnum >= numleafs)
            Com.Error(Defines.ERR_DROP, "CM_LeafCluster: bad number");
        return map_leafs[leafnum].cluster;
    }

    public int CM_LeafArea(int leafnum) {
        if (leafnum < 0 || leafnum >= numleafs)
            Com.Error(Defines.ERR_DROP, "CM_LeafArea: bad number");
        return map_leafs[leafnum].area;
    }

    cplane_t[] box_planes;

    public int box_headnode;

    cbrush_t box_brush;

    cleaf_t box_leaf;

    /** Set up the planes and nodes so that the six floats of a bounding box can
     * just be stored out and get a proper clipping hull structure.
     */
    public void CM_InitBoxHull() {

        box_headnode = numnodes; //rst: still room for 6 brushes left?

        box_planes = new cplane_t[] { map_planes[numplanes],
                map_planes[numplanes + 1], map_planes[numplanes + 2],
                map_planes[numplanes + 3], map_planes[numplanes + 4],
                map_planes[numplanes + 5], map_planes[numplanes + 6],
                map_planes[numplanes + 7], map_planes[numplanes + 8],
                map_planes[numplanes + 9], map_planes[numplanes + 10],
                map_planes[numplanes + 11], map_planes[numplanes + 12] };

        if (numnodes + 6 > Defines.MAX_MAP_NODES
                || numbrushes + 1 > Defines.MAX_MAP_BRUSHES
                || numleafbrushes + 1 > Defines.MAX_MAP_LEAFBRUSHES
                || numbrushsides + 6 > Defines.MAX_MAP_BRUSHSIDES
                || numplanes + 12 > Defines.MAX_MAP_PLANES)
            Com.Error(Defines.ERR_DROP, "Not enough room for box tree");

        box_brush = map_brushes[numbrushes];
        box_brush.numsides = 6;
        box_brush.firstbrushside = numbrushsides;
        box_brush.contents = Defines.CONTENTS_MONSTER;

        box_leaf = map_leafs[numleafs];
        box_leaf.contents = Defines.CONTENTS_MONSTER;
        box_leaf.firstleafbrush = (short) numleafbrushes;
        box_leaf.numleafbrushes = 1;

        map_leafbrushes[numleafbrushes] = numbrushes;

        int side;
        cnode_t c;
        cplane_t p;
        cbrushside_t s;

        for (int i = 0; i < 6; i++) {
            side = i & 1;

            // brush sides
            s = map_brushsides[numbrushsides + i];
            s.plane = map_planes[(numplanes + i * 2 + side)];
            s.surface = nullsurface;

            // nodes
            c = map_nodes[box_headnode + i];
            c.plane = map_planes[(numplanes + i * 2)];
            c.children[side] = -1 - emptyleaf;
            if (i != 5)
                c.children[side ^ 1] = box_headnode + i + 1;
            else
                c.children[side ^ 1] = -1 - numleafs;

            // planes
            p = box_planes[i * 2];
            p.type = (byte) (i >> 1);
            p.signbits = 0;
            Math3D.VectorClear(p.normal);
            p.normal[i >> 1] = 1;

            p = box_planes[i * 2 + 1];
            p.type = (byte) (3 + (i >> 1));
            p.signbits = 0;
            Math3D.VectorClear(p.normal);
            p.normal[i >> 1] = -1;
        }
    }

    /** To keep everything totally uniform, bounding boxes are turned into small
     * BSP trees instead of being compared directly. */
    public int HeadnodeForBox(float[] mins, float[] maxs) {
        box_planes[0].dist = maxs[0];
        box_planes[1].dist = -maxs[0];
        box_planes[2].dist = mins[0];
        box_planes[3].dist = -mins[0];
        box_planes[4].dist = maxs[1];
        box_planes[5].dist = -maxs[1];
        box_planes[6].dist = mins[1];
        box_planes[7].dist = -mins[1];
        box_planes[8].dist = maxs[2];
        box_planes[9].dist = -maxs[2];
        box_planes[10].dist = mins[2];
        box_planes[11].dist = -mins[2];

        return box_headnode;
    }

    /** Recursively searches the leaf number that contains the 3d point. */
    private int CM_PointLeafnum_r(float[] p, int num) {
        float d;
        cnode_t node;
        cplane_t plane;

        while (num >= 0) {
            node = map_nodes[num];
            plane = node.plane;

            if (plane.type < 3)
                d = p[plane.type] - plane.dist;
            else
                d = Math3D.DotProduct(plane.normal, p) - plane.dist;
            if (d < 0)
                num = node.children[1];
            else
                num = node.children[0];
        }

        Globals.c_pointcontents++; // optimize counter

        return -1 - num;
    }

    /** Searches the leaf number that contains the 3d point. */
    public int CM_PointLeafnum(float[] p) {
    	// sound may call this without map loaded
        if (numplanes == 0)
            return 0; 
        return CM_PointLeafnum_r(p, 0);
    }


    private int leaf_count;
    private int leaf_maxcount;

    private static int[] leaf_list;
    private static float[] leaf_mins;
    private static float[] leaf_maxs;

    private static int leaf_topnode;

    /** Recursively fills in a list of all the leafs touched. */    
    private void CM_BoxLeafnums_r(int nodenum) {
        cplane_t plane;
        cnode_t node;
        int s;

        while (true) {
            if (nodenum < 0) {
                if (leaf_count >= leaf_maxcount) {
                    Com.DPrintf("CM_BoxLeafnums_r: overflow\n");
                    return;
                }
                leaf_list[leaf_count++] = -1 - nodenum;
                return;
            }

            node = map_nodes[nodenum];
            plane = node.plane;

            s = Math3D.BoxOnPlaneSide(leaf_mins, leaf_maxs, plane);

            if (s == 1)
                nodenum = node.children[0];
            else if (s == 2)
                nodenum = node.children[1];
            else {
                // go down both
                if (leaf_topnode == -1)
                    leaf_topnode = nodenum;
                CM_BoxLeafnums_r(node.children[0]);
                nodenum = node.children[1];
            }
        }
    }

    /** Fills in a list of all the leafs touched and starts with the head node. */
    private int CM_BoxLeafnums_headnode(float[] mins, float[] maxs, int[] list, int listsize, int headnode, int[] topnode) {
        leaf_list = list;
        leaf_count = 0;
        leaf_maxcount = listsize;
        leaf_mins = mins;
        leaf_maxs = maxs;

        leaf_topnode = -1;

        CM_BoxLeafnums_r(headnode);

        if (topnode != null)
            topnode[0] = leaf_topnode;

        return leaf_count;
    }

    /** Fills in a list of all the leafs touched. */
    public int CM_BoxLeafnums(float[] mins, float[] maxs, int[] list, int listsize, int[] topnode) {
        return CM_BoxLeafnums_headnode(mins, maxs, list, listsize,
                map_cmodels[0].headnode, topnode);
    }

    /** Returns a tag that describes the content of the point. */
    public int PointContents(float[] p, int headnode) {
        int l;

        if (numnodes == 0) // map not loaded
            return 0;

        l = CM_PointLeafnum_r(p, headnode);

        return map_leafs[l].contents;
    }

    /*
     * ================== CM_TransformedPointContents
     * 
     * Handles offseting and rotation of the end points for moving and rotating
     * entities ==================
     */
    public int TransformedPointContents(float[] p, int headnode,
            float[] origin, float[] angles) {
        float[] p_l = { 0, 0, 0 };
        float[] temp = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0, 0 };
        int l;

        // subtract origin offset
        Math3D.VectorSubtract(p, origin, p_l);

        // rotate start and end into the models frame of reference
        if (headnode != box_headnode
                && (angles[0] != 0 || angles[1] != 0 || angles[2] != 0)) {
            Math3D.AngleVectors(angles, forward, right, up);

            Math3D.VectorCopy(p_l, temp);
            p_l[0] = Math3D.DotProduct(temp, forward);
            p_l[1] = -Math3D.DotProduct(temp, right);
            p_l[2] = Math3D.DotProduct(temp, up);
        }

        l = CM_PointLeafnum_r(p_l, headnode);

        return map_leafs[l].contents;
    }

    /*
     * ===============================================================================
     * 
     * BOX TRACING
     * 
     * ===============================================================================
     */

    // 1/32 epsilon to keep floating point happy
    private static final float DIST_EPSILON = 0.03125f;

    private float[] trace_start = { 0, 0, 0 };
    private float[] trace_end = { 0, 0, 0 };

    private float[] trace_mins = { 0, 0, 0 };
    private float[] trace_maxs = { 0, 0, 0 };

    private float[] trace_extents = { 0, 0, 0 };

    private trace_t trace_trace = new trace_t();

    private int trace_contents;

    private boolean trace_ispoint; // optimized case

    /*
     * ================ CM_ClipBoxToBrush ================
     */
    public void CM_ClipBoxToBrush(float[] mins, float[] maxs, float[] p1, float[] p2, trace_t trace, cbrush_t brush) {
        int i, j;
        cplane_t plane, clipplane;
        float dist;
        float enterfrac, leavefrac;
        float[] ofs = { 0, 0, 0 };
        float d1, d2;
        boolean getout, startout;
        float f;
        cbrushside_t side, leadside;

        enterfrac = -1;
        leavefrac = 1;
        clipplane = null;

        if (brush.numsides == 0)
            return;

        Globals.c_brush_traces++;

        getout = false;
        startout = false;
        leadside = null;

        for (i = 0; i < brush.numsides; i++) {
            side = map_brushsides[brush.firstbrushside + i];
            plane = side.plane;

            // FIXME: special case for axial

            if (!trace_ispoint) { // general box case

                // push the plane out apropriately for mins/maxs

                // FIXME: use signbits into 8 way lookup for each mins/maxs
                for (j = 0; j < 3; j++) {
                    if (plane.normal[j] < 0)
                        ofs[j] = maxs[j];
                    else
                        ofs[j] = mins[j];
                }
                dist = Math3D.DotProduct(ofs, plane.normal);
                dist = plane.dist - dist;
            } else { // special point case
                dist = plane.dist;
            }

            d1 = Math3D.DotProduct(p1, plane.normal) - dist;
            d2 = Math3D.DotProduct(p2, plane.normal) - dist;

            if (d2 > 0)
                getout = true; // endpoint is not in solid
            if (d1 > 0)
                startout = true;

            // if completely in front of face, no intersection
            if (d1 > 0 && d2 >= d1)
                return;

            if (d1 <= 0 && d2 <= 0)
                continue;

            // crosses face
            if (d1 > d2) { // enter
                f = (d1 - DIST_EPSILON) / (d1 - d2);
                if (f > enterfrac) {
                    enterfrac = f;
                    clipplane = plane;
                    leadside = side;
                }
            } else { // leave
                f = (d1 + DIST_EPSILON) / (d1 - d2);
                if (f < leavefrac)
                    leavefrac = f;
            }
        }

        if (!startout) { // original point was inside brush
            trace.startsolid = true;
            if (!getout)
                trace.allsolid = true;
            return;
        }
        if (enterfrac < leavefrac) {
            if (enterfrac > -1 && enterfrac < trace.fraction) {
                if (enterfrac < 0)
                    enterfrac = 0;
                trace.fraction = enterfrac;
                // copy
                trace.plane.set(clipplane);
                trace.surface = leadside.surface.c;
                trace.contents = brush.contents;
            }
        }
    }

    /*
     * ================ CM_TestBoxInBrush ================
     */
    public void CM_TestBoxInBrush(float[] mins, float[] maxs, float[] p1, trace_t trace, cbrush_t brush) {
        int i, j;
        cplane_t plane;
        float dist;
        float[] ofs = { 0, 0, 0 };
        float d1;
        cbrushside_t side;

        if (brush.numsides == 0)
            return;

        for (i = 0; i < brush.numsides; i++) {
            side = map_brushsides[brush.firstbrushside + i];
            plane = side.plane;

            // FIXME: special case for axial
            // general box case
            // push the plane out apropriately for mins/maxs
            // FIXME: use signbits into 8 way lookup for each mins/maxs

            for (j = 0; j < 3; j++) {
                if (plane.normal[j] < 0)
                    ofs[j] = maxs[j];
                else
                    ofs[j] = mins[j];
            }
            dist = Math3D.DotProduct(ofs, plane.normal);
            dist = plane.dist - dist;

            d1 = Math3D.DotProduct(p1, plane.normal) - dist;

            // if completely in front of face, no intersection
            if (d1 > 0)
                return;

        }

        // inside this brush
        trace.startsolid = trace.allsolid = true;
        trace.fraction = 0;
        trace.contents = brush.contents;
    }

    /**
     * CM_TraceToLeaf.
     */
    public void CM_TraceToLeaf(int leafnum) {
        int k;
        int brushnum;
        cleaf_t leaf;
        cbrush_t b;

        leaf = map_leafs[leafnum];
        if (0 == (leaf.contents & trace_contents))
            return;

        // trace line against all brushes in the leaf
        for (k = 0; k < leaf.numleafbrushes; k++) {

            brushnum = map_leafbrushes[leaf.firstleafbrush + k];
            b = map_brushes[brushnum];
            if (b.checkcount == checkcount)
                continue; // already checked this brush in another leaf
            b.checkcount = checkcount;

            if (0 == (b.contents & trace_contents))
                continue;
            CM_ClipBoxToBrush(trace_mins, trace_maxs, trace_start, trace_end,
                    trace_trace, b);
            if (0 == trace_trace.fraction)
                return;
        }

    }

    /*
     * ================ CM_TestInLeaf ================
     */
    public void CM_TestInLeaf(int leafnum) {
        int k;
        int brushnum;
        cleaf_t leaf;
        cbrush_t b;

        leaf = map_leafs[leafnum];
        if (0 == (leaf.contents & trace_contents))
            return;
        // trace line against all brushes in the leaf
        for (k = 0; k < leaf.numleafbrushes; k++) {
            brushnum = map_leafbrushes[leaf.firstleafbrush + k];
            b = map_brushes[brushnum];
            if (b.checkcount == checkcount)
                continue; // already checked this brush in another leaf
            b.checkcount = checkcount;

            if (0 == (b.contents & trace_contents))
                continue;
            CM_TestBoxInBrush(trace_mins, trace_maxs, trace_start, trace_trace,
                    b);
            if (0 == trace_trace.fraction)
                return;
        }

    }

    /*
     * ================== CM_RecursiveHullCheck ==================
     */
    public void CM_RecursiveHullCheck(int num, float p1f, float p2f, float[] p1, float[] p2) {
        cnode_t node;
        cplane_t plane;
        float t1, t2, offset;
        float frac, frac2;
        float idist;
        int i;
        int side;
        float midf;

        if (trace_trace.fraction <= p1f)
            return; // already hit something nearer

        // if < 0, we are in a leaf node
        if (num < 0) {
            CM_TraceToLeaf(-1 - num);
            return;
        }

        //
        // find the point distances to the seperating plane
        // and the offset for the size of the box
        //
        node = map_nodes[num];
        plane = node.plane;

        if (plane.type < 3) {
            t1 = p1[plane.type] - plane.dist;
            t2 = p2[plane.type] - plane.dist;
            offset = trace_extents[plane.type];
        } else {
            t1 = Math3D.DotProduct(plane.normal, p1) - plane.dist;
            t2 = Math3D.DotProduct(plane.normal, p2) - plane.dist;
            if (trace_ispoint)
                offset = 0;
            else
                offset = Math.abs(trace_extents[0] * plane.normal[0])
                        + Math.abs(trace_extents[1] * plane.normal[1])
                        + Math.abs(trace_extents[2] * plane.normal[2]);
        }

        // see which sides we need to consider
        if (t1 >= offset && t2 >= offset) {
            CM_RecursiveHullCheck(node.children[0], p1f, p2f, p1, p2);
            return;
        }
        if (t1 < -offset && t2 < -offset) {
            CM_RecursiveHullCheck(node.children[1], p1f, p2f, p1, p2);
            return;
        }

        // put the crosspoint DIST_EPSILON pixels on the near side
        if (t1 < t2) {
            idist = 1.0f / (t1 - t2);
            side = 1;
            frac2 = (t1 + offset + DIST_EPSILON) * idist;
            frac = (t1 - offset + DIST_EPSILON) * idist;
        } else if (t1 > t2) {
            idist = 1.0f / (t1 - t2);
            side = 0;
            frac2 = (t1 - offset - DIST_EPSILON) * idist;
            frac = (t1 + offset + DIST_EPSILON) * idist;
        } else {
            side = 0;
            frac = 1;
            frac2 = 0;
        }

        // move up to the node
        if (frac < 0)
            frac = 0;
        if (frac > 1)
            frac = 1;

        midf = p1f + (p2f - p1f) * frac;
        float[] mid = Vec3Cache.get();

        for (i = 0; i < 3; i++)
            mid[i] = p1[i] + frac * (p2[i] - p1[i]);

        CM_RecursiveHullCheck(node.children[side], p1f, midf, p1, mid);

        // go past the node
        if (frac2 < 0)
            frac2 = 0;
        if (frac2 > 1)
            frac2 = 1;

        midf = p1f + (p2f - p1f) * frac2;
        for (i = 0; i < 3; i++)
            mid[i] = p1[i] + frac2 * (p2[i] - p1[i]);

        CM_RecursiveHullCheck(node.children[side ^ 1], midf, p2f, mid, p2);
        Vec3Cache.release();
    }

    //======================================================================

    /*
     * ================== CM_BoxTrace ==================
     */
    public trace_t BoxTrace(float[] start, float[] end, float[] mins, float[] maxs, int headnode, int brushmask) {

        // for multi-check avoidance
        checkcount++;

        // for statistics, may be zeroed
        Globals.c_traces++;

        // fill in a default trace
        //was: memset(& trace_trace, 0, sizeof(trace_trace));
        trace_trace = new trace_t();

        trace_trace.fraction = 1;
        trace_trace.surface = nullsurface.c;

        if (numnodes == 0) {
            // map not loaded
            return trace_trace;
        }

        trace_contents = brushmask;
        Math3D.VectorCopy(start, trace_start);
        Math3D.VectorCopy(end, trace_end);
        Math3D.VectorCopy(mins, trace_mins);
        Math3D.VectorCopy(maxs, trace_maxs);

        //
        // check for position test special case
        //
        if (start[0] == end[0] && start[1] == end[1] && start[2] == end[2]) {

            int leafs[] = new int[1024];
            int i, numleafs;
            float[] c1 = { 0, 0, 0 }, c2 = { 0, 0, 0 };
            int topnode = 0;

            Math3D.VectorAdd(start, mins, c1);
            Math3D.VectorAdd(start, maxs, c2);

            for (i = 0; i < 3; i++) {
                c1[i] -= 1;
                c2[i] += 1;
            }

            int tn[] = { topnode };

            numleafs = CM_BoxLeafnums_headnode(c1, c2, leafs, 1024, headnode,
                    tn);
            topnode = tn[0];
            for (i = 0; i < numleafs; i++) {
                CM_TestInLeaf(leafs[i]);
                if (trace_trace.allsolid)
                    break;
            }
            Math3D.VectorCopy(start, trace_trace.endpos);
            return trace_trace;
        }

        //
        // check for point special case
        //
        if (mins[0] == 0 && mins[1] == 0 && mins[2] == 0 && maxs[0] == 0
                && maxs[1] == 0 && maxs[2] == 0) {
            trace_ispoint = true;
            Math3D.VectorClear(trace_extents);
        } else {
            trace_ispoint = false;
            trace_extents[0] = -mins[0] > maxs[0] ? -mins[0] : maxs[0];
            trace_extents[1] = -mins[1] > maxs[1] ? -mins[1] : maxs[1];
            trace_extents[2] = -mins[2] > maxs[2] ? -mins[2] : maxs[2];
        }

        //
        // general sweeping through world
        //
        CM_RecursiveHullCheck(headnode, 0, 1, start, end);

        if (trace_trace.fraction == 1) {
            Math3D.VectorCopy(end, trace_trace.endpos);
        } else {
            for (int i = 0; i < 3; i++)
                trace_trace.endpos[i] = start[i] + trace_trace.fraction
                        * (end[i] - start[i]);
        }
        return trace_trace;
    }

    /**
     * CM_TransformedBoxTrace handles offseting and rotation of the end points for moving and rotating
     * entities.
     */
    public trace_t TransformedBoxTrace(float[] start, float[] end,
            float[] mins, float[] maxs, int headnode, int brushmask,
            float[] origin, float[] angles) {
        trace_t trace;
        float[] start_l = { 0, 0, 0 }, end_l = { 0, 0, 0 };
        float[] a = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0, 0 };
        float[] temp = { 0, 0, 0 };
        boolean rotated;

        // subtract origin offset
        Math3D.VectorSubtract(start, origin, start_l);
        Math3D.VectorSubtract(end, origin, end_l);

        // rotate start and end into the models frame of reference
        if (headnode != box_headnode
                && (angles[0] != 0 || angles[1] != 0 || angles[2] != 0))
            rotated = true;
        else
            rotated = false;

        if (rotated) {
            Math3D.AngleVectors(angles, forward, right, up);

            Math3D.VectorCopy(start_l, temp);
            start_l[0] = Math3D.DotProduct(temp, forward);
            start_l[1] = -Math3D.DotProduct(temp, right);
            start_l[2] = Math3D.DotProduct(temp, up);

            Math3D.VectorCopy(end_l, temp);
            end_l[0] = Math3D.DotProduct(temp, forward);
            end_l[1] = -Math3D.DotProduct(temp, right);
            end_l[2] = Math3D.DotProduct(temp, up);
        }

        // sweep the box through the model
        trace = BoxTrace(start_l, end_l, mins, maxs, headnode, brushmask);

        if (rotated && trace.fraction != 1.0) {
            // FIXME: figure out how to do this with existing angles
            Math3D.VectorNegate(angles, a);
            Math3D.AngleVectors(a, forward, right, up);

            Math3D.VectorCopy(trace.plane.normal, temp);
            trace.plane.normal[0] = Math3D.DotProduct(temp, forward);
            trace.plane.normal[1] = -Math3D.DotProduct(temp, right);
            trace.plane.normal[2] = Math3D.DotProduct(temp, up);
        }

        trace.endpos[0] = start[0] + trace.fraction * (end[0] - start[0]);
        trace.endpos[1] = start[1] + trace.fraction * (end[1] - start[1]);
        trace.endpos[2] = start[2] + trace.fraction * (end[2] - start[2]);

        return trace;
    }

    /*
     * ===============================================================================
     * PVS / PHS
     * ===============================================================================
     */

    /*
     * =================== CM_DecompressVis ===================
     */
    public void CM_DecompressVis(byte[] in, int offset, byte[] out) {
        int c;

        int row;

        row = (numclusters + 7) >> 3;
        int outp = 0;
        int inp = offset;

        if (in == null || numvisibility == 0) { // no vis info, so make all
                                                // visible
            while (row != 0) {
                out[outp++] = (byte) 0xFF;
                row--;
            }
            return;
        }

        do {
            if (in[inp] != 0) {
                out[outp++] = in[inp++];
                continue;
            }

            c = in[inp + 1] & 0xFF;
            inp += 2;
            if (outp + c > row) {
                c = row - (outp);
                Com.DPrintf("warning: Vis decompression overrun\n");
            }
            while (c != 0) {
                out[outp++] = 0;
                c--;
            }
        } while (outp < row);
    }

    public byte[] pvsrow = new byte[Defines.MAX_MAP_LEAFS / 8];

    public byte[] phsrow = new byte[Defines.MAX_MAP_LEAFS / 8];

    public byte[] CM_ClusterPVS(int cluster) {
        if (cluster == -1)
            Arrays.fill(pvsrow, 0, (numclusters + 7) >> 3, (byte) 0);
        else
            CM_DecompressVis(map_visibility,
                    map_vis.bitofs[cluster][Defines.DVIS_PVS], pvsrow);
        return pvsrow;
    }

    public byte[] CM_ClusterPHS(int cluster) {
        if (cluster == -1)
            Arrays.fill(phsrow, 0, (numclusters + 7) >> 3, (byte) 0);
        else
            CM_DecompressVis(map_visibility,
                    map_vis.bitofs[cluster][Defines.DVIS_PHS], phsrow);
        return phsrow;
    }

    /*
     * ===============================================================================
     * AREAPORTALS
     * ===============================================================================
     */

    public void FloodArea_r(carea_t area, int floodnum) {
        //Com.Printf("FloodArea_r(" + floodnum + ")...\n");
        int i;
        qfiles.dareaportal_t p;

        if (area.floodvalid == floodvalid) {
            if (area.floodnum == floodnum)
                return;
            Com.Error(Defines.ERR_DROP, "FloodArea_r: reflooded");
        }

        area.floodnum = floodnum;
        area.floodvalid = floodvalid;

        for (i = 0; i < area.numareaportals; i++) {
            p = map_areaportals[area.firstareaportal + i];
            if (portalopen[p.portalnum])
                FloodArea_r(map_areas[p.otherarea], floodnum);
        }
    }

    /**
     * FloodAreaConnections.
     */
    public void FloodAreaConnections() {
        Com.DPrintf("FloodAreaConnections...\n");

        int i;
        carea_t area;
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

    /**
     * CM_SetAreaPortalState.
     */
    public void CM_SetAreaPortalState(int portalnum, boolean open) {
        if (portalnum > numareaportals)
            Com.Error(Defines.ERR_DROP, "areaportal > numareaportals");

        portalopen[portalnum] = open;
        FloodAreaConnections();
    }

    /**
     * CM_AreasConnected returns true, if two areas are connected.
     */

    public boolean CM_AreasConnected(int area1, int area2) {
        if (map_noareas.value != 0)
            return true;

        if (area1 > numareas || area2 > numareas)
            Com.Error(Defines.ERR_DROP, "area > numareas");

        if (map_areas[area1].floodnum == map_areas[area2].floodnum)
            return true;

        return false;
    }

    /**
     * CM_WriteAreaBits writes a length byte followed by a bit vector of all the areas that area
     * in the same flood as the area parameter
     * 
     * This is used by the client refreshes to cull visibility.
     */
    public int CM_WriteAreaBits(byte[] buffer, int area) {
        int i;
        int floodnum;
        int bytes;

        bytes = (numareas + 7) >> 3;

        if (map_noareas.value != 0) { 
            // for debugging, send everything
            Arrays.fill(buffer, 0, bytes, (byte) 255);
        } else {
            Arrays.fill(buffer, 0, bytes, (byte) 0);
            floodnum = map_areas[area].floodnum;
            for (i = 0; i < numareas; i++) {
                if (map_areas[i].floodnum == floodnum || area == 0)
                    buffer[i >> 3] |= 1 << (i & 7);
            }
        }

        return bytes;
    }

    /**
     * CM_WritePortalState writes the portal state to a savegame file.
     */

    public void CM_WritePortalState(RandomAccessFile os) {

        try {
            for (int n = 0; n < portalopen.length; n++)
                if (portalopen[n])
                    os.writeInt(1);
                else
                    os.writeInt(0);
        } catch (Exception e) {
            Com.Printf("ERROR:" + e);
            e.printStackTrace();
        }
    }

    /**
     * CM_ReadPortalState reads the portal state from a savegame file and recalculates the area
     * connections.
     */
    public void CM_ReadPortalState(RandomAccessFile f) {

        //was: FS_Read(portalopen, sizeof(portalopen), f);
        int len = portalopen.length * 4;

        byte buf[] = new byte[len];

        FS.Read(buf, len, f);

        ByteBuffer bb = ByteBuffer.wrap(buf);
        IntBuffer ib = bb.asIntBuffer();

        for (int n = 0; n < portalopen.length; n++)
            portalopen[n] = ib.get() != 0;

        FloodAreaConnections();
    }

    /**
     * CM_HeadnodeVisible returns true if any leaf under headnode has a cluster that is potentially
     * visible.
     */
    public boolean CM_HeadnodeVisible(int nodenum, byte visbits[]) {
        if (nodenum < 0) {
            int leafnum = -1 - nodenum;
            int cluster = map_leafs[leafnum].cluster;
            if (cluster == -1) return false;
            
            if (0 != (visbits[cluster >>> 3] & (1 << (cluster & 7)))) return true;
            
            return false;
        }

        cnode_t node = map_nodes[nodenum];
        if (CM_HeadnodeVisible(node.children[0], visbits)) return true;
        
        return CM_HeadnodeVisible(node.children[1], visbits);
    }
}