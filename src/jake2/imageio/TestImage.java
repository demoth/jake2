/*
 * Created on Nov 17, 2003
 *
 */
package jake2.imageio;

import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import javax.imageio.*;

/**
 * @author cwei
 *
 */
public class TestImage {

	static final String TEST_FILE = "pics/colormap.pcx";

	static final String[] images =
		{
			"models/monsters/mutant/skin.pcx",
			"textures/e1u1/basemap.wal",
			"textures/e1u1/wswall1_1.wal",
			"textures/e1u2/elev_dr2.wal",
			"textures/e2u1/hdoor1_2.wal",
			"env/space1bk.pcx"};

	public static void main(String[] args) throws Exception {
		
		if (args == null || args.length == 0) {
			usage();
		}
		
		File path = new File(args[0]);
		
		if (!path.isDirectory()) {
			System.err.println(path.toString() + " is not a directory");
			usage();
		}
		
		if (!new File(path.getPath() + "/" +TEST_FILE).canRead()) {
			System.err.println(path.getPath() + " is not a unpacked quake2-pak file location");
			usage();
		}
		
		System.out.println("TestImage\n");

		ImageIO.scanForPlugins();

		for (int i = 0; i < images.length; i++) {
			File f = new File(path.getPath() + "/"+ images[i]);
			System.out.println(images[i] + " length: " + f.length());
			BufferedImage image;
			try {
				image = ImageIO.read(f);
				ImageFrame frame = new ImageFrame(image);
				frame.setTitle(f.getName());
				frame.setVisible(true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	static void usage() {
		System.out.println("usage: TestImage <path to unpacked quake2-pak file>");
		System.exit(0);
	}

/*	public static void palette() throws Exception {

		File f = new File("../../unpack2/pics/colormap.pcx");

		RandomAccessFile rf = new RandomAccessFile(f, "r");

		rf.skipBytes((int) rf.length() - 768);

		byte[] rgb = new byte[768];

		rf.read(rgb, 0, 768);

		byte[] a = new byte[256];
		byte[] r = new byte[256];
		byte[] g = new byte[256];
		byte[] b = new byte[256];

		for (int i = 0; i < 256; ++i) {
			a[i] = (i != 255) ? (byte) 0xff : (byte) 0x00;
			r[i] = rgb[i * 3 + 0];
			g[i] = rgb[i * 3 + 1];
			b[i] = rgb[i * 3 + 2];
		}

		for (int i = 0; i < 256; ++i) {
			if ((i % 16) != 15) {
				System.out.print(a[i] + ", ");
			} else {
				System.out.println(a[i] + ", ");
			}
		}

	}
*/
}
