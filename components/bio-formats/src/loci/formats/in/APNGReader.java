//
// APNGReader.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

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

package loci.formats.in;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Vector;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.codec.ByteVector;
import loci.formats.gui.AWTImageTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * APNGReader is the file format reader for
 * Animated Portable Network Graphics (APNG) images.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/APNGReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/APNGReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class APNGReader extends FormatReader {

  // -- Constants --

  // Valid values for dispose operation field:
  //private static final int DISPOSE_OP_NONE = 0;
  //private static final int DISPOSE_OP_BACKGROUND = 1;
  //private static final int DISPOSE_OP_PREVIOUS = 2;

  private static final byte[] PNG_SIGNATURE = new byte[] {
    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };

  // -- Fields --

  private Vector<PNGBlock> blocks;
  private Vector<int[]> frameCoordinates;

  private byte[][] lut;

  // -- Constructor --

  /** Constructs a new APNGReader. */
  public APNGReader() {
    super("Animated PNG", "png");
    domains = new String[] {FormatTools.GRAPHICS_DOMAIN};
  }

  // -- IFormatReader API methods --

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
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    BufferedImage data = (BufferedImage) openPlane(no, x, y, w, h);
    byte[][] t = AWTImageTools.getPixelBytes(data, false);

    for (int c=0; c<t.length; c++) {
      System.arraycopy(t[c], 0, buf, c * t[c].length, t[c].length);
    }

    return buf;
  }

  /* @see loci.formats.IFormatReader#openPlane(int, int, int, int, int int) */
  public Object openPlane(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, -1, x, y, w, h);

    if (no == 0) {
      in.seek(0);
      DataInputStream dis =
        new DataInputStream(new BufferedInputStream(in, 4096));
      return ImageIO.read(dis).getSubimage(x, y, w, h);
    }

    ByteVector stream = new ByteVector();
    stream.add(PNG_SIGNATURE);

    boolean fdatValid = false;
    int fctlCount = 0;

    int[] coords = frameCoordinates.get(no);

    for (PNGBlock block : blocks) {
      if (!block.type.equals("IDAT") && !block.type.equals("fdAT") &&
        !block.type.equals("acTL") && !block.type.equals("fcTL"))
      {
        byte[] b = new byte[block.length + 12];
        DataTools.unpackBytes(block.length, b, 0, 4, isLittleEndian());
        byte[] typeBytes = block.type.getBytes();
        System.arraycopy(typeBytes, 0, b, 4, 4);
        in.seek(block.offset);
        in.read(b, 8, b.length - 12);
        if (block.type.equals("IHDR")) {
          DataTools.unpackBytes(coords[2], b, 8, 4, isLittleEndian());
          DataTools.unpackBytes(coords[3], b, 12, 4, isLittleEndian());
        }
        int crc = (int) computeCRC(b, b.length - 4);
        DataTools.unpackBytes(crc, b, b.length - 4, 4, isLittleEndian());
        stream.add(b);
        b = null;
      }
      else if (block.type.equals("fcTL")) {
        fdatValid = fctlCount == no;
        fctlCount++;
      }
      else if (block.type.equals("fdAT")) {
        in.seek(block.offset + 4);
        if (fdatValid) {
          byte[] b = new byte[block.length + 8];
          DataTools.unpackBytes(block.length - 4, b, 0, 4, isLittleEndian());
          b[4] = 'I';
          b[5] = 'D';
          b[6] = 'A';
          b[7] = 'T';
          in.read(b, 8, b.length - 12);
          int crc = (int) computeCRC(b, b.length - 4);
          DataTools.unpackBytes(crc, b, b.length - 4, 4, isLittleEndian());
          stream.add(b);
          b = null;
        }
      }
    }

    RandomAccessInputStream s =
      new RandomAccessInputStream(stream.toByteArray());
    DataInputStream dis = new DataInputStream(new BufferedInputStream(s, 4096));
    BufferedImage b = ImageIO.read(dis);
    dis.close();
    BufferedImage first = (BufferedImage)
      openPlane(0, 0, 0, getSizeX(), getSizeY());

    // paste current image onto first image

    WritableRaster firstRaster = first.getRaster();
    WritableRaster currentRaster = b.getRaster();

    firstRaster.setDataElements(coords[0], coords[1], currentRaster);
    return new BufferedImage(first.getColorModel(), firstRaster, false, null);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      lut = null;
      frameCoordinates = null;
      blocks = null;
    }
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#getNativeDataType() */
  public Class getNativeDataType() {
    return BufferedImage.class;
  }

  // -- Internal FormatReader methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    debug("APNGReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessInputStream(id);

    // check that this is a valid PNG file
    byte[] signature = new byte[8];
    in.read(signature);

    if (signature[0] != (byte) 0x89 || signature[1] != 0x50 ||
      signature[2] != 0x4e || signature[3] != 0x47 || signature[4] != 0x0d ||
      signature[5] != 0x0a || signature[6] != 0x1a || signature[7] != 0x0a)
    {
      throw new FormatException("Invalid PNG signature.");
    }

    // read data chunks - each chunk consists of the following:
    // 1) 32 bit length
    // 2) 4 char type
    // 3) 'length' bytes of data
    // 4) 32 bit CRC

    blocks = new Vector<PNGBlock>();
    frameCoordinates = new Vector<int[]>();

    while (in.getFilePointer() < in.length()) {
      int length = in.readInt();
      String type = in.readString(4);

      PNGBlock block = new PNGBlock();
      block.length = length;
      block.type = type;
      block.offset = in.getFilePointer();
      blocks.add(block);

      if (type.equals("acTL")) {
        // APNG-specific chunk
        core[0].imageCount = in.readInt();
        int loop = in.readInt();
        addGlobalMeta("Loop count", loop);
      }
      else if (type.equals("fcTL")) {
        in.skipBytes(4);
        int w = in.readInt();
        int h = in.readInt();
        int x = in.readInt();
        int y = in.readInt();
        frameCoordinates.add(new int[] {x, y, w, h});
        in.skipBytes(length - 20);
      }
      else in.skipBytes(length);

      in.skipBytes(4); // skip the CRC
    }

    if (core[0].imageCount == 0) core[0].imageCount = 1;
    core[0].sizeZ = 1;
    core[0].sizeT = getImageCount();

    core[0].dimensionOrder = "XYCTZ";
    core[0].interleaved = false;

    BufferedImage img =
      ImageIO.read(new DataInputStream(new RandomAccessInputStream(currentId)));
    core[0].sizeX = img.getWidth();
    core[0].sizeY = img.getHeight();
    core[0].rgb = img.getRaster().getNumBands() > 1;
    core[0].sizeC = img.getRaster().getNumBands();
    core[0].pixelType = AWTImageTools.getPixelType(img);
    core[0].indexed = img.getColorModel() instanceof IndexColorModel;
    core[0].falseColor = false;

    if (isIndexed()) {
      lut = new byte[3][256];
      IndexColorModel model = (IndexColorModel) img.getColorModel();
      model.getReds(lut[0]);
      model.getGreens(lut[1]);
      model.getBlues(lut[2]);
    }

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);
    MetadataTools.setDefaultCreationDate(store, id, 0);
  }

  // -- Helper methods --

  private long computeCRC(byte[] buf, int len) {
    CRC32 crc = new CRC32();
    crc.update(buf, 0, len);
    return crc.getValue();
  }

  // -- Helper class --

  class PNGBlock {
    public long offset;
    public int length;
    public String type;
  }

}
