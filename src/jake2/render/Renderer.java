/*
 * Renderer.java
 * Copyright (C) 2003
 *
 * $Id: Renderer.java,v 1.5 2003-11-25 14:42:30 cwei Exp $
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
package jake2.render;

import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import jake2.client.refexport_t;
import jake2.client.refimport_t;

/**
 * Renderer
 * 
 * @author cwei
 */
public class Renderer {
	
	private static Logger logger = Logger.getLogger(Renderer.class.getName());

	static Vector drivers = new Vector(3); 

	public static final String DEFAULT = JoglRenderer.DRIVER_NAME; 
	private static final String DEFAULT_CLASS = "jake2.render.JoglRenderer"; 

	static {
		try {
			Class.forName("jake2.render.JoglRenderer");
		} catch (ClassNotFoundException e) {
			logger.log(Level.SEVERE, "can't found " +  DEFAULT_CLASS);
			e.printStackTrace();
		}
	};

	public static void register(Ref impl) {
		if (impl == null) {
			throw new IllegalArgumentException("Ref implementation can't be null");
		}
		if (!drivers.contains(impl)) {
			drivers.add(impl);
		}
	}
	
	/**
	 * Factory method to get the Renderer implementation.
	 * @return refexport_t (Renderer singleton)
	 */
	public static refexport_t getDriver(String driverName, refimport_t rimp) {
		if (rimp == null) throw new IllegalArgumentException("refimport_t can't be null");
		// find a driver
		Ref driver = null;
		int count = drivers.size();
		for (int i=0; i < count && driver == null; i++) {
			driver = (Ref)drivers.get(i);
			if (driver.getName().equals(driverName)) {
				return driver.GetRefAPI(rimp);
			}
		}
		logger.log(Level.INFO, "Refresh driver \"" + driverName + "\"not found");
		// null if driver not found
		return null;
	}

	public static String[] getDriverNames() {
		int count = drivers.size();
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = ((Ref)drivers.get(i)).getName();
		}
		return names;
	}
}