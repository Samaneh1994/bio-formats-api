//
// MNGReader.java
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

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import loci.common.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * MNGReader is the file format reader for Multiple Network Graphics (MNG)
 * files.  Does not support JNG (JPEG Network Graphics).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/MNGReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/MNGReader.java">SVN</a></dd></dl>
 */
public class MNGReader extends FormatReader {

  // -- Fields --

  /** Offsets to each plane. */
  private Vector[] offsets;

  /** Length (in bytes) of each plane. */
  private Vector[] lengths;

  // -- Constructor --

  /** Constructs a new MNG reader. */
  public MNGReader() { super("Multiple Network Graphics", "mng"); }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    if (!FormatTools.validStream(stream, blockCheckLen, false)) return false;
    return stream.readLong() == 0x8a4d4e470d0a1a0aL;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    byte[] tmp = ImageTools.getBytes(openImage(no, x, y, w, h), true);
    System.arraycopy(tmp, 0, buf, 0, (int) Math.min(tmp.length, buf.length));
    return buf;
  }

  /* @see loci.formats.IFormatReader#openImage(int, int, int, int, int) */
  public BufferedImage openImage(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);

    long offset = ((Long) offsets[series].get(no)).longValue();
    in.seek(offset);
    long end = ((Long) lengths[series].get(no)).longValue();
    byte[] b = new byte[(int) (end - offset + 8)];
    in.read(b, 8, b.length - 8);
    b[0] = (byte) 0x89;
    b[1] = 0x50;
    b[2] = 0x4e;
    b[3] = 0x47;
    b[4] = 0x0d;
    b[5] = 0x0a;
    b[6] = 0x1a;
    b[7] = 0x0a;

    return ImageIO.read(new ByteArrayInputStream(b)).getSubimage(x, y, w, h);
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    offsets = lengths = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("MNGReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);
    in.order(false);

    status("Verifying MNG format");

    offsets = new Vector[1];
    lengths = new Vector[1];

    offsets[0] = new Vector();
    lengths[0] = new Vector();

    in.skipBytes(12);

    if (!"MHDR".equals(in.readString(4))) {
      throw new FormatException("Invalid MNG file.");
    }

    status("Reading dimensions");

    in.skipBytes(32);

    Vector stack = new Vector();
    int maxIterations = 0;
    int currentIteration = 0;

    status("Finding image offsets");

    // read sequence of [len, code, value] tags

    while (in.getFilePointer() < in.length()) {
      int len = in.readInt();
      String code = in.readString(4);

      long fp = in.getFilePointer();

      if (code.equals("IHDR")) {
        offsets[0].add(new Long(fp - 8));
      }
      else if (code.equals("IEND")) {
        lengths[0].add(new Long(fp + len + 4));
      }
      else if (code.equals("LOOP")) {
        stack.add(new Long(fp + len + 4));
        in.skipBytes(1);
        maxIterations = in.readInt();
      }
      else if (code.equals("ENDL")) {
        long seek = ((Long) stack.get(stack.size() - 1)).longValue();
        if (currentIteration < maxIterations) {
          in.seek(seek);
          currentIteration++;
        }
        else {
          stack.remove(stack.size() - 1);
          maxIterations = 0;
          currentIteration = 0;
        }
      }

      in.seek(fp + len + 4);
    }

    status("Populating metadata");

    // easiest way to get image dimensions is by opening the first plane

    Hashtable seriesOffsets = new Hashtable();
    Hashtable seriesLengths = new Hashtable();

    for (int i=0; i<offsets[0].size(); i++) {
      long offset = ((Long) offsets[0].get(i)).longValue();
      in.seek(offset);
      long end = ((Long) lengths[0].get(i)).longValue();
      if (end < offset) continue;
      byte[] b = new byte[(int) (end - offset + 8)];
      in.read(b, 8, b.length - 8);
      b[0] = (byte) 0x89;
      b[1] = 0x50;
      b[2] = 0x4e;
      b[3] = 0x47;
      b[4] = 0x0d;
      b[5] = 0x0a;
      b[6] = 0x1a;
      b[7] = 0x0a;

      BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
      String data = img.getWidth() + "-" + img.getHeight() + "-" +
        img.getRaster().getNumBands() + "-" + ImageTools.getPixelType(img);
      Vector v = new Vector();
      if (seriesOffsets.containsKey(data)) {
        v = (Vector) seriesOffsets.get(data);
      }
      v.add(new Long(offset));
      seriesOffsets.put(data, v);

      v = new Vector();
      if (seriesLengths.containsKey(data)) {
        v = (Vector) seriesLengths.get(data);
      }
      v.add(new Long(end));
      seriesLengths.put(data, v);
    }

    String[] keys = (String[]) seriesOffsets.keySet().toArray(new String[0]);

    if (keys.length == 0) {
      throw new FormatException("Pixel data not found.");
    }

    core = new CoreMetadata[keys.length];

    offsets = new Vector[keys.length];
    lengths = new Vector[keys.length];
    for (int i=0; i<keys.length; i++) {
      core[i] = new CoreMetadata();
      StringTokenizer st = new StringTokenizer(keys[i], "-");
      core[i].sizeX = Integer.parseInt(st.nextToken());
      core[i].sizeY = Integer.parseInt(st.nextToken());
      core[i].sizeC = Integer.parseInt(st.nextToken());
      core[i].pixelType = Integer.parseInt(st.nextToken());
      core[i].rgb = core[i].sizeC > 1;
      offsets[i] = (Vector) seriesOffsets.get(keys[i]);
      core[i].imageCount = offsets[i].size();
      core[i].sizeT = core[i].imageCount;
      lengths[i] = (Vector) seriesLengths.get(keys[i]);
      core[i].sizeZ = 1;
      core[i].dimensionOrder = "XYCZT";
      core[i].interleaved = false;
      core[i].metadataComplete = true;
      core[i].indexed = false;
      core[i].littleEndian = false;
      core[i].falseColor = false;
    }

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    for (int i=0; i<core.length; i++) {
      store.setImageName("Series " + i, i);
      MetadataTools.setDefaultCreationDate(store, id, i);
    }
    MetadataTools.populatePixels(store, this);
  }

}
