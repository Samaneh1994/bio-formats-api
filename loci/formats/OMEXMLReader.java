//
// OMEXMLReader.java
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
import java.io.*;
import java.util.Hashtable;
import java.util.Vector;
import java.util.zip.*;

/**
 * OMEXMLReader is the file format reader for OME-XML files.
 *
 * @author Melissa Linkert linkert at cs.wisc.edu
 */
public class OMEXMLReader extends FormatReader {

  // -- Constants --

  private static final int FOURBYTE = 4;
  private static final byte PAD = (byte) '=';

  private static byte[] base64Alphabet = new byte[255];

  static {
    for (int i=0; i<255; i++) {
      base64Alphabet[i] = (byte) -1;
    }
    for (int i = 'Z'; i >= 'A'; i--) {
      base64Alphabet[i] = (byte) (i - 'A');
    }
   for (int i = 'z'; i >= 'a'; i--) {
     base64Alphabet[i] = (byte) (i - 'a' + 26);
   }
   for (int i = '9'; i >= '0'; i--) {
     base64Alphabet[i] = (byte) (i - '0' + 52);
   }

   base64Alphabet['+'] = 62;
   base64Alphabet['/'] = 63;
  }


  // -- Fields --

  /** Current file. */
  protected RandomAccessFile in;

  /** Flag indicating whether current file is little endian. */
  protected boolean littleEndian;

  /** Number of image planes in the file. */
  protected int numImages = 0;

  /** Number of bits per pixel. */
  protected int bpp = 1;

  /** Offset to each plane's data. */
  protected Vector offsets;

  /** String indicating the compression type. */
  protected String compression;


  // -- Constructor --

  /** Constructs a new OME-XML reader. */
  public OMEXMLReader() { super("OME-XML", "ome"); }


  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for an OME-XML file. */
  public boolean isThisType(byte[] block) {
    return false;
  }

  /** Determines the number of images in the given OME-XML file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return numImages;
  }

  /** Obtains the specified image from the given OME-XML file. */
  public Image open(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    int width =
      Integer.parseInt(OMETools.getAttribute(ome, "Pixels", "SizeX"));
    int height =
      Integer.parseInt(OMETools.getAttribute(ome, "Pixels", "SizeY"));
    int channels = 1;

    if (no == 0) {
      in.seek(((Integer) offsets.get(no)).intValue());
    }

    byte[] buf;
    if (no < getImageCount(id) - 1) {
      buf = new byte[((Integer) offsets.get(no+1)).intValue() -
        (int) in.getFilePointer()];
    }
    else {
      buf = new byte[(int) (in.length() - in.getFilePointer())];
    }
    in.read(buf);
    String data = new String(buf);

    // retrieve the compressed pixel data

    int dataStart = data.indexOf(">") + 1;
    String pix = data.substring(dataStart);
    pix = pix.substring(0, pix.indexOf("<"));

    byte[] pixels = decode(pix);

    if (compression.equals("bzip2")) {
      byte[] tempPixels = pixels;
      pixels = new byte[tempPixels.length - 2];
      System.arraycopy(tempPixels, 2, pixels, 0, pixels.length);

      ByteArrayInputStream bais = new ByteArrayInputStream(pixels);
      CBZip2InputStream bzip = new CBZip2InputStream(bais);
      pixels = new byte[width*height*bpp];
      for (int i=0; i<pixels.length; i++) {
        pixels[i] = (byte) bzip.read();
      }
    }
    else if (compression.equals("zlib")) {
      try {
        Inflater decompressor = new Inflater();
        decompressor.setInput(pixels, 0, pixels.length);
        pixels = new byte[width * height * bpp];
        int resultLength = decompressor.inflate(pixels);
        decompressor.end();
      }
      catch (DataFormatException dfe) {
        throw new FormatException("Error uncompressing zlib data.");
      }
    }

    // handle varying bytes per pixel

    if (bpp == 2) {
      short[] bytes = new short[pixels.length / 2];
      for (int i=0; i<pixels.length; i+=2) {
        if ((i/2) < bytes.length) {
          bytes[i/2] = DataTools.bytesToShort(pixels, i, littleEndian);
        }
      }
      return DataTools.makeImage(bytes, width, height, channels, false);
    }
    else if (bpp == 4) {
      int[] bytes = new int[pixels.length / 4];
      for (int i=0; i<pixels.length; i+=4) {
        bytes[i/4] = DataTools.bytesToInt(pixels, i, littleEndian);
      }
    }

    return DataTools.makeImage(pixels, width, height, channels, false);
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    if (in != null) in.close();
    in = null;
    currentId = null;
  }

  /** Initializes the given OME-XML file. */
  protected void initFile(String id) throws FormatException, IOException {
    close();
    currentId = id;
    metadata = new Hashtable();
    in = new RandomAccessFile(id, "r");
    offsets = new Vector();

    in.seek(500);

    // read a block of 100 characters, looking for the "BigEndian" pattern
    byte[] buf;
    boolean found = false;
    while (!found) {
      buf = new byte[100];
      in.read(buf);
      String test = new String(buf);

      int ndx = test.indexOf("BigEndian");
      if (ndx != -1) {
        found = true;
        String endian = test.substring(ndx + 11);
        if (endian.toLowerCase().endsWith("t")) littleEndian = false;
        else littleEndian = true;
      }
    }

    in.seek(500);

    // look for the first BinData element

    found = false;
    while (!found && in.getFilePointer() < in.length()) {
      buf = new byte[100];
      in.read(buf);
      String test = new String(buf);

      int ndx = test.indexOf("<Bin");
      if (ndx != -1) {
        if (ndx != test.indexOf("<Bin:External")) {
          found = true;
          offsets.add(new Integer((int) in.getFilePointer() - (100 - ndx)));
        }
      }
    }

    if (!found) {
      throw new FormatException("Pixel data not found");
    }

    in.seek(0);
    buf = new byte[((Integer) offsets.get(0)).intValue()];
    in.read(buf);
    String xml = new String(buf);
    xml += "</Pixels></Image></OME>";  // might lose some data this way

    ome = OMETools.createRoot(xml);

    int sizeX = 0;
    int sizeY = 0;

    if (ome != null) {
      String type = OMETools.getAttribute(ome, "Pixels", "PixelType");
      if (type.endsWith("16")) bpp = 2;
      else if (type.endsWith("32")) bpp = 4;
      else if (type.equals("float")) bpp = 8;

      sizeX = Integer.parseInt(OMETools.getAttribute(ome, "Pixels", "SizeX"));
      sizeY = Integer.parseInt(OMETools.getAttribute(ome, "Pixels", "SizeY"));
    }
    else {
      throw new FormatException("To use this feature, please install the " +
       "loci.ome.xml package, available from http://www.loci.wisc.edu/ome/");
    }


    // calculate the number of raw bytes of pixel data that we are expecting
    int expected = sizeX * sizeY * bpp;

    // find the compression type and adjust 'expected' accordingly

    in.seek(((Integer) offsets.get(0)).intValue());
    buf = new byte[400];
    in.read(buf);
    String data = new String(buf);

    int compressionStart = data.indexOf("Compression") + 13;
    int compressionEnd = data.indexOf("\"", compressionStart);
    if (compressionStart != -1 && compressionEnd != -1) {
      compression = data.substring(compressionStart, compressionEnd);
    }
    else compression = "none";

    if (!compression.equals("none")) {
      expected /= 2;
    }

    int iteration = 0;
    while (in.getFilePointer() < in.length()) {
      in.skipBytes(expected);

      // look for next BinData element
      found = false;
      buf = new byte[100];
      while (!found && in.getFilePointer() < in.length()) {
        in.read(buf);
        String test = new String(buf);

        int ndx = test.indexOf("<Bin");
        if (ndx != -1) {
          found = true;
          offsets.add(new Integer((int) in.getFilePointer() - (100 - ndx)));
        }
      }

      iteration++;
    }

    numImages = offsets.size();
  }


  // -- Helper methods --

  /**
   * Decodes a Base64 encoded String.
   * Much of this code was adapted from the Apache Commons Codec source.
   */
  private byte[] decode(String s) throws FormatException {
    byte[] base64Data = s.getBytes();

    if (base64Data.length == 0) return new byte[0];

    int numberQuadruple = base64Data.length / FOURBYTE;
    byte[] decodedData = null;
    byte b1 = 0, b2 = 0, b3 = 0, b4 = 0, marker0 = 0, marker1 = 0;

    int encodedIndex = 0;
    int dataIndex = 0;

    int lastData = base64Data.length;
    while (base64Data[lastData - 1] == PAD) {
      if (--lastData == 0) {
        return new byte[0];
      }
    }
    decodedData = new byte[lastData - numberQuadruple];

    for (int i=0; i<numberQuadruple; i++) {
      dataIndex = i * 4;
      marker0 = base64Data[dataIndex + 2];
      marker1 = base64Data[dataIndex + 3];

      b1 = base64Alphabet[base64Data[dataIndex]];
      b2 = base64Alphabet[base64Data[dataIndex + 1]];

      if (marker0 != PAD && marker1 != PAD) {
        b3 = base64Alphabet[marker0];
        b4 = base64Alphabet[marker1];

        decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
        decodedData[encodedIndex + 1] =
          (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
        decodedData[encodedIndex + 2] = (byte) (b3 << 6 | b4);
      }
      else if (marker0 == PAD) {
        decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
      }
      else if (marker1 == PAD) {
        b3 = base64Alphabet[marker0];

        decodedData[encodedIndex] = (byte) (b1 << 2 | b2 >> 4);
        decodedData[encodedIndex + 1] =
          (byte) (((b2 & 0xf) << 4) | ((b3 >> 2) & 0xf));
      }
      encodedIndex += 3;
    }
    return decodedData;
  }


  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new OMEXMLReader().testRead(args);
  }

}
