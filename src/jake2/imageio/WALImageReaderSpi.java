/*
 * Created on Nov 18, 2003
 *
 */
package jake2.imageio;

import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

/**
 * @author cwei
 * 
 * Quake 2 stores textures in a proprietary 2D image format called WAL.
 * While this isn't actually part of the BSP file format,
 * it's essential information for loading Quake 2 maps.
 * WAL textures are stored in a 8-bit indexed color format with a specific palette being used by all textures.
 * (this palette is stored in the PAK data file that comes with Quake 2 e.g. pics/colormap.pcx).
 * Four mip-map levels are stored for each texture at sizes decreasing by a factor of two.
 * This is mostly for software rendering since most 3D APIs will automatically generate
 * the mip-map levels when you create a texture.
 * Each frame of an animated texture is stored as an individual WAL file,
 * and the animation sequence is encoded by storing the name of the next texture in the sequence for each frame;
 * texture names are stored with paths and without any extension.
 * The format for the WAL file header is the wal_header structure:
 * <code>
	struct wal_header
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
	</code>
 * The actual texture data is stored in an 8-bits-per-pixel raw format in a left-right, top-down order.
 * <br>
 * put the own ImageReaderSpi class name in the file (relative to classpath):
 * <code>META-INF/services/javax.imageio.spi.ImageReaderSpi</code>
 * 
 *  */
public class WALImageReaderSpi extends ImageReaderSpi {

	static final String vendorName = "jteam@in-chemnitz.de";
	static final String version = "1.0_beta";
	static final String[] names = { "quake2 wal" };
	static final String[] suffixes = { "wal" };
	static final String[] MIMETypes = { "image/x-quake2-wal" };
	static final String readerClassName = "jake2.imageio.WALImageReader";
	static final String[] writerSpiNames = null; //		{ "jake2.imageio.WALImageWriterSpi" };

	// Metadata formats, more information below
	static final boolean supportsStandardStreamMetadataFormat = false;
	static final String nativeStreamMetadataFormatName = null;

	static final String nativeStreamMetadataFormatClassName = null;
	static final String[] extraStreamMetadataFormatNames = null;
	static final String[] extraStreamMetadataFormatClassNames = null;
	static final boolean supportsStandardImageMetadataFormat = false;
	static final String nativeImageMetadataFormatName =
		"jake2.imageio.WALMetaData_1.0";
	static final String nativeImageMetadataFormatClassName = null;	// "jake2.imageio.WALMetadata";
	static final String[] extraImageMetadataFormatNames = null;
	static final String[] extraImageMetadataFormatClassNames = null;

	public WALImageReaderSpi() {

		super(
			vendorName,
			version,
			names,
			suffixes,
			MIMETypes,
			readerClassName,
			ImageReaderSpi.STANDARD_INPUT_TYPE, // Accept ImageInputStreams
			writerSpiNames,
			supportsStandardStreamMetadataFormat,
			nativeStreamMetadataFormatName,
			nativeStreamMetadataFormatClassName,
			extraStreamMetadataFormatNames,
			extraStreamMetadataFormatClassNames,
			supportsStandardImageMetadataFormat,
			nativeImageMetadataFormatName,
			nativeImageMetadataFormatClassName,
			extraImageMetadataFormatNames,
			extraImageMetadataFormatClassNames);
	}

	public boolean canDecodeInput(Object source) throws IOException {
		if (!(source instanceof ImageInputStream)) {
			return false;
		}
		ImageInputStream stream = (ImageInputStream)source;
		byte[] buffer = new byte[WAL.HEADER_SIZE];
		try {
			stream.mark();
			stream.readFully(buffer);
			stream.reset();
			// buffer will be converted to members and header checked
			WAL.Header wal = new WAL.Header(buffer); 
		} catch (IllegalArgumentException e) {
			return false;
		}
		return true;
	}

	/**
	 * returns a WALImageReader
	 */
	public ImageReader createReaderInstance(Object extension)
		throws IOException {
		return new WALImageReader(this);
	}

	public String getDescription(Locale locale) {
		return "id-software's Quake2 wal format for textures";
	}
}
