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

// Created on 09.01.2004 by RST.
// $Id: SuperAdapter.java,v 1.5 2004-09-28 22:47:41 cawe Exp $

package jake2.game;

import jake2.qcommon.Com;

import java.util.Hashtable;
import java.util.Vector;

public class SuperAdapter {

	/** Adapter registration. */
	private static void register(SuperAdapter sa, String id) {
		adapters.put(id, sa);
	}

	/** Adapter repository. */
	private static Hashtable adapters= new Hashtable();

	/** Returns the adapter from the repository given by its ID. */
	public static SuperAdapter getFromID(String key) {
		SuperAdapter sa= (SuperAdapter) adapters.get(key);

		// try to create the adapter
		if (sa == null) {
			Com.DPrintf("SuperAdapter.getFromID():adapter not found->" + key + "\n");
			int pos= key.indexOf('$');
			String classname= key;
			if (pos != -1)
				classname= key.substring(0, pos);

			// load class and instantiate
			try {
				//Com.DPrintf("SuperAdapter.getFromID():loading class->" + classname + "\n");
				Class.forName(classname);
			}
			catch (Exception e) {
				Com.DPrintf("SuperAdapter.getFromID():class not found->" + classname + "\n");
			}

			// try it again...			
			sa= (SuperAdapter) adapters.get(key);

			if (sa == null)
				Com.DPrintf("jake2: could not load adapter:" + key + "\n");
		}

		return sa;
	}

	/** Constructor, does the adapter registration. */
	public SuperAdapter() {
		StackTraceElement tr[]= new Throwable().getStackTrace();
		adapterid= tr[2].getClassName();
		if (adapterid == "")
			new Throwable("error in creating an adapter id!").printStackTrace();
		else
			register(this, adapterid);
	}

	/** Returns the Adapter-ID. */
	public String getID() {
		return adapterid;
	}

	/** Adapter id. */
	private String adapterid;
}
