/*
 * Created on Nov 17, 2003
 *
 */
package jake2.imageio;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.*;

/**
 * @author cwei
 *
 */
public class TestImage {

	static final String TEST_FILE = "pics/colormap.pcx";
	static final long PAUSE = 15; // ms
	static final int ENTRIES = 2501;

	File pakPath;
	List imageFiles;

	TestImage(File pakPath) {
		this.pakPath = pakPath;
		this.imageFiles = new ArrayList(ENTRIES);
	}

	public static void main(String[] args) throws Exception {

		if (args == null || args.length == 0) {
			usage();
		}

		File path = new File(args[0]);

		if (!path.isDirectory()) {
			System.err.println(path.toString() + " is not a directory");
			usage();
		}

		if (!new File(path.getPath() + "/" + TEST_FILE).canRead()) {
			System.err.println(
				path.getPath() + " is not a unpacked quake2-pak file location");
			usage();
		}

		System.out.println("*** Start Image test ***\n");

		ImageIO.scanForPlugins();

		TestImage test = new TestImage(path);
		test.run();

		System.gc();
		Runtime rt = Runtime.getRuntime();
		System.out.println(
			"JVM total memory: " + rt.totalMemory() / 1024 + " Kbytes\n");

		System.out.println("*** Image test is succeeded :-) ***\n");
	}

	static void usage() {
		System.out.println(
			"usage: TestImage <path to unpacked quake2-pak file>");
		System.exit(0);
	}

	void run() {

		System.out.println("begin directory scanning ...");
		scanDirectory(pakPath);
		System.out.println(imageFiles.size() + " graphic files found\n");

		ImageFrame frame = new ImageFrame(null);
		frame.setVisible(true);

		File f = null;
		BufferedImage image = null;

		for (Iterator it = imageFiles.iterator(); it.hasNext();) {
			f = (File) it.next();
			try {
				image = scale(ImageIO.read(f));
				frame.showImage(image);
				frame.setTitle(f.getPath());

				Thread.sleep(PAUSE);

			} catch (IOException e) {
				System.err.println(e.getMessage());
			} catch (InterruptedException e) {
			}
		}
		frame.dispose();
		imageFiles.clear();
	}

	void scanDirectory(File dir) {
		File[] files = dir.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				/* matches *.pcx or *.wal files */
				return name.matches(".*(pcx|wal)$");
			}
		});

		if (files != null && files.length > 0) {
			imageFiles.addAll(Arrays.asList(files));
		}

		File[] dirs = dir.listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});

		if (dirs != null && dirs.length > 0) {
			for (int i = 0; i < dirs.length; i++) {
				System.out.println(dirs[i]);
				// recursive directory scanning
				scanDirectory(dirs[i]);
			}
		}
	}

	BufferedImage scale(BufferedImage src) {
		BufferedImage dst = null;

		int size = Math.max(src.getHeight(), src.getWidth());

		double scale = 1.5;

		if (size < 50) {
			scale = 4.0;
		} else if (size < 200) {
			scale = 2.5;
		} else if (size > 400) {
			scale = 1.5;
		}
		BufferedImageOp op =
			new AffineTransformOp(
				AffineTransform.getScaleInstance(scale, scale),
				AffineTransformOp.TYPE_NEAREST_NEIGHBOR/*TYPE_BILINEAR*/);
		dst = op.filter(src, null);

		return (dst != null) ? dst : src;
	}
}
