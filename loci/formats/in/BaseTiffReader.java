//
// BaseTiffReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.IOException;
import java.text.*;
import java.util.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * BaseTiffReader is the superclass for file format readers compatible with
 * or derived from the TIFF 6.0 file format.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/BaseTiffReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/BaseTiffReader.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 */
public abstract class BaseTiffReader extends MinimalTiffReader {

  // -- Constants --

  /** EXIF tags. */
  private static final int EXIF_VERSION = 36864;
  private static final int FLASH_PIX_VERSION = 40960;
  private static final int COLOR_SPACE = 40961;
  private static final int COMPONENTS_CONFIGURATION = 37121;
  private static final int COMPRESSED_BITS_PER_PIXEL = 37122;
  private static final int PIXEL_X_DIMENSION = 40962;
  private static final int PIXEL_Y_DIMENSION = 40963;
  private static final int MAKER_NOTE = 37500;
  private static final int USER_COMMENT = 37510;
  private static final int RELATED_SOUND_FILE = 40964;
  private static final int DATE_TIME_ORIGINAL = 36867;
  private static final int DATE_TIME_DIGITIZED = 36868;
  private static final int SUB_SEC_TIME = 37520;
  private static final int SUB_SEC_TIME_ORIGINAL = 37521;
  private static final int SUB_SEC_TIME_DIGITIZED = 37522;
  private static final int EXPOSURE_TIME = 33434;
  private static final int F_NUMBER = 33437;
  private static final int EXPOSURE_PROGRAM = 34850;
  private static final int SPECTRAL_SENSITIVITY = 34852;
  private static final int ISO_SPEED_RATINGS = 34855;
  private static final int OECF = 34856;
  private static final int SHUTTER_SPEED_VALUE = 37377;
  private static final int APERTURE_VALUE = 37378;
  private static final int BRIGHTNESS_VALUE = 37379;
  private static final int EXPOSURE_BIAS_VALUE = 37380;
  private static final int MAX_APERTURE_VALUE = 37381;
  private static final int SUBJECT_DISTANCE = 37382;
  private static final int METERING_MODE = 37383;
  private static final int LIGHT_SOURCE = 37384;
  private static final int FLASH = 37385;
  private static final int FOCAL_LENGTH = 37386;
  private static final int FLASH_ENERGY = 41483;
  private static final int SPATIAL_FREQUENCY_RESPONSE = 41484;
  private static final int FOCAL_PLANE_X_RESOLUTION = 41486;
  private static final int FOCAL_PLANE_Y_RESOLUTION = 41487;
  private static final int FOCAL_PLANE_RESOLUTION_UNIT = 41488;
  private static final int SUBJECT_LOCATION = 41492;
  private static final int EXPOSURE_INDEX = 41493;
  private static final int SENSING_METHOD = 41495;
  private static final int FILE_SOURCE = 41728;
  private static final int SCENE_TYPE = 41729;
  private static final int CFA_PATTERN = 41730;

  // -- Constructors --

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String suffix) { super(name, suffix); }

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String[] suffixes) {
    super(name, suffixes);
  }

  // -- Internal BaseTiffReader API methods --

  /** Populates the metadata hashtable and metadata store. */
  protected void initMetadata() throws FormatException, IOException {
    initStandardMetadata();
    initMetadataStore();
  }

  /**
   * Parses standard metadata.
   *
   * NOTE: Absolutely <b>no</b> calls to the metadata store should be made in
   * this method or methods that override this method. Data <b>will</b> be
   * overwritten if you do so.
   */
  protected void initStandardMetadata() throws FormatException, IOException {
    Hashtable ifd = ifds[0];
    put("ImageWidth", ifd, TiffTools.IMAGE_WIDTH);
    put("ImageLength", ifd, TiffTools.IMAGE_LENGTH);
    put("BitsPerSample", ifd, TiffTools.BITS_PER_SAMPLE);

    // retrieve EXIF values, if available

    long exifOffset = TiffTools.getIFDLongValue(ifd, TiffTools.EXIF, false, 0);
    if (exifOffset != 0) {
      Hashtable exif = TiffTools.getIFD(in, 1, exifOffset);

      Enumeration keys = exif.keys();
      while (keys.hasMoreElements()) {
        int key = ((Integer) keys.nextElement()).intValue();
        addMeta(getExifTagName(key), exif.get(new Integer(key)));
      }
    }

    int comp = TiffTools.getCompression(ifd);
    String compression = null;
    switch (comp) {
      case TiffTools.UNCOMPRESSED:
        compression = "None";
        break;
      case TiffTools.CCITT_1D:
        compression = "CCITT Group 3 1-Dimensional Modified Huffman";
        break;
      case TiffTools.GROUP_3_FAX:
        compression = "CCITT T.4 bilevel encoding";
        break;
      case TiffTools.GROUP_4_FAX:
        compression = "CCITT T.6 bilevel encoding";
        break;
      case TiffTools.LZW:
        compression = "LZW";
        break;
      case TiffTools.JPEG:
        compression = "JPEG";
        break;
      case TiffTools.PACK_BITS:
        compression = "PackBits";
        break;
    }
    put("Compression", compression);

    int photo = TiffTools.getPhotometricInterpretation(ifd);
    String photoInterp = null;
    String metaDataPhotoInterp = null;

    switch (photo) {
      case TiffTools.WHITE_IS_ZERO:
        photoInterp = "WhiteIsZero";
        metaDataPhotoInterp = "Monochrome";
        break;
      case TiffTools.BLACK_IS_ZERO:
        photoInterp = "BlackIsZero";
        metaDataPhotoInterp = "Monochrome";
        break;
      case TiffTools.RGB:
        photoInterp = "RGB";
        metaDataPhotoInterp = "RGB";
        break;
      case TiffTools.RGB_PALETTE:
        photoInterp = "Palette";
        metaDataPhotoInterp = "Monochrome";
        break;
      case TiffTools.TRANSPARENCY_MASK:
        photoInterp = "Transparency Mask";
        metaDataPhotoInterp = "RGB";
        break;
      case TiffTools.CMYK:
        photoInterp = "CMYK";
        metaDataPhotoInterp = "CMYK";
        break;
      case TiffTools.Y_CB_CR:
        photoInterp = "YCbCr";
        metaDataPhotoInterp = "RGB";
        break;
      case TiffTools.CIE_LAB:
        photoInterp = "CIELAB";
        metaDataPhotoInterp = "RGB";
        break;
      case TiffTools.CFA_ARRAY:
        photoInterp = "Color Filter Array";
        metaDataPhotoInterp = "RGB";
        break;
    }
    put("PhotometricInterpretation", photoInterp);
    put("MetaDataPhotometricInterpretation", metaDataPhotoInterp);

    putInt("CellWidth", ifd, TiffTools.CELL_WIDTH);
    putInt("CellLength", ifd, TiffTools.CELL_LENGTH);

    int or = TiffTools.getIFDIntValue(ifd, TiffTools.ORIENTATION);

    // adjust the width and height if necessary
    if (or == 8) {
      put("ImageWidth", ifd, TiffTools.IMAGE_LENGTH);
      put("ImageLength", ifd, TiffTools.IMAGE_WIDTH);
    }

    String orientation = null;
    // there is no case 0
    switch (or) {
      case 1:
        orientation = "1st row -> top; 1st column -> left";
        break;
      case 2:
        orientation = "1st row -> top; 1st column -> right";
        break;
      case 3:
        orientation = "1st row -> bottom; 1st column -> right";
        break;
      case 4:
        orientation = "1st row -> bottom; 1st column -> left";
        break;
      case 5:
        orientation = "1st row -> left; 1st column -> top";
        break;
      case 6:
        orientation = "1st row -> right; 1st column -> top";
        break;
      case 7:
        orientation = "1st row -> right; 1st column -> bottom";
        break;
      case 8:
        orientation = "1st row -> left; 1st column -> bottom";
        break;
    }
    put("Orientation", orientation);
    putInt("SamplesPerPixel", ifd, TiffTools.SAMPLES_PER_PIXEL);

    put("Software", ifd, TiffTools.SOFTWARE);
    put("Instrument Make", ifd, TiffTools.MAKE);
    put("Instrument Model", ifd, TiffTools.MODEL);
    put("Document Name", ifd, TiffTools.DOCUMENT_NAME);
    put("DateTime", ifd, TiffTools.DATE_TIME);
    put("Artist", ifd, TiffTools.ARTIST);

    put("HostComputer", ifd, TiffTools.HOST_COMPUTER);
    put("Copyright", ifd, TiffTools.COPYRIGHT);

    put("NewSubfileType", ifd, TiffTools.NEW_SUBFILE_TYPE);

    int thresh = TiffTools.getIFDIntValue(ifd, TiffTools.THRESHHOLDING);
    String threshholding = null;
    switch (thresh) {
      case 1:
        threshholding = "No dithering or halftoning";
        break;
      case 2:
        threshholding = "Ordered dithering or halftoning";
        break;
      case 3:
        threshholding = "Randomized error diffusion";
        break;
    }
    put("Threshholding", threshholding);

    int fill = TiffTools.getIFDIntValue(ifd, TiffTools.FILL_ORDER);
    String fillOrder = null;
    switch (fill) {
      case 1:
        fillOrder = "Pixels with lower column values are stored " +
          "in the higher order bits of a byte";
        break;
      case 2:
        fillOrder = "Pixels with lower column values are stored " +
          "in the lower order bits of a byte";
        break;
    }
    put("FillOrder", fillOrder);

    putInt("Make", ifd, TiffTools.MAKE);
    putInt("Model", ifd, TiffTools.MODEL);
    putInt("MinSampleValue", ifd, TiffTools.MIN_SAMPLE_VALUE);
    putInt("MaxSampleValue", ifd, TiffTools.MAX_SAMPLE_VALUE);
    putInt("XResolution", ifd, TiffTools.X_RESOLUTION);
    putInt("YResolution", ifd, TiffTools.Y_RESOLUTION);

    int planar = TiffTools.getIFDIntValue(ifd,
      TiffTools.PLANAR_CONFIGURATION);
    String planarConfig = null;
    switch (planar) {
      case 1:
        planarConfig = "Chunky";
        break;
      case 2:
        planarConfig = "Planar";
        break;
    }
    put("PlanarConfiguration", planarConfig);

    putInt("XPosition", ifd, TiffTools.X_POSITION);
    putInt("YPosition", ifd, TiffTools.Y_POSITION);
    putInt("FreeOffsets", ifd, TiffTools.FREE_OFFSETS);
    putInt("FreeByteCounts", ifd, TiffTools.FREE_BYTE_COUNTS);
    putInt("GrayResponseUnit", ifd, TiffTools.GRAY_RESPONSE_UNIT);
    putInt("GrayResponseCurve", ifd, TiffTools.GRAY_RESPONSE_CURVE);
    putInt("T4Options", ifd, TiffTools.T4_OPTIONS);
    putInt("T6Options", ifd, TiffTools.T6_OPTIONS);

    int res = TiffTools.getIFDIntValue(ifd, TiffTools.RESOLUTION_UNIT);
    String resUnit = null;
    switch (res) {
      case 1:
        resUnit = "None";
        break;
      case 2:
        resUnit = "Inch";
        break;
      case 3:
        resUnit = "Centimeter";
        break;
    }
    put("ResolutionUnit", resUnit);

    putInt("PageNumber", ifd, TiffTools.PAGE_NUMBER);
    putInt("TransferFunction", ifd, TiffTools.TRANSFER_FUNCTION);

    int predict = TiffTools.getIFDIntValue(ifd, TiffTools.PREDICTOR);
    String predictor = null;
    switch (predict) {
      case 1:
        predictor = "No prediction scheme";
        break;
      case 2:
        predictor = "Horizontal differencing";
        break;
    }
    put("Predictor", predictor);

    putInt("WhitePoint", ifd, TiffTools.WHITE_POINT);
    putInt("PrimaryChromacities", ifd, TiffTools.PRIMARY_CHROMATICITIES);

    putInt("HalftoneHints", ifd, TiffTools.HALFTONE_HINTS);
    putInt("TileWidth", ifd, TiffTools.TILE_WIDTH);
    putInt("TileLength", ifd, TiffTools.TILE_LENGTH);
    putInt("TileOffsets", ifd, TiffTools.TILE_OFFSETS);
    putInt("TileByteCounts", ifd, TiffTools.TILE_BYTE_COUNTS);

    int ink = TiffTools.getIFDIntValue(ifd, TiffTools.INK_SET);
    String inkSet = null;
    switch (ink) {
      case 1:
        inkSet = "CMYK";
        break;
      case 2:
        inkSet = "Other";
        break;
    }
    put("InkSet", inkSet);

    putInt("InkNames", ifd, TiffTools.INK_NAMES);
    putInt("NumberOfInks", ifd, TiffTools.NUMBER_OF_INKS);
    putInt("DotRange", ifd, TiffTools.DOT_RANGE);
    put("TargetPrinter", ifd, TiffTools.TARGET_PRINTER);
    putInt("ExtraSamples", ifd, TiffTools.EXTRA_SAMPLES);

    int fmt = TiffTools.getIFDIntValue(ifd, TiffTools.SAMPLE_FORMAT);
    String sampleFormat = null;
    switch (fmt) {
      case 1:
        sampleFormat = "unsigned integer";
        break;
      case 2:
        sampleFormat = "two's complement signed integer";
        break;
      case 3:
        sampleFormat = "IEEE floating point";
        break;
      case 4:
        sampleFormat = "undefined";
        break;
    }
    put("SampleFormat", sampleFormat);

    putInt("SMinSampleValue", ifd, TiffTools.S_MIN_SAMPLE_VALUE);
    putInt("SMaxSampleValue", ifd, TiffTools.S_MAX_SAMPLE_VALUE);
    putInt("TransferRange", ifd, TiffTools.TRANSFER_RANGE);

    int jpeg = TiffTools.getIFDIntValue(ifd, TiffTools.JPEG_PROC);
    String jpegProc = null;
    switch (jpeg) {
      case 1:
        jpegProc = "baseline sequential process";
        break;
      case 14:
        jpegProc = "lossless process with Huffman coding";
        break;
    }
    put("JPEGProc", jpegProc);

    putInt("JPEGInterchangeFormat", ifd, TiffTools.JPEG_INTERCHANGE_FORMAT);
    putInt("JPEGRestartInterval", ifd, TiffTools.JPEG_RESTART_INTERVAL);

    putInt("JPEGLosslessPredictors",
      ifd, TiffTools.JPEG_LOSSLESS_PREDICTORS);
    putInt("JPEGPointTransforms", ifd, TiffTools.JPEG_POINT_TRANSFORMS);
    putInt("JPEGQTables", ifd, TiffTools.JPEG_Q_TABLES);
    putInt("JPEGDCTables", ifd, TiffTools.JPEG_DC_TABLES);
    putInt("JPEGACTables", ifd, TiffTools.JPEG_AC_TABLES);
    putInt("YCbCrCoefficients", ifd, TiffTools.Y_CB_CR_COEFFICIENTS);

    int ycbcr = TiffTools.getIFDIntValue(ifd,
      TiffTools.Y_CB_CR_SUB_SAMPLING);
    String subSampling = null;
    switch (ycbcr) {
      case 1:
        subSampling = "chroma image dimensions = luma image dimensions";
        break;
      case 2:
        subSampling = "chroma image dimensions are " +
          "half the luma image dimensions";
        break;
      case 4:
        subSampling = "chroma image dimensions are " +
          "1/4 the luma image dimensions";
        break;
    }
    put("YCbCrSubSampling", subSampling);

    putInt("YCbCrPositioning", ifd, TiffTools.Y_CB_CR_POSITIONING);
    putInt("ReferenceBlackWhite", ifd, TiffTools.REFERENCE_BLACK_WHITE);

    // bits per sample and number of channels
    int[] q = TiffTools.getBitsPerSample(ifd);
    int bps = q[0];
    int numC = q.length;

    // numC isn't set properly if we have an indexed color image, so we need
    // to reset it here

    if (photo == TiffTools.RGB_PALETTE || photo == TiffTools.CFA_ARRAY) {
      numC = 3;
    }

    put("BitsPerSample", bps);
    put("NumberOfChannels", numC);

    // TIFF comment
    String comment = TiffTools.getComment(ifd);
    if (comment != null && !comment.startsWith("<?xml")) {
      // sanitize comment
      comment = comment.replaceAll("\r\n", "\n"); // CR-LF to LF
      comment = comment.replaceAll("\r", "\n"); // CR to LF
      put("Comment", comment);
    }

    int samples = TiffTools.getSamplesPerPixel(ifd);
    core[0].rgb = samples > 1 || photo == TiffTools.RGB;
    core[0].interleaved = false;
    core[0].littleEndian = TiffTools.isLittleEndian(ifds[0]);

    core[0].sizeX = (int) TiffTools.getImageWidth(ifds[0]);
    core[0].sizeY = (int) TiffTools.getImageLength(ifds[0]);
    core[0].sizeZ = 1;
    core[0].sizeC = isRGB() ? samples : 1;
    core[0].sizeT = ifds.length;
    core[0].metadataComplete = true;
    core[0].indexed = photo == TiffTools.RGB_PALETTE &&
      (get8BitLookupTable() != null || get16BitLookupTable() != null);
    if (isIndexed()) {
      core[0].sizeC = 1;
      core[0].rgb = false;
    }
    if (getSizeC() == 1 && !isIndexed()) core[0].rgb = false;
    core[0].falseColor = false;
    core[0].currentOrder = "XYCZT";
    core[0].pixelType = getPixelType(ifds[0]);
  }

  /**
   * Populates the metadata store using the data parsed in
   * {@link #initStandardMetadata()} along with some further parsing done in
   * the method itself.
   *
   * All calls to the active <code>MetadataStore</code> should be made in this
   * method and <b>only</b> in this method. This is especially important for
   * sub-classes that override the getters for pixel set array size, etc.
   */
  protected void initMetadataStore() throws FormatException {
    // the metadata store we're working with
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);

    // set the pixel values in the metadata store
    MetadataTools.populatePixels(store, this);

    // populate Experimenter
    String artist = null;
    Object o = TiffTools.getIFDValue(ifds[0], TiffTools.ARTIST);
    if (o instanceof String) artist = (String) o;
    else if (o instanceof String[]) {
      String[] s = (String[]) o;
      for (int i=0; i<s.length; i++) {
        artist += s[i];
        if (i < s.length - 1) artist += "\n";
      }
    }
    if (artist != null) {
      String firstName = null, lastName = null;
      int ndx = artist.indexOf(" ");
      if (ndx < 0) lastName = artist;
      else {
        firstName = artist.substring(0, ndx);
        lastName = artist.substring(ndx + 1);
      }
      String email = (String)
        TiffTools.getIFDValue(ifds[0], TiffTools.HOST_COMPUTER);
      store.setExperimenterFirstName(firstName, 0);
      store.setExperimenterLastName(lastName, 0);
      store.setExperimenterEmail(email, 0);
    }

    // format the creation date to ISO 8061

    String creationDate = getImageCreationDate();
    String date = parseDate(creationDate, "yyyy:MM:dd HH:mm:ss");
    if (date == null) date = parseDate(creationDate, "dd/MM/yyyy HH:mm:ss.SS");
    if (creationDate != null && date == null && debug) {
      debug("Warning: unknown creation date format: " + creationDate);
    }
    creationDate = date;

    // populate Image

    if (creationDate != null) {
      store.setImageCreationDate(creationDate, 0);
    }
    else MetadataTools.setDefaultCreationDate(store, getCurrentFile(), 0);
    store.setImageDescription(TiffTools.getComment(ifds[0]), 0);

    // set the X and Y pixel dimensions

    int resolutionUnit = TiffTools.getIFDIntValue(ifds[0],
      TiffTools.RESOLUTION_UNIT);
    TiffRational xResolution = TiffTools.getIFDRationalValue(ifds[0],
      TiffTools.X_RESOLUTION, false);
    TiffRational yResolution = TiffTools.getIFDRationalValue(ifds[0],
      TiffTools.Y_RESOLUTION, false);
    float pixX = xResolution == null ? 0f : 1 / xResolution.floatValue();
    float pixY = yResolution == null ? 0f : 1 / yResolution.floatValue();

    switch (resolutionUnit) {
      case 2:
        // resolution is expressed in pixels per inch
        pixX /= 0.0254;
        pixY /= 0.0254;
        break;
      case 3:
        // resolution is expressed in pixels per centimeter
        pixX *= 100;
        pixY *= 100;
        break;
    }

    store.setDimensionsPhysicalSizeX(new Float(pixX), 0, 0);
    store.setDimensionsPhysicalSizeY(new Float(pixY), 0, 0);
    store.setDimensionsPhysicalSizeZ(new Float(0), 0, 0);

    // populate StageLabel
    Object x = TiffTools.getIFDValue(ifds[0], TiffTools.X_POSITION);
    Object y = TiffTools.getIFDValue(ifds[0], TiffTools.Y_POSITION);
    Float stageX;
    Float stageY;
    if (x instanceof TiffRational) {
      stageX = x == null ? null : new Float(((TiffRational) x).floatValue());
      stageY = y == null ? null : new Float(((TiffRational) y).floatValue());
    }
    else {
      stageX = x == null ? null : new Float((String) x);
      stageY = y == null ? null : new Float((String) y);
    }
    // populate Instrument
    //String make = (String) TiffTools.getIFDValue(ifd, TiffTools.MAKE);
    //String model = (String) TiffTools.getIFDValue(ifd, TiffTools.MODEL);
    //store.setInstrumentModel(model, 0);
    //store.setInstrumentManufacturer(make, 0);
  }

  /**
   * Retrieves the image creation date.
   * @return the image creation date.
   */
  protected String getImageCreationDate() {
    Object o = TiffTools.getIFDValue(ifds[0], TiffTools.DATE_TIME);
    if (o instanceof String) return (String) o;
    if (o instanceof String[]) return ((String[]) o)[0];
    return null;
  }

  // -- Internal FormatReader API methods - metadata convenience --

  protected void put(String key, Object value) {
    if (value == null) return;
    if (value instanceof String) value = ((String) value).trim();
    addMeta(key, value);
  }

  protected void put(String key, int value) {
    if (value == -1) return; // indicates missing value
    addMeta(key, new Integer(value));
  }

  protected void put(String key, boolean value) {
    put(key, new Boolean(value));
  }
  protected void put(String key, byte value) { put(key, new Byte(value)); }
  protected void put(String key, char value) { put(key, new Character(value)); }
  protected void put(String key, double value) { put(key, new Double(value)); }
  protected void put(String key, float value) { put(key, new Float(value)); }
  protected void put(String key, long value) { put(key, new Long(value)); }
  protected void put(String key, short value) { put(key, new Short(value)); }

  protected void put(String key, Hashtable ifd, int tag) {
    put(key, TiffTools.getIFDValue(ifd, tag));
  }

  protected void putInt(String key, Hashtable ifd, int tag) {
    put(key, TiffTools.getIFDIntValue(ifd, tag));
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("BaseTiffReader.initFile(" + id + ")");
    super.initFile(id);
    initMetadata();
  }

  // -- Helper methods --

  private static String getExifTagName(int tag) {
    switch (tag) {
      case EXIF_VERSION:
        return "EXIF Version";
      case FLASH_PIX_VERSION:
        return "FlashPix Version";
      case COLOR_SPACE:
        return "Color Space";
      case COMPONENTS_CONFIGURATION:
        return "Components Configuration";
      case COMPRESSED_BITS_PER_PIXEL:
        return "Compressed Bits Per Pixel";
      case PIXEL_X_DIMENSION:
        return "Image width";
      case PIXEL_Y_DIMENSION:
        return "Image height";
      case MAKER_NOTE:
        return "Maker Note";
      case USER_COMMENT:
        return "User comment";
      case RELATED_SOUND_FILE:
        return "Related sound file";
      case DATE_TIME_ORIGINAL:
        return "Original date/time";
      case DATE_TIME_DIGITIZED:
        return "Date/time digitized";
      case SUB_SEC_TIME:
        return "Date/time subseconds";
      case SUB_SEC_TIME_ORIGINAL:
        return "Original date/time subseconds";
      case SUB_SEC_TIME_DIGITIZED:
        return "Digitized date/time subseconds";
      case EXPOSURE_TIME:
        return "Exposure time";
      case F_NUMBER:
        return "F Number";
      case EXPOSURE_PROGRAM:
        return "Exposure program";
      case SPECTRAL_SENSITIVITY:
        return "Spectral sensitivity";
      case ISO_SPEED_RATINGS:
        return "ISO speed ratings";
      case OECF:
        return "Optoelectric conversion factor";
      case SHUTTER_SPEED_VALUE:
        return "Shutter speed";
      case APERTURE_VALUE:
        return "Aperture value";
      case BRIGHTNESS_VALUE:
        return "Brightness value";
      case EXPOSURE_BIAS_VALUE:
        return "Exposure Bias value";
      case MAX_APERTURE_VALUE:
        return "Max aperture value";
      case SUBJECT_DISTANCE:
        return "Subject distance";
      case METERING_MODE:
        return "Metering mode";
      case LIGHT_SOURCE:
        return "Light source";
      case FLASH:
        return "Flash";
      case FOCAL_LENGTH:
        return "Focal length";
      case FLASH_ENERGY:
        return "Flash energy";
      case SPATIAL_FREQUENCY_RESPONSE:
        return "Spatial frequency response";
      case FOCAL_PLANE_X_RESOLUTION:
        return "Focal plane X resolution";
      case FOCAL_PLANE_Y_RESOLUTION:
        return "Focal plane Y resolution";
      case FOCAL_PLANE_RESOLUTION_UNIT:
        return "Focal plane resolution unit";
      case SUBJECT_LOCATION:
        return "Subject location";
      case EXPOSURE_INDEX:
        return "Exposure index";
      case SENSING_METHOD:
        return "Sensing method";
      case FILE_SOURCE:
        return "File source";
      case SCENE_TYPE:
        return "Scene type";
      case CFA_PATTERN:
        return "CFA Pattern";
    }
    return null;
  }

  private static String parseDate(String date, String format) {
    if (date == null) return null;
    try {
      SimpleDateFormat parse = new SimpleDateFormat(format);
      Date d = parse.parse(date, new ParsePosition(0));
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      return sdf.format(d);
    }
    catch (NullPointerException exc) {
      return null;
    }
  }

}
