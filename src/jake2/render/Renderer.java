/*
 * Renderer.java
 * Copyright (C) 2003
 *
 * $Id: Renderer.java,v 1.3 2003-11-24 15:06:28 cwei Exp $
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

import java.awt.Dimension;
import java.util.Arrays;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import jake2.client.refexport_t;
import jake2.client.refimport_t;
import jake2.qcommon.xcommand_t;

import jake2.game.cvar_t;

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
	
	public static void main(String[] args) {
		
		// only for testing
		// a simple refimport_t implementation
		refimport_t rimp = new refimport_t() {
			public void Sys_Error(int err_level, String str, Object[] vargs) {
			}

			public void Cmd_AddCommand(String name, xcommand_t cmd) {
			}

			public void Cmd_RemoveCommand(String name) {
			}

			public int Cmd_Argc() {
				return 0;
			}

			public String Cmd_Argv(int i) {
				return null;
			}

			public void Cmd_ExecuteText(int exec_when, String text) {
			}

			public void Con_Printf(
				int print_level,
				String str,
				Object[] vargs) {
			}

			public int FS_LoadFile(String name, byte[] buf) {
				return 0;
			}

			public void FS_FreeFile(byte[] buf) {
			}

			public String FS_Gamedir() {
				return null;
			}

			public cvar_t Cvar_Get(String name, String value, int flags) {
				return null;
			}

			public cvar_t Cvar_Set(String name, String value) {
				return null;
			}

			public void Cvar_SetValue(String name, float value) {
			}

			public boolean Vid_GetModeInfo(Dimension dim, int mode) {
				return false;
			}

			public void Vid_MenuInit() {
			}

			public void Vid_NewWindow(int width, int height) {
			}
		};
		
		try {
			//Class.forName("jake2.render.JoglRenderer");
			String[] names = Renderer.getDriverNames();
			System.out.println("Registered Drivers: " + Arrays.asList(names));

			refexport_t re = Renderer.getDriver("jogl", rimp);

			System.out.println("Use driver: " + re);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}