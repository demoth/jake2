/*
 * Created on Nov 18, 2003
 *
 */
package jake2.imageio;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * @author cwei
 *
 */
public class WALImageReader extends ImageReader {

	ImageInputStream stream = null;
	int width, height;
	String name;
	String next;

	boolean gotHeader = false;

	public WALImageReader(ImageReaderSpi originatingProvider) {
		super(originatingProvider);
	}

	public void setInput(Object input, boolean seekForwardOnly) {
		super.setInput(input, seekForwardOnly);
		if (input == null) {
			this.stream = null;
			return;
		}
		if (input instanceof ImageInputStream) {
			this.stream = (ImageInputStream) input;
		} else {
			throw new IllegalArgumentException("bad input");
		}
	}

	public int getHeight(int imageIndex) throws IOException {
		checkIndex(imageIndex);
		readHeader();
		return height;
	}

	public int getWidth(int imageIndex) throws IOException {
		checkIndex(imageIndex);
		readHeader();
		return width;
	}

	public int getNumImages(boolean allowSearch) throws IOException {
		// only 1 image
		return 1;
	}

	public Iterator getImageTypes(int imageIndex) throws IOException {
		checkIndex(imageIndex);
		readHeader();

		ImageTypeSpecifier imageType = null;
		java.util.List l = new ArrayList(1);

		imageType =
			ImageTypeSpecifier.createIndexed(
				Q2ColorMap.RED,
				Q2ColorMap.GREEN,
				Q2ColorMap.BLUE,
				Q2ColorMap.ALPHA,
				8,
				DataBuffer.TYPE_BYTE);

		l.add(imageType);
		return l.iterator();
	}

	public IIOMetadata getStreamMetadata() throws IOException {
		return null;
	}

	public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
		return null;
	}

	public BufferedImage read(int imageIndex, ImageReadParam param)
		throws IOException {

		checkIndex(imageIndex);
		readHeader();

		//		Compute initial source region, clip against destination later
		Rectangle sourceRegion = getSourceRegion(param, width, height);

		// Set everything to default values
		int sourceXSubsampling = 1;
		int sourceYSubsampling = 1;
		int[] sourceBands = null;
		int[] destinationBands = null;
		Point destinationOffset = new Point(0, 0);

		// Get values from the ImageReadParam, if any
		if (param != null) {
			sourceXSubsampling = param.getSourceXSubsampling();
			sourceYSubsampling = param.getSourceYSubsampling();
			sourceBands = param.getSourceBands();
			destinationBands = param.getDestinationBands();
			destinationOffset = param.getDestinationOffset();
		}

		//		 Get the specified detination image or create a new one
		BufferedImage dst =
			getDestination(param, getImageTypes(0), width, height);

		// Enure band settings from param are compatible with images
		int inputBands = 1;
		checkReadParamBandSettings(
			param,
			inputBands,
			dst.getSampleModel().getNumBands());

		int[] bandOffsets = new int[inputBands];
		for (int i = 0; i < inputBands; i++) {
			bandOffsets[i] = i;
		}
		int bytesPerRow = width * inputBands;
		DataBufferByte rowDB = new DataBufferByte(bytesPerRow);
		WritableRaster rowRas =
			Raster.createInterleavedRaster(
				rowDB,
				width,
				1,
				bytesPerRow,
				inputBands,
				bandOffsets,
				new Point(0, 0));
		byte[] rowBuf = rowDB.getData();

		// Create an int[] that can a single pixel
		int[] pixel = rowRas.getPixel(0, 0, (int[]) null);

		WritableRaster imRas = dst.getWritableTile(0, 0);
		int dstMinX = imRas.getMinX();
		int dstMaxX = dstMinX + imRas.getWidth() - 1;
		int dstMinY = imRas.getMinY();
		int dstMaxY = dstMinY + imRas.getHeight() - 1;

		// Create a child raster exposing only the desired source bands
		if (sourceBands != null) {
			rowRas =
				rowRas.createWritableChild(0, 0, width, 1, 0, 0, sourceBands);
		}

		// Create a child raster exposing only the desired dest bands
		if (destinationBands != null) {
			imRas =
				imRas.createWritableChild(
					0,
					0,
					imRas.getWidth(),
					imRas.getHeight(),
					0,
					0,
					destinationBands);

		}

		for (int srcY = 0; srcY < height; srcY++) {
			// Read the row
			try {
				stream.readFully(rowBuf);
			} catch (IOException e) {
				throw new IIOException("Error reading line " + srcY, e);
			}

			// Reject rows that lie outside the source region,
			// or which aren't part of the subsampling
			if ((srcY < sourceRegion.y)
				|| (srcY >= sourceRegion.y + sourceRegion.height)
				|| (((srcY - sourceRegion.y) % sourceYSubsampling) != 0)) {
				continue;
			}

			// Determine where the row will go in the destination
			int dstY =
				destinationOffset.y
					+ (srcY - sourceRegion.y) / sourceYSubsampling;
			if (dstY < dstMinY) {
				continue; // The row is above imRas
			}
			if (dstY > dstMaxY) {
				break; // We're done with the image
			}

			// Copy each (subsampled) source pixel into imRas
			for (int srcX = sourceRegion.x;
				srcX < sourceRegion.x + sourceRegion.width;
				srcX++) {
				if (((srcX - sourceRegion.x) % sourceXSubsampling) != 0) {
					continue;
				}
				int dstX =
					destinationOffset.x
						+ (srcX - sourceRegion.x) / sourceXSubsampling;
				if (dstX < dstMinX) {
					continue; // The pixel is to the left of imRas
				}
				if (dstX > dstMaxX) {
					break; // We're done with the row
				}

				// Copy the pixel, sub-banding is done automatically
				rowRas.getPixel(srcX, 0, pixel);
				imRas.setPixel(dstX, dstY, pixel);
			}
		}
		return dst;
	}

	private void checkIndex(int imageIndex) {
		if (imageIndex != 0) {
			throw new IndexOutOfBoundsException("bad image index");
		}
	}

	private void readHeader() throws IIOException {
		/*		struct wal_header
				{
					char    name[32];        // name of the texture
		 
					uint32  width;           // width (in pixels) of the largest mipmap level
					uint32  height;          // height (in pixels) of the largest mipmap level
		 
					int32   offset[4];       // byte offset of the start of each of the 4 mipmap levels
		
					char    next_name[32];   // name of the next texture in the animation
		
					uint32  flags;           // ?
					uint32  contents;        // ?
					uint32  value;           // ?
				};
		*/
		if (gotHeader) {
			return;
		}

		gotHeader = true;

		System.out.println("WAL read header");

		if (stream == null) {
			if (this.input == null) {
				throw new IllegalStateException("No input stream");
			}
			stream = (ImageInputStream) input;

		}

		byte[] name = new byte[32];

		try {
			stream.readFully(name);
		} catch (IOException e) {
			throw new IIOException("Error reading texture name", e);
		}

		char[] tmp = new char[32];
		for (int i = 0; i < 32; ++i) {
			tmp[i] = ((char) name[i]);
		}

		this.name = String.copyValueOf(tmp);

		System.out.println("WAL name: " + this.name);

		// Read width, height, color type, newline

		byte[] i = new byte[4];

		try {
			stream.readFully(i);
			this.width =
				((i[3] & 0x7f) << 24)
					+ ((i[2] & 0xff) << 16)
					+ ((i[1] & 0xff) << 8)
					+ (i[0] & 0xff);

			stream.readFully(i);
			this.height =
				((i[3] & 0x7f) << 24)
					+ ((i[2] & 0xff) << 16)
					+ ((i[1] & 0xff) << 8)
					+ (i[0] & 0xff);

			System.out.println(
				"WAL width: " + this.width + " height: " + this.height);

			// offset for the 4 mipmap levels
			int[] offset = new int[4];
			offset[0] = stream.readInt();
			offset[1] = stream.readInt();
			offset[2] = stream.readInt();
			offset[3] = stream.readInt();

			// next file name for animation
			byte[] next = new byte[32];
			stream.readFully(next);

			for (int j = 0; j < 32; ++j) {
				tmp[j] = ((char) name[j]);
			}
			this.next = String.copyValueOf(tmp);
			System.out.println("WAL next: " + this.next);

			// unknow entries
			int flags = stream.readInt();
			int contents = stream.readInt();
			int value = stream.readInt();

		} catch (IOException e) {
			throw new IIOException("Error reading header", e);
		}

	}
}
