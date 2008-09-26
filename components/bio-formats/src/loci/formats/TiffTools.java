//
// TiffTools.java
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

package loci.formats;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import loci.formats.codec.*;

/**
 * A utility class for manipulating TIFF files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/TiffTools.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/TiffTools.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Eric Kjellman egkjellman at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 * @author Chris Allan callan at blackcat.ca
 */
public final class TiffTools {

  // -- Constants --

  private static final boolean DEBUG = false;

  /** The number of bytes in each IFD entry. */
  public static final int BYTES_PER_ENTRY = 12;

  /** The number of bytes in each IFD entry of a BigTIFF file. */
  public static final int BIG_TIFF_BYTES_PER_ENTRY = 20;

  // non-IFD tags (for internal use)
  public static final int LITTLE_ENDIAN = 0;
  public static final int BIG_TIFF = 1;

  // IFD types
  public static final int BYTE = 1;
  public static final int ASCII = 2;
  public static final int SHORT = 3;
  public static final int LONG = 4;
  public static final int RATIONAL = 5;
  public static final int SBYTE = 6;
  public static final int UNDEFINED = 7;
  public static final int SSHORT = 8;
  public static final int SLONG = 9;
  public static final int SRATIONAL = 10;
  public static final int FLOAT = 11;
  public static final int DOUBLE = 12;
  public static final int LONG8 = 16;
  public static final int SLONG8 = 17;
  public static final int IFD8 = 18;

  public static final int[] BYTES_PER_ELEMENT = {
    -1, // invalid type
    1, // BYTE
    1, // ASCII
    2, // SHORT
    4, // LONG
    8, // RATIONAL
    1, // SBYTE
    1, // UNDEFINED
    2, // SSHORT
    4, // SLONG
    8, // SRATIONAL
    4, // FLOAT
    8, // DOUBLE
    -1, // invalid type
    -1, // invalid type
    -1, // invalid type
    8, // LONG8
    8, // SLONG8
    8 // IFD8
  };

  // IFD tags
  public static final int NEW_SUBFILE_TYPE = 254;
  public static final int SUBFILE_TYPE = 255;
  public static final int IMAGE_WIDTH = 256;
  public static final int IMAGE_LENGTH = 257;
  public static final int BITS_PER_SAMPLE = 258;
  public static final int COMPRESSION = 259;
  public static final int PHOTOMETRIC_INTERPRETATION = 262;
  public static final int THRESHHOLDING = 263;
  public static final int CELL_WIDTH = 264;
  public static final int CELL_LENGTH = 265;
  public static final int FILL_ORDER = 266;
  public static final int DOCUMENT_NAME = 269;
  public static final int IMAGE_DESCRIPTION = 270;
  public static final int MAKE = 271;
  public static final int MODEL = 272;
  public static final int STRIP_OFFSETS = 273;
  public static final int ORIENTATION = 274;
  public static final int SAMPLES_PER_PIXEL = 277;
  public static final int ROWS_PER_STRIP = 278;
  public static final int STRIP_BYTE_COUNTS = 279;
  public static final int MIN_SAMPLE_VALUE = 280;
  public static final int MAX_SAMPLE_VALUE = 281;
  public static final int X_RESOLUTION = 282;
  public static final int Y_RESOLUTION = 283;
  public static final int PLANAR_CONFIGURATION = 284;
  public static final int PAGE_NAME = 285;
  public static final int X_POSITION = 286;
  public static final int Y_POSITION = 287;
  public static final int FREE_OFFSETS = 288;
  public static final int FREE_BYTE_COUNTS = 289;
  public static final int GRAY_RESPONSE_UNIT = 290;
  public static final int GRAY_RESPONSE_CURVE = 291;
  public static final int T4_OPTIONS = 292;
  public static final int T6_OPTIONS = 293;
  public static final int RESOLUTION_UNIT = 296;
  public static final int PAGE_NUMBER = 297;
  public static final int TRANSFER_FUNCTION = 301;
  public static final int SOFTWARE = 305;
  public static final int DATE_TIME = 306;
  public static final int ARTIST = 315;
  public static final int HOST_COMPUTER = 316;
  public static final int PREDICTOR = 317;
  public static final int WHITE_POINT = 318;
  public static final int PRIMARY_CHROMATICITIES = 319;
  public static final int COLOR_MAP = 320;
  public static final int HALFTONE_HINTS = 321;
  public static final int TILE_WIDTH = 322;
  public static final int TILE_LENGTH = 323;
  public static final int TILE_OFFSETS = 324;
  public static final int TILE_BYTE_COUNTS = 325;
  public static final int INK_SET = 332;
  public static final int INK_NAMES = 333;
  public static final int NUMBER_OF_INKS = 334;
  public static final int DOT_RANGE = 336;
  public static final int TARGET_PRINTER = 337;
  public static final int EXTRA_SAMPLES = 338;
  public static final int SAMPLE_FORMAT = 339;
  public static final int S_MIN_SAMPLE_VALUE = 340;
  public static final int S_MAX_SAMPLE_VALUE = 341;
  public static final int TRANSFER_RANGE = 342;
  public static final int JPEG_TABLES = 347;
  public static final int JPEG_PROC = 512;
  public static final int JPEG_INTERCHANGE_FORMAT = 513;
  public static final int JPEG_INTERCHANGE_FORMAT_LENGTH = 514;
  public static final int JPEG_RESTART_INTERVAL = 515;
  public static final int JPEG_LOSSLESS_PREDICTORS = 517;
  public static final int JPEG_POINT_TRANSFORMS = 518;
  public static final int JPEG_Q_TABLES = 519;
  public static final int JPEG_DC_TABLES = 520;
  public static final int JPEG_AC_TABLES = 521;
  public static final int Y_CB_CR_COEFFICIENTS = 529;
  public static final int Y_CB_CR_SUB_SAMPLING = 530;
  public static final int Y_CB_CR_POSITIONING = 531;
  public static final int REFERENCE_BLACK_WHITE = 532;
  public static final int COPYRIGHT = 33432;
  public static final int EXIF = 34665;

  // compression types
  public static final int UNCOMPRESSED = 1;
  public static final int CCITT_1D = 2;
  public static final int GROUP_3_FAX = 3;
  public static final int GROUP_4_FAX = 4;
  public static final int LZW = 5;
  //public static final int JPEG = 6;
  public static final int JPEG = 7;
  public static final int PACK_BITS = 32773;
  public static final int PROPRIETARY_DEFLATE = 32946;
  public static final int DEFLATE = 8;
  public static final int THUNDERSCAN = 32809;
  public static final int JPEG_2000 = 33003;
  public static final int ALT_JPEG = 33007;
  public static final int NIKON = 34713;
  public static final int LURAWAVE = 65535;

  // photometric interpretation types
  public static final int WHITE_IS_ZERO = 0;
  public static final int BLACK_IS_ZERO = 1;
  public static final int RGB = 2;
  public static final int RGB_PALETTE = 3;
  public static final int TRANSPARENCY_MASK = 4;
  public static final int CMYK = 5;
  public static final int Y_CB_CR = 6;
  public static final int CIE_LAB = 8;
  public static final int CFA_ARRAY = 32803;

  // TIFF header constants
  public static final int MAGIC_NUMBER = 42;
  public static final int BIG_TIFF_MAGIC_NUMBER = 43;
  public static final int LITTLE = 0x49;
  public static final int BIG = 0x4d;

  // -- Constructor --

  private TiffTools() { }

  // -- TiffTools API methods --

  /**
   * Tests the given data block to see if it represents
   * the first few bytes of a TIFF file.
   */
  public static boolean isValidHeader(byte[] block) {
    return checkHeader(block) != null;
  }

  /**
   * Tests the given stream to see if it represents
   * a TIFF file.
   */
  public static boolean isValidHeader(RandomAccessStream stream) {
    try {
      return checkHeader(stream) != null;
    }
    catch (IOException e) {
      return false;
    }
  }

  /**
   * Checks the TIFF header.
   * @return true if little-endian,
   *         false if big-endian,
   *         or null if not a TIFF.
   */
  public static Boolean checkHeader(byte[] block) {
    try {
      RandomAccessStream s = new RandomAccessStream(block);
      Boolean result = checkHeader(s);
      s.close();
      return result;
    }
    catch (IOException e) {
      return null;
    }
  }

  /**
   * Checks the TIFF header.
   * @return true if little-endian,
   *         false if big-endian,
   *         or null if not a TIFF.
   */
  public static Boolean checkHeader(RandomAccessStream stream)
    throws IOException
  {
    if (stream.length() < 4) return null;

    // byte order must be II or MM
    stream.seek(0);
    int endianOne = stream.read();
    int endianTwo = stream.read();
    boolean littleEndian = endianOne == LITTLE && endianTwo == LITTLE; // II
    boolean bigEndian = endianOne == BIG && endianTwo == BIG; // MM
    if (!littleEndian && !bigEndian) return null;

    // check magic number (42)
    stream.order(littleEndian);
    short magic = stream.readShort();
    if (magic != MAGIC_NUMBER && magic != BIG_TIFF_MAGIC_NUMBER) return null;

    return new Boolean(littleEndian);
  }

  /** Gets whether this is a BigTIFF IFD. */
  public static boolean isBigTiff(Hashtable ifd) throws FormatException {
    return ((Boolean)
      getIFDValue(ifd, BIG_TIFF, false, Boolean.class)).booleanValue();
  }

  /** Gets whether the TIFF information in the given IFD is little-endian. */
  public static boolean isLittleEndian(Hashtable ifd) throws FormatException {
    return ((Boolean)
      getIFDValue(ifd, LITTLE_ENDIAN, true, Boolean.class)).booleanValue();
  }

  // --------------------------- Reading TIFF files ---------------------------

  // -- IFD parsing methods --

  /**
   * Gets all IFDs within the given TIFF file, or null
   * if the given file is not a valid TIFF file.
   */
  public static Hashtable[] getIFDs(RandomAccessStream in) throws IOException {
    // check TIFF header
    Boolean result = checkHeader(in);
    if (result == null) return null;

    in.seek(2);
    boolean bigTiff = in.readShort() == BIG_TIFF_MAGIC_NUMBER;

    long offset = getFirstOffset(in, bigTiff);

    // compute maximum possible number of IFDs, for loop safety
    // each IFD must have at least one directory entry, which means that
    // each IFD must be at least 2 + 12 + 4 = 18 bytes in length
    long ifdMax = (in.length() - 8) / 18;

    // read in IFDs
    Vector v = new Vector();
    for (long ifdNum=0; ifdNum<ifdMax; ifdNum++) {
      Hashtable ifd = getIFD(in, ifdNum, offset, bigTiff);
      if (ifd == null || ifd.size() <= 1) break;
      v.add(ifd);
      offset = bigTiff ? in.readLong() : (long) (in.readInt() & 0xffffffffL);
      if (offset <= 0 || offset >= in.length()) break;
    }

    Hashtable[] ifds = new Hashtable[v.size()];
    v.copyInto(ifds);
    return ifds;
  }

  /**
   * Gets the first IFD within the given TIFF file, or null
   * if the given file is not a valid TIFF file.
   */
  public static Hashtable getFirstIFD(RandomAccessStream in) throws IOException
  {
    // check TIFF header
    Boolean result = checkHeader(in);
    if (result == null) return null;

    in.seek(2);
    boolean bigTiff = in.readShort() == BIG_TIFF_MAGIC_NUMBER;

    long offset = getFirstOffset(in, bigTiff);

    Hashtable ifd = getIFD(in, 0, offset, bigTiff);
    ifd.put(new Integer(BIG_TIFF), new Boolean(bigTiff));
    return ifd;
  }

  /**
   * Retrieve a given entry from the first IFD in a stream.
   *
   * @param in the stream to retrieve the entry from.
   * @param tag the tag of the entry to be retrieved.
   * @return an object representing the entry's fields.
   * @throws IOException when there is an error accessing the stream <i>in</i>.
   */
  public static TiffIFDEntry getFirstIFDEntry(RandomAccessStream in, int tag)
    throws IOException
  {
    // First lets re-position the file pointer by checking the TIFF header
    Boolean result = checkHeader(in);
    if (result == null) return null;

    in.seek(2);
    boolean bigTiff = in.readShort() == BIG_TIFF_MAGIC_NUMBER;

    // Get the offset of the first IFD
    long offset = getFirstOffset(in, bigTiff);

    // The following loosely resembles the logic of getIFD()...
    in.seek(offset);
    long numEntries = bigTiff ? in.readLong() : in.readShort() & 0xffff;

    for (int i = 0; i < numEntries; i++) {
      in.seek(offset + // The beginning of the IFD
        2 + // The width of the initial numEntries field
        (bigTiff ? BIG_TIFF_BYTES_PER_ENTRY : BYTES_PER_ENTRY) * i);

      int entryTag = in.readShort() & 0xffff;

      // Skip this tag unless it matches the one we want
      if (entryTag != tag) continue;

      // Parse the entry's "Type"
      int entryType = in.readShort() & 0xffff;

      // Parse the entry's "ValueCount"
      int valueCount =
        bigTiff ? (int) (in.readLong() & 0xffffffff) : in.readInt();
      if (valueCount < 0) {
        throw new RuntimeException("Count of '" + valueCount + "' unexpected.");
      }

      // Parse the entry's "ValueOffset"
      long valueOffset = bigTiff ? in.readLong() : in.readInt();

      return new TiffIFDEntry(entryTag, entryType, valueCount, valueOffset);
    }
    throw new UnknownTagException();
  }

  /**
   * Gets offset to the first IFD, or -1 if stream is not TIFF.
   * Assumes the stream is positioned properly (checkHeader just called).
   */
  public static long getFirstOffset(RandomAccessStream in)
    throws IOException
  {
    return getFirstOffset(in, false);
  }

  /**
   * Gets offset to the first IFD, or -1 if stream is not TIFF.
   * Assumes the stream is positioned properly (checkHeader just called).
   *
   * @param bigTiff true if this is a BigTIFF file (8 byte pointers).
   */
  public static long getFirstOffset(RandomAccessStream in, boolean bigTiff)
    throws IOException
  {
    if (bigTiff) in.skipBytes(4);
    return bigTiff ? in.readLong() : in.readInt();
  }

  /** Gets the IFD stored at the given offset. */
  public static Hashtable getIFD(RandomAccessStream in, long ifdNum,
    long offset) throws IOException
  {
    return getIFD(in, ifdNum, offset, false);
  }

  /** Gets the IFD stored at the given offset. */
  public static Hashtable getIFD(RandomAccessStream in,
    long ifdNum, long offset, boolean bigTiff) throws IOException
  {
    Hashtable ifd = new Hashtable();

    // save little-endian flag to internal LITTLE_ENDIAN tag
    ifd.put(new Integer(LITTLE_ENDIAN), new Boolean(in.isLittleEndian()));
    ifd.put(new Integer(BIG_TIFF), new Boolean(bigTiff));

    // read in directory entries for this IFD
    if (DEBUG) {
      debug("getIFDs: seeking IFD #" + ifdNum + " at " + offset);
    }
    in.seek(offset);
    long numEntries = bigTiff ? in.readLong() : in.readShort() & 0xffff;
    if (DEBUG) debug("getIFDs: " + numEntries + " directory entries to read");
    if (numEntries == 0 || numEntries == 1) return ifd;

    int bytesPerEntry = bigTiff ? BIG_TIFF_BYTES_PER_ENTRY : BYTES_PER_ENTRY;
    int baseOffset = bigTiff ? 8 : 2;
    int threshhold = bigTiff ? 8 : 4;

    for (int i=0; i<numEntries; i++) {
      in.seek(offset + baseOffset + bytesPerEntry * i);
      int tag = in.readShort() & 0xffff;
      int type = in.readShort() & 0xffff;
      // BigTIFF case is a slight hack
      int count = bigTiff ? (int) (in.readLong() & 0xffffffff) : in.readInt();

      if (DEBUG) {
        debug("getIFDs: read " + getIFDTagName(tag) +
          " (type=" + getIFDTypeName(type) + "; count=" + count + ")");
      }
      if (count < 0) return null; // invalid data
      Object value = null;

      if (count > threshhold / BYTES_PER_ELEMENT[type]) {
        long pointer = bigTiff ? in.readLong() :
          (long) (in.readInt() & 0xffffffffL);
        in.seek(pointer);
      }

      if (type == BYTE) {
        // 8-bit unsigned integer
        if (count == 1) value = new Short(in.readByte());
        else {
          byte[] bytes = new byte[count];
          in.readFully(bytes);
          // bytes are unsigned, so use shorts
          short[] shorts = new short[count];
          for (int j=0; j<count; j++) shorts[j] = (short) (bytes[j] & 0xff);
          value = shorts;
        }
      }
      else if (type == ASCII) {
        // 8-bit byte that contain a 7-bit ASCII code;
        // the last byte must be NUL (binary zero)
        byte[] ascii = new byte[count];
        in.read(ascii);

        // count number of null terminators
        int nullCount = 0;
        for (int j=0; j<count; j++) {
          if (ascii[j] == 0 || j == count - 1) nullCount++;
        }

        // convert character array to array of strings
        String[] strings = nullCount == 1 ? null : new String[nullCount];
        String s = null;
        int c = 0, ndx = -1;
        for (int j=0; j<count; j++) {
          if (ascii[j] == 0) {
            s = new String(ascii, ndx + 1, j - ndx - 1);
            ndx = j;
          }
          else if (j == count - 1) {
            // handle non-null-terminated strings
            s = new String(ascii, ndx + 1, j - ndx);
          }
          else s = null;
          if (strings != null && s != null) strings[c++] = s;
        }
        value = strings == null ? (Object) s : strings;
      }
      else if (type == SHORT) {
        // 16-bit (2-byte) unsigned integer
        if (count == 1) value = new Integer(in.readShort() & 0xffff);
        else {
          int[] shorts = new int[count];
          for (int j=0; j<count; j++) {
            shorts[j] = in.readShort() & 0xffff;
          }
          value = shorts;
        }
      }
      else if (type == LONG) {
        // 32-bit (4-byte) unsigned integer
        if (count == 1) value = new Long(in.readInt());
        else {
          long[] longs = new long[count];
          for (int j=0; j<count; j++) longs[j] = in.readInt();
          value = longs;
        }
      }
      else if (type == LONG8 || type == SLONG8 || type == IFD8) {
        if (count == 1) value = new Long(in.readLong());
        else {
          long[] longs = new long[count];
          for (int j=0; j<count; j++) longs[j] = in.readLong();
          value = longs;
        }
      }
      else if (type == RATIONAL || type == SRATIONAL) {
        // Two LONGs: the first represents the numerator of a fraction;
        // the second, the denominator
        // Two SLONG's: the first represents the numerator of a fraction,
        // the second the denominator
        if (count == 1) value = new TiffRational(in.readInt(), in.readInt());
        else {
          TiffRational[] rationals = new TiffRational[count];
          for (int j=0; j<count; j++) {
            rationals[j] = new TiffRational(in.readInt(), in.readInt());
          }
          value = rationals;
        }
      }
      else if (type == SBYTE || type == UNDEFINED) {
        // SBYTE: An 8-bit signed (twos-complement) integer
        // UNDEFINED: An 8-bit byte that may contain anything,
        // depending on the definition of the field
        if (count == 1) value = new Byte(in.readByte());
        else {
          byte[] sbytes = new byte[count];
          in.readFully(sbytes);
          value = sbytes;
        }
      }
      else if (type == SSHORT) {
        // A 16-bit (2-byte) signed (twos-complement) integer
        if (count == 1) value = new Short(in.readShort());
        else {
          short[] sshorts = new short[count];
          for (int j=0; j<count; j++) sshorts[j] = in.readShort();
          value = sshorts;
        }
      }
      else if (type == SLONG) {
        // A 32-bit (4-byte) signed (twos-complement) integer
        if (count == 1) value = new Integer(in.readInt());
        else {
          int[] slongs = new int[count];
          for (int j=0; j<count; j++) slongs[j] = in.readInt();
          value = slongs;
        }
      }
      else if (type == FLOAT) {
        // Single precision (4-byte) IEEE format
        if (count == 1) value = new Float(in.readFloat());
        else {
          float[] floats = new float[count];
          for (int j=0; j<count; j++) floats[j] = in.readFloat();
          value = floats;
        }
      }
      else if (type == DOUBLE) {
        // Double precision (8-byte) IEEE format
        if (count == 1) value = new Double(in.readDouble());
        else {
          double[] doubles = new double[count];
          for (int j=0; j<count; j++) {
            doubles[j] = in.readDouble();
          }
          value = doubles;
        }
      }
      if (value != null) ifd.put(new Integer(tag), value);
    }
    in.seek(offset + baseOffset + bytesPerEntry * numEntries);

    return ifd;
  }

  /** Gets the name of the IFD tag encoded by the given number. */
  public static String getIFDTagName(int tag) { return getFieldName(tag); }

  /** Gets the name of the IFD type encoded by the given number. */
  public static String getIFDTypeName(int type) { return getFieldName(type); }

  /**
   * This method uses reflection to scan the values of this class's
   * static fields, returning the first matching field's name. It is
   * probably not very efficient, and is mainly intended for debugging.
   */
  public static String getFieldName(int value) {
    Field[] fields = TiffTools.class.getFields();
    for (int i=0; i<fields.length; i++) {
      try {
        if (fields[i].getInt(null) == value) return fields[i].getName();
      }
      catch (IllegalAccessException exc) { }
      catch (IllegalArgumentException exc) { }
    }
    return "" + value;
  }

  /** Gets the given directory entry value from the specified IFD. */
  public static Object getIFDValue(Hashtable ifd, int tag) {
    return ifd.get(new Integer(tag));
  }

  /**
   * Gets the given directory entry value from the specified IFD,
   * performing some error checking.
   */
  public static Object getIFDValue(Hashtable ifd,
    int tag, boolean checkNull, Class checkClass) throws FormatException
  {
    Object value = ifd.get(new Integer(tag));
    if (checkNull && value == null) {
      throw new FormatException(
        getIFDTagName(tag) + " directory entry not found");
    }
    if (checkClass != null && value != null &&
      !checkClass.isInstance(value))
    {
      // wrap object in array of length 1, if appropriate
      Class cType = checkClass.getComponentType();
      Object array = null;
      if (cType == value.getClass()) {
        array = Array.newInstance(value.getClass(), 1);
        Array.set(array, 0, value);
      }
      if (cType == boolean.class && value instanceof Boolean) {
        array = Array.newInstance(boolean.class, 1);
        Array.setBoolean(array, 0, ((Boolean) value).booleanValue());
      }
      else if (cType == byte.class && value instanceof Byte) {
        array = Array.newInstance(byte.class, 1);
        Array.setByte(array, 0, ((Byte) value).byteValue());
      }
      else if (cType == char.class && value instanceof Character) {
        array = Array.newInstance(char.class, 1);
        Array.setChar(array, 0, ((Character) value).charValue());
      }
      else if (cType == double.class && value instanceof Double) {
        array = Array.newInstance(double.class, 1);
        Array.setDouble(array, 0, ((Double) value).doubleValue());
      }
      else if (cType == float.class && value instanceof Float) {
        array = Array.newInstance(float.class, 1);
        Array.setFloat(array, 0, ((Float) value).floatValue());
      }
      else if (cType == int.class && value instanceof Integer) {
        array = Array.newInstance(int.class, 1);
        Array.setInt(array, 0, ((Integer) value).intValue());
      }
      else if (cType == long.class && value instanceof Long) {
        array = Array.newInstance(long.class, 1);
        Array.setLong(array, 0, ((Long) value).longValue());
      }
      else if (cType == short.class && value instanceof Short) {
        array = Array.newInstance(short.class, 1);
        Array.setShort(array, 0, ((Short) value).shortValue());
      }
      if (array != null) return array;

      throw new FormatException(getIFDTagName(tag) +
        " directory entry is the wrong type (got " +
        value.getClass().getName() + ", expected " + checkClass.getName());
    }
    return value;
  }

  /**
   * Gets the given directory entry value in long format from the
   * specified IFD, performing some error checking.
   */
  public static long getIFDLongValue(Hashtable ifd, int tag,
    boolean checkNull, long defaultValue) throws FormatException
  {
    long value = defaultValue;
    Number number = (Number) getIFDValue(ifd, tag, checkNull, Number.class);
    if (number != null) value = number.longValue();
    return value;
  }

  /**
   * Gets the given directory entry value in int format from the
   * specified IFD, or -1 if the given directory does not exist.
   */
  public static int getIFDIntValue(Hashtable ifd, int tag) {
    int value = -1;
    try {
      value = getIFDIntValue(ifd, tag, false, -1);
    }
    catch (FormatException exc) { }
    return value;
  }

  /**
   * Gets the given directory entry value in int format from the
   * specified IFD, performing some error checking.
   */
  public static int getIFDIntValue(Hashtable ifd, int tag,
    boolean checkNull, int defaultValue) throws FormatException
  {
    int value = defaultValue;
    Number number = (Number) getIFDValue(ifd, tag, checkNull, Number.class);
    if (number != null) value = number.intValue();
    return value;
  }

  /**
   * Gets the given directory entry value in rational format from the
   * specified IFD, performing some error checking.
   */
  public static TiffRational getIFDRationalValue(Hashtable ifd, int tag,
    boolean checkNull) throws FormatException
  {
    return (TiffRational) getIFDValue(ifd, tag, checkNull, TiffRational.class);
  }

  /**
   * Gets the given directory entry values in long format
   * from the specified IFD, performing some error checking.
   */
  public static long[] getIFDLongArray(Hashtable ifd,
    int tag, boolean checkNull) throws FormatException
  {
    Object value = getIFDValue(ifd, tag, checkNull, null);
    long[] results = null;
    if (value instanceof long[]) results = (long[]) value;
    else if (value instanceof Number) {
      results = new long[] {((Number) value).longValue()};
    }
    else if (value instanceof Number[]) {
      Number[] numbers = (Number[]) value;
      results = new long[numbers.length];
      for (int i=0; i<results.length; i++) results[i] = numbers[i].longValue();
    }
    else if (value instanceof int[]) { // convert int[] to long[]
      int[] integers = (int[]) value;
      results = new long[integers.length];
      for (int i=0; i<integers.length; i++) results[i] = integers[i];
    }
    else if (value != null) {
      throw new FormatException(getIFDTagName(tag) +
        " directory entry is the wrong type (got " +
        value.getClass().getName() +
        ", expected Number, long[], Number[] or int[])");
    }
    return results;
  }

  /**
   * Gets the given directory entry values in int format
   * from the specified IFD, performing some error checking.
   */
  public static int[] getIFDIntArray(Hashtable ifd,
    int tag, boolean checkNull) throws FormatException
  {
    Object value = getIFDValue(ifd, tag, checkNull, null);
    int[] results = null;
    if (value instanceof int[]) results = (int[]) value;
    else if (value instanceof Number) {
      results = new int[] {((Number) value).intValue()};
    }
    else if (value instanceof Number[]) {
      Number[] numbers = (Number[]) value;
      results = new int[numbers.length];
      for (int i=0; i<results.length; i++) results[i] = numbers[i].intValue();
    }
    else if (value != null) {
      throw new FormatException(getIFDTagName(tag) +
        " directory entry is the wrong type (got " +
        value.getClass().getName() + ", expected Number, int[] or Number[])");
    }
    return results;
  }

  /**
   * Gets the given directory entry values in short format
   * from the specified IFD, performing some error checking.
   */
  public static short[] getIFDShortArray(Hashtable ifd,
    int tag, boolean checkNull) throws FormatException
  {
    Object value = getIFDValue(ifd, tag, checkNull, null);
    short[] results = null;
    if (value instanceof short[]) results = (short[]) value;
    else if (value instanceof Number) {
      results = new short[] {((Number) value).shortValue()};
    }
    else if (value instanceof Number[]) {
      Number[] numbers = (Number[]) value;
      results = new short[numbers.length];
      for (int i=0; i<results.length; i++) {
        results[i] = numbers[i].shortValue();
      }
    }
    else if (value != null) {
      throw new FormatException(getIFDTagName(tag) +
        " directory entry is the wrong type (got " +
        value.getClass().getName() +
        ", expected Number, short[] or Number[])");
    }
    return results;
  }

  /** Convenience method for obtaining the ImageDescription from an IFD. */
  public static String getComment(Hashtable ifd) {
    if (ifd == null) return null;

    // extract comment
    Object o = TiffTools.getIFDValue(ifd, TiffTools.IMAGE_DESCRIPTION);
    String comment = null;
    if (o instanceof String) comment = (String) o;
    else if (o instanceof String[]) {
      String[] s = (String[]) o;
      if (s.length > 0) comment = s[0];
    }
    else if (o != null) comment = o.toString();

    if (comment != null) {
      // sanitize line feeds
      comment = comment.replaceAll("\r\n", "\n");
      comment = comment.replaceAll("\r", "\n");
    }
    return comment;
  }

  /** Convenience method for obtaining a file's first ImageDescription. */
  public static String getComment(String id)
    throws FormatException, IOException
  {
    // read first IFD
    RandomAccessStream in = new RandomAccessStream(id);
    Hashtable ifd = TiffTools.getFirstIFD(in);
    in.close();
    return getComment(ifd);
  }

  // -- Image reading methods --

  /** Reads the image defined in the given IFD from the specified file. */
  public static byte[][] getSamples(Hashtable ifd, RandomAccessStream in)
    throws FormatException, IOException
  {
    int samplesPerPixel = getSamplesPerPixel(ifd);
    int bpp = getBitsPerSample(ifd)[0];
    while ((bpp % 8) != 0) bpp++;
    bpp /= 8;
    long width = getImageWidth(ifd);
    long length = getImageLength(ifd);
    byte[] b = new byte[(int) (width * length * samplesPerPixel * bpp)];

    getSamples(ifd, in, b);
    byte[][] samples = new byte[samplesPerPixel][(int) (width * length * bpp)];
    for (int i=0; i<samplesPerPixel; i++) {
      System.arraycopy(b, (int) (i*width*length*bpp), samples[i], 0,
        samples[i].length);
    }
    b = null;
    return samples;
  }

  public static byte[] getSamples(Hashtable ifd, RandomAccessStream in,
    byte[] buf) throws FormatException, IOException
  {
    long width = getImageWidth(ifd);
    long length = getImageLength(ifd);
    return getSamples(ifd, in, buf, 0, 0, width, length);
  }

  public static byte[] getSamples(Hashtable ifd, RandomAccessStream in,
    byte[] buf, int x, int y, long width, long height)
    throws FormatException, IOException
  {
    if (DEBUG) debug("parsing IFD entries");

    // get internal non-IFD entries
    boolean littleEndian = isLittleEndian(ifd);
    in.order(littleEndian);

    // get relevant IFD entries
    long imageWidth = getImageWidth(ifd);
    long imageLength = getImageLength(ifd);
    int[] bitsPerSample = getBitsPerSample(ifd);
    int samplesPerPixel = getSamplesPerPixel(ifd);
    int compression = getCompression(ifd);
    int photoInterp = getPhotometricInterpretation(ifd);
    long[] stripOffsets = getStripOffsets(ifd);
    long[] stripByteCounts = getStripByteCounts(ifd);
    long[] rowsPerStripArray = getRowsPerStrip(ifd);

    boolean fakeByteCounts = stripByteCounts == null;
    boolean fakeRPS = rowsPerStripArray == null;
    boolean isTiled = stripOffsets == null ||
      ifd.get(new Integer(TILE_WIDTH)) != null;

    long[] maxes = getIFDLongArray(ifd, MAX_SAMPLE_VALUE, false);
    long maxValue = maxes == null ? 0 : maxes[0];

    if (isTiled) {
      if (stripOffsets == null) {
        stripOffsets = getIFDLongArray(ifd, TILE_OFFSETS, true);
      }
      if (stripByteCounts == null) {
        stripByteCounts = getIFDLongArray(ifd, TILE_BYTE_COUNTS, true);
      }
      rowsPerStripArray = new long[] {imageLength};
    }
    else if (fakeByteCounts) {
      // technically speaking, this shouldn't happen (since TIFF writers are
      // required to write the StripByteCounts tag), but we'll support it
      // anyway

      // don't rely on RowsPerStrip, since it's likely that if the file doesn't
      // have the StripByteCounts tag, it also won't have the RowsPerStrip tag
      stripByteCounts = new long[stripOffsets.length];
      if (stripByteCounts.length == 1) {
        stripByteCounts[0] = imageWidth * imageLength * (bitsPerSample[0] / 8);
      }
      else {
        stripByteCounts[0] = stripOffsets[0];
        for (int i=1; i<stripByteCounts.length; i++) {
          stripByteCounts[i] = stripOffsets[i] - stripByteCounts[i-1];
        }
      }
    }

    boolean lastBitsZero = bitsPerSample[bitsPerSample.length - 1] == 0;

    if (fakeRPS && !isTiled) {
      // create a false rowsPerStripArray if one is not present
      // it's sort of a cheap hack, but here's how it's done:
      // RowsPerStrip = stripByteCounts / (imageLength * bitsPerSample)
      // since stripByteCounts and bitsPerSample are arrays, we have to
      // iterate through each item

      rowsPerStripArray = new long[bitsPerSample.length];

      long temp = stripByteCounts[0];
      stripByteCounts = new long[bitsPerSample.length];
      Arrays.fill(stripByteCounts, temp);
      temp = bitsPerSample[0];
      if (temp == 0) temp = 8;
      bitsPerSample = new int[bitsPerSample.length];
      Arrays.fill(bitsPerSample, (int) temp);
      temp = stripOffsets[0];

      // we have two files that reverse the endianness for BitsPerSample,
      // StripOffsets, and StripByteCounts

      if (bitsPerSample[0] > 64) {
        bitsPerSample[0] = DataTools.swap(bitsPerSample[0]);
        stripOffsets[0] = DataTools.swap(stripOffsets[0]);
        stripByteCounts[0] = DataTools.swap(stripByteCounts[0]);
      }

      if (rowsPerStripArray.length == 1 && stripByteCounts[0] !=
        (imageWidth * imageLength * (bitsPerSample[0] / 8)) &&
        compression == UNCOMPRESSED)
      {
        for (int i=0; i<stripByteCounts.length; i++) {
          stripByteCounts[i] =
            imageWidth * imageLength * (bitsPerSample[i] / 8);
          stripOffsets[0] = in.length() - stripByteCounts[0] - 48 * imageWidth;
          if (i != 0) {
            stripOffsets[i] = stripOffsets[i - 1] + stripByteCounts[i];
          }

          in.seek(stripOffsets[i]);
          in.read(buf, (int) (i*imageWidth), (int) imageWidth);
          boolean isZero = true;
          for (int j=0; j<imageWidth; j++) {
            if (buf[(int) (i*imageWidth + j)] != 0) {
              isZero = false;
              break;
            }
          }

          while (isZero) {
            stripOffsets[i] -= imageWidth;
            in.seek(stripOffsets[i]);
            in.read(buf, (int) (i*imageWidth), (int) imageWidth);
            for (int j=0; j<imageWidth; j++) {
              if (buf[(int) (i*imageWidth + j)] != 0) {
                isZero = false;
                stripOffsets[i] -= (stripByteCounts[i] - imageWidth);
                break;
              }
            }
          }
        }
      }

      for (int i=0; i<bitsPerSample.length; i++) {
        // case 1: we're still within bitsPerSample array bounds
        if (i < bitsPerSample.length) {
          if (i == samplesPerPixel) {
            bitsPerSample[i] = 0;
            lastBitsZero = true;
          }

          // remember that the universe collapses when we divide by 0
          if (bitsPerSample[i] != 0) {
            rowsPerStripArray[i] = (long) stripByteCounts[i] /
              (imageWidth * (bitsPerSample[i] / 8));
          }
          else if (bitsPerSample[i] == 0 && i > 0) {
            rowsPerStripArray[i] = (long) stripByteCounts[i] /
              (imageWidth * (bitsPerSample[i - 1] / 8));
            bitsPerSample[i] = bitsPerSample[i - 1];
          }
          else {
            throw new FormatException("BitsPerSample is 0");
          }
        }
        // case 2: we're outside bitsPerSample array bounds
        else if (i >= bitsPerSample.length) {
          rowsPerStripArray[i] = (long) stripByteCounts[i] /
            (imageWidth * (bitsPerSample[bitsPerSample.length - 1] / 8));
        }
      }

      if (compression != UNCOMPRESSED) {
        for (int i=0; i<stripByteCounts.length; i++) {
          stripByteCounts[i] *= 2;
        }
      }
    }

    if (lastBitsZero) {
      bitsPerSample[bitsPerSample.length - 1] = 0;
    }

    TiffRational xResolution = getIFDRationalValue(ifd, X_RESOLUTION, false);
    TiffRational yResolution = getIFDRationalValue(ifd, Y_RESOLUTION, false);
    int planarConfig = getIFDIntValue(ifd, PLANAR_CONFIGURATION, false, 1);
    int resolutionUnit = getIFDIntValue(ifd, RESOLUTION_UNIT, false, 2);
    if (xResolution == null || yResolution == null) resolutionUnit = 0;
    int[] colorMap = getIFDIntArray(ifd, COLOR_MAP, false);
    int predictor = getIFDIntValue(ifd, PREDICTOR, false, 1);

    if (DEBUG) {
      StringBuffer sb = new StringBuffer();
      sb.append("IFD directory entry values:");
      sb.append("\n\tLittleEndian=");
      sb.append(littleEndian);
      sb.append("\n\tImageWidth=");
      sb.append(imageWidth);
      sb.append("\n\tImageLength=");
      sb.append(imageLength);
      sb.append("\n\tBitsPerSample=");
      sb.append(bitsPerSample[0]);
      for (int i=1; i<bitsPerSample.length; i++) {
        sb.append(",");
        sb.append(bitsPerSample[i]);
      }
      sb.append("\n\tSamplesPerPixel=");
      sb.append(samplesPerPixel);
      sb.append("\n\tCompression=");
      sb.append(compression);
      sb.append("\n\tPhotometricInterpretation=");
      sb.append(photoInterp);
      sb.append("\n\tStripOffsets=");
      sb.append(stripOffsets[0]);
      for (int i=1; i<stripOffsets.length; i++) {
        sb.append(",");
        sb.append(stripOffsets[i]);
      }
      sb.append("\n\tRowsPerStrip=");
      sb.append(rowsPerStripArray[0]);
      for (int i=1; i<rowsPerStripArray.length; i++) {
        sb.append(",");
        sb.append(rowsPerStripArray[i]);
      }
      sb.append("\n\tStripByteCounts=");
      sb.append(stripByteCounts[0]);
      for (int i=1; i<stripByteCounts.length; i++) {
        sb.append(",");
        sb.append(stripByteCounts[i]);
      }
      sb.append("\n\tXResolution=");
      sb.append(xResolution);
      sb.append("\n\tYResolution=");
      sb.append(yResolution);
      sb.append("\n\tPlanarConfiguration=");
      sb.append(planarConfig);
      sb.append("\n\tResolutionUnit=");
      sb.append(resolutionUnit);
      sb.append("\n\tColorMap=");
      if (colorMap == null) sb.append("null");
      else {
        sb.append(colorMap[0]);
        for (int i=1; i<colorMap.length; i++) {
          sb.append(",");
          sb.append(colorMap[i]);
        }
      }
      sb.append("\n\tPredictor=");
      sb.append(predictor);
      debug(sb.toString());
    }

    for (int i=0; i<samplesPerPixel; i++) {
      if (bitsPerSample[i] < 1) {
        throw new FormatException("Illegal BitsPerSample (" +
          bitsPerSample[i] + ")");
      }
      // don't support odd numbers of bits (except for 1)
      else if (bitsPerSample[i] % 2 != 0 && bitsPerSample[i] != 1) {
        throw new FormatException("Sorry, unsupported BitsPerSample (" +
          bitsPerSample[i] + ")");
      }
    }

    if (bitsPerSample.length < samplesPerPixel) {
      throw new FormatException("BitsPerSample length (" +
        bitsPerSample.length + ") does not match SamplesPerPixel (" +
        samplesPerPixel + ")");
    }
    else if (photoInterp == TRANSPARENCY_MASK) {
      throw new FormatException(
        "Sorry, Transparency Mask PhotometricInterpretation is not supported");
    }
    else if (photoInterp == CIE_LAB) {
      throw new FormatException(
        "Sorry, CIELAB PhotometricInterpretation is not supported");
    }
    else if (photoInterp != WHITE_IS_ZERO &&
      photoInterp != BLACK_IS_ZERO && photoInterp != RGB &&
      photoInterp != RGB_PALETTE && photoInterp != CMYK &&
      photoInterp != Y_CB_CR && photoInterp != CFA_ARRAY)
    {
      throw new FormatException("Unknown PhotometricInterpretation (" +
        photoInterp + ")");
    }

    long rowsPerStrip = rowsPerStripArray[0];
    for (int i=1; i<rowsPerStripArray.length; i++) {
      if (rowsPerStrip != rowsPerStripArray[i]) {
        throw new FormatException(
          "Sorry, non-uniform RowsPerStrip is not supported");
      }
    }

    if (compression == GROUP_3_FAX) Arrays.fill(bitsPerSample, 8);
    else if (compression == JPEG) photoInterp = RGB;

    long numStrips = (imageLength + rowsPerStrip - 1) / rowsPerStrip;

    if (isTiled || fakeRPS) numStrips = stripOffsets.length;
    if (planarConfig == 2) numStrips *= samplesPerPixel;

    if (stripOffsets.length < numStrips && !fakeRPS) {
      throw new FormatException("StripOffsets length (" +
        stripOffsets.length + ") does not match expected " +
        "number of strips (" + numStrips + ")");
    }
    else if (fakeRPS) numStrips = stripOffsets.length;

    if (stripByteCounts.length < numStrips) {
      throw new FormatException("StripByteCounts length (" +
        stripByteCounts.length + ") does not match expected " +
        "number of strips (" + numStrips + ")");
    }

    if (width > Integer.MAX_VALUE || height > Integer.MAX_VALUE ||
      width * height > Integer.MAX_VALUE)
    {
      throw new FormatException("Sorry, ImageWidth x ImageLength > " +
        Integer.MAX_VALUE + " is not supported (" +
        width + " x " + height + ")");
    }
    int numSamples = (int) (width * height);

    if (planarConfig != 1 && planarConfig != 2) {
      throw new FormatException(
        "Unknown PlanarConfiguration (" + planarConfig + ")");
    }

    // read in image strips
    if (DEBUG) {
      debug("reading image data (samplesPerPixel=" +
        samplesPerPixel + "; numSamples=" + numSamples + ")");
    }

    if (photoInterp == CFA_ARRAY) {
      if (colorMap == null) {
        colorMap = getIFDIntArray(ifd, TiffTools.COLOR_MAP, false);
        if (colorMap == null) {
          colorMap = new int[4];
          if (littleEndian) {
            colorMap[0] = 2;
            colorMap[1] = 0;
            colorMap[2] = 2;
            colorMap[3] = 0;
          }
          else {
            colorMap[0] = 0;
            colorMap[1] = 2;
            colorMap[2] = 0;
            colorMap[3] = 2;
          }
        }
      }
      int[] tempMap = new int[colorMap.length + 2];
      System.arraycopy(colorMap, 0, tempMap, 0, colorMap.length);
      tempMap[tempMap.length - 2] = (int) imageWidth;
      tempMap[tempMap.length - 1] = (int) imageLength;
      colorMap = tempMap;
    }
    else if (photoInterp == Y_CB_CR) {
      colorMap = new int[5 + samplesPerPixel * 2];
      int[] ref = getIFDIntArray(ifd, REFERENCE_BLACK_WHITE, false);
      if (ref != null) System.arraycopy(ref, 0, colorMap, 0, ref.length);
      else {
        colorMap[0] = colorMap[2] = colorMap[4] = 0;
        colorMap[1] = colorMap[3] = colorMap[5] = 255;
      }
      ref = getIFDIntArray(ifd, Y_CB_CR_SUB_SAMPLING, false);
      if (ref == null) ref = new int[] {2, 2};
      System.arraycopy(ref, 0, colorMap, samplesPerPixel * 2, ref.length);
      TiffRational[] coeffs =
        (TiffRational[]) getIFDValue(ifd, Y_CB_CR_COEFFICIENTS);
      if (coeffs != null) {
        for (int i=0; i<coeffs.length; i++) {
          colorMap[colorMap.length - coeffs.length + i] = (int) (10000 *
            ((float) coeffs[i].getNumerator() / coeffs[i].getDenominator()));
        }
      }
      else {
        colorMap[colorMap.length - 3] = 2990;
        colorMap[colorMap.length - 2] = 5870;
        colorMap[colorMap.length - 1] = 1140;
      }
    }

    if (stripOffsets.length > 1 && (stripOffsets[stripOffsets.length - 1] ==
      stripOffsets[stripOffsets.length - 2]))
    {
      long[] tmp = stripOffsets;
      stripOffsets = new long[tmp.length - 1];
      System.arraycopy(tmp, 0, stripOffsets, 0, stripOffsets.length);
      numStrips--;
    }

    byte[] jpegTable = null;
    if (compression == JPEG) {
      jpegTable = (byte[]) TiffTools.getIFDValue(ifd, JPEG_TABLES);
    }

    if (isTiled) {
      long tileWidth = getIFDLongValue(ifd, TILE_WIDTH, true, 0);
      long tileLength = getIFDLongValue(ifd, TILE_LENGTH, true, 0);

      int numTileRows = (int) (imageLength / tileLength);
      if (numTileRows * tileLength < imageLength) numTileRows++;

      int numTileCols = (int) (imageWidth / tileWidth);
      if (numTileCols * tileWidth < imageWidth) numTileCols++;

      Rectangle imageBounds = new Rectangle(x, y, (int) width, (int) height);
      int endX = (int) width + x;
      int endY = (int) height + y;
      int pixel = (int) bitsPerSample[0] / 8;
      int channels = 1;
      if (planarConfig != 2) channels = samplesPerPixel;
      else pixel *= samplesPerPixel;

      for (int row=0; row<numTileRows; row++) {
        for (int col=0; col<numTileCols; col++) {
          Rectangle tileBounds = new Rectangle(col * (int) tileWidth,
            row * (int) tileLength, (int) tileWidth, (int) tileLength);

          if (!imageBounds.intersects(tileBounds)) continue;

          int tileNumber = row * numTileCols + col;
          byte[] tile = new byte[(int) stripByteCounts[tileNumber]];

          in.seek(stripOffsets[tileNumber] & 0xffffffffL);
          in.read(tile);

          int size = (int) (tileWidth * tileLength * pixel * channels);
          if (jpegTable != null) {
            byte[] q = new byte[jpegTable.length + tile.length - 4];
            System.arraycopy(jpegTable, 0, q, 0, jpegTable.length - 2);
            System.arraycopy(tile, 2, q, jpegTable.length - 2, tile.length - 2);
            tile = uncompress(q, compression, size);
          }
          else tile = uncompress(tile, compression, size);

          undifference(tile, bitsPerSample, tileWidth, planarConfig, predictor,
            littleEndian);
          byte[] t = new byte[size];
          unpackBytes(t, 0, tile, bitsPerSample, photoInterp, colorMap,
            littleEndian, maxValue, planarConfig, 0, 1, tileWidth);

          // adjust tile bounds, if necessary

          int tileX = (int) Math.max(tileBounds.x, x);
          int tileY = (int) Math.max(tileBounds.y, y);

          int realX = tileX % (int) tileWidth;
          int realY = tileY % (int) tileLength;

          int twidth = (int) Math.min(endX - tileX, tileWidth - realX);
          int theight = (int) Math.min(endY - tileY, tileLength - realY);

          // copy appropriate portion of the tile to the output buffer

          int rowLen = pixel * (int) tileWidth;
          int copy = pixel * twidth;
          int tileSize = (int) (tileWidth * tileLength * pixel);
          int planeSize = (int) (width * height * pixel);
          for (int q=0; q<channels; q++) {
            for (int tileRow=0; tileRow<theight; tileRow++) {
              int src = q * tileSize + (realY + tileRow) * rowLen +
                realX * pixel;
              int dest = (int) (q * planeSize +
                (tileY - y + tileRow) * width * pixel + (tileX - x) * pixel);
              System.arraycopy(t, src, buf, dest, copy);
            }
          }
        }
      }
    }
    else {
      int offset = 0;

      if (rowsPerStrip <= 0 || numStrips <= 0) {
        numStrips = 1;
        rowsPerStrip = (int) imageLength;
      }

      for (int strip=0, row=0; strip<numStrips; row+=rowsPerStrip, strip++) {
        if (((row % imageLength) + rowsPerStrip < y) ||
          ((row % imageLength) >= y + height))
        {
          continue;
        }

        try {
          int size = (int) imageWidth * (bitsPerSample[0] / 8);
          if (planarConfig != 2) size *= samplesPerPixel;
          int nRows = (int) rowsPerStrip;
          if ((row % imageLength) < y) nRows -= (y - (row % imageLength));
          if ((row % imageLength) + rowsPerStrip > y + height) {
            nRows -= ((row % imageLength) + rowsPerStrip - height - y);
          }
          size *= rowsPerStrip;

          if (DEBUG) debug("reading image strip #" + strip);
          if (stripOffsets[strip] < 0) {
            stripOffsets[strip] = (long) (stripOffsets[strip] & 0xffffffffL);
          }
          in.seek(stripOffsets[strip]);

          if (stripByteCounts[strip] > Integer.MAX_VALUE) {
            throw new FormatException("Sorry, StripByteCounts > " +
              Integer.MAX_VALUE + " is not supported");
          }
          byte[] bytes = new byte[(int) stripByteCounts[strip]];
          in.read(bytes);
          if (jpegTable != null) {
            byte[] q = new byte[jpegTable.length + bytes.length - 4];
            System.arraycopy(jpegTable, 0, q, 0, jpegTable.length - 2);
            System.arraycopy(bytes, 2, q, jpegTable.length - 2,
              bytes.length - 2);
            bytes = uncompress(q, compression, size);
          }
          else bytes = uncompress(bytes, compression, size);

          undifference(bytes, bitsPerSample,
            imageWidth, planarConfig, predictor, littleEndian);

          if (x != 0 || width != imageWidth || y != 0 ||
            height != imageLength)
          {
            byte[] tmp = bytes;
            int extra = (int) bitsPerSample[0] / 8;
            if (planarConfig != 2) extra *= samplesPerPixel;
            int rowLen = (int) width * extra;
            int srcRowLen = (int) imageWidth * extra;
            bytes = new byte[(int) (nRows * extra * width)];

            int startRow = (row % imageLength) < y ?
              (int) (y - (row % imageLength)) : 0;
            int endRow = (int) ((row % imageLength) + rowsPerStrip >
              y + height ?  y + height - (row % imageLength) : rowsPerStrip);
            for (int n=startRow; n<endRow; n++) {
              int srcOffset = n * srcRowLen + x * extra;
              System.arraycopy(tmp, srcOffset, bytes,
                (n - startRow) * rowLen, rowLen);
            }
            nRows = endRow - startRow;
          }

          unpackBytes(buf, offset, bytes, bitsPerSample,
            photoInterp, colorMap, littleEndian, maxValue, planarConfig,
            strip, (int) numStrips, width);
          int div = bitsPerSample[0] / 8;
          if (div == 0) div = 1;
          if (bitsPerSample[0] % 8 != 0) div++;
          if (planarConfig != 2) div *= samplesPerPixel;
          offset += bytes.length / div;
        }
        catch (Exception e) {
          // CTR TODO - eliminate catch-all exception handling
          if (strip == 0) {
            if (e instanceof FormatException) throw (FormatException) e;
            else throw new FormatException(e);
          }
          byte[] bytes = new byte[numSamples];
          undifference(bytes, bitsPerSample, imageWidth, planarConfig,
            predictor, littleEndian);
          offset = (int) (imageWidth * row);
          unpackBytes(buf, offset, bytes, bitsPerSample, photoInterp,
            colorMap, littleEndian, maxValue, planarConfig,
            strip, (int) numStrips, imageWidth);
        }
      }
    }

    return buf;
  }

  /**
   * Extracts pixel information from the given byte array according to the
   * bits per sample, photometric interpretation, and the specified byte
   * ordering.
   * No error checking is performed.
   * This method is tailored specifically for planar (separated) images.
   */
  public static void planarUnpack(byte[] samples, int startIndex,
    byte[] bytes, int[] bitsPerSample, int photoInterp, boolean littleEndian,
    int strip, int numStrips) throws FormatException
  {
    int numChannels = bitsPerSample.length;
    int numSamples = samples.length / numChannels;
    if (bitsPerSample[bitsPerSample.length - 1] == 0) numChannels--;

    // determine which channel the strip belongs to

    if (numStrips < numChannels) numStrips = numChannels;
    int channelNum = strip / (numStrips / numChannels);

    BitBuffer bb = new BitBuffer(bytes);

    int index = 0;
    int counter = 0;
    int numBytes = bitsPerSample[0] / 8;
    if (bitsPerSample[0] % 8 != 0) numBytes++;
    int realBytes = numBytes;
    if (numBytes == 3) numBytes++;

    for (int j=0; j<bytes.length / realBytes; j++) {
      int value = bb.getBits(bitsPerSample[0]);

      if (photoInterp == WHITE_IS_ZERO) {
        value = (int) (Math.pow(2, bitsPerSample[0]) - 1 - value);
      }
      else if (photoInterp == CMYK) {
        value = Integer.MAX_VALUE - value;
      }

      if (numBytes*(startIndex + j) < samples.length) {
        DataTools.unpackBytes(value, samples, numBytes*(startIndex + j),
          numBytes, littleEndian);
      }
    }
  }

  /**
   * Extracts pixel information from the given byte array according to the
   * bits per sample, photometric interpretation and color map IFD directory
   * entry values, and the specified byte ordering.
   * No error checking is performed.
   */
  public static void unpackBytes(byte[] samples, int startIndex,
    byte[] bytes, int[] bitsPerSample, int photoInterp, int[] colorMap,
    boolean littleEndian, long maxValue, int planar, int strip, int numStrips,
    long imageWidth) throws FormatException
  {
    if (planar == 2) {
      planarUnpack(samples, startIndex, bytes, bitsPerSample, photoInterp,
        littleEndian, strip, numStrips);
      return;
    }

    int nSamples = samples.length / bitsPerSample.length;
    int nChannels = bitsPerSample.length;

    int totalBits = 0;
    for (int i=0; i<bitsPerSample.length; i++) totalBits += bitsPerSample[i];
    int sampleCount = 8 * bytes.length / totalBits;
    if (photoInterp == Y_CB_CR) sampleCount *= 3;

    if (DEBUG) {
      debug("unpacking " + sampleCount + " samples (startIndex=" + startIndex +
        "; totalBits=" + totalBits + "; numBytes=" + bytes.length + ")");
    }

    int bps0 = bitsPerSample[0];
    int numBytes = bps0 / 8;
    boolean noDiv8 = bps0 % 8 != 0;
    boolean bps8 = bps0 == 8;
    boolean bps16 = bps0 == 16;

    if (photoInterp == CFA_ARRAY) {
      imageWidth = colorMap[colorMap.length - 2];
    }

    int row = 0, col = 0;

    if (imageWidth != 0) row = startIndex / (int) imageWidth;

    int cw = 0;
    int ch = 0;

    if (photoInterp == CFA_ARRAY) {
      byte[] c = new byte[2];
      c[0] = (byte) colorMap[0];
      c[1] = (byte) colorMap[1];

      cw = DataTools.bytesToInt(c, littleEndian);
      c[0] = (byte) colorMap[2];
      c[1] = (byte) colorMap[3];
      ch = DataTools.bytesToInt(c, littleEndian);

      int[] tmp = colorMap;
      colorMap = new int[tmp.length - 6];
      System.arraycopy(tmp, 4, colorMap, 0, colorMap.length);
    }

    int index = 0;
    int count = 0;

    BitBuffer bb = new BitBuffer(bytes);

    byte[] copyByteArray = new byte[numBytes];

    for (int j=0; j<sampleCount; j++) {
      for (int i=0; i<nChannels; i++) {
        if (noDiv8) {
          // bits per sample is not a multiple of 8

          int ndx = startIndex + j;
          short s = 0;
          if ((i == 0 && (photoInterp == CFA_ARRAY ||
            photoInterp == RGB_PALETTE) || (photoInterp != CFA_ARRAY &&
            photoInterp != RGB_PALETTE)))
          {
            s = (short) (bb.getBits(bps0) & 0xffff);
            if ((ndx % imageWidth) == imageWidth - 1 && bps0 < 8) {
              bb.skipBits((imageWidth * bps0 * sampleCount) % 8);
            }
          }

          if (photoInterp == WHITE_IS_ZERO || photoInterp == CMYK) {
            // invert colors
            s = (short) (Math.pow(2, bitsPerSample[0]) - 1 - s);
          }

          if (photoInterp == CFA_ARRAY) {
            if (i == 0) {
              int pixelIndex = (int) ((row + (count / cw))*imageWidth + col +
                (count % cw));

              samples[colorMap[count]*nSamples + pixelIndex] = (byte) s;
              count++;

              if (count == colorMap.length) {
                count = 0;
                col += cw*ch;
                if (col == imageWidth) col = cw;
                else if (col > imageWidth) {
                  row += ch;
                  col = 0;
                }
              }
            }
          }
          else {
            if (i*nSamples + (ndx + 1)*(numBytes + 1) <= samples.length) {
              DataTools.unpackBytes(s, samples,
                i*nSamples + ndx*(numBytes + 1), numBytes + 1, littleEndian);
            }
          }
        }
        else if (bps8) {
          // special case handles 8-bit data more quickly

          int ndx = startIndex + j;
          if (i*nSamples + ndx >= samples.length) break;

          if (photoInterp != Y_CB_CR) {
            samples[i*nSamples + ndx] = (byte) (bytes[index++] & 0xff);
          }

          if (photoInterp == WHITE_IS_ZERO) { // invert color value
            samples[i*nSamples + ndx] =
              (byte) (255 - samples[i*nSamples + ndx]);
          }
          else if (photoInterp == CMYK) {
            samples[i*nSamples + ndx] =
              (byte) (Integer.MAX_VALUE - samples[i*nSamples + ndx]);
          }
          else if (photoInterp == Y_CB_CR) {
            if (i == bitsPerSample.length - 1) {
              float lumaRed = (float) colorMap[colorMap.length - 3];
              float lumaGreen = (float) colorMap[colorMap.length - 2];
              float lumaBlue = (float) colorMap[colorMap.length - 1];
              lumaRed /= 10000;
              lumaGreen /= 10000;
              lumaBlue /= 10000;

              int subX = colorMap[colorMap.length - 5];
              int subY = colorMap[colorMap.length - 4];

              int block = subX * subY;
              int lumaIndex = j + (2 * (j / block));
              int chromaIndex = (j / block) * (block + 2) + block;

              if (chromaIndex + 1 >= bytes.length) break;

              int tile = ndx / block;
              int pixel = ndx % block;
              long r = subY * (tile / (imageWidth / subX)) + (pixel / subX);
              long c = subX * (tile % (imageWidth / subX)) + (pixel % subX);

              int idx = (int) (r * imageWidth + c);

              if (idx < nSamples) {
                int y = (bytes[lumaIndex] & 0xff) - colorMap[0];
                int cb = (bytes[chromaIndex] & 0xff) - colorMap[2];
                int cr = (bytes[chromaIndex + 1] & 0xff) - colorMap[4];

                int red = (int) (cr * (2 - 2 * lumaRed) + y);
                int blue = (int) (cb * (2 - 2 * lumaBlue) + y);
                int green = (int)
                  ((y - lumaBlue * blue - lumaRed * red) / lumaGreen);

                samples[idx] = (byte) red;
                samples[nSamples + idx] = (byte) green;
                samples[2*nSamples + idx] = (byte) blue;
              }
            }
          }
        }  // End if (bps8)
        else if (bps16) {
          int ndx = startIndex + j;
          int nioIndex =
            numBytes + index < bytes.length ? index : bytes.length - numBytes;
          short v = DataTools.bytesToShort(bytes, nioIndex, 2, littleEndian);
          index += numBytes;

          if (photoInterp == WHITE_IS_ZERO) { // invert color value
            long max = (long) Math.pow(2, numBytes * 8) - 1;
            v = (short) (max - v);
          }
          else if (photoInterp == CMYK) {
            v = (short) (Integer.MAX_VALUE - v);
          }
          if (ndx*2 >= nSamples) break;
          DataTools.unpackShort(v, samples, i*nSamples + ndx*2, littleEndian);
        }  // End if (bps16)
        else {
          if (numBytes + index < bytes.length) {
            System.arraycopy(bytes, index, copyByteArray, 0, numBytes);
          }
          else {
            System.arraycopy(bytes, bytes.length - numBytes, copyByteArray,
              0, numBytes);
          }
          index += numBytes;
          int ndx = startIndex + j;
          long v = DataTools.bytesToLong(copyByteArray, littleEndian);

          if (photoInterp == WHITE_IS_ZERO) { // invert color value
            long max = 1;
            for (int q=0; q<numBytes; q++) max *= 8;
            v = max - v;
          }
          else if (photoInterp == CMYK) {
            v = Integer.MAX_VALUE - v;
          }
          if (ndx*numBytes >= nSamples) break;
          DataTools.unpackBytes(v, samples, i*nSamples + ndx*numBytes,
            numBytes, littleEndian);
        } // end else
      }
    }
  }

  // -- Decompression methods --

  /** Decodes a strip of data compressed with the given compression scheme. */
  public static byte[] uncompress(byte[] input, int compression, int size)
    throws FormatException, IOException
  {
    if (compression < 0) compression += 65536;
    if (compression == UNCOMPRESSED) return input;
    else if (compression == CCITT_1D) {
      throw new FormatException(
        "Sorry, CCITT Group 3 1-Dimensional Modified Huffman " +
        "run length encoding compression mode is not supported");
    }
    else if (compression == GROUP_3_FAX) {
      //return new T4FaxCodec().decompress(input);
      throw new FormatException("Sorry, CCITT T.4 bi-level encoding " +
        "(Group 3 Fax) compression mode is not supported");
    }
    else if (compression == GROUP_4_FAX) {
      throw new FormatException("Sorry, CCITT T.6 bi-level encoding " +
        "(Group 4 Fax) compression mode is not supported");
    }
    else if (compression == LZW) {
      return new LZWCodec().decompress(input, new Integer(size));
    }
    else if (compression == JPEG || compression == ALT_JPEG) {
      return new JPEGCodec().decompress(input,
        new Object[] {Boolean.TRUE, Boolean.TRUE});
    }
    else if (compression == JPEG_2000) {
      return new JPEG2000Codec().decompress(input,
        new Object[] {Boolean.TRUE, Boolean.TRUE});
    }
    else if (compression == PACK_BITS) {
      return new PackbitsCodec().decompress(input, new Integer(size));
    }
    else if (compression == PROPRIETARY_DEFLATE || compression == DEFLATE) {
      return new ZlibCodec().decompress(input);
    }
    else if (compression == THUNDERSCAN) {
      throw new FormatException("Sorry, " +
        "Thunderscan compression mode is not supported");
    }
    else if (compression == NIKON) {
      //return new NikonCodec().decompress(input);
      throw new FormatException("Sorry, Nikon compression mode is not " +
        "supported; we hope to support it in the future");
    }
    else if (compression == LURAWAVE) {
      return new LuraWaveCodec().decompress(input, new Integer(size));
    }
    else {
      throw new FormatException(
        "Unknown Compression type (" + compression + ")");
    }
  }

  /** Undoes in-place differencing according to the given predictor value. */
  public static void undifference(byte[] input, int[] bitsPerSample,
    long width, int planarConfig, int predictor, boolean little)
    throws FormatException
  {
    if (predictor == 2) {
      if (DEBUG) debug("reversing horizontal differencing");
      int len = bitsPerSample.length;
      if (planarConfig == 2 || bitsPerSample[len - 1] == 0) len = 1;
      if (bitsPerSample[0] <= 8) {
        for (int b=0; b<input.length; b++) {
          if (b / len % width == 0) continue;
          input[b] += input[b - len];
        }
      }
      else if (bitsPerSample[0] <= 16) {
        short[] s = (short[]) DataTools.makeDataArray(input, 2, false, little);
        for (int b=0; b<s.length; b++) {
          if (b / len % width == 0) continue;
          s[b] += s[b - len];
        }
        for (int i=0; i<s.length; i++) {
          DataTools.unpackShort(s[i], input, i*2, little);
        }
      }
    }
    else if (predictor != 1) {
      throw new FormatException("Unknown Predictor (" + predictor + ")");
    }
  }

  // --------------------------- Writing TIFF files ---------------------------

  // -- IFD population methods --

  /** Adds a directory entry to an IFD. */
  public static void putIFDValue(Hashtable ifd, int tag, Object value) {
    ifd.put(new Integer(tag), value);
  }

  /** Adds a directory entry of type BYTE to an IFD. */
  public static void putIFDValue(Hashtable ifd, int tag, short value) {
    putIFDValue(ifd, tag, new Short(value));
  }

  /** Adds a directory entry of type SHORT to an IFD. */
  public static void putIFDValue(Hashtable ifd, int tag, int value) {
    putIFDValue(ifd, tag, new Integer(value));
  }

  /** Adds a directory entry of type LONG to an IFD. */
  public static void putIFDValue(Hashtable ifd, int tag, long value) {
    putIFDValue(ifd, tag, new Long(value));
  }

  // -- IFD writing methods --

  /**
   * Writes the given IFD value to the given output object.
   * @param ifdOut output object for writing IFD stream
   * @param extraBuf buffer to which "extra" IFD information should be written
   * @param extraOut data output wrapper for extraBuf (passed for efficiency)
   * @param offset global offset to use for IFD offset values
   * @param tag IFD tag to write
   * @param value IFD value to write
   */
  public static void writeIFDValue(DataOutput ifdOut,
    ByteArrayOutputStream extraBuf, DataOutputStream extraOut, long offset,
    int tag, Object value, boolean bigTiff) throws FormatException, IOException
  {
    // convert singleton objects into arrays, for simplicity
    if (value instanceof Short) {
      value = new short[] {((Short) value).shortValue()};
    }
    else if (value instanceof Integer) {
      value = new int[] {((Integer) value).intValue()};
    }
    else if (value instanceof Long) {
      value = new long[] {((Long) value).longValue()};
    }
    else if (value instanceof TiffRational) {
      value = new TiffRational[] {(TiffRational) value};
    }
    else if (value instanceof Float) {
      value = new float[] {((Float) value).floatValue()};
    }
    else if (value instanceof Double) {
      value = new double[] {((Double) value).doubleValue()};
    }

    int dataLength = bigTiff ? 8 : 4;

    // write directory entry to output buffers
    ifdOut.writeShort(tag); // tag
    if (value instanceof short[]) { // BYTE
      short[] q = (short[]) value;
      ifdOut.writeShort(BYTE); // type
      if (bigTiff) ifdOut.writeLong(q.length);
      else ifdOut.writeInt(q.length);
      if (q.length <= dataLength) {
        for (int i=0; i<q.length; i++) ifdOut.writeByte(q[i]); // value(s)
        for (int i=q.length; i<dataLength; i++) ifdOut.writeByte(0); // padding
      }
      else {
        if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
        else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]); // values
      }
    }
    else if (value instanceof String) { // ASCII
      char[] q = ((String) value).toCharArray();
      ifdOut.writeShort(ASCII); // type
      if (bigTiff) ifdOut.writeLong(q.length + 1);
      else ifdOut.writeInt(q.length + 1);
      if (q.length < dataLength) {
        for (int i=0; i<q.length; i++) ifdOut.writeByte(q[i]); // value(s)
        for (int i=q.length; i<dataLength; i++) ifdOut.writeByte(0); // padding
      }
      else {
        if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
        else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
        for (int i=0; i<q.length; i++) extraOut.writeByte(q[i]); // values
        extraOut.writeByte(0); // concluding NULL byte
      }
    }
    else if (value instanceof int[]) { // SHORT
      int[] q = (int[]) value;
      ifdOut.writeShort(SHORT); // type
      if (bigTiff) ifdOut.writeLong(q.length);
      else ifdOut.writeInt(q.length);
      if (q.length <= dataLength / 2) {
        for (int i=0; i<q.length; i++) ifdOut.writeShort(q[i]); // value(s)
        for (int i=q.length; i<dataLength / 2; i++) {
          ifdOut.writeShort(0); // padding
        }
      }
      else {
        if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
        else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
        for (int i=0; i<q.length; i++) extraOut.writeShort(q[i]); // values
      }
    }
    else if (value instanceof long[]) { // LONG
      long[] q = (long[]) value;

      if (bigTiff) {
        ifdOut.writeShort(LONG8);
        ifdOut.writeLong(q.length);

        if (q.length <= dataLength / 4) {
          for (int i=0; i<q.length; i++) ifdOut.writeLong(q[0]);
          for (int i=q.length; i<dataLength / 4; i++) {
            ifdOut.writeLong(0);
          }
        }
        else {
          ifdOut.writeLong(offset + extraBuf.size());
          for (int i=0; i<q.length; i++) {
            extraOut.writeLong(q[i]);
          }
        }
      }
      else {
        ifdOut.writeShort(LONG);
        ifdOut.writeInt(q.length);
        if (q.length <= dataLength / 4) {
          for (int i=0; i<q.length; i++) ifdOut.writeInt((int) q[0]);
          for (int i=q.length; i<dataLength / 4; i++) {
            ifdOut.writeInt(0); // padding
          }
        }
        else {
          ifdOut.writeInt((int) (offset + extraBuf.size()));
          for (int i=0; i<q.length; i++) {
            extraOut.writeInt((int) q[i]);
          }
        }
      }
    }
    else if (value instanceof TiffRational[]) { // RATIONAL
      TiffRational[] q = (TiffRational[]) value;
      ifdOut.writeShort(RATIONAL); // type
      if (bigTiff) ifdOut.writeLong(q.length);
      else ifdOut.writeInt(q.length);
      if (bigTiff && q.length == 1) {
        ifdOut.writeInt((int) q[0].getNumerator());
        ifdOut.writeInt((int) q[0].getDenominator());
      }
      else {
        if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
        else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
        for (int i=0; i<q.length; i++) {
          extraOut.writeInt((int) q[i].getNumerator()); // values
          extraOut.writeInt((int) q[i].getDenominator()); // values
        }
      }
    }
    else if (value instanceof float[]) { // FLOAT
      float[] q = (float[]) value;
      ifdOut.writeShort(FLOAT); // type
      if (bigTiff) ifdOut.writeLong(q.length);
      else ifdOut.writeInt(q.length);
      if (q.length <= dataLength / 4) {
        for (int i=0; i<q.length; i++) ifdOut.writeFloat(q[0]); // value
        for (int i=q.length; i<dataLength / 4; i++) {
          ifdOut.writeInt(0); // padding
        }
      }
      else {
        if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
        else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
        for (int i=0; i<q.length; i++) extraOut.writeFloat(q[i]); // values
      }
    }
    else if (value instanceof double[]) { // DOUBLE
      double[] q = (double[]) value;
      ifdOut.writeShort(DOUBLE); // type
      if (bigTiff) ifdOut.writeLong(q.length);
      else ifdOut.writeInt(q.length);
      if (bigTiff) ifdOut.writeLong(offset + extraBuf.size());
      else ifdOut.writeInt((int) (offset + extraBuf.size())); // offset
      for (int i=0; i<q.length; i++) extraOut.writeDouble(q[i]); // values
    }
    else {
      throw new FormatException("Unknown IFD value type (" +
        value.getClass().getName() + "): " + value);
    }
  }

  /**
   * Surgically overwrites an existing IFD value with the given one. This
   * method requires that the IFD directory entry already exist. It
   * intelligently updates the count field of the entry to match the new
   * length. If the new length is longer than the old length, it appends the
   * new data to the end of the file and updates the offset field; if not, or
   * if the old data is already at the end of the file, it overwrites the old
   * data in place.
   */
  public static void overwriteIFDValue(RandomAccessFile raf,
    int ifd, int tag, Object value) throws FormatException, IOException
  {
    if (DEBUG) {
      debug("overwriteIFDValue (ifd=" + ifd + "; tag=" + tag + "; value=" +
        value + ")");
    }
    byte[] header = new byte[4];
    raf.seek(0);
    raf.readFully(header);
    if (!isValidHeader(header)) {
      throw new FormatException("Invalid TIFF header");
    }
    boolean little = header[0] == LITTLE && header[1] == LITTLE; // II
    boolean bigTiff = header[2] == 0x2b || header[3] == 0x2b;
    long offset = bigTiff ? 8 : 4; // offset to the IFD
    long num = 0; // number of directory entries

    int baseOffset = bigTiff ? 8 : 2;
    int bytesPerEntry = bigTiff ? BIG_TIFF_BYTES_PER_ENTRY : BYTES_PER_ENTRY;

    raf.seek(offset);

    // skip to the correct IFD
    for (int i=0; i<=ifd; i++) {
      offset = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read4UnsignedBytes(raf, little);
      if (offset <= 0) {
        throw new FormatException("No such IFD (" + ifd + " of " + i + ")");
      }
      raf.seek(offset);
      num = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read2UnsignedBytes(raf, little);
      if (i < ifd) raf.seek(offset + baseOffset + bytesPerEntry * num);
    }

    // search directory entries for proper tag
    for (int i=0; i<num; i++) {
      int oldTag = DataTools.read2UnsignedBytes(raf, little);
      int oldType = DataTools.read2UnsignedBytes(raf, little);
      int oldCount =
        bigTiff ? (int) (DataTools.read8SignedBytes(raf, little) & 0xffffffff) :
        DataTools.read4SignedBytes(raf, little);
      long oldOffset = bigTiff ? DataTools.read8SignedBytes(raf, little) :
        DataTools.read4SignedBytes(raf, little);
      if (oldTag == tag) {
        // write new value to buffers
        ByteArrayOutputStream ifdBuf = new ByteArrayOutputStream(bytesPerEntry);
        DataOutputStream ifdOut = new DataOutputStream(ifdBuf);
        ByteArrayOutputStream extraBuf = new ByteArrayOutputStream();
        DataOutputStream extraOut = new DataOutputStream(extraBuf);
        writeIFDValue(ifdOut, extraBuf, extraOut, oldOffset, tag, value,
          bigTiff);
        byte[] bytes = ifdBuf.toByteArray();
        byte[] extra = extraBuf.toByteArray();

        // extract new directory entry parameters
        int newTag = DataTools.bytesToInt(bytes, 0, 2, false);
        int newType = DataTools.bytesToInt(bytes, 2, 2, false);
        int newCount;
        long newOffset;
        if (bigTiff) {
          newCount =
            (int) (DataTools.bytesToLong(bytes, 4, false) & 0xffffffff);
          newOffset = DataTools.bytesToLong(bytes, 12, false);
        }
        else {
          newCount = DataTools.bytesToInt(bytes, 4, false);
          newOffset = DataTools.bytesToInt(bytes, 8, false);
        }
        boolean terminate = false;
        if (DEBUG) {
          debug("overwriteIFDValue:\n\told: (tag=" + oldTag + "; type=" +
            oldType + "; count=" + oldCount + "; offset=" + oldOffset +
            ");\n\tnew: (tag=" + newTag + "; type=" + newType + "; count=" +
            newCount + "; offset=" + newOffset + ")");
        }

        // determine the best way to overwrite the old entry
        if (extra.length == 0) {
          // new entry is inline; if old entry wasn't, old data is orphaned
          // do not override new offset value since data is inline
          if (DEBUG) debug("overwriteIFDValue: new entry is inline");
        }
        else if (oldOffset +
          oldCount * BYTES_PER_ELEMENT[oldType] == raf.length())
        {
          // old entry was already at EOF; overwrite it
          newOffset = oldOffset;
          terminate = true;
          if (DEBUG) debug("overwriteIFDValue: old entry is at EOF");
        }
        else if (newCount <= oldCount) {
          // new entry is as small or smaller than old entry; overwrite it
          newOffset = oldOffset;
          if (DEBUG) debug("overwriteIFDValue: new entry is <= old entry");
        }
        else {
          // old entry was elsewhere; append to EOF, orphaning old entry
          newOffset = raf.length();
          if (DEBUG) debug("overwriteIFDValue: old entry will be orphaned");
        }

        // overwrite old entry
        raf.seek(raf.getFilePointer() - (bigTiff ? 18 : 10)); // jump back
        DataTools.writeShort(raf, newType, little);
        if (bigTiff) DataTools.writeLong(raf, newCount, little);
        else DataTools.writeInt(raf, newCount, little);
        if (bigTiff) DataTools.writeLong(raf, newOffset, little);
        else DataTools.writeInt(raf, (int) newOffset, little);
        if (extra.length > 0) {
          raf.seek(newOffset);
          raf.write(extra);
        }
        if (terminate) raf.setLength(raf.getFilePointer());
        return;
      }
    }

    throw new FormatException("Tag not found (" + getIFDTagName(tag) + ")");
  }

  /** Convenience method for overwriting a file's first ImageDescription. */
  public static void overwriteComment(String id, Object value)
    throws FormatException, IOException
  {
    RandomAccessFile raf = new RandomAccessFile(id, "rw");
    overwriteIFDValue(raf, 0, TiffTools.IMAGE_DESCRIPTION, value);
    raf.close();
  }

  // -- Image writing methods --

  /**
   * Writes the given field to the specified output stream using the given
   * byte offset and IFD, in big-endian format.
   *
   * @param img The field to write
   * @param ifd Hashtable representing the TIFF IFD; can be null
   * @param out The output stream to which the TIFF data should be written
   * @param offset The value to use for specifying byte offsets
   * @param last Whether this image is the final IFD entry of the TIFF data
   * @param bigTiff Whether this image should be written as BigTIFF
   * @return total number of bytes written
   */
  public static long writeImage(BufferedImage img, Hashtable ifd,
    OutputStream out, long offset, boolean last, boolean bigTiff)
    throws FormatException, IOException
  {
    if (img == null) throw new FormatException("Image is null");
    if (DEBUG) debug("writeImage (offset=" + offset + "; last=" + last + ")");

    byte[][] values = ImageTools.getPixelBytes(img, false);

    int width = img.getWidth();
    int height = img.getHeight();

    if (values.length < 1 || values.length > 3) {
      throw new FormatException("Image has an unsupported " +
        "number of range components (" + values.length + ")");
    }
    if (values.length == 2) {
      // pad values with extra set of zeroes
      values = new byte[][] {
        values[0], values[1], new byte[values[0].length]
      };
    }

    int bytesPerPixel = values[0].length / (width * height);

    // populate required IFD directory entries (except strip information)
    if (ifd == null) ifd = new Hashtable();
    putIFDValue(ifd, IMAGE_WIDTH, width);
    putIFDValue(ifd, IMAGE_LENGTH, height);
    if (getIFDValue(ifd, BITS_PER_SAMPLE) == null) {
      int bps = 8 * bytesPerPixel;
      int[] bpsArray = new int[values.length];
      Arrays.fill(bpsArray, bps);
      putIFDValue(ifd, BITS_PER_SAMPLE, bpsArray);
    }
    if (img.getRaster().getTransferType() == DataBuffer.TYPE_FLOAT) {
      putIFDValue(ifd, SAMPLE_FORMAT, 3);
    }
    if (getIFDValue(ifd, COMPRESSION) == null) {
      putIFDValue(ifd, COMPRESSION, UNCOMPRESSED);
    }
    if (getIFDValue(ifd, PHOTOMETRIC_INTERPRETATION) == null) {
      putIFDValue(ifd, PHOTOMETRIC_INTERPRETATION, values.length == 1 ? 1 : 2);
    }
    if (getIFDValue(ifd, SAMPLES_PER_PIXEL) == null) {
      putIFDValue(ifd, SAMPLES_PER_PIXEL, values.length);
    }
    if (getIFDValue(ifd, X_RESOLUTION) == null) {
      putIFDValue(ifd, X_RESOLUTION, new TiffRational(1, 1)); // no unit
    }
    if (getIFDValue(ifd, Y_RESOLUTION) == null) {
      putIFDValue(ifd, Y_RESOLUTION, new TiffRational(1, 1)); // no unit
    }
    if (getIFDValue(ifd, RESOLUTION_UNIT) == null) {
      putIFDValue(ifd, RESOLUTION_UNIT, 1); // no unit
    }
    if (getIFDValue(ifd, SOFTWARE) == null) {
      putIFDValue(ifd, SOFTWARE, "LOCI Bio-Formats");
    }
    if (getIFDValue(ifd, IMAGE_DESCRIPTION) == null) {
      putIFDValue(ifd, IMAGE_DESCRIPTION, "");
    }

    // create pixel output buffers
    int stripSize = Math.max(8192, width * bytesPerPixel * values.length);
    int rowsPerStrip = stripSize / (width * bytesPerPixel * values.length);
    int stripsPerImage = (height + rowsPerStrip - 1) / rowsPerStrip;
    int[] bps = (int[]) getIFDValue(ifd, BITS_PER_SAMPLE, true, int[].class);
    ByteArrayOutputStream[] stripBuf =
      new ByteArrayOutputStream[stripsPerImage];
    DataOutputStream[] stripOut = new DataOutputStream[stripsPerImage];
    for (int i=0; i<stripsPerImage; i++) {
      stripBuf[i] = new ByteArrayOutputStream(stripSize);
      stripOut[i] = new DataOutputStream(stripBuf[i]);
    }

    // write pixel strips to output buffers
    for (int y=0; y<height; y++) {
      int strip = y / rowsPerStrip;
      for (int x=0; x<width; x++) {
        int ndx = y * width * bytesPerPixel + x * bytesPerPixel;
        for (int c=0; c<values.length; c++) {
          int q = values[c][ndx];
          if (bps[c] == 8) stripOut[strip].writeByte(q);
          else if (bps[c] == 16) {
            stripOut[strip].writeByte(q);
            stripOut[strip].writeByte(values[c][ndx+1]);
          }
          else if (bps[c] == 32) {
            for (int i=0; i<4; i++) {
              stripOut[strip].writeByte(values[c][ndx + i]);
            }
          }
          else {
            throw new FormatException("Unsupported bits per sample value (" +
              bps[c] + ")");
          }
        }
      }
    }

    // compress strips according to given differencing and compression schemes
    int planarConfig = getIFDIntValue(ifd, PLANAR_CONFIGURATION, false, 1);
    int predictor = getIFDIntValue(ifd, PREDICTOR, false, 1);
    int compression = getIFDIntValue(ifd, COMPRESSION, false, UNCOMPRESSED);
    byte[][] strips = new byte[stripsPerImage][];
    for (int i=0; i<stripsPerImage; i++) {
      strips[i] = stripBuf[i].toByteArray();
      difference(strips[i], bps, width, planarConfig, predictor);
      strips[i] = compress(strips[i], compression);
    }

    // record strip byte counts and offsets
    long[] stripByteCounts = new long[stripsPerImage];
    long[] stripOffsets = new long[stripsPerImage];
    putIFDValue(ifd, STRIP_OFFSETS, stripOffsets);
    putIFDValue(ifd, ROWS_PER_STRIP, rowsPerStrip);
    putIFDValue(ifd, STRIP_BYTE_COUNTS, stripByteCounts);

    Object[] keys = ifd.keySet().toArray();
    Arrays.sort(keys); // sort IFD tags in ascending order

    int keyCount = keys.length;
    if (ifd.containsKey(new Integer(LITTLE_ENDIAN))) keyCount--;
    if (ifd.containsKey(new Integer(BIG_TIFF))) keyCount--;

    int ifdBytes = (bigTiff ? 16 : 6) + (bigTiff ? 20 : 12) * keyCount;
    long pixelBytes = 0;
    for (int i=0; i<stripsPerImage; i++) {
      stripByteCounts[i] = strips[i].length;
      stripOffsets[i] = pixelBytes + offset + ifdBytes;
      pixelBytes += stripByteCounts[i];
    }

    // create IFD output buffers
    ByteArrayOutputStream ifdBuf = new ByteArrayOutputStream(ifdBytes);
    DataOutputStream ifdOut = new DataOutputStream(ifdBuf);
    ByteArrayOutputStream extraBuf = new ByteArrayOutputStream();
    DataOutputStream extraOut = new DataOutputStream(extraBuf);

    offset += ifdBytes + pixelBytes;

    // write IFD to output buffers

    // number of directory entries
    if (bigTiff) ifdOut.writeLong(keyCount);
    else ifdOut.writeShort(keyCount);
    for (int k=0; k<keys.length; k++) {
      Object key = keys[k];
      if (!(key instanceof Integer)) {
        throw new FormatException("Malformed IFD tag (" + key + ")");
      }
      if (((Integer) key).intValue() == LITTLE_ENDIAN) continue;
      if (((Integer) key).intValue() == BIG_TIFF) continue;
      Object value = ifd.get(key);
      if (DEBUG) {
        String sk = getIFDTagName(((Integer) key).intValue());
        String sv = value instanceof int[] ?
          ("int[" + ((int[]) value).length + "]") : value.toString();
        debug("writeImage: writing " + sk + " (value=" + sv + ")");
      }
      writeIFDValue(ifdOut, extraBuf, extraOut, offset,
        ((Integer) key).intValue(), value, bigTiff);
    }
    // offset to next IFD
    if (bigTiff) ifdOut.writeLong(last ? 0 : offset + extraBuf.size());
    else ifdOut.writeInt(last ? 0 : (int) (offset + extraBuf.size()));

    // flush buffers to output stream
    byte[] ifdArray = ifdBuf.toByteArray();
    byte[] extraArray = extraBuf.toByteArray();
    long numBytes = ifdArray.length + extraArray.length;
    out.write(ifdArray);
    for (int i=0; i<strips.length; i++) {
      out.write(strips[i]);
      numBytes += strips[i].length;
    }
    out.write(extraArray);
    out.flush();
    return numBytes;
  }

  /**
   * Retrieves the image's width (TIFF tag ImageWidth) from a given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the image's width.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static long getImageWidth(Hashtable ifd) throws FormatException {
    return getIFDLongValue(ifd, IMAGE_WIDTH, true, 0);
  }

  /**
   * Retrieves the image's length (TIFF tag ImageLength) from a given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the image's length.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static long getImageLength(Hashtable ifd) throws FormatException {
    return getIFDLongValue(ifd, IMAGE_LENGTH, true, 0);
  }

  /**
   * Retrieves the image's bits per sample (TIFF tag BitsPerSample) from a given
   * TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the image's bits per sample. The length of the array is equal to
   *   the number of samples per pixel.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   * @see #getSamplesPerPixel(Hashtable)
   */
  public static int[] getBitsPerSample(Hashtable ifd) throws FormatException {
    int[] bitsPerSample = getIFDIntArray(ifd, BITS_PER_SAMPLE, false);
    if (bitsPerSample == null) bitsPerSample = new int[] {1};
    return bitsPerSample;
  }

  /**
   * Retrieves the number of samples per pixel for the image (TIFF tag
   * SamplesPerPixel) from a given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the number of samples per pixel.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static int getSamplesPerPixel(Hashtable ifd) throws FormatException {
    return getIFDIntValue(ifd, SAMPLES_PER_PIXEL, false, 1);
  }

  /**
   * Retrieves the image's compression type (TIFF tag Compression) from a
   * given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the image's compression type. As of TIFF 6.0 this is one of:
   * <ul>
   *  <li>Uncompressed (1)</li>
   *  <li>CCITT 1D (2)</li>
   *  <li>Group 3 Fax (3)</li>
   *  <li>Group 4 Fax (4)</li>
   *  <li>LZW (5)</li>
   *  <li>JPEG (6)</li>
   *  <li>PackBits (32773)</li>
   * </ul>
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static int getCompression(Hashtable ifd) throws FormatException {
    return getIFDIntValue(ifd, COMPRESSION, false, UNCOMPRESSED);
  }

  /**
   * Retrieves the image's photometric interpretation (TIFF tag
   * PhotometricInterpretation) from a given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the image's photometric interpretation. As of TIFF 6.0 this is one
   * of:
   * <ul>
   *  <li>WhiteIsZero (0)</li>
   *  <li>BlackIsZero (1)</li>
   *  <li>RGB (2)</li>
   *  <li>RGB Palette (3)</li>
   *  <li>Transparency mask (4)</li>
   *  <li>CMYK (5)</li>
   *  <li>YbCbCr (6)</li>
   *  <li>CIELab (8)</li>
   * </ul>
   *
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static int getPhotometricInterpretation(Hashtable ifd)
    throws FormatException
  {
    return getIFDIntValue(ifd, PHOTOMETRIC_INTERPRETATION, true, 0);
  }

  /**
   * Retrieves the strip offsets for the image (TIFF tag StripOffsets) from a
   * given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the strip offsets for the image. The lenght of the array is equal
   *   to the number of strips per image. <i>StripsPerImage =
   *   floor ((ImageLength + RowsPerStrip - 1) / RowsPerStrip)</i>.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   * @see #getStripByteCounts(Hashtable)
   * @see #getRowsPerStrip(Hashtable)
   */
  public static long[] getStripOffsets(Hashtable ifd) throws FormatException {
    return getIFDLongArray(ifd, STRIP_OFFSETS, false);
  }

  /**
   * Retrieves strip byte counts for the image (TIFF tag StripByteCounts) from a
   * given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the byte counts for each strip. The length of the array is equal to
   *   the number of strips per image. <i>StripsPerImage =
   *   floor((ImageLength + RowsPerStrip - 1) / RowsPerStrip)</i>.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   * @see #getStripOffsets(Hashtable)
   */
  public static long[] getStripByteCounts(Hashtable ifd) throws FormatException
  {
    return getIFDLongArray(ifd, STRIP_BYTE_COUNTS, false);
  }

  /**
   * Retrieves the number of rows per strip for image (TIFF tag RowsPerStrip)
   * from a given TIFF IFD.
   * @param ifd a TIFF IFD hashtable.
   * @return the number of rows per strip.
   * @throws FormatException if there is a problem parsing the IFD metadata.
   */
  public static long[] getRowsPerStrip(Hashtable ifd) throws FormatException {
    return getIFDLongArray(ifd, ROWS_PER_STRIP, false);
  }

  // -- Compression methods --

  /** Encodes a strip of data with the given compression scheme. */
  public static byte[] compress(byte[] input, int compression)
    throws FormatException, IOException
  {
    if (compression == UNCOMPRESSED) return input;
    else if (compression == CCITT_1D) {
      throw new FormatException(
        "Sorry, CCITT Group 3 1-Dimensional Modified Huffman " +
        "run length encoding compression mode is not supported");
    }
    else if (compression == GROUP_3_FAX) {
      throw new FormatException("Sorry, CCITT T.4 bi-level encoding " +
        "(Group 3 Fax) compression mode is not supported");
    }
    else if (compression == GROUP_4_FAX) {
      throw new FormatException("Sorry, CCITT T.6 bi-level encoding " +
        "(Group 4 Fax) compression mode is not supported");
    }
    else if (compression == LZW) {
      LZWCodec c = new LZWCodec();
      return c.compress(input, 0, 0, null, null);
      // return Compression.lzwCompress(input);
    }

    else if (compression == JPEG) {
      throw new FormatException(
        "Sorry, JPEG compression mode is not supported");
    }
    else if (compression == PACK_BITS) {
      throw new FormatException(
        "Sorry, PackBits compression mode is not supported");
    }
    else {
      throw new FormatException(
        "Unknown Compression type (" + compression + ")");
    }
  }

  /** Performs in-place differencing according to the given predictor value. */
  public static void difference(byte[] input, int[] bitsPerSample,
    long width, int planarConfig, int predictor) throws FormatException
  {
    if (predictor == 2) {
      if (DEBUG) debug("performing horizontal differencing");
      for (int b=input.length-1; b>=0; b--) {
        if (b / bitsPerSample.length % width == 0) continue;
        input[b] -= input[b - bitsPerSample.length];
      }
    }
    else if (predictor != 1) {
      throw new FormatException("Unknown Predictor (" + predictor + ")");
    }
  }

  // -- Debugging --

  /** Prints a debugging message with current time. */
  public static void debug(String message) {
    LogTools.println(System.currentTimeMillis() + ": " + message);
  }

}
