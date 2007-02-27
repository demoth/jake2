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

// Created on 11.11.2003 by RST.

package jake2.game;

import jake2.util.QuakeFile;

import java.io.IOException;

public class mmove_t {
	public mmove_t(int firstframe, int lastframe, mframe_t frame[], EntThinkAdapter endfunc) {
		
		this.firstframe= firstframe;
		this.lastframe= lastframe;
		this.frame= frame;
		this.endfunc= endfunc;
	}

	public mmove_t()
	{}

	public int firstframe;
	public int lastframe;
	public mframe_t frame[]; //ptr
	public EntThinkAdapter endfunc;
	

	/** Writes the structure to a random acccess file. */
	public void write(QuakeFile f) throws IOException
	{
		f.writeInt(firstframe);
		f.writeInt(lastframe);
		if (frame == null)
			f.writeInt(-1);
		else 
		{
			f. writeInt(frame.length);
			for (int n=0; n < frame.length; n++)
				frame[n].write(f);
		}
		f.writeAdapter(endfunc);
	}
	
	/** Read the mmove_t from the RandomAccessFile. */
	public void read(QuakeFile f) throws IOException
	{
		firstframe = f.readInt();
		lastframe = f.readInt();
		
		int len = f.readInt();
		
		frame = new mframe_t[len];
		for (int n=0; n < len ; n++)
		{			
			frame[n] = new mframe_t();
			frame[n].read(f);
		}
		endfunc = (EntThinkAdapter) f.readAdapter();
	}
}
