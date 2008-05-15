//
// PSDReader.java
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
import loci.formats.*;
import loci.formats.codec.PackbitsCodec;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * PSDReader is the file format reader for Photoshop PSD files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/PSDReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/PSDReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class PSDReader extends FormatReader {

  // -- Fields --

  /** Lookup table. */
  private byte[][] lut;

  /** Offset to pixel data. */
  private long offset;

  // -- Constructor --

  /** Constructs a new PSD reader. */
  public PSDReader() { super("Adobe Photoshop", "psd"); }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    return new String(block).startsWith("8BPS");
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() {
    FormatTools.assertId(currentId, true, 1);
    return lut;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length, w, h);

    in.seek(offset);

    int plane = core.sizeX[0] * core.sizeY[0] *
      FormatTools.getBytesPerPixel(core.pixelType[0]);
    int[][] lens = new int[core.sizeC[0]][core.sizeY[0]];
    boolean compressed = in.readShort() == 1;

    int bpp = FormatTools.getBytesPerPixel(core.pixelType[0]);

    if (compressed) {
      PackbitsCodec codec = new PackbitsCodec();
      for (int c=0; c<core.sizeC[0]; c++) {
        for (int row=0; row<core.sizeY[0]; row++) {
          lens[c][row] = in.readShort();
        }
      }

      for (int c=0; c<core.sizeC[0]; c++) {
        for (int row=0; row<core.sizeY[0]; row++) {
          if (row < y || row >= (y + h)) in.skipBytes(lens[c][row]);
          else {
            byte[] b = new byte[lens[c][row]];
            in.read(b);
            b = codec.decompress(b, new Integer(core.sizeX[0] * bpp));
            System.arraycopy(b, x * bpp, buf,
              c * h * bpp * w + (row - y) * bpp * w, w * bpp);
          }
        }
      }
    }
    else {
      for (int c=0; c<core.sizeC[0]; c++) {
        in.skipBytes(y * bpp * core.sizeX[0]);
        for (int row=0; row<h; row++) {
          in.skipBytes(x * bpp);
          in.read(buf, c * h * w * bpp + row * w * bpp, w * bpp);
          in.skipBytes(bpp * (core.sizeX[0] - w - x));
        }
      }
    }
    return buf;
  }

  // -- IFormatHandler API Methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    lut = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("PSDReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);
    core.littleEndian[0] = false;

    if (!in.readString(4).equals("8BPS")) {
      throw new FormatException("Not a valid Photoshop file.");
    }

    int version = in.readShort();
    addMeta("Version", new Integer(version));

    in.skipBytes(6); // reserved, set to 0
    core.sizeC[0] = in.readShort();
    core.sizeY[0] = in.readInt();
    core.sizeX[0] = in.readInt();

    int bits = in.readShort();
    addMeta("Bits per pixel", new Integer(bits));
    switch (bits) {
      case 16:
        core.pixelType[0] = FormatTools.UINT16;
        break;
      default: core.pixelType[0] = FormatTools.UINT8;
    }

    int colorMode = in.readShort();
    String modeString = null;
    switch (colorMode) {
      case 0:
        modeString = "monochrome";
        break;
      case 1:
        modeString = "gray-scale";
        break;
      case 2:
        modeString = "palette color";
        break;
      case 3:
        modeString = "RGB";
        break;
      case 4:
        modeString = "CMYK";
        break;
      case 6:
        modeString = "Duotone";
        break;
      case 7:
        modeString = "Multichannel color";
        break;
      case 8:
        modeString = "Duotone";
        break;
      case 9:
        modeString = "LAB color";
        break;
    }
    addMeta("Color mode", modeString);

    // read color mode block, if present

    int modeDataLength = in.readInt();
    long fp = in.getFilePointer();
    if (modeDataLength != 0) {
      if (colorMode == 2) {
        lut = new byte[3][256];
        for (int i=0; i<lut.length; i++) {
          in.read(lut[i]);
        }
      }
      in.seek(fp + modeDataLength);
    }

    // read image resources block

    in.skipBytes(4);

    while (in.readString(4).equals("8BIM")) {
      int tag = in.readShort();
      int read = 1;
      while (in.read() != 0) read++;
      if (read % 2 == 1) in.skipBytes(1);

      int size = in.readInt();
      if (size % 2 == 1) size++;
      in.skipBytes(size);
    }
    in.seek(in.getFilePointer() - 4);

    int blockLen = in.readInt();
    int layerLen = in.readInt();
    int layerCount = in.readShort();
    int[] w = new int[layerCount];
    int[] h = new int[layerCount];
    int[] c = new int[layerCount];
    for (int i=0; i<layerCount; i++) {
      int top = in.readInt();
      int left = in.readInt();
      int bottom = in.readInt();
      int right = in.readInt();
      w[i] = right - left;
      h[i] = bottom - top;
      c[i] = in.readShort();
      in.skipBytes(c[i] * 6 + 12);
      int len = in.readInt();
      if (len % 2 == 1) len++;
      in.skipBytes(len);
    }

    // skip over pixel data for each layer
    for (int i=0; i<layerCount; i++) {
      int[] lens = new int[h[i]];
      for (int cc=0; cc<c[i]; cc++) {
        boolean compressed = in.readShort() == 1;
        if (!compressed) in.skipBytes(w[i] * h[i]);
        else {
          for (int y=0; y<h[i]; y++) {
            lens[y] = in.readShort();
          }
          for (int y=0; y<h[i]; y++) {
            in.skipBytes(lens[y]);
          }
        }
      }
    }

    in.skipBytes((int) (in.getFilePointer() % 2) + 4);
    while (in.read() != '8');
    in.skipBytes(7);
    int len = in.readInt();
    if ((len % 4) != 0) len += 4 - (len % 4);
    in.skipBytes(len);

    String s = in.readString(4);
    while (s.equals("8BIM")) {
      in.skipBytes(4);
      len = in.readInt();
      if ((len % 4) != 0) len += 4 - (len % 4);
      in.skipBytes(len);
      s = in.readString(4);
    }

    offset = in.getFilePointer() - 4;

    core.sizeZ[0] = 1;
    core.sizeT[0] = 1;
    core.rgb[0] = modeString.equals("RGB");
    core.imageCount[0] = core.sizeC[0] / (core.rgb[0] ? 3 : 1);
    core.indexed[0] = modeString.equals("palette color");
    core.falseColor[0] = false;
    core.currentOrder[0] = "XYCZT";
    core.interleaved[0] = false;
    core.metadataComplete[0] = true;

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);
    store.setImageCreationDate(
      DataTools.convertDate(System.currentTimeMillis(), DataTools.UNIX), 0);
    MetadataTools.populatePixels(store, this);
  }

}
