/*
 * Created on Nov 17, 2003
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
 * put the own ImageReaderSpi class name in the file (relative to classpath):
 * <code>META-INF/services/javax.imageio.spi.ImageReaderSpi</code>
 *
 */
public class PCXImageReaderSpi extends ImageReaderSpi {

	static final String vendorName = "cwei.in-chemnitz.de";
	static final String version = "1.0_beta";
	static final String[] names = { "quake2 pcx" };
	static final String[] suffixes = { "pcx" };
	static final String[] MIMETypes = { "image/x-quake2-pcx" };
	static final String readerClassName = "cwei.imageio.PCXImageReader";
	static final String[] writerSpiNames = null; //		{ "cwei.imageio.PCXImageWriterSpi" };

	// Metadata formats, more information below
	static final boolean supportsStandardStreamMetadataFormat = false;
	static final String nativeStreamMetadataFormatName = null;

	static final String nativeStreamMetadataFormatClassName = null;
	static final String[] extraStreamMetadataFormatNames = null;
	static final String[] extraStreamMetadataFormatClassNames = null;
	static final boolean supportsStandardImageMetadataFormat = false;
	static final String nativeImageMetadataFormatName =
		"cwei.imageio.PCXMetaData_1.0";
	static final String nativeImageMetadataFormatClassName = null;	// "cwei.imageio.PCXMetadata";
	static final String[] extraImageMetadataFormatNames = null;
	static final String[] extraImageMetadataFormatClassNames = null;

	public PCXImageReaderSpi() {

		super(
			vendorName,
			version,
			names,
			suffixes,
			MIMETypes,
			readerClassName,
			ImageReaderSpi.STANDARD_INPUT_TYPE,
		// Accept ImageInputStreams
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
		return parseStream((ImageInputStream) source);
	}

	public ImageReader createReaderInstance(Object extension)
		throws IOException {
			return new PCXImageReader(this);
	}

	/* (non-Javadoc)
	 * @see javax.imageio.spi.IIOServiceProvider#getDescription(java.util.Locale)
	 */
	public String getDescription(Locale locale) {
		return "id-software's Quake2 pcx format";
	}
	
	private boolean parseStream(ImageInputStream stream) {
		byte[] buffer = new byte[PCX.HEADER_SIZE];
		try {
			stream.mark();
			stream.readFully(buffer);
			stream.reset();
			// buffer is copied (readonly) and header checked
			PCX.Header pcx = new PCX.Header(buffer); 
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}


}
