//
// DimensionSwapper.java
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import loci.formats.meta.MetadataStore;

/**
 * Handles swapping the dimension order of an image series. This class is
 * useful for both reassigning ZCT sizes (the input dimension order), and
 * shuffling around the resultant planar order (the output dimension order).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/DimensionSwapper.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/DimensionSwapper.java">SVN</a></dd></dl>
 */
public class DimensionSwapper extends ReaderWrapper {

  // -- Fields --

  /** The output dimension order for the image series. */
  protected String outputOrder;

  // -- Constructors --

  /** Constructs a DimensionSwapper around a new image reader. */
  public DimensionSwapper() { super(); }

  /** Constructs a DimensionSwapper with the given reader. */
  public DimensionSwapper(IFormatReader r) { super(r); }

  // -- DimensionSwapper API methods --

  /**
   * Sets the input dimension order according to the given string (e.g.,
   * "XYZCT"). This string indicates the planar rasterization order from the
   * source, overriding the detected order. It may result in the dimensional
   * axis sizes changing.
   *
   * If the given order is identical to the file's native order, then
   * nothing happens. Note that this method will throw an exception if X and Y
   * do not appear in positions 0 and 1 (although X and Y can be reversed).
   */
  public void swapDimensions(String order) {
    FormatTools.assertId(getCurrentFile(), true, 2);

    if (order == null) throw new IllegalArgumentException("order is null");

    String oldOrder = getDimensionOrder();
    if (order.equals(oldOrder)) return;

    if (order.length() != 5) {
      throw new IllegalArgumentException("order is unexpected length (" +
        order.length() + ")");
    }

    int newX = order.indexOf("X");
    int newY = order.indexOf("Y");
    int newZ = order.indexOf("Z");
    int newC = order.indexOf("C");
    int newT = order.indexOf("T");

    if (newX < 0) throw new IllegalArgumentException("X does not appear");
    if (newY < 0) throw new IllegalArgumentException("Y does not appear");
    if (newZ < 0) throw new IllegalArgumentException("Z does not appear");
    if (newC < 0) throw new IllegalArgumentException("C does not appear");
    if (newT < 0) throw new IllegalArgumentException("T does not appear");

    if (newX > 1) {
      throw new IllegalArgumentException("X in unexpected position (" +
        newX + ")");
    }
    if (newY > 1) {
      throw new IllegalArgumentException("Y in unexpected position (" +
        newY + ")");
    }

    int[] dims = new int[5];

    int oldX = oldOrder.indexOf("X");
    int oldY = oldOrder.indexOf("Y");
    int oldZ = oldOrder.indexOf("Z");
    int oldC = oldOrder.indexOf("C");
    int oldT = oldOrder.indexOf("T");

    if (oldC != newC && getRGBChannelCount() > 1) {
      throw new IllegalArgumentException(
        "Cannot swap C dimension when RGB channel count > 1");
    }

    dims[oldX] = getSizeX();
    dims[oldY] = getSizeY();
    dims[oldZ] = getSizeZ();
    dims[oldC] = getSizeC();
    dims[oldT] = getSizeT();

    int series = getSeries();
    CoreMetadata core = getCoreMetadata();

    core.sizeX[series] = dims[newX];
    core.sizeY[series] = dims[newY];
    core.sizeZ[series] = dims[newZ];
    core.sizeC[series] = dims[newC];
    core.sizeT[series] = dims[newT];
    core.currentOrder[series] = order;

    if (oldC != newC) {
      // C was overridden; clear the sub-C dimensional metadata
      core.cLengths[series] = null;
      core.cTypes[series] = null;
    }

    MetadataStore store = getMetadataStore();
    MetadataTools.populatePixels(store, this);
  }

  /**
   * Sets the output dimension order according to the given string (e.g.,
   * "XYZCT"). This string indicates the final planar rasterization
   * order&mdash;i.e., the mapping from 1D plane number to 3D (Z, C, T) tuple.
   * Changing it will not affect the Z, C or T sizes but will alter the order
   * in which planes are returned when iterating.
   *
   * This method is useful when your application requires a particular output
   * dimension order; e.g., ImageJ virtual stacks must be in XYCZT order.
   */
  public void setOutputOrder(String outputOrder) {
    this.outputOrder = outputOrder;
  }

  public String getOutputOrder() {
    return outputOrder;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#getSizeX() */
  public int getSizeX() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().sizeX[getSeries()];
  }

  /* @see loci.formats.IFormatReader#getSizeY() */
  public int getSizeY() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().sizeY[getSeries()];
  }

  /* @see loci.formats.IFormatReader#getSizeZ() */
  public int getSizeZ() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().sizeZ[getSeries()];
  }

  /* @see loci.formats.IFormatReader#getSizeC() */
  public int getSizeC() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().sizeC[getSeries()];
  }

  /* @see loci.formats.IFormatReader#getSizeT() */
  public int getSizeT() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().sizeT[getSeries()];
  }

  /* @see loci.formats.IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata().currentOrder[getSeries()];
  }

  /* @see loci.formats.IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return super.openBytes(reorder(no));
  }

  /* @see loci.formats.IFormatReader#openBytes(int, int, int, int, int) */
  public byte[] openBytes(int no, int x, int y, int width, int height)
    throws FormatException, IOException
  {
    return super.openBytes(reorder(no), x, y, width, height);
  }

  /* @see loci.formats.IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return super.openBytes(reorder(no), buf);
  }

  /*
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y,
    int width, int height) throws FormatException, IOException
  {
    return super.openBytes(reorder(no));
  }

  /* @see loci.formats.IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    return super.openImage(reorder(no));
  }

  /* @see loci.formats.IFormatReader#openImage(int, int, int, int, int) */
  public BufferedImage openImage(int no, int x, int y, int width, int height)
    throws FormatException, IOException
  {
    return super.openImage(reorder(no), x, y, width, height);
  }

  /* @see loci.formats.IFormatReader#openThumbImage(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    return super.openThumbBytes(reorder(no));
  }

  /* @see loci.formats.IFormatReader#openThumbImage(int) */
  public BufferedImage openThumbImage(int no)
    throws FormatException, IOException
  {
    return super.openThumbImage(reorder(no));
  }

  /* @see loci.formats.IFormatReader#getIndex(int, int, int) */
  public int getIndex(int z, int c, int t) {
    if (outputOrder == null) return super.getIndex(z, c, t);
    FormatTools.assertId(getCurrentFile(), true, 2);
    int zSize = getSizeZ();
    int cSize = getEffectiveSizeC();
    int tSize = getSizeT();
    int num = getImageCount();
    return FormatTools.getIndex(outputOrder,
      zSize, cSize, tSize, num, z, c, t);
  }

  /* @see loci.formats.IFormatReader#getZCTCoords(int) */
  public int[] getZCTCoords(int index) {
    if (outputOrder == null) return super.getZCTCoords(index);
    FormatTools.assertId(getCurrentFile(), true, 2);
    int zSize = reader.getSizeZ();
    int cSize = reader.getEffectiveSizeC();
    int tSize = reader.getSizeT();
    int num = reader.getImageCount();
    return FormatTools.getZCTCoords(outputOrder,
      zSize, cSize, tSize, num, index);
  }

  // -- Helper methods --

  protected int reorder(int no) throws FormatException {
    if (outputOrder == null) return no;
    return FormatTools.getReorderedIndex(reader, outputOrder, no);
  }

}
