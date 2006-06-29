//
// ZeissZVIReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

/**
 * ZeissZVIReader is the file format reader for Zeiss ZVI files.
 *
 * @author Melissa Linkert linkert at cs.wisc.edu
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class ZeissZVIReader extends FormatReader {

  // -- Fields --

  private LegacyZVIReader legacy;
  private Hashtable pixelData = new Hashtable();
  private Hashtable headerData = new Hashtable();
  private byte[] header; // general image header data
  private int nImages = 0;  // number of images
  private Vector tags;  // tags data
  private int channels;

  // -- Fields used by parseDir --
  private int counter = 0;  // the number of the Image entry
  private int imageWidth = 0;
  private int imageHeight = 0;
  private int bytesPerPixel = 0;
  private int pixelFormat;

  private boolean needLegacy;
  private int width;
  private int height;
  private int bitsPerSample;
  private int dataLength;
  private int len;
  private int shuffle;
  private int previousCut;

  // -- Constructor --

  /** Constructs a new Zeiss ZVI reader. */
  public ZeissZVIReader() { super("Zeiss Vision Image", "zvi"); }


  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for a Zeiss ZVI file. */
  public boolean isThisType(byte[] block) {
    return OLEParser.isOLE(block);
  }

  /** Determines the number of images in the given Zeiss ZVI file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    if (needLegacy) return legacy.getImageCount(id);
    return (isRGB(id) && separated) ? (3 * nImages) : nImages;
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    if (needLegacy) return legacy.isRGB(id);
    return channels > 1;
  }

  /** Returns the number of channels in the file. */
  public int getChannelCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return channels;
  }

  /** Obtains the specified image from the given ZVI file, as a byte array. */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    if (needLegacy) return legacy.openBytes(id, no);

    // read image header data

    try {
      int c = (isRGB(id) && separated) ? 3 : 1;
      byte[] imageHead = (byte[]) headerData.get(new Integer(no / c));

      if (imageHead != null) {
        int pointer = 14;
        int numBytes = DataTools.bytesToInt(imageHead, pointer, 2, true);
        pointer += 2 + numBytes;

        pointer += 2;
        width = DataTools.bytesToInt(imageHead, pointer, 4, true);
        pointer += 4 + 2;

        height = DataTools.bytesToInt(imageHead, pointer, 4, true);
        pointer += 4 + 2;

        int depth = DataTools.bytesToInt(imageHead, pointer, 4, true);
        pointer += 4 + 2;

        pixelFormat = DataTools.bytesToInt(imageHead, pointer, 4, true);
        pointer += 4 + 2;

        pointer += 6; // count field is always 0

        int validBPP = DataTools.bytesToInt(imageHead, pointer, 4, true);
        pointer += 4 + 2;

        // read image bytes and convert to floats

        pointer = 0;
        int numSamples = width*height;
        bitsPerSample = validBPP;

        switch (pixelFormat) {
          case 1: channels = 3; break;
          case 2: channels = 4; break;
          case 3: channels = 1; break;
          case 4: channels = 1; break;
          case 6: channels = 1; break;
          case 8: channels = 3; break;
          default: channels = 1;
        }

        if ((width > imageWidth) && (imageWidth > 0)) {
          width = imageWidth;
          height = imageHeight;
          bitsPerSample = bytesPerPixel * 8;
        }
      }
      else {
        width = imageWidth;
        height = imageHeight;
        bitsPerSample = bytesPerPixel * 8;
      }

      byte[] px = (byte[]) pixelData.get(new Integer(no / c));
      byte[] tempPx = new byte[px.length];

      if (bitsPerSample > 64) { bitsPerSample = 8; }

      int bpp = bitsPerSample / 8;

      // chop any extra bytes off of the pixel array

      if (px.length > (width * height * bpp)) {
        int check = 0;
        int chop = 0;
        while (check != imageWidth && chop < 4000) {
          check = DataTools.bytesToInt(px, chop, 4, true);
          chop++;
        }
        chop += 23;
        if (bpp == 2 && (chop % 2 != 0)) chop++;

        if (check != imageWidth) chop = 0;

        if (chop > 0) {
          byte[] tmp = new byte[px.length - chop];
          System.arraycopy(px, px.length - tmp.length, tmp, 0, tmp.length);
          px = tmp;
        }
      }

      if (bpp == 3) {
        // reverse the channels

        int off = 0;
        int length = width * 3;

        for (int i=0; i<height; i++) {
          for (int j=0; j<width; j++) {
            tempPx[off + j*3] = px[off + j*3 + 2];
            tempPx[off + j*3 + 1] = px[off + j*3 + 1];
            tempPx[off + j*3 + 2] = px[off + j*3];
          }
          off += length;
        }
      }
      else if (bpp != 6) tempPx = px;
      else {
        // slight hack
        int mul = (int) (0.32 * imageWidth * bpp);
        for (int i=0; i<imageHeight; i++) {
          System.arraycopy(px, i*width*bpp, tempPx, (i+1)*width*bpp - mul, mul);
          System.arraycopy(px, i*width*bpp + mul, tempPx, i*width*bpp,
            width*bpp - mul);
        }

        px = tempPx;
        tempPx = new byte[px.length];

        // reverse the channels
        int off = 0;
        int length = width * bpp;

        for (int i=0; i<height; i++) {
          for (int j=0; j<width; j++) {
            tempPx[off + j*6] = px[off + j*6 + 4];
            tempPx[off + j*6 + 1] = px[off + j*6 + 5];
            tempPx[off + j*6 + 2] = px[off + j*6 + 2];
            tempPx[off + j*6 + 3] = px[off + j*6 + 3];
            tempPx[off + j*6 + 4] = px[off + j*6];
            tempPx[off + j*6 + 5] = px[off + j*6 + 1];
          }
          off += length;
        }
      }

      // reverse row order

      if ((bitsPerSample / 8) % 3 != 0 && (bitsPerSample != 8)) {
        px = new byte[tempPx.length];
        int off = (height - 1) * width * bpp;
        int newOff = 0;
        int length = width * bpp;
        for (int i=0; i<height; i++) {
          System.arraycopy(tempPx, off, px, newOff, length);
          off -= length;
          newOff += length;
        }
        tempPx = px;
      }

      if (!isRGB(id) || !separated) {
        return tempPx;
      }
      else {
        return ImageTools.splitChannels(tempPx, 3, false, true)[no % 3];
      }
    }
    catch (Exception e) {
      needLegacy = true;
      return legacy.openBytes(id, no);
    }
  }

  /** Obtains the specified image from the given Zeiss ZVI file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    if (needLegacy) return legacy.openImage(id, no);
    byte[] data = openBytes(id, no);
    int bpp = bitsPerSample / 8;
    if (bpp == 0) bpp = bytesPerPixel;
    if (bpp > 4) bpp /= 3;
    return ImageTools.makeImage(data, width, height,
      (!isRGB(id) || separated) ? 1 : 3, true, bpp, false);
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    header = null;
    tags = null;
    pixelData = null;
    headerData = null;
  }

  /** Initializes the given Zeiss ZVI file. */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);
    legacy = new LegacyZVIReader();

    OLEParser parser = new OLEParser(id);
    parser.parse(0);
    Vector[] files = parser.getFiles();

    headerData = new Hashtable();
    pixelData = new Hashtable();
    tags = new Vector();

    for (int i=0; i<files[1].size(); i++) {
      String path = (String) files[0].get(i);
      path = DataTools.stripString(path);
      if (path.endsWith("Tags/Contents")) {
        tags.add(files[1].get(i));
      }
    }

    int largest = 0;
    int largestIndex = 0;

    int nextItem = 0;
    Vector itemNames = parser.getNames();

    for (int i=0; i<files[0].size(); i++) {
      byte[] data = (byte[]) files[1].get(i);
      if (data.length > largest) largestIndex = i;

      String pathName = ((String) files[0].get(i)).trim();
      pathName = DataTools.stripString(pathName);

      boolean isContents = pathName.endsWith("Contents");
      boolean isImage = pathName.endsWith("Image");

      try {
        if (((isContents &&
          ((pathName.indexOf("Item") != -1) || pathName.indexOf("Image") != -1)
          && data.length > 6000)) || (data.length == dataLength))
        {
          header = data;

          while ((dataLength != 0) && (data.length < dataLength) && isContents
            && ((pathName.indexOf("Item") != -1) ||
            pathName.indexOf("Image") != -1))
          {
            i++;
            data = (byte[]) files[1].get(i);
            if (data.length > largest) largestIndex = i;

            pathName = ((String) files[0].get(i)).trim();
            pathName = DataTools.stripString(pathName);
            isContents = pathName.endsWith("Contents");
          }

          int imageNum = 0;
          if (pathName.indexOf("Item") != -1) {
            String num = pathName.substring(pathName.lastIndexOf("Item") + 5,
              pathName.lastIndexOf(")"));
            imageNum = Integer.parseInt(num);
          }
          if (nextItem < itemNames.size()) {
            String num = ((String) itemNames.get(nextItem)).trim();
            if (num.length() > 1) num = DataTools.stripString(num);
            int n = Integer.parseInt(num);

            // choose whether to use imageNum or n

            if (n != imageNum) {
              if (pixelData.containsKey(new Integer(imageNum))) {
                imageNum = n;
              }
            }

            if (pathName.indexOf("Item") != -1) {
              num = pathName.substring(0, pathName.lastIndexOf("Item"));
              while (pixelData.containsKey(new Integer(imageNum)) &&
                (num.indexOf("Item") != -1))
              {
                String s = num.substring(num.lastIndexOf("Item") + 5,
                  num.lastIndexOf(")"));
                imageNum = Integer.parseInt(s);
                num = num.substring(0, num.lastIndexOf("Item"));
              }
            }

            // if we *still* don't find a valid key, give up and use
            // the legacy reader

            if (pixelData.containsKey(new Integer(imageNum))) {
              if (legacy.getImageCount(id) == 1) break;
              needLegacy = true;
              legacy.initFile(id);
              return;
            }

            nextItem++;
          }

          int byteCount = 2;
          byteCount += 4; // version field
          byteCount += 6; // type field
          byteCount += 2;
          int numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
          byteCount += 2 + numBytes;

          byteCount += 2;
          imageWidth = DataTools.bytesToInt(header, byteCount, 4, true);
          byteCount += 6;
          imageHeight = DataTools.bytesToInt(header, byteCount, 4, true);
          byteCount += 4;

          byteCount += 6; // depth
          byteCount += 6; // pixel format
          byteCount += 6; // count

          byteCount += 2;
          bitsPerSample = DataTools.bytesToInt(header, byteCount, 4, true);
          byteCount += 4;

          numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
          byteCount += 2 + numBytes; // plugin CLSID
          byteCount += 38; // not sure what this is for

          byteCount += 2;
          numBytes = DataTools.bytesToInt(header, byteCount, 4, true);
          byteCount += 4 + numBytes; // layers

          byteCount += 2;
          numBytes = DataTools.bytesToInt(header, byteCount, 4, true);
          byteCount += 4 + numBytes; // scaling

          byteCount += 2;
          numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
          byteCount += 2 + numBytes; // root folder name

          byteCount += 2;
          numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
          byteCount += 2 + numBytes; // display item name

          byteCount += 28; // streamed header data

          // get pixel data

          if (header.length > byteCount) {
            byte[] head = new byte[byteCount];
            System.arraycopy(header, 0, head, 0, head.length);
            headerData.put(new Integer(imageNum), (Object) head);
            byte[] px = new byte[header.length - byteCount];
            System.arraycopy(header, byteCount, px, 0, px.length);

            if (!pixelData.containsKey(new Integer(imageNum))) {
              shuffle = parser.shuffle();

              // nasty special case...I pity the person who finds a bug in this
              if (shuffle > 0) {
                byte[] chunkOne = new byte[shuffle];
                byte[] chunkTwo = new byte[parser.length() - shuffle + 11700];
                System.arraycopy(px, 0, chunkOne, 0, chunkOne.length);
                System.arraycopy(px, chunkOne.length, chunkTwo, 0,
                  chunkTwo.length);

                byte[] tct = new byte[chunkOne.length];
                int bpp = bitsPerSample / 8;
                int mul = (int) (imageWidth - (imageWidth * 0.01));
                mul *= bpp;
                mul += 2;

                for (int k=0; k<(chunkOne.length / (bpp*imageWidth)); k++) {
                  System.arraycopy(chunkOne, k*bpp*imageWidth, tct,
                    (k+1)*bpp*imageWidth - mul, mul);
                  System.arraycopy(chunkOne, k*bpp*imageWidth+mul, tct,
                    k*bpp*imageWidth, bpp*imageWidth - mul);
                }

                chunkOne = tct;

                byte[] tco = new byte[chunkTwo.length];
                mul = (int) (imageWidth * 0.14);
                mul *= bpp;

                for (int k=0; k<(chunkTwo.length / (bpp*imageWidth)); k++) {
                  System.arraycopy(chunkTwo, k*bpp*imageWidth, tco,
                    (k+1)*bpp*imageWidth - mul, mul);
                  System.arraycopy(chunkTwo, k*bpp*imageWidth+mul, tco,
                    k*bpp*imageWidth, bpp*imageWidth - mul);
                }

                chunkTwo = tco;

                px = new byte[px.length];
                System.arraycopy(chunkTwo, 0, px, 0, chunkTwo.length);
                System.arraycopy(chunkOne, 0, px, chunkTwo.length,
                  chunkOne.length);

                // now we have to shift the whole array to the right by
                // 0.01 * width pixels

                mul = imageWidth - ((int) (imageWidth * 0.01));
                mul *= bpp;

                byte[] tmp = new byte[px.length];
                for (int k=0; k<imageHeight; k++) {
                  System.arraycopy(px, k*bpp*imageWidth, tmp,
                    (k+1)*bpp*imageWidth - mul, mul);
                  System.arraycopy(px, k*bpp*imageWidth+mul, tmp,
                    k*bpp*imageWidth, bpp*imageWidth - mul);
                }

                px = tmp;
              }

              pixelData.put(new Integer(imageNum), (Object) px);
              dataLength = px.length + head.length;
              nImages++;
            }
          }
          else break;
        }
        else if (isContents && isImage) {
          // we've found the header data

          header = data;

          int pointer = 14;
          int length = DataTools.bytesToInt(header, pointer, 2, true);
          pointer += 4 + length;

          imageWidth = DataTools.bytesToInt(header, pointer, 4, true);
          pointer += 6;
          imageHeight = DataTools.bytesToInt(header, pointer, 4, true);
          pointer += 6;
          pointer += 6;

          pixelFormat = DataTools.bytesToInt(header, pointer, 4, true);
          pointer += 6;

          switch (pixelFormat) {
            case 1: bytesPerPixel = 3; break;
            case 2: bytesPerPixel = 4; break;
            case 3: bytesPerPixel = 1; break;
            case 4: bytesPerPixel = 2; break;
            case 6: bytesPerPixel = 4; break;
            case 8: bytesPerPixel = 6; break;
            default: bytesPerPixel = 1;
          }

          if ((bytesPerPixel % 2) != 0) channels = 3;
        }
      }
      catch (Exception e) { }
    }

    // set the legacy reader's plane ordering, just in case this reader fails

    Vector ordering = new Vector();
    Vector first = new Vector();
    Vector realNames = parser.getNames();
    for (int i=0; i<realNames.size(); i++) {
      String pathName = ((String) realNames.get(i)).trim();

      if (!ordering.contains(new Integer(
        Integer.parseInt(DataTools.stripString(pathName)))))
      {
        ordering.add(new Integer(Integer.parseInt(
          DataTools.stripString(pathName))));
      }
    }
    legacy.setOrdering(ordering);

    if (nImages == 0) {
      // HACK
      // just grab the largest file

      header = (byte[]) files[1].get(largestIndex);
      String pathName = (String) files[0].get(largestIndex);

      int imageNum = 0;
      if (pathName.indexOf("Item") != -1) {
        String num = pathName.substring(pathName.indexOf("Item") + 5,
          pathName.indexOf(")"));
        imageNum = Integer.parseInt(num);
      }

      int byteCount = 166;

      byteCount += 2;
      imageWidth = DataTools.bytesToInt(header, byteCount, 4, true);
      byteCount += 6;
      imageHeight = DataTools.bytesToInt(header, byteCount, 4, true);
      byteCount += 4;

      byteCount += 6; // depth
      byteCount += 6; // pixel format
      byteCount += 6; // count
      byteCount += 2;
      bytesPerPixel = DataTools.bytesToInt(header, byteCount, 4, true) / 8;
      byteCount += 4;

      int numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
      byteCount += 2 + numBytes; // plugin CLSID
      byteCount += 38; // not sure what this is for

      byteCount += 2;
      numBytes = DataTools.bytesToInt(header, byteCount, 4, true);
      byteCount += 4 + numBytes; // layers

      byteCount += 2;
      numBytes = DataTools.bytesToInt(header, byteCount, 4, true);
      byteCount += 4 + numBytes; // scaling

      byteCount += 2;
      numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
      byteCount += 2 + numBytes; // root folder name

      byteCount += 2;
      numBytes = DataTools.bytesToInt(header, byteCount, 2, true);
      byteCount += 2 + numBytes; // display item name

      byteCount += 28; // streamed header data

      // get pixel data

      if (header.length > byteCount) {
        byte[] head = new byte[byteCount];
        System.arraycopy(header, 0, head, 0, head.length);
        headerData.put(new Integer(nImages), (Object) head);
        byte[] px = new byte[header.length - byteCount];
        System.arraycopy(header, byteCount, px, 0, px.length);

        pixelData.put(new Integer(nImages), (Object) px);
        nImages++;
      }
    }
    openBytes(id, 0);  // set needLegacy appropriately
    initMetadata();
  }


  // -- Helper methods --

  /** Populates the metadata hashtable. */
  protected void initMetadata() throws FormatException, IOException {
    // parse the "header" byte array
    // right now we're using header data from an image item

    metadata.put("Legacy?", needLegacy ? "yes" : "no");

    if (header == null) return;
    if (needLegacy) metadata = legacy.getMetadata(currentId);

    int pt = 14;
    int numBytes = DataTools.bytesToInt(header, pt, 2, true);
    pt += 2 + numBytes;

    pt += 2;

    metadata.put("ImageWidth",
      new Integer(DataTools.bytesToInt(header, pt, 4, true)));
    pt += 6;
    metadata.put("ImageHeight",
      new Integer(DataTools.bytesToInt(header, pt, 4, true)));
    pt += 6;
    pt += 6;
    int pixel = DataTools.bytesToInt(header, pt, 4, true);
    pt += 6;

    String fmt;
    switch (pixel) {
      case 1: fmt = "8-bit RGB Triple (B, G, R)"; break;
      case 2: fmt = "8-bit RGB Quad (B, G, R, A)"; break;
      case 3: fmt = "8-bit grayscale"; break;
      case 4: fmt = "16-bit signed integer"; break;
      case 5: fmt = "32-bit integer"; break;
      case 6: fmt = "32-bit IEEE float"; break;
      case 7: fmt = "64-bit IEEE float"; break;
      case 8: fmt = "16-bit unsigned RGB Triple (B, G, R)"; break;
      case 9: fmt = "32-bit RGB Triple (B, G, R)"; break;
      default: fmt = "unknown";
    }

    metadata.put("PixelFormat", fmt);

    metadata.put("NumberOfImages", new Integer(getImageCount(currentId)));
    pt += 6;
    metadata.put("BitsPerPixel",
      new Integer(DataTools.bytesToInt(header, pt, 4, true)));
    pt += 6;

    if (ome != null) {
      String type;
      switch (pixel) {
        case 1: type = "Uint8"; break;
        case 2: type = "Uint8"; break;
        case 3: type = "Uint8"; break;
        case 4: type = "int16"; break;
        case 5: type = "Uint32"; break;
        case 6: type = "float"; break;
        case 7: type = "float"; break;
        case 8: type = "Uint16"; break;
        case 9: type = "Uint32"; break;
        default: type = "Uint8";
      }

      Integer sizeX = (Integer) metadata.get("ImageWidth");
      Integer sizeY = (Integer) metadata.get("ImageHeight");
      if (needLegacy) {
        sizeX = (Integer) metadata.get("Width");
        sizeY = (Integer) metadata.get("Height");
      }

      OMETools.setPixels(ome, sizeX, sizeY,
        new Integer(1), // SizeZ
        new Integer(1), // SizeC
        new Integer(getImageCount(currentId)), // SizeT
        type, // PixelType
        null, // BigEndian
        "XYZTC"); // DimensionOrder

      OMETools.setImageName(ome, currentId);
    }

    // parse the "tags" byte array

    if (tags == null || tags.size() == 0) return;

    for (int k=0; k<tags.size(); k++) {
      byte[] tag = (byte[]) tags.get(k);
      pt = 16;

      int w = DataTools.bytesToInt(tag, pt, 2, true);
      pt += 2;

      int majorVersion = DataTools.bytesToInt(tag, pt, 2, true);
      pt += 2;
      int minorVersion = DataTools.bytesToInt(tag, pt, 2, true);
      pt += 2;

      w = DataTools.bytesToInt(tag, pt, 2, true);
      pt += 2;

      int numTags = DataTools.bytesToInt(tag, pt, 4, true);
      pt += 4;

      for (int i=0; i<numTags; i++) {
        // we have duples of {value, ID} form

        if (pt < 0 || pt >= tag.length) break;
        // first read the data type
        int type = DataTools.bytesToInt(tag, pt, 2, true);
        pt += 2;

        // read in appropriate amount of data
        Object data = null;
        int length;

        try {
          switch (type) {
            case 2: // VT_I2: 16 bit integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 2, true));
              pt += 2;
              break;
            case 3: // VT_I4: 32 bit integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 4, true));
              pt += 4;
              break;
            case 4: // VT_R4: 32 bit float
              data = new Float(Float.intBitsToFloat(
                DataTools.bytesToInt(tag, pt, 4, true)));
              pt += 4;
              break;
            case 5: // VT_R8: 64 bit float
              data = new Double(Double.longBitsToDouble(
                DataTools.bytesToLong(tag, pt, 8, true)));
              pt += 8;
              break;
            case 7: // VT_DATE: 64 bit float
              data = new Double(Double.longBitsToDouble(
                DataTools.bytesToLong(tag, pt, 8, true)));
              pt += 8;
              break;
            case 8: // VT_BSTR: streamed storage object
              length = DataTools.bytesToInt(tag, pt, 2, true);
              pt += 2;
              data = new String(tag, pt, length);
              pt += length;
              break;
            case 11: // VT_BOOL: 16 bit integer (true if !0)
              int temp = DataTools.bytesToInt(tag, pt, 4, true);
              data = new Boolean(temp != 0);
              pt += 4;
              break;
            case 16: // VT_I1: 8 bit integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 1, true));
              pt += 1;
              break;
            case 17: // VT_UI1: 8 bit unsigned integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 1, true));
              pt += 1;
              break;
            case 18: // VT_UI2: 16 bit unsigned integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 2, true));
              pt += 2;
              break;
            case 19: // VT_UI4: 32 bit unsigned integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 4, true));
              pt += 4;
              break;
            case 20: // VT_I8: 64 bit integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 8, true));
              pt += 8;
              break;
            case 21: // VT_UI8: 64 bit unsigned integer
              data = new Integer(DataTools.bytesToInt(tag, pt, 8, true));
              pt += 8;
              break;
            case 65: // VT_BLOB: binary data
              length = DataTools.bytesToInt(tag, pt, 4, true);
              pt += 4;
              try {
                data = new String(tag, pt, length);
              }
              catch (Throwable e) { data = null; }
              pt += length;
              break;
            case 68: // VT_STORED_OBJECT: streamed storage object
              length = DataTools.bytesToInt(tag, pt, 2, true);
              pt += 2;
              data = new String(tag, pt, length);
              pt += length;
              break;
            default:
              data = null;
          }
        }
        catch (Exception e) { }

        pt += 2;  // skip over ID type
        // read in tag ID
        int tagID = DataTools.bytesToInt(tag, pt, 4, true);
        pt += 4;

        pt += 6; // skip over attribute and attribute type (unused)

        // really ugly switch statement to put metadata in hashtable
        if (data != null) {
          switch (tagID) {
            case 222: metadata.put("Compression", data); break;
            case 258:
              metadata.put("BlackValue", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome,
//                  "Grey Channel", "BlackLevel", "" + data);
//              }
              break;
            case 259:
              metadata.put("WhiteValue", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome,
//                  "Grey Channel", "WhiteLevel", "" + data);
//              }
              break;
            case 260:
              metadata.put("ImageDataMappingAutoRange", data);
              break;
            case 261:
              metadata.put("Thumbnail", data);
              break;
            case 262:
              metadata.put("GammaValue", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome,
//                  "Grey Channel", "GammaLevel", "" + data);
//              }
              break;
            case 264: metadata.put("ImageOverExposure", data); break;
            case 265: metadata.put("ImageRelativeTime1", data); break;
            case 266: metadata.put("ImageRelativeTime2", data); break;
            case 267: metadata.put("ImageRelativeTime3", data); break;
            case 268: metadata.put("ImageRelativeTime4", data); break;
            case 515: metadata.put("ImageWidth", data); break;
            case 516: metadata.put("ImageHeight", data); break;
            //case 518: metadata.put("PixelType", data); break;
            case 519: metadata.put("NumberOfRawImages", data); break;
            case 520: metadata.put("ImageSize", data); break;
            case 523: metadata.put("Acquisition pause annotation", data); break;
            case 530: metadata.put("Document Subtype", data); break;
            case 531: metadata.put("Acquisition Bit Depth", data); break;
            case 534: metadata.put("Z-Stack single representative", data);
                      break;
            case 769: metadata.put("Scale Factor for X", data); break;
            case 770: metadata.put("Scale Unit for X", data); break;
            case 771: metadata.put("Scale Width", data); break;
            case 772: metadata.put("Scale Factor for Y", data); break;
            case 773: metadata.put("Scale Unit for Y", data); break;
            case 774: metadata.put("Scale Height", data); break;
            case 775: metadata.put("Scale Factor for Z", data); break;
            case 776: metadata.put("Scale Unit for Z", data); break;
            case 777: metadata.put("Scale Depth", data); break;
            case 778: metadata.put("Scaling Parent", data); break;
            case 1001:
              metadata.put("Date", data);
              if (ome != null) OMETools.setCreationDate(ome, data.toString());
              break;
            case 1002: metadata.put("code", data); break;
            case 1003: metadata.put("Source", data); break;
            case 1004: metadata.put("Message", data); break;
            case 1026: metadata.put("8-bit acquisition", data); break;
            case 1027: metadata.put("Camera Bit Depth", data); break;
            case 1029: metadata.put("MonoReferenceLow", data); break;
            case 1030: metadata.put("MonoReferenceHigh", data); break;
            case 1031: metadata.put("RedReferenceLow", data); break;
            case 1032: metadata.put("RedReferenceHigh", data); break;
            case 1033: metadata.put("GreenReferenceLow", data); break;
            case 1034: metadata.put("GreenReferenceHigh", data); break;
            case 1035: metadata.put("BlueReferenceLow", data); break;
            case 1036: metadata.put("BlueReferenceHigh", data); break;
            case 1041: metadata.put("FrameGrabber Name", data); break;
            case 1042: metadata.put("Camera", data); break;
            case 1044: metadata.put("CameraTriggerSignalType", data); break;
            case 1045: metadata.put("CameraTriggerEnable", data); break;
            case 1046: metadata.put("GrabberTimeout", data); break;
            case 1281:
              metadata.put("MultiChannelEnabled", data);
              if (((Integer) data).intValue() == 1 && ome != null) {
                OMETools.setSizeC(ome, nImages);
                OMETools.setSizeT(ome, 1);
                OMETools.setDimensionOrder(ome, "XYCZT");
              }
              break;
            case 1282: metadata.put("MultiChannel Color", data); break;
            case 1283: metadata.put("MultiChannel Weight", data); break;
            case 1284: metadata.put("Channel Name", data); break;
            case 1536: metadata.put("DocumentInformationGroup", data); break;
            case 1537:
              metadata.put("Title", data);
              if (ome != null) OMETools.setImageName(ome, data.toString());
              break;
            case 1538:
              metadata.put("Author", data);
              if (ome != null) {
                // populate Experimenter element
                String name = data.toString();
                if (name != null) {
                  String firstName = null, lastName = null;
                  int ndx = name.indexOf(" ");
                  if (ndx < 0) lastName = name;
                  else {
                    firstName = name.substring(0, ndx);
                    lastName = name.substring(ndx + 1);
                  }
                  OMETools.setExperimenter(ome,
                    firstName, lastName, null, null, null, null);
                }
              }
              break;
            case 1539: metadata.put("Keywords", data); break;
            case 1540:
              metadata.put("Comments", data);
              if (ome != null) OMETools.setDescription(ome, data.toString());
              break;
            case 1541: metadata.put("SampleID", data); break;
            case 1542: metadata.put("Subject", data); break;
            case 1543: metadata.put("RevisionNumber", data); break;
            case 1544: metadata.put("Save Folder", data); break;
            case 1545: metadata.put("FileLink", data); break;
            case 1546: metadata.put("Document Type", data); break;
            case 1547: metadata.put("Storage Media", data); break;
            case 1548: metadata.put("File ID", data); break;
            case 1549: metadata.put("Reference", data); break;
            case 1550: metadata.put("File Date", data); break;
            case 1551: metadata.put("File Size", data); break;
            case 1553: metadata.put("Filename", data); break;
            case 1792:
              metadata.put("ProjectGroup", data);
              if (ome != null) {
                OMETools.setGroup(ome, data.toString(), null, null);
              }
              break;
            case 1793: metadata.put("Acquisition Date", data); break;
            case 1794: metadata.put("Last modified by", data); break;
            case 1795: metadata.put("User company", data); break;
            case 1796: metadata.put("User company logo", data); break;
            case 1797: metadata.put("Image", data); break;
            case 1800: metadata.put("User ID", data); break;
            case 1801: metadata.put("User Name", data); break;
            case 1802: metadata.put("User City", data); break;
            case 1803: metadata.put("User Address", data); break;
            case 1804: metadata.put("User Country", data); break;
            case 1805: metadata.put("User Phone", data); break;
            case 1806: metadata.put("User Fax", data); break;
            case 2049: metadata.put("Objective Name", data); break;
            case 2050: metadata.put("Optovar", data); break;
            case 2051: metadata.put("Reflector", data); break;
            case 2052: metadata.put("Condenser Contrast", data); break;
            case 2053: metadata.put("Transmitted Light Filter 1", data); break;
            case 2054: metadata.put("Transmitted Light Filter 2", data); break;
            case 2055: metadata.put("Reflected Light Shutter", data); break;
            case 2056: metadata.put("Condenser Front Lens", data); break;
            case 2057: metadata.put("Excitation Filter Name", data); break;
            case 2060:
              metadata.put("Transmitted Light Fieldstop Aperture", data);
              break;
            case 2061: metadata.put("Reflected Light Aperture", data); break;
            case 2062: metadata.put("Condenser N.A.", data); break;
            case 2063: metadata.put("Light Path", data); break;
            case 2064: metadata.put("HalogenLampOn", data); break;
            case 2065: metadata.put("Halogen Lamp Mode", data); break;
            case 2066: metadata.put("Halogen Lamp Voltage", data); break;
            case 2068: metadata.put("Fluorescence Lamp Level", data); break;
            case 2069: metadata.put("Fluorescence Lamp Intensity", data); break;
            case 2070: metadata.put("LightManagerEnabled", data); break;
            case 2072: metadata.put("Focus Position", data); break;
            case 2073:
              metadata.put("Stage Position X", data);
              if (ome != null) {
                OMETools.setStageX(ome, Integer.parseInt(data.toString()));
              }
              break;
            case 2074:
              metadata.put("Stage Position Y", data);
              if (ome != null) {
                OMETools.setStageY(ome, Integer.parseInt(data.toString()));
              }
              break;
            case 2075:
              metadata.put("Microscope Name", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome, "Microscope", "Name", "" + data);
//              }
              break;
            case 2076: metadata.put("Objective Magnification", data); break;
            case 2077: metadata.put("Objective N.A.", data); break;
            case 2078: metadata.put("MicroscopeIllumination", data); break;
            case 2079: metadata.put("External Shutter 1", data); break;
            case 2080: metadata.put("External Shutter 2", data); break;
            case 2081: metadata.put("External Shutter 3", data); break;
            case 2082: metadata.put("External Filter Wheel 1 Name", data);
                       break;
            case 2083: metadata.put("External Filter Wheel 2 Name", data);
                       break;
            case 2084: metadata.put("Parfocal Correction", data); break;
            case 2086: metadata.put("External Shutter 4", data); break;
            case 2087: metadata.put("External Shutter 5", data); break;
            case 2088: metadata.put("External Shutter 6", data); break;
            case 2089: metadata.put("External Filter Wheel 3 Name", data);
                       break;
            case 2090: metadata.put("External Filter Wheel 4 Name", data);
                       break;
            case 2103: metadata.put("Objective Turret Position", data); break;
            case 2104: metadata.put("Objective Contrast Method", data); break;
            case 2105: metadata.put("Objective Immersion Type", data); break;
            case 2107: metadata.put("Reflector Position", data); break;
            case 2109:
              metadata.put("Transmitted Light Filter 1 Position", data);
              break;
            case 2110:
              metadata.put("Transmitted Light Filter 2 Position", data);
              break;
            case 2112: metadata.put("Excitation Filter Position", data); break;
            case 2113: metadata.put("Lamp Mirror Position", data); break;
            case 2114:
              metadata.put("External Filter Wheel 1 Position", data);
              break;
            case 2115:
              metadata.put("External Filter Wheel 2 Position", data);
              break;
            case 2116:
              metadata.put("External Filter Wheel 3 Position", data);
              break;
            case 2117:
              metadata.put("External Filter Wheel 4 Position", data);
              break;
            case 2118: metadata.put("Lightmanager Mode", data); break;
            case 2119: metadata.put("Halogen Lamp Calibration", data); break;
            case 2120: metadata.put("CondenserNAGoSpeed", data); break;
            case 2121:
              metadata.put("TransmittedLightFieldstopGoSpeed", data);
              break;
            case 2122: metadata.put("OptovarGoSpeed", data); break;
            case 2123: metadata.put("Focus calibrated", data); break;
            case 2124: metadata.put("FocusBasicPosition", data); break;
            case 2125: metadata.put("FocusPower", data); break;
            case 2126: metadata.put("FocusBacklash", data); break;
            case 2127: metadata.put("FocusMeasurementOrigin", data); break;
            case 2128: metadata.put("FocusMeasurementDistance", data); break;
            case 2129: metadata.put("FocusSpeed", data); break;
            case 2130: metadata.put("FocusGoSpeed", data); break;
            case 2131: metadata.put("FocusDistance", data); break;
            case 2132: metadata.put("FocusInitPosition", data); break;
            case 2133: metadata.put("Stage calibrated", data); break;
            case 2134: metadata.put("StagePower", data); break;
            case 2135: metadata.put("StageXBacklash", data); break;
            case 2136: metadata.put("StageYBacklash", data); break;
            case 2137: metadata.put("StageSpeedX", data); break;
            case 2138: metadata.put("StageSpeedY", data); break;
            case 2139: metadata.put("StageSpeed", data); break;
            case 2140: metadata.put("StageGoSpeedX", data); break;
            case 2141: metadata.put("StageGoSpeedY", data); break;
            case 2142: metadata.put("StageStepDistanceX", data); break;
            case 2143: metadata.put("StageStepDistanceY", data); break;
            case 2144: metadata.put("StageInitialisationPositionX", data);
                       break;
            case 2145: metadata.put("StageInitialisationPositionY", data);
                       break;
            case 2146: metadata.put("MicroscopeMagnification", data); break;
            case 2147: metadata.put("ReflectorMagnification", data); break;
            case 2148: metadata.put("LampMirrorPosition", data); break;
            case 2149: metadata.put("FocusDepth", data); break;
            case 2150:
              metadata.put("MicroscopeType", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome, "Microscope", "Type", "" + data);
//              }
              break;
            case 2151: metadata.put("Objective Working Distance", data); break;
            case 2152:
              metadata.put("ReflectedLightApertureGoSpeed", data);
              break;
            case 2153: metadata.put("External Shutter", data); break;
            case 2154: metadata.put("ObjectiveImmersionStop", data); break;
            case 2155: metadata.put("Focus Start Speed", data); break;
            case 2156: metadata.put("Focus Acceleration", data); break;
            case 2157: metadata.put("ReflectedLightFieldstop", data); break;
            case 2158:
              metadata.put("ReflectedLightFieldstopGoSpeed", data);
              break;
            case 2159: metadata.put("ReflectedLightFilter 1", data); break;
            case 2160: metadata.put("ReflectedLightFilter 2", data); break;
            case 2161:
              metadata.put("ReflectedLightFilter1Position", data);
              break;
            case 2162:
              metadata.put("ReflectedLightFilter2Position", data);
              break;
            case 2163: metadata.put("TransmittedLightAttenuator", data); break;
            case 2164: metadata.put("ReflectedLightAttenuator", data); break;
            case 2165: metadata.put("Transmitted Light Shutter", data); break;
            case 2166:
              metadata.put("TransmittedLightAttenuatorGoSpeed", data);
              break;
            case 2167:
              metadata.put("ReflectedLightAttenuatorGoSpeed", data);
              break;
            case 2176:
              metadata.put("TransmittedLightVirtualFilterPosition", data);
              break;
            case 2177:
              metadata.put("TransmittedLightVirtualFilter", data);
              break;
            case 2178:
              metadata.put("ReflectedLightVirtualFilterPosition", data);
              break;
            case 2179: metadata.put("ReflectedLightVirtualFilter", data); break;
            case 2180:
              metadata.put("ReflectedLightHalogenLampMode", data);
              break;
            case 2181:
              metadata.put("ReflectedLightHalogenLampVoltage", data);
              break;
            case 2182:
              metadata.put("ReflectedLightHalogenLampColorTemperature", data);
              break;
            case 2183: metadata.put("ContrastManagerMode", data); break;
            case 2184: metadata.put("Dazzle Protection Active", data); break;
            case 2195:
              metadata.put("Zoom", data);
//              if (ome != null) {
//                OMETools.setAttribute(ome,
//                  "DisplayOptions", "Zoom", "" + data);
//              }
              break;
            case 2196: metadata.put("ZoomGoSpeed", data); break;
            case 2197: metadata.put("LightZoom", data); break;
            case 2198: metadata.put("LightZoomGoSpeed", data); break;
            case 2199: metadata.put("LightZoomCoupled", data); break;
            case 2200:
              metadata.put("TransmittedLightHalogenLampMode", data);
              break;
            case 2201:
              metadata.put("TransmittedLightHalogenLampVoltage", data);
              break;
            case 2202:
              metadata.put("TransmittedLightHalogenLampColorTemperature", data);
              break;
            case 2203: metadata.put("Reflected Coldlight Mode", data); break;
            case 2204:
              metadata.put("Reflected Coldlight Intensity", data);
              break;
            case 2205:
              metadata.put("Reflected Coldlight Color Temperature", data);
              break;
            case 2206: metadata.put("Transmitted Coldlight Mode", data); break;
            case 2207:
              metadata.put("Transmitted Coldlight Intensity", data);
              break;
            case 2208:
              metadata.put("Transmitted Coldlight Color Temperature", data);
              break;
            case 2209:
              metadata.put("Infinityspace Portchanger Position", data);
              break;
            case 2210: metadata.put("Beamsplitter Infinity Space", data); break;
            case 2211: metadata.put("TwoTv VisCamChanger Position", data);
                       break;
            case 2212: metadata.put("Beamsplitter Ocular", data); break;
            case 2213:
              metadata.put("TwoTv CamerasChanger Position", data);
              break;
            case 2214: metadata.put("Beamsplitter Cameras", data); break;
            case 2215: metadata.put("Ocular Shutter", data); break;
            case 2216: metadata.put("TwoTv CamerasChangerCube", data); break;
            case 2218: metadata.put("Ocular Magnification", data); break;
            case 2219: metadata.put("Camera Adapter Magnification", data);
                       break;
            case 2220: metadata.put("Microscope Port", data); break;
            case 2221: metadata.put("Ocular Total Magnification", data); break;
            case 2222: metadata.put("Field of View", data); break;
            case 2223: metadata.put("Ocular", data); break;
            case 2224: metadata.put("CameraAdapter", data); break;
            case 2225: metadata.put("StageJoystickEnabled", data); break;
            case 2226:
              metadata.put("ContrastManager Contrast Method", data);
              break;
            case 2229:
              metadata.put("CamerasChanger Beamsplitter Type", data);
              break;
            case 2235: metadata.put("Rearport Slider Position", data); break;
            case 2236: metadata.put("Rearport Source", data); break;
            case 2237:
              metadata.put("Beamsplitter Type Infinity Space", data);
              break;
            case 2238: metadata.put("Fluorescence Attenuator", data); break;
            case 2239:
              metadata.put("Fluorescence Attenuator Position", data);
              break;
            case 2307: metadata.put("Camera Framestart Left", data); break;
            case 2308: metadata.put("Camera Framestart Top", data); break;
            case 2309: metadata.put("Camera Frame Width", data); break;
            case 2310: metadata.put("Camera Frame Height", data); break;
            case 2311: metadata.put("Camera Binning", data); break;
            case 2312: metadata.put("CameraFrameFull", data); break;
            case 2313: metadata.put("CameraFramePixelDistance", data); break;
            case 2318: metadata.put("DataFormatUseScaling", data); break;
            case 2319: metadata.put("CameraFrameImageOrientation", data); break;
            case 2320: metadata.put("VideoMonochromeSignalType", data); break;
            case 2321: metadata.put("VideoColorSignalType", data); break;
            case 2322: metadata.put("MeteorChannelInput", data); break;
            case 2323: metadata.put("MeteorChannelSync", data); break;
            case 2324: metadata.put("WhiteBalanceEnabled", data); break;
            case 2325: metadata.put("CameraWhiteBalanceRed", data); break;
            case 2326: metadata.put("CameraWhiteBalanceGreen", data); break;
            case 2327: metadata.put("CameraWhiteBalanceBlue", data); break;
            case 2331: metadata.put("CameraFrameScalingFactor", data); break;
            case 2562: metadata.put("Meteor Camera Type", data); break;
            case 2564: metadata.put("Exposure Time [ms]", data); break;
            case 2568:
              metadata.put("CameraExposureTimeAutoCalculate", data);
              break;
            case 2569: metadata.put("Meteor Gain Value", data); break;
            case 2571: metadata.put("Meteor Gain Automatic", data); break;
            case 2572: metadata.put("MeteorAdjustHue", data); break;
            case 2573: metadata.put("MeteorAdjustSaturation", data); break;
            case 2574: metadata.put("MeteorAdjustRedLow", data); break;
            case 2575: metadata.put("MeteorAdjustGreenLow", data); break;
            case 2576: metadata.put("Meteor Blue Low", data); break;
            case 2577: metadata.put("MeteorAdjustRedHigh", data); break;
            case 2578: metadata.put("MeteorAdjustGreenHigh", data); break;
            case 2579: metadata.put("MeteorBlue High", data); break;
            case 2582:
              metadata.put("CameraExposureTimeCalculationControl", data);
              break;
            case 2585:
              metadata.put("AxioCamFadingCorrectionEnable", data);
              break;
            case 2587: metadata.put("CameraLiveImage", data); break;
            case 2588: metadata.put("CameraLiveEnabled", data); break;
            case 2589: metadata.put("LiveImageSyncObjectName", data); break;
            case 2590: metadata.put("CameraLiveSpeed", data); break;
            case 2591: metadata.put("CameraImage", data); break;
            case 2592: metadata.put("CameraImageWidth", data); break;
            case 2593: metadata.put("CameraImageHeight", data); break;
            case 2594: metadata.put("CameraImagePixelType", data); break;
            case 2595: metadata.put("CameraImageShMemoryName", data); break;
            case 2596: metadata.put("CameraLiveImageWidth", data); break;
            case 2597: metadata.put("CameraLiveImageHeight", data); break;
            case 2598: metadata.put("CameraLiveImagePixelType", data); break;
            case 2599: metadata.put("CameraLiveImageShMemoryName", data); break;
            case 2600: metadata.put("CameraLiveMaximumSpeed", data); break;
            case 2601: metadata.put("CameraLiveBinning", data); break;
            case 2602: metadata.put("CameraLiveGainValue", data); break;
            case 2603: metadata.put("CameraLiveExposureTimeValue", data); break;
            case 2604: metadata.put("CameraLiveScalingFactor", data); break;
            case 2822: metadata.put("ImageTile Index", data); break;
            case 2823: metadata.put("Image acquisition Index", data); break;
            case 2841: metadata.put("Original Stage Position X", data); break;
            case 2842: metadata.put("Original Stage Position Y", data); break;
            case 3088: metadata.put("LayerDrawFlags", data); break;
            case 3334: metadata.put("RemainingTime", data); break;
            case 3585: metadata.put("User Field 1", data); break;
            case 3586: metadata.put("User Field 2", data); break;
            case 3587: metadata.put("User Field 3", data); break;
            case 3588: metadata.put("User Field 4", data); break;
            case 3589: metadata.put("User Field 5", data); break;
            case 3590: metadata.put("User Field 6", data); break;
            case 3591: metadata.put("User Field 7", data); break;
            case 3592: metadata.put("User Field 8", data); break;
            case 3593: metadata.put("User Field 9", data); break;
            case 3594: metadata.put("User Field 10", data); break;
            case 3840: metadata.put("ID", data); break;
            case 3841: metadata.put("Name", data); break;
            case 3842: metadata.put("Value", data); break;
            case 5501: metadata.put("PvCamClockingMode", data); break;
            case 8193: metadata.put("Autofocus Status Report", data); break;
            case 8194: metadata.put("Autofocus Position", data); break;
            case 8195: metadata.put("Autofocus Position Offset", data); break;
            case 8196:
              metadata.put("Autofocus Empty Field Threshold", data);
              break;
            case 8197: metadata.put("Autofocus Calibration Name", data); break;
            case 8198:
              metadata.put("Autofocus Current Calibration Item", data);
              break;
            case 65537: metadata.put("CameraFrameFullWidth", data); break;
            case 65538: metadata.put("CameraFrameFullHeight", data); break;
            case 65541: metadata.put("AxioCam Shutter Signal", data); break;
            case 65542: metadata.put("AxioCam Delay Time", data); break;
            case 65543: metadata.put("AxioCam Shutter Control", data); break;
            case 65544:
              metadata.put("AxioCam BlackRefIsCalculated", data);
              break;
            case 65545: metadata.put("AxioCam Black Reference", data); break;
            case 65547: metadata.put("Camera Shading Correction", data); break;
            case 65550: metadata.put("AxioCam Enhance Color", data); break;
            case 65551: metadata.put("AxioCam NIR Mode", data); break;
            case 65552: metadata.put("CameraShutterCloseDelay", data); break;
            case 65553:
              metadata.put("CameraWhiteBalanceAutoCalculate", data);
              break;
            case 65556: metadata.put("AxioCam NIR Mode Available", data); break;
            case 65557:
              metadata.put("AxioCam Fading Correction Available", data);
              break;
            case 65559:
              metadata.put("AxioCam Enhance Color Available", data);
              break;
            case 65565: metadata.put("MeteorVideoNorm", data); break;
            case 65566: metadata.put("MeteorAdjustWhiteReference", data); break;
            case 65567: metadata.put("MeteorBlackReference", data); break;
            case 65568: metadata.put("MeteorChannelInputCountMono", data);
                        break;
            case 65570: metadata.put("MeteorChannelInputCountRGB", data); break;
            case 65571: metadata.put("MeteorEnableVCR", data); break;
            case 65572: metadata.put("Meteor Brightness", data); break;
            case 65573: metadata.put("Meteor Contrast", data); break;
            case 65575: metadata.put("AxioCam Selector", data); break;
            case 65576: metadata.put("AxioCam Type", data); break;
            case 65577: metadata.put("AxioCam Info", data); break;
            case 65580: metadata.put("AxioCam Resolution", data); break;
            case 65581: metadata.put("AxioCam Color Model", data); break;
            case 65582: metadata.put("AxioCam MicroScanning", data); break;
            case 65585: metadata.put("Amplification Index", data); break;
            case 65586: metadata.put("Device Command", data); break;
            case 65587: metadata.put("BeamLocation", data); break;
            case 65588: metadata.put("ComponentType", data); break;
            case 65589: metadata.put("ControllerType", data); break;
            case 65590:
              metadata.put("CameraWhiteBalanceCalculationRedPaint", data);
              break;
            case 65591:
              metadata.put("CameraWhiteBalanceCalculationBluePaint", data);
              break;
            case 65592: metadata.put("CameraWhiteBalanceSetRed", data); break;
            case 65593: metadata.put("CameraWhiteBalanceSetGreen", data); break;
            case 65594: metadata.put("CameraWhiteBalanceSetBlue", data); break;
            case 65595:
              metadata.put("CameraWhiteBalanceSetTargetRed", data);
              break;
            case 65596:
              metadata.put("CameraWhiteBalanceSetTargetGreen", data);
              break;
            case 65597:
              metadata.put("CameraWhiteBalanceSetTargetBlue", data);
              break;
            case 65598: metadata.put("ApotomeCamCalibrationMode", data); break;
            case 65599: metadata.put("ApoTome Grid Position", data); break;
            case 65600: metadata.put("ApotomeCamScannerPosition", data); break;
            case 65601: metadata.put("ApoTome Full Phase Shift", data); break;
            case 65602: metadata.put("ApoTome Grid Name", data); break;
            case 65603: metadata.put("ApoTome Staining", data); break;
            case 65604: metadata.put("ApoTome Processing Mode", data); break;
            case 65605: metadata.put("ApotmeCamLiveCombineMode", data); break;
            case 65606: metadata.put("ApoTome Filter Name", data); break;
            case 65607: metadata.put("Apotome Filter Strength", data); break;
            case 65608: metadata.put("ApotomeCamFilterHarmonics", data); break;
            case 65609: metadata.put("ApoTome Grating Period", data); break;
            case 65610: metadata.put("ApoTome Auto Shutter Used", data); break;
            case 65611: metadata.put("Apotome Cam Status", data); break;
            case 65612: metadata.put("ApotomeCamNormalize", data); break;
            case 65613: metadata.put("ApotomeCamSettingsManager", data); break;
            case 65614: metadata.put("DeepviewCamSupervisorMode", data); break;
            case 65615: metadata.put("DeepView Processing", data); break;
            case 65616: metadata.put("DeepviewCamFilterName", data); break;
            case 65617: metadata.put("DeepviewCamStatus", data); break;
            case 65618: metadata.put("DeepviewCamSettingsManager", data); break;
            case 65619: metadata.put("DeviceScalingName", data); break;
            case 65620: metadata.put("CameraShadingIsCalculated", data); break;
            case 65621:
              metadata.put("CameraShadingCalculationName", data);
              break;
            case 65622: metadata.put("CameraShadingAutoCalculate", data); break;
            case 65623: metadata.put("CameraTriggerAvailable", data); break;
            case 65626: metadata.put("CameraShutterAvailable", data); break;
            case 65627:
              metadata.put("AxioCam ShutterMicroScanningEnable", data);
              break;
            case 65628: metadata.put("ApotomeCamLiveFocus", data); break;
            case 65629: metadata.put("DeviceInitStatus", data); break;
            case 65630: metadata.put("DeviceErrorStatus", data); break;
            case 65631:
              metadata.put("ApotomeCamSliderInGridPosition", data);
              break;
            case 65632: metadata.put("Orca NIR Mode Used", data); break;
            case 65633: metadata.put("Orca Analog Gain", data); break;
            case 65634: metadata.put("Orca Analog Offset", data); break;
            case 65635: metadata.put("Orca Binning", data); break;
            case 65636: metadata.put("Orca Bit Depth", data); break;
            case 65637: metadata.put("ApoTome Averaging Count", data); break;
            case 65638: metadata.put("DeepView DoF", data); break;
            case 65639: metadata.put("DeepView EDoF", data); break;
            case 65643: metadata.put("DeepView Slider Name", data); break;
          }
        }
      }
    }
  }

  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new ZeissZVIReader().testRead(args);
  }

}
