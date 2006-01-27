// 
// BaseTiffReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Hashtable;

/**
 * BaseTiffReader is the superclass for file format readers compatible with
 * or derived from the TIFF 6.0 file format.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at cs.wisc.edu
 */

public abstract class BaseTiffReader extends FormatReader {

  // -- Fields --

  /** Random access file for the current TIFF. */
  protected RandomAccessFile in;

  /** List of IFDs for the current TIFF. */
  protected Hashtable[] ifds;

  /** Number of images in the current TIFF stack. */
  protected int numImages;

  
  // -- Constructors --

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String suffix) { super(name, suffix); }
  public BaseTiffReader(String name, String[] suffixes) { 
    super(name, suffixes); 
  }

  // -- BaseTiffReader API methods --

  /** Gets the dimensions of the given (possibly multi-page) TIFF file. */
  public int[] getTiffDimensions(String id) 
    throws FormatException, IOException 
  {
    if (!id.equals(currentId)) initFile(id);
    if (ifds == null || ifds.length == 0) return null;
    return new int[] {
      TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_WIDTH, false, -1),
      TiffTools.getIFDIntValue(ifds[0], TiffTools.IMAGE_LENGTH, false, -1),
      numImages
    };	      
  }
  
  // -- Internal BaseTiffReader API methods --

  /** Populates the metadata hashtable and OME root node. */
  protected void initMetadata() {
    initStandardMetadata();
    initOMEMetadata();
  }	  
 
  /** Parses standard metadata. */
  protected void initStandardMetadata() {
    Hashtable ifd = ifds[0];
    put("ImageWidth", ifd, TiffTools.IMAGE_WIDTH);
    put("ImageLength", ifd, TiffTools.IMAGE_LENGTH);
    put("BitsPerSample", ifd, TiffTools.BITS_PER_SAMPLE);

    int comp = TiffTools.getIFDIntValue(ifd, TiffTools.COMPRESSION);
    String compression = null;
    switch(comp) {
      case TiffTools.UNCOMPRESSED:
        compression = "None"; break;
      case TiffTools.CCITT_1D:
	compression = "CCITT Group 3 1-Dimensional Modified Huffman"; break;
      case TiffTools.GROUP_3_FAX:
        compression = "CCITT T.4 bilevel encoding"; break;
      case TiffTools.GROUP_4_FAX:
        compression = "CCITT T.6 bilevel encoding"; break;
      case TiffTools.LZW:
        compression = "LZW"; break;
      case TiffTools.JPEG:
        compression = "JPEG"; break;
      case TiffTools.PACK_BITS:
        compression = "PackBits"; break;
    }
    put("Compression", compression);

    int photo = TiffTools.getIFDIntValue(ifd,
      TiffTools.PHOTOMETRIC_INTERPRETATION);
    String photoInterp = null;
    switch (photo) {
      case TiffTools.WHITE_IS_ZERO:
        photoInterp = "WhiteIsZero"; break;
      case TiffTools.BLACK_IS_ZERO:
        photoInterp = "BlackIsZero"; break;
      case TiffTools.RGB:
        photoInterp = "RGB"; break;
      case TiffTools.RGB_PALETTE:
        photoInterp = "Palette"; break;
      case TiffTools.TRANSPARENCY_MASK:
        photoInterp = "Transparency Mask"; break;
      case TiffTools.CMYK:
        photoInterp = "CMYK"; break;
      case TiffTools.Y_CB_CR:
        photoInterp = "YCbCr"; break;
      case TiffTools.CIE_LAB:
        photoInterp = "CIELAB"; break;
    }
    put("PhotometricInterpretation", photoInterp);

    putInt("CellWidth", ifd, TiffTools.CELL_WIDTH);
    putInt("CellLength", ifd, TiffTools.CELL_LENGTH);

    int or = TiffTools.getIFDIntValue(ifd, TiffTools.ORIENTATION);
    String orientation = null;
    // there is no case 0
    switch (or) {
      case 1: orientation = "1st row -> top; 1st column -> left"; break;
      case 2: orientation = "1st row -> top; 1st column -> right"; break;
      case 3: orientation = "1st row -> bottom; 1st column -> right"; break;
      case 4: orientation = "1st row -> bottom; 1st column -> left"; break;
      case 5: orientation = "1st row -> left; 1st column -> top"; break;
      case 6: orientation = "1st row -> right; 1st column -> top"; break;
      case 7: orientation = "1st row -> right; 1st column -> bottom"; break;
      case 8: orientation = "1st row -> left; 1st column -> bottom"; break;
    }
    put("Orientation", orientation);
    putInt("SamplesPerPixel", ifd, TiffTools.SAMPLES_PER_PIXEL);

    put("Software", ifd, TiffTools.SOFTWARE);
    put("DateTime", ifd, TiffTools.DATE_TIME);
    put("Artist", ifd, TiffTools.ARTIST);

    put("HostComputer", ifd, TiffTools.HOST_COMPUTER);
    put("Copyright", ifd, TiffTools.COPYRIGHT);

    put("NewSubfileType", ifd, TiffTools.NEW_SUBFILE_TYPE);

    int thresh = TiffTools.getIFDIntValue(ifd, TiffTools.THRESHHOLDING);
    String threshholding = null;
    switch (thresh) {
      case 1: threshholding = "No dithering or halftoning"; break;
      case 2: threshholding = "Ordered dithering or halftoning"; break;
      case 3: threshholding = "Randomized error diffusion"; break;
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
      case 1: planarConfig = "Chunky"; break;
      case 2: planarConfig = "Planar"; break;
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
      case 1: resUnit = "None"; break;
      case 2: resUnit = "Inch"; break;
      case 3: resUnit = "Centimeter"; break;
    }
    put("ResolutionUnit", resUnit);

    putInt("PageNumber", ifd, TiffTools.PAGE_NUMBER);
    putInt("TransferFunction", ifd, TiffTools.TRANSFER_FUNCTION);

    int predict = TiffTools.getIFDIntValue(ifd, TiffTools.PREDICTOR);
    String predictor = null;
    switch (predict) {
      case 1: predictor = "No prediction scheme"; break;
      case 2: predictor = "Horizontal differencing"; break;
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
      case 1: inkSet = "CMYK"; break;
      case 2: inkSet = "Other"; break;
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
      case 1: sampleFormat = "unsigned integer"; break;
      case 2: sampleFormat = "two's complement signed integer"; break;
      case 3: sampleFormat = "IEEE floating point"; break;
      case 4: sampleFormat = "undefined"; break;
    }
    put("SampleFormat", sampleFormat);

    putInt("SMinSampleValue", ifd, TiffTools.S_MIN_SAMPLE_VALUE);
    putInt("SMaxSampleValue", ifd, TiffTools.S_MAX_SAMPLE_VALUE);
    putInt("TransferRange", ifd, TiffTools.TRANSFER_RANGE);

    int jpeg = TiffTools.getIFDIntValue(ifd, TiffTools.JPEG_PROC);
    String jpegProc = null;
    switch (jpeg) {
      case 1: jpegProc = "baseline sequential process"; break;
      case 14: jpegProc = "lossless process with Huffman coding"; break;
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
  }	  
  
  /** Parses OME-XML metadata. */
  protected void initOMEMetadata() {
    final String unknown = "unknown";
    Hashtable ifd = ifds[0];
    try {
      if (ome == null) return; // OME-XML functionality is not available

      OMETools.setAttribute(ome, "Pixels", "SizeX", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.IMAGE_WIDTH));
      OMETools.setAttribute(ome, "Pixels", "SizeY", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.IMAGE_LENGTH));
      OMETools.setAttribute(ome, "Pixels", "SizeZ", "" + 1);
      OMETools.setAttribute(ome, "Pixels", "SizeT", "" + ifds.length);
      OMETools.setAttribute(ome, "Pixels", "SizeC", "" + 1);

      boolean little = TiffTools.isLittleEndian(ifd);
      OMETools.setAttribute(ome,
        "Pixels", "BigEndian", little ? "false" : "true");

      OMETools.setAttribute(ome, "Experimenter", "FirstName", "" +
        TiffTools.getIFDValue(ifd, TiffTools.ARTIST, false, String.class));
      OMETools.setAttribute(ome, "Experimenter", "LastName", "" +
        TiffTools.getIFDValue(ifd, TiffTools.ARTIST, false, String.class));

      String email = TiffTools.getIFDValue(ifd, TiffTools.ARTIST, false,
        String.class) + "@" + TiffTools.getIFDValue(ifd,
        TiffTools.HOST_COMPUTER, false, String.class);

      OMETools.setAttribute(ome, "Experimenter", "Email", email);
      OMETools.setAttribute(ome, "Group", "Name", "OME");
      OMETools.setAttribute(ome, "Image", "Description", "" +
        TiffTools.getIFDValue(ifd, TiffTools.IMAGE_DESCRIPTION,
        false, String.class));

      OMETools.setAttribute(ome, "Image", "PixelSizeX", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.CELL_WIDTH, false, 0));
      OMETools.setAttribute(ome, "Image", "PixelSizeY", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.CELL_LENGTH, false, 0));
      OMETools.setAttribute(ome, "Image", "PixelSizeZ", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.ORIENTATION, false, 0));
      OMETools.setAttribute(ome, "Image", "Created", "" +
        TiffTools.getIFDValue(ifd, TiffTools.DATE_TIME, false, String.class));

      int sample = TiffTools.getIFDIntValue(ifd, TiffTools.SAMPLE_FORMAT);
      String pixelType;
      switch (sample) {
        case 1: pixelType = "int"; break;
        case 2: pixelType = "Uint"; break;
        case 3: pixelType = "float"; break;
        default: pixelType = unknown;
      }
      if (pixelType.indexOf("int") >= 0) { // int or Uint
        int bps = TiffTools.getIFDIntValue(ifd,
          TiffTools.BITS_PER_SAMPLE);
        pixelType += bps;
      }
      OMETools.setAttribute(ome, "Image", "PixelType", pixelType);

      OMETools.setAttribute(ome, "ChannelInfo", "SamplesPerPixel", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.SAMPLES_PER_PIXEL));

      int photoInterp2 = TiffTools.getIFDIntValue(ifd,
        TiffTools.PHOTOMETRIC_INTERPRETATION, true, 0);
      String photo2;
      switch (photoInterp2) {
        case 0: photo2 = "monochrome"; break;
        case 1: photo2 = "monochrome"; break;
        case 2: photo2 = "RGB"; break;
        case 3: photo2 = "monochrome"; break;
        case 4: photo2 = "RGB"; break;
        default: photo2 = unknown;
      }
      OMETools.setAttribute(ome, "ChannelInfo",
        "PhotometricInterpretation", photo2);

      OMETools.setAttribute(ome, "StageLabel", "X", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.X_POSITION));
      OMETools.setAttribute(ome, "StageLabel", "Y", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.Y_POSITION));

      OMETools.setAttribute(ome, "Instrument", "Model", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.MODEL));
      OMETools.setAttribute(ome, "Instrument", "SerialNumber", "" +
        TiffTools.getIFDIntValue(ifd, TiffTools.MAKE));
    }
    catch (FormatException exc) { exc.printStackTrace(); }
  }	  
  
  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for an IPLab file. */
  public boolean isThisType(byte[] block) {
    return TiffTools.isValidHeader(block);
  }

  /** Determines the number of images in the given IPLab file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);	  
    return numImages;
  }

  /** Obtains the specified image from the given IPLab file. */
  public Image open(String id, int no) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= numImages) {
      throw new FormatException("Invalid image number: " + no);
    }

    return TiffTools.getImage(ifds[no], in);
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    if (in != null) in.close();
    in = null;
    currentId = null;
  }

  /** Initializes the given IPLab file. */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    in = new RandomAccessFile(id, "r");
    ifds = TiffTools.getIFDs(in);
    if (ifds == null) throw new FormatException("No IFDs found");
    numImages = ifds.length;
    initMetadata();
  }

  // -- Helper methods --

  protected void put(String key, Object value) {
    if (value == null) return;
    metadata.put(key, value);
  }

  protected void put(String key, int value) {
    if (value == -1) return; // indicates missing value
      metadata.put(key, new Integer(value));
    }

  protected void put(String key, boolean value) {
    put(key, new Boolean(value));
  }
  protected void put(String key, byte value) { put(key, new Byte(value)); }
  protected void put(String key, char value) {
    put(key, new Character(value));
  }
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
}
