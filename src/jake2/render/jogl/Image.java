/*
 * Image.java
 * Copyright (C) 2003
 *
 * $Id: Image.java,v 1.1 2003-12-27 16:24:25 cwei Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.qcommon.qfiles;

import java.awt.Dimension;

/**
 * Image
 * 
 * @author cwei
 */
public class Image extends Main {
	
	protected int[] d_8to24table = new int[256]; 
	
//	/*
//	==============
//	LoadPCX
//	==============
//	*/
//	void LoadPCX (char *filename, byte **pic, byte **palette, int *width, int *height)
	byte[] LoadPCX (String filename, byte[][] palette, Dimension dim)
	{
//		byte	*raw;
		qfiles.pcx_t pcx;
		int		x, y;
//		int		len;
		int dataByte;
		int runLength;
//		byte	*out, *pix;
//
//		*pic = NULL;
//		*palette = NULL;
//
//		//
//		// load the file
//		//
//		len = ri.FS_LoadFile (filename, (void **)&raw);
		byte[] raw = ri.FS_LoadFile(filename);
		if (raw == null)
		{
			ri.Con_Printf (Globals.PRINT_DEVELOPER, "Bad pcx file " + filename + '\n', null);
			return null;
		}

		//
		// parse the PCX file
		//
		pcx = new qfiles.pcx_t(raw);
//
//		pcx->xmin = LittleShort(pcx->xmin);
//		pcx->ymin = LittleShort(pcx->ymin);
//		pcx->xmax = LittleShort(pcx->xmax);
//		pcx->ymax = LittleShort(pcx->ymax);
//		pcx->hres = LittleShort(pcx->hres);
//		pcx->vres = LittleShort(pcx->vres);
//		pcx->bytes_per_line = LittleShort(pcx->bytes_per_line);
//		pcx->palette_type = LittleShort(pcx->palette_type);
//
//		raw = &pcx->data;
//
		if (pcx.manufacturer != 0x0a
			|| pcx.version != 5
			|| pcx.encoding != 1
			|| pcx.bits_per_pixel != 8
			|| pcx.xmax >= 640
			|| pcx.ymax >= 480) {
				
			ri.Con_Printf(Defines.PRINT_ALL, "Bad pcx file " + filename +'\n');
			return null;
		}
//
		byte[] pix = new byte[(pcx.ymax+1) * (pcx.xmax+1)];
//
//		*pic = out;
//
//		pix = out;
//
		if (palette != null)
		{
			palette[0] = new byte[768];
			System.arraycopy(raw, raw.length - 768, palette[0], 0, 768);
//			memcpy (*palette, (byte *)pcx + len - 768, 768);
		}

		if (dim != null) {
			dim.width = pcx.xmax+1;
			dim.height = pcx.ymax+1;
		}
//
		int index = 0;
		for (y=0 ; y<=pcx.ymax ; y++, index += pcx.xmax+1)
		{
			for (x=0 ; x<=pcx.xmax ; )
			{
				dataByte = pcx.data.get() & 0xff;
//
				if((dataByte & 0xC0) == 0xC0)
				{
					runLength = dataByte & 0x3F;
					dataByte = pcx.data.get() & 0xff;
				}
				else
					runLength = 1;

				while(runLength-- > 0)
					pix[x++ + index] = (byte)dataByte;
			}

		}
//
//		if ( raw - (byte *)pcx > len)
//		{
//			ri.Con_Printf (PRINT_DEVELOPER, "PCX file %s was malformed", filename);
//			free (*pic);
//			*pic = NULL;
//		}
//
//		ri.FS_FreeFile (pcx);
		return pix;
	}
	
	


}
