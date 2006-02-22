//
// DataTools.java
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

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * A utility class with convenience methods for word decoding.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public abstract class DataTools {

  // -- Image construction --

  /**
   * Creates an image from the given unsigned byte data.
   * If the interleaved flag is set, the channels are assumed to be
   * interleaved; otherwise they are assumed to be sequential.
   * For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   * while "RRR...GGG...BBB..." is sequential.
   */
  public static BufferedImage makeImage(byte[] data,
    int w, int h, int c, boolean interleaved)
  {
    int dataType = DataBuffer.TYPE_BYTE;
    ColorModel colorModel = makeColorModel(c, dataType);
    if (colorModel == null) return null;
    int pixelStride = interleaved ? c : 1;
    int scanlineStride = interleaved ? (c * w) : w;
    int[] bandOffsets = new int[c];
    for (int i=0; i<c; i++) bandOffsets[i] = interleaved ? i : (i * w * h);
    SampleModel model = new ComponentSampleModel(dataType,
      w, h, pixelStride, scanlineStride, bandOffsets);
    DataBuffer buffer = new DataBufferByte(data, c * w * h);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /**
   * Creates an image from the given unsigned short data.
   * If the interleaved flag is set, the channels are assumed to be
   * interleaved; otherwise they are assumed to be sequential.
   * For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   * while "RRR...GGG...BBB..." is sequential.
   */
  public static BufferedImage makeImage(short[] data,
    int w, int h, int c, boolean interleaved)
  {
    int dataType = DataBuffer.TYPE_USHORT;
    ColorModel colorModel = makeColorModel(c, dataType);
    if (colorModel == null) return null;
    int pixelStride = interleaved ? c : 1;
    int scanlineStride = interleaved ? (c * w) : w;
    int[] bandOffsets = new int[c];
    for (int i=0; i<c; i++) bandOffsets[i] = interleaved ? i : (i * w * h);
    SampleModel model = new ComponentSampleModel(dataType,
      w, h, pixelStride, scanlineStride, bandOffsets);
    DataBuffer buffer = new DataBufferUShort(data, c * w * h);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /**
   * Creates an image from the given signed int data.
   * If the interleaved flag is set, the channels are assumed to be
   * interleaved; otherwise they are assumed to be sequential.
   * For example, for RGB data, the pattern "RGBRGBRGB..." is interleaved,
   * while "RRR...GGG...BBB..." is sequential.
   */
  public static BufferedImage makeImage(int[] data,
    int w, int h, int c, boolean interleaved)
  {
    int dataType = DataBuffer.TYPE_INT;
    ColorModel colorModel = makeColorModel(c, dataType);
    if (colorModel == null) return null;
    int pixelStride = interleaved ? c : 1;
    int scanlineStride = interleaved ? (c * w) : w;
    int[] bandOffsets = new int[c];
    for (int i=0; i<c; i++) bandOffsets[i] = interleaved ? i : (i * w * h);
    SampleModel model = new ComponentSampleModel(dataType,
      w, h, pixelStride, scanlineStride, bandOffsets);
    DataBuffer buffer = new DataBufferInt(data, c * w * h);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /**
   * Creates an image from the given unsigned byte data.
   * It is assumed that each channel corresponds to one element of the array.
   * For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   */
  public static BufferedImage makeImage(byte[][] data, int w, int h) {
    int dataType = DataBuffer.TYPE_BYTE;
    ColorModel colorModel = makeColorModel(data.length, dataType);
    if (colorModel == null) return null;
    SampleModel model = new BandedSampleModel(dataType, w, h, data.length);
    DataBuffer buffer = new DataBufferByte(data, data[0].length);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /**
   * Creates an image from the given unsigned short data.
   * It is assumed that each channel corresponds to one element of the array.
   * For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   */
  public static BufferedImage makeImage(short[][] data, int w, int h) {
    int dataType = DataBuffer.TYPE_USHORT;
    ColorModel colorModel = makeColorModel(data.length, dataType);
    if (colorModel == null) return null;
    SampleModel model = new BandedSampleModel(dataType, w, h, data.length);
    DataBuffer buffer = new DataBufferUShort(data, data[0].length);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /**
   * Creates an image from the given signed int data.
   * It is assumed that each channel corresponds to one element of the array.
   * For example, for RGB data, data[0] is R, data[1] is G, and data[2] is B.
   */
  public static BufferedImage makeImage(int[][] data, int w, int h) {
    int dataType = DataBuffer.TYPE_INT;
    ColorModel colorModel = makeColorModel(data.length, dataType);
    if (colorModel == null) return null;
    SampleModel model = new BandedSampleModel(dataType, w, h, data.length);
    DataBuffer buffer = new DataBufferInt(data, data[0].length);
    WritableRaster raster = Raster.createWritableRaster(model, buffer, null);
    return new BufferedImage(colorModel, raster, false, null);
  }

  /** Gets a color space for the given number of color components. */
  public static ColorModel makeColorModel(int c, int dataType) {
    int type;
    switch (c) {
      case 1: type = ColorSpace.CS_GRAY; break;
      case 3: type = ColorSpace.CS_sRGB; break;
      case 4: type = ColorSpace.CS_sRGB; break;
      default: return null;
    }
    return new ComponentColorModel(ColorSpace.getInstance(type),
      c == 4, false, ColorModel.TRANSLUCENT, dataType);
  }


  // -- Data reading --

  /** Reads bytes from the given random access file or array. */
  public static void readFully(RandomAccessFile in, byte[] bytes)
    throws IOException
  {
    if (in instanceof RandomAccessArray) {
      ((RandomAccessArray) in).copyArray(bytes);
    }
    else in.readFully(bytes);
  }

  /** Reads 1 signed byte [-128, 127]. */
  public static byte readSignedByte(RandomAccessFile in) throws IOException {
    byte[] b = new byte[1];
    readFully(in, b);
    return b[0];
  }

  /** Reads 1 unsigned byte [0, 255]. */
  public static short readUnsignedByte(RandomAccessFile in)
    throws IOException
  {
    short q = readSignedByte(in);
    if (q < 0) q += 256;
    return q;
  }

  /** Reads 2 signed bytes [-32768, 32767]. */
  public static short read2SignedBytes(RandomAccessFile in, boolean little)
    throws IOException
  {
    byte[] bytes = new byte[2];
    readFully(in, bytes);
    return bytesToShort(bytes, little);
  }

  /** Reads 2 unsigned bytes [0, 65535]. */
  public static int read2UnsignedBytes(RandomAccessFile in, boolean little)
    throws IOException
  {
    int q = read2SignedBytes(in, little);
    if (q < 0) q += 65536;
    return q;
  }

  /** Reads 4 signed bytes [-2147483648, 2147483647]. */
  public static int read4SignedBytes(RandomAccessFile in, boolean little)
    throws IOException
  {
    byte[] bytes = new byte[4];
    readFully(in, bytes);
    return bytesToInt(bytes, little);
  }

  /** Reads 4 unsigned bytes [0, 4294967296]. */
  public static long read4UnsignedBytes(RandomAccessFile in, boolean little)
    throws IOException
  {
    long q = read4SignedBytes(in, little);
    if (q < 0) q += 4294967296L;
    return q;
  }

  /** Reads 8 signed bytes [-9223372036854775808, 9223372036854775807]. */
  public static long read8SignedBytes(RandomAccessFile in, boolean little)
    throws IOException
  {
    byte[] bytes = new byte[8];
    readFully(in, bytes);
    return bytesToLong(bytes, little);
  }

  /** Reads 4 bytes in single precision IEEE format. */
  public static float readFloat(RandomAccessFile in, boolean little)
    throws IOException
  {
    return Float.intBitsToFloat(read4SignedBytes(in, little));
  }

  /** Reads 8 bytes in double precision IEEE format. */
  public static double readDouble(RandomAccessFile in, boolean little)
    throws IOException
  {
    return Double.longBitsToDouble(read8SignedBytes(in, little));
  }


  // -- Data writing --
  
  /** Writes a string to the given random access file. */
  public static void writeString(RandomAccessFile out, String s) 
    throws IOException
  {
    byte[] bytes =  s.getBytes("UTF-8");
    out.write(bytes);
  }

  /**
   * Writes an integer to the given random access file
   * in little-endian format.
   */
  public static void writeInt(RandomAccessFile out, int v) 
    throws IOException
  {
    out.write(v & 0xFF);
    out.write((v >>> 8) & 0xFF);
    out.write((v >>> 16) & 0xFF);
    out.write((v >>> 24) & 0xFF);
  }

  /**
   * Writes a short to the given random access file
   * in little-endian format.
   */
  public static void writeShort(RandomAccessFile out, int v) 
    throws IOException
  {        
    out.write(v & 0xFF);
    out.write((v >>> 8) & 0xFF);
  }


  // -- Word decoding --

  /**
   * Translates up to the first len bytes of a byte array beyond the given
   * offset to a short. If there are fewer than 2 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(byte[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    short total = 0;
    for (int i=0, ndx=off; i<len; i++, ndx++) {
      total |= (bytes[ndx] < 0 ? 256 + bytes[ndx] :
        (int) bytes[ndx]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 2 bytes of a byte array beyond the given
   * offset to a short. If there are fewer than 2 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(byte[] bytes, int off, boolean little) {
    return bytesToShort(bytes, off, 2, little);
  }

  /**
   * Translates up to the first 2 bytes of a byte array to a short.
   * If there are fewer than 2 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(byte[] bytes, boolean little) {
    return bytesToShort(bytes, 0, 2, little);
  }

  /**
   * Translates up to the first len bytes of a byte array byond the given
   * offset to a short. If there are fewer than 2 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(short[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    short total = 0;
    for (int i=0, ndx=off; i<len; i++, ndx++) {
      total |= ((int) bytes[ndx]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 2 bytes of a byte array byond the given
   * offset to a short. If there are fewer than 2 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(short[] bytes, int off, boolean little) {
    return bytesToShort(bytes, off, 2, little);
  }

  /**
   * Translates up to the first 2 bytes of a byte array to a short.
   * If there are fewer than 2 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static short bytesToShort(short[] bytes, boolean little) {
    return bytesToShort(bytes, 0, 2, little);
  }

  /**
   * Translates up to the first len bytes of a byte array beyond the given
   * offset to an int. If there are fewer than 4 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(byte[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    int total = 0;
    for (int i=0, ndx=off; i<len; i++, ndx++) {
      total |= (bytes[ndx] < 0 ? 256 + bytes[ndx] :
        (int) bytes[ndx]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 4 bytes of a byte array beyond the given
   * offset to an int. If there are fewer than 4 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(byte[] bytes, int off, boolean little) {
    return bytesToInt(bytes, off, 4, little);
  }

  /**
   * Translates up to the first 4 bytes of a byte array to an int.
   * If there are fewer than 4 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(byte[] bytes, boolean little) {
    return bytesToInt(bytes, 0, 4, little);
  }

  /**
   * Translates up to the first len bytes of a byte array beyond the given
   * offset to an int. If there are fewer than 4 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(short[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    int total = 0;
    for (int i=0, ndx=off; i<len; i++, ndx++) {
      total |= ((int) bytes[ndx]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 4 bytes of a byte array beyond the given
   * offset to an int. If there are fewer than 4 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(short[] bytes, int off, boolean little) {
    return bytesToInt(bytes, off, 4, little);
  }

  /**
   * Translates up to the first 4 bytes of a byte array to an int.
   * If there are fewer than 4 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static int bytesToInt(short[] bytes, boolean little) {
    return bytesToInt(bytes, 0, 4, little);
  }

  /**
   * Translates up to the first len bytes of a byte array beyond the given
   * offset to a long. If there are fewer than 8 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(byte[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    long total = 0;
    for (int i=0; i<len; i++) {
      total |= (bytes[i] < 0 ? 256L + bytes[i] :
        (long) bytes[i]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 8 bytes of a byte array beyond the given
   * offset to a long. If there are fewer than 8 bytes in the array,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(byte[] bytes, int off, boolean little) {
    return bytesToLong(bytes, off, 8, little);
  }

  /**
   * Translates up to the first 8 bytes of a byte array to a long.
   * If there are fewer than 8 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(byte[] bytes, boolean little) {
    return bytesToLong(bytes, 0, 8, little);
  }

  /**
   * Translates up to the first len bytes of a byte array beyond the given
   * offset to a long. If there are fewer than 8 bytes to be translated,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(short[] bytes, int off, int len,
    boolean little)
  {
    if (bytes.length - off < len) len = bytes.length - off;
    long total = 0;
    for (int i=0, ndx=off; i<len; i++, ndx++) {
      total |= ((long) bytes[ndx]) << ((little ? i : len - i - 1) * 8);
    }
    return total;
  }

  /**
   * Translates up to the first 8 bytes of a byte array beyond the given
   * offset to a long. If there are fewer than 8 bytes to be translated,
   * the MSBs are all assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(short[] bytes, int off, boolean little) {
    return bytesToLong(bytes, off, 8, little);
  }

  /**
   * Translates up to the first 8 bytes of a byte array to a long.
   * If there are fewer than 8 bytes in the array, the MSBs are all
   * assumed to be zero (regardless of endianness).
   */
  public static long bytesToLong(short[] bytes, boolean little) {
    return bytesToLong(bytes, 0, 8, little);
  }

  /** Translates bytes from the given array into a string. */
  public static String bytesToString(short[] bytes, int off, int len) {
    if (bytes.length - off < len) len = bytes.length - off;
    for (int i=0; i<len; i++) {
      if (bytes[off + i] == 0) {
        len = i;
        break;
      }
    }
    byte[] b = new byte[len];
    for (int i=0; i<b.length; i++) b[i] = (byte) bytes[off + i];
    return new String(b);
  }

  /** Translates bytes from the given array into a string. */
  public static String bytesToString(short[] bytes, int off) {
    return bytesToString(bytes, off, bytes.length - off);
  }

  /** Translates bytes from the given array into a string. */
  public static String bytesToString(short[] bytes) {
    return bytesToString(bytes, 0, bytes.length);
  }
  
}
