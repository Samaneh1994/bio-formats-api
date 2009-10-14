//
// DimensionSwapper.java
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

package loci.formats;

import java.io.IOException;

import loci.formats.meta.MetadataStore;

/**
 * Handles swapping the dimension order of an image series. This class is
 * useful for both reassigning ZCT sizes (the input dimension order), and
 * shuffling around the resultant planar order (the output dimension order).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/DimensionSwapper.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/DimensionSwapper.java">SVN</a></dd></dl>
 */
public class DimensionSwapper extends ReaderWrapper {

  // -- Utility methods --

  /** Converts the given reader into a DimensionSwapper, wrapping if needed. */
  public static DimensionSwapper makeDimensionSwapper(IFormatReader r) {
    if (r instanceof DimensionSwapper) return (DimensionSwapper) r;
    return new DimensionSwapper(r);
  }

  // -- Constructors --

  /** Constructs a DimensionSwapper around a new image reader. */
  public DimensionSwapper() { super(); }

  /** Constructs a DimensionSwapper with the given reader. */
  public DimensionSwapper(IFormatReader r) { super(r); }

  private String[] outputOrder;

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

    if (oldC != newC && reader.getRGBChannelCount() > 1) {
      throw new IllegalArgumentException(
        "Cannot swap C dimension when RGB channel count > 1");
    }

    dims[oldX] = getSizeX();
    dims[oldY] = getSizeY();
    dims[oldZ] = getSizeZ();
    dims[oldC] = getSizeC();
    dims[oldT] = getSizeT();

    int series = getSeries();
    CoreMetadata[] core = getCoreMetadata();

    core[series].sizeX = dims[newX];
    core[series].sizeY = dims[newY];
    core[series].sizeZ = dims[newZ];
    core[series].sizeC = dims[newC];
    core[series].sizeT = dims[newT];
    //core.currentOrder[series] = order;
    if (outputOrder[series] == null) {
      outputOrder[series] = core[series].dimensionOrder;
    }
    core[series].dimensionOrder = order;

    if (oldC != newC) {
      // C was overridden; clear the sub-C dimensional metadata
      core[series].cLengths = new int[] {getSizeC()};
      core[series].cTypes = new String[] {FormatTools.CHANNEL};
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
    FormatTools.assertId(getCurrentFile(), true, 2);
    this.outputOrder[getSeries()] = outputOrder;
  }

  public String getInputOrder() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].dimensionOrder;
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#getSizeX() */
  public int getSizeX() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].sizeX;
  }

  /* @see IFormatReader#getSizeY() */
  public int getSizeY() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].sizeY;
  }

  /* @see IFormatReader#getSizeZ() */
  public int getSizeZ() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].sizeZ;
  }

  /* @see IFormatReader#getSizeC() */
  public int getSizeC() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].sizeC;
  }

  /* @see IFormatReader#getSizeT() */
  public int getSizeT() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return getCoreMetadata()[getSeries()].sizeT;
  }

  /* @see IFormatReader#getChannelDimLengths() */
  public int[] getChannelDimLengths() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    int[] cLengths = getCoreMetadata()[getSeries()].cLengths;
    return cLengths == null ? super.getChannelDimLengths() : cLengths;
  }

  /* @see IFormatReader#getChannelDimTypes() */
  public String[] getChannelDimTypes() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    String[] cTypes = getCoreMetadata()[getSeries()].cTypes;
    return cTypes == null ? super.getChannelDimTypes() : cTypes;
  }

  /* @see IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    if (outputOrder[getSeries()] != null) return outputOrder[getSeries()];
    return getInputOrder();
  }

  /* @see IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return super.openBytes(reorder(no));
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return super.openBytes(reorder(no), x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return super.openBytes(reorder(no), buf);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return super.openBytes(reorder(no), buf, x, y, w, h);
  }

  /* @see IFormatReader#openThumbImage(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    return super.openThumbBytes(reorder(no));
  }

  /* @see IFormatReader#getZCTCoords(int) */
  public int[] getZCTCoords(int no) {
    return FormatTools.getZCTCoords(this, no);
  }

  /* @see IFormatReader#getIndex(int, int, int) */
  public int getIndex(int z, int c, int t) {
    return FormatTools.getIndex(this, z, c, t);
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    String oldFile = getCurrentFile();
    super.setId(id);
    if (!id.equals(oldFile) || outputOrder == null) {
      outputOrder = new String[getSeriesCount()];
    }
  }

  // -- Helper methods --

  protected int reorder(int no) throws FormatException {
    if (getInputOrder() == null) return no;
    return FormatTools.getReorderedIndex(getInputOrder(), getDimensionOrder(),
      getSizeZ(), getEffectiveSizeC(), getSizeT(), getImageCount(), no);
  }

}
