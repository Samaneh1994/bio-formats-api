//
// ChannelMerger.java
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

import java.awt.image.BufferedImage;
import java.io.*;
import loci.common.*;

/**
 * Logic to automatically merge channels in a file.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/ChannelMerger.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/ChannelMerger.java">SVN</a></dd></dl>
 */
public class ChannelMerger extends ReaderWrapper {

  // -- Constructor --

  /** Constructs a ChannelMerger around a new image reader. */
  public ChannelMerger() { super(); }

  /** Constructs a ChannelMerger with the given reader. */
  public ChannelMerger(IFormatReader r) { super(r); }

  // -- ChannelMerger API methods --

  /** Determines whether the channels in the file can be merged. */
  public boolean canMerge() {
    int c = getSizeC();
    return c > 1 && c <= 4 && !reader.isRGB();
  }

  // -- ChannelMerger API methods --

  /**
   * Returns the image number in the original dataset that corresponds to the
   * given image number.
   *
   * @param no is an image number greater than or equal to 0 and less than
   *   getImageCount()
   * @return the corresponding image number in the original (unmerged) data.
   */
  public int getOriginalIndex(int no) throws FormatException, IOException {
    int imageCount = getImageCount();
    int originalCount = reader.getImageCount();

    if (imageCount == originalCount) return no;
    int[] coords = getZCTCoords(no);
    return reader.getIndex(coords[0], coords[1], coords[2]);
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#getImageCount() */
  public int getImageCount() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    int no = reader.getImageCount();
    if (canMerge()) no /= getSizeC();
    return no;
  }

  /* @see IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    String order = reader.getDimensionOrder();
    if (canMerge()) {
      StringBuffer sb = new StringBuffer(order);
      while (order.indexOf("C") != 2) {
        char pre = order.charAt(order.indexOf("C") - 1);
        sb.setCharAt(order.indexOf("C"), pre);
        sb.setCharAt(order.indexOf(pre), 'C');
        order = sb.toString();
      }
    }
    return order;
  }

  /* @see IFormatReader#isRGB() */
  public boolean isRGB() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return canMerge() || reader.isRGB();
  }

  /* @see IFormatReader#isIndexed() */
  public boolean isIndexed() {
    FormatTools.assertId(getCurrentFile(), true, 2);
    return !canMerge() && reader.isIndexed();
  }

  /* @see IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return openBytes(no , buf, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    int bpp = FormatTools.getBytesPerPixel(getPixelType());
    int ch = getRGBChannelCount();
    byte[] newBuffer = new byte[w * h * ch * bpp];
    return openBytes(no, newBuffer, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(getCurrentFile(), true, 2);
    if (!canMerge()) return super.openBytes(no, buf, x, y, w, h);
    int sizeC = getSizeC();
    int[] nos = getZCTCoords(no);
    int z = nos[0], t = nos[2];
    for (int c=0; c<sizeC; c++) {
      byte[] b = reader.openBytes(reader.getIndex(z, c, t), x, y, w, h);
      System.arraycopy(b, 0, buf, c * b.length, b.length);
    }
    return buf;
  }

  /* @see IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    return openImage(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openImage(int, int, int, int, int) */
  public BufferedImage openImage(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return AWTImageTools.openImage(openBytes(no, x, y, w, h), this, w, h);
  }

  /* @see IFormatReader#openThumbImage(int) */
  public BufferedImage openThumbImage(int no)
    throws FormatException, IOException
  {
    FormatTools.assertId(getCurrentFile(), true, 2);
    if (!canMerge()) return super.openThumbImage(no);
    return AWTImageTools.scale(openImage(no), getThumbSizeX(),
      getThumbSizeY(), true);
  }

  public int getIndex(int z, int c, int t) {
    return FormatTools.getIndex(this, z, c, t);
  }

  public int[] getZCTCoords(int index) {
    return FormatTools.getZCTCoords(this, index);
  }

}
