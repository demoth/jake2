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

// Created on 24.07.2004 by RST.
// $Id: QuakeFile.java,v 1.3 2004-09-10 19:02:56 salomo Exp $

package jake2.util;

import jake2.game.Game;
import jake2.game.SuperAdapter;
import jake2.game.edict_t;
import jake2.game.gitem_t;
import jake2.qcommon.Com;

import java.io.*;

/** 
 * RandomAccessFile, but handles readString/WriteString specially and
 * offers other helper functions 
 */
public class QuakeFile extends RandomAccessFile
{

	/** Standard Constructor.*/
	public QuakeFile(String filename, String mode) throws FileNotFoundException
	{
		super(filename, mode);
	}

	/** Writes a Vector to a RandomAccessFile. */
	public void writeVector(float v[]) throws IOException
	{
		for (int n= 0; n < 3; n++)
			writeFloat(v[n]);
	}

	/** Writes a Vector to a RandomAccessFile. */
	public float[] readVector() throws IOException
	{
		float res[]= { 0, 0, 0 };
		for (int n= 0; n < 3; n++)
			res[n]= readFloat();

		return res;
	}

	/** Reads a length specified string from a file. */
	public String readString() throws IOException
	{
		int len= readInt();

		if (len == -1)
			return null;

		if (len == 0)
			return "";

		byte bb[]= new byte[len];

		super.read(bb, 0, len);

		return new String(bb, 0, len);
	}

	/** Writes a length specified string to a file. */
	public void writeString(String s) throws IOException
	{
		if (s == null)
		{
			writeInt(-1);
			return;
		}

		writeInt(s.length());
		if (s.length() != 0)
			writeBytes(s);
	}

	/** Writes the edict reference. */
	public void writeEdictRef(edict_t ent) throws IOException
	{
		if (ent == null)
			writeInt(-1);
		else
		{
			writeInt(ent.s.number);
		}
	}

	/** 
	 * Reads an edict index from a file and returns the edict.
	 */

	public edict_t readEdictRef() throws IOException
	{
		int i= readInt();

		// handle -1
		if (i < 0)
			return null;

		if (i > Game.g_edicts.length)
		{
			Com.DPrintf("jake2: illegal edict num:" + i + "\n");
			return null;
		}

		// valid edict.
		return Game.g_edicts[i];
	}

	/** Writes the Adapter-ID to the file. */
	public void writeAdapter(SuperAdapter a) throws IOException
	{
		writeInt(3988);
		if (a == null)
			writeString(null);
		else
		{
			String str= a.getID();
			if (a == null)
			{
				Com.DPrintf("writeAdapter: invalid Adapter id for " + a + "\n");
			}
			writeString(str);
		}
	}

	/** Reads the adapter id and returns the adapter. */
	public SuperAdapter readAdapter() throws IOException
	{
		if (readInt() != 3988)
			Com.DPrintf("wrong read position: readadapter 3988 \n");

		String id= readString();

		if (id == null)
		{
			// null adapter. :-)
			return null;
		}

		return SuperAdapter.getFromID(id);
	}

	/** Writes an item reference. */
	public void writeItem(gitem_t item) throws IOException
	{
		if (item == null)
			writeInt(-1);
		else
			writeInt(item.index);
	}

	/** Reads the item index and returns the game item. */
	public gitem_t readItem() throws IOException
	{
		int ndx= readInt();
		if (ndx == -1)
			return null;
		else
			return Game.itemlist[ndx];
	}

}
