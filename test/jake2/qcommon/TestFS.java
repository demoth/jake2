/*
 * TestFS.java
 * Copyright (C) 2003
 *
 * $Id: TestFS.java,v 1.2 2003-11-26 12:35:49 cwei Exp $
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

import java.io.IOException;
import java.util.logging.*;

/**
 * TestFS
 * 
 * @author cwei
 */
public class TestFS {

	public static void main(String[] args) {
		System.out.println("*** Start FS test ***\n");
		
		init();
		
		FS.InitFilesystem();
		FS.Path_f();
//		FS.Dir_f();
		
		System.out.println("\n*** FS test is succeeded :-) ***");
	}

	static void init() {
		// init the global LogManager with the logging.properties file
		try {
			LogManager.getLogManager().readConfiguration(
				TestFS.class.getResourceAsStream("/jake2/logging.properties"));
		} catch (SecurityException secEx) {
			secEx.printStackTrace();
		} catch (IOException ioEx) {
			System.err.println(
				"FATAL Error: can't load /jake2/logging.properties (classpath)");
			ioEx.printStackTrace();
		}
	}
}
