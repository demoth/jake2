/*
 * Created on Nov 17, 2003
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
public class PCXImageReader extends ImageReader {

	ImageInputStream stream = null;
	PCX.Header header = null;

	public PCXImageReader(ImageReaderSpi originatingProvider) {
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
		return header.height;
	}

	public int getWidth(int imageIndex) throws IOException {
		checkIndex(imageIndex);
		readHeader();
		return header.width;
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
		Rectangle sourceRegion =
			getSourceRegion(param, header.width, header.height);

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
			getDestination(
				param,
				getImageTypes(0),
				header.width,
				header.height);

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
		int bytesPerRow = header.width * inputBands;
		DataBufferByte rowDB = new DataBufferByte(bytesPerRow);
		WritableRaster rowRas =
			Raster.createInterleavedRaster(
				rowDB,
				header.width,
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
				rowRas.createWritableChild(
					0,
					0,
					header.width,
					1,
					0,
					0,
					sourceBands);
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

		for (int srcY = 0; srcY < header.height; srcY++) {
			// Read the row
			try {
				decodeRow(rowBuf);
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
		if ((stream.readUnsignedByte()) == 0x0c) {
			System.out.print("PCX has a color palette with ");
			System.out.println(
				(stream.length() - stream.getStreamPosition())
					+ " Bytes, but use the default palette (quake2)");
		}
		return dst;
	}

	private void checkIndex(int imageIndex) {
		if (imageIndex != 0) {
			throw new IndexOutOfBoundsException("bad image index");
		}
	}

	/**
	 * run length decoding for PCX images
	 * @param buffer
	 * @throws IOException
	 */
	private void decodeRow(byte[] buffer) throws IOException {
		int dataByte = 0;
		int runLength = 0;
		int index = 0;

		while (index < buffer.length) {
			dataByte = stream.readUnsignedByte();
			if ((dataByte & 0xc0) == 0xc0) {
				runLength = dataByte & 0x3f;
				dataByte = stream.readUnsignedByte();
			} else {
				runLength = 1;
			}

			while (runLength-- > 0) {
				buffer[index++] = (byte) (dataByte & 0xff);
			}
		}
	}

	private void readHeader() throws IIOException {
		if (header != null) {
			return;
		}

		System.out.println("PCX read header");

		if (stream == null) {
			if (this.input == null) {
				throw new IllegalStateException("No input stream");
			}
			stream = (ImageInputStream) input;
		}

		byte[] buffer = new byte[PCX.HEADER_SIZE];

		try {
			stream.readFully(buffer);
			this.header = new PCX.Header(buffer);
			System.out.println(
				"PCX width: " + header.width + " height: " + header.height);

		} catch (IOException e) {
			throw new IIOException("Error reading quake2 PCX header", e);
		}

	}

}
