/*
 * TestTGA.java
 * Copyright (C) 2003
 *
 * $Id: TestTGA.java,v 1.1 2004-03-17 01:13:17 cwei Exp $
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

import java.awt.Dimension;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.logging.*;

import javax.imageio.stream.MemoryCacheImageInputStream;



/**
 * TestTGA
 * 
 * @author cwei
 */
public class TestTGA {

	public static void main(String[] args) {
		System.out.println("*** Start TGA test ***\n");

		init();

		FS.InitFilesystem();
	
		// search for pack_t
		FS.searchpath_t search;
		Collection filenames = new TreeSet();
		for (search = FS.fs_searchpaths; search != null; search = search.next) {
			// is the element a pak file?
			if (search.pack != null) {
				// add all the pak file names
				filenames.addAll(search.pack.files.keySet());
			}
		}
	
		ImageFrame frame = new ImageFrame(null);
		frame.setVisible(true);
		frame.setLocation(50, 50);
		frame.setSize(800, 800);

		byte[] buffer = null;
		Dimension dim = new Dimension();	
		BufferedImage image = null;

		int[] pixel = new int[512 * 512];

		 
		for (Iterator it = filenames.iterator(); it.hasNext();) {

			String filename = it.next().toString();
			if (!filename.endsWith(".tga")) continue;

			System.out.println(filename);
			buffer = LoadTGA(filename, dim);

			if (buffer != null) {
				try {

					int w = dim.width;
					int h = dim.height;
					int size = w * h;
		
					int r, g, b, a;
					
					for (int i = 0; i < size; i++)
					{
						r = buffer[4* i + 0] & 0xFF;
						g = buffer[4* i + 1] & 0xFF;
						b = buffer[4* i + 2] & 0xFF;
						a = buffer[4* i +3] & 0xFF;
	
						pixel[i] = (a << 24) | (r << 16) | (g << 8) | (b << 0); 
					}
		
					image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
					image.setRGB(0,  0, w, h, pixel, 0, w);
					
					AffineTransformOp op = new AffineTransformOp(AffineTransform.getScaleInstance(3, 3), AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
					BufferedImage tmp = op.filter(image, null);

					frame.showImage(tmp);
					frame.setTitle(filename);

					Thread.sleep(500);

				} catch (InterruptedException e) {
				}
			}
		}
		frame.dispose();
	
		System.gc();
		Runtime rt = Runtime.getRuntime();
		System.out.println(
			"\nJVM total memory: " + rt.totalMemory() / 1024 + " Kbytes");

		System.out.println("\n*** TGA test is succeeded :-) ***");
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
	
	/*
	=============
	LoadTGA
	=============
	*/
	static byte[] LoadTGA(String name, Dimension dim) {
		int columns, rows, numPixels;
		int pixbuf; // index into pic
		int row, column;
		byte[] raw;
		ByteBuffer buf_p;
		int length;
		qfiles.tga_t targa_header;
		byte[] pic = null;

		//
		// load the file
		//
		raw = FS.LoadFile(name);
		
		if (raw == null)
		{
			System.out.println("Bad tga file "+ name +'\n');
			return null;
		}
		
		targa_header = new qfiles.tga_t(raw);
		
		if (targa_header.image_type != 2 && targa_header.image_type != 10) 
			System.out.println("LoadTGA: Only type 2 and 10 targa RGB images supported\n");
		
		if (targa_header.colormap_type != 0 || (targa_header.pixel_size != 32 && targa_header.pixel_size != 24))
			System.out.println("LoadTGA: Only 32 or 24 bit images supported (no colormaps)\n");
		
		columns = targa_header.width;
		rows = targa_header.height;
		numPixels = columns * rows;
		
		if (dim != null) {
			dim.width = columns;
			dim.height = rows;
		}
		
		pic = new byte[numPixels * 4]; // targa_rgba;
		
		if (targa_header.id_length != 0)
			targa_header.data.position(targa_header.id_length);  // skip TARGA image comment
		
		buf_p = targa_header.data;
			
		byte red,green,blue,alphabyte;
		red = green = blue = alphabyte = 0;
		int packetHeader, packetSize, j;
		
		if (targa_header.image_type==2) {  // Uncompressed, RGB images
			for(row=rows-1; row>=0; row--) {
				
				pixbuf = row * columns * 4;
				
				for(column=0; column<columns; column++) {
					switch (targa_header.pixel_size) {
						case 24:
									
							blue = buf_p.get();
							green = buf_p.get();
							red = buf_p.get();
							pic[pixbuf++] = red;
							pic[pixbuf++] = green;
							pic[pixbuf++] = blue;
							pic[pixbuf++] = (byte)255;
							break;
						case 32:
							blue = buf_p.get();
							green = buf_p.get();
							red = buf_p.get();
							alphabyte = buf_p.get();
							pic[pixbuf++] = red;
							pic[pixbuf++] = green;
							pic[pixbuf++] = blue;
							pic[pixbuf++] = alphabyte;
							break;
					}
				}
			}
		}
		else if (targa_header.image_type==10) {   // Runlength encoded RGB images
			for(row=rows-1; row>=0; row--) {
				
				pixbuf = row * columns * 4;
				try {

					for(column=0; column<columns; ) {
					
						packetHeader= buf_p.get() & 0xFF;
						packetSize = 1 + (packetHeader & 0x7f);
					
						if ((packetHeader & 0x80) != 0) {        // run-length packet
							switch (targa_header.pixel_size) {
								case 24:
									blue = buf_p.get();
									green = buf_p.get();
									red = buf_p.get();
									alphabyte = (byte)255;
									break;
								case 32:
									blue = buf_p.get();
									green = buf_p.get();
									red = buf_p.get();
									alphabyte = buf_p.get();
									break;
							}
				
							for(j=0;j<packetSize;j++) {
								pic[pixbuf++]=red;
								pic[pixbuf++]=green;
								pic[pixbuf++]=blue;
								pic[pixbuf++]=alphabyte;
								column++;
								if (column==columns) { // run spans across rows
									column=0;
									if (row>0)
										row--;
									else
										// goto label breakOut;
										throw new longjmpException();
			
									pixbuf = row * columns * 4;
								}
							}
						}
						else { // non run-length packet
							for(j=0;j<packetSize;j++) {
								switch (targa_header.pixel_size) {
									case 24:
										blue = buf_p.get();
										green = buf_p.get();
										red = buf_p.get();
										pic[pixbuf++] = red;
										pic[pixbuf++] = green;
										pic[pixbuf++] = blue;
										pic[pixbuf++] = (byte)255;
										break;
									case 32:
										blue = buf_p.get();
										green = buf_p.get();
										red = buf_p.get();
										alphabyte = buf_p.get();
										pic[pixbuf++] = red;
										pic[pixbuf++] = green;
										pic[pixbuf++] = blue;
										pic[pixbuf++] = alphabyte;
										break;
								}
								column++;
								if (column==columns) { // pixel packet run spans across rows
									column=0;
									if (row>0)
										row--;
									else
										// goto label breakOut;
										throw new longjmpException();
			
									pixbuf = row * columns * 4;
								}						
							}
						}
					}
				} catch (longjmpException e){
					// label breakOut:
				}
			}
		}
		return pic;
	}

}
