/*
 * TestFS.java
 * Copyright (C) 2003
 *
 * $Id: TestFS.java,v 1.5 2003-12-25 18:16:47 cwei Exp $
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

import jake2.game.Cmd;
import jake2.imageio.ImageFrame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.*;

import javax.imageio.ImageIO;

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

		Cmd.cmd_argc = 3;
		Cmd.cmd_argv = new String[] { "link", "unknown.pcx", "../../baseq2/space.pcx" };
		FS.Link_f();

		FS.Path_f();

		Cmd.cmd_argv = new String[] { "link", "unknown1.pcx", "../../baseq2/config.cfg" };
		FS.Link_f();

		FS.Path_f();
		
		// loescht den link
		Cmd.cmd_argv = new String[] { "link", "unknown1.pcx", "" };
		FS.Link_f();

		FS.Path_f();


		Cmd.cmd_argc = 2;
		Cmd.cmd_argv = new String[] { "dir", "players/male/*.[a-zA-Z_0-9]?x" };
		FS.Dir_f();


		String[] filenames =
			{
				"pics/conback.pcx",
				"pics/colormap.pcx",
				"pics/victory.pcx",
				"pics/help.pcx",
				"unknown.pcx" };

		ImageFrame frame = new ImageFrame(null);
		frame.setVisible(true);
		byte[] buffer = null;

		for (int i = 0; i < filenames.length; i++) {
			String filename = filenames[i];

			//System.out.println("Load " + filename + " : " + FS.FileLength(filename));

			buffer = FS.LoadFile(filename);

			if (buffer != null) {

				try {
					BufferedImage image =
						ImageIO.read(new ByteArrayInputStream(buffer));
					frame.showImage(image);
					try {
						Thread.sleep(500);
					} catch (InterruptedException e1) {
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
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
