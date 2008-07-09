//
// Index16ColorModel.java
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

import java.awt.image.*;
import java.io.IOException;

/**
 * ColorModel that handles 16 bits per channel lookup tables.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/Index16ColorModel.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/Index16ColorModel.java">SVN</a></dd></dl>
 */
public class Index16ColorModel extends ColorModel {

  // -- Fields --

  /** Lookup tables. */
  private short[] redShort, greenShort, blueShort, alphaShort;

  private int pixelBits;

  // -- Constructors --

  public Index16ColorModel(int bits, int size, short[][] table)
    throws IOException
  {
    super(bits);

    if (table == null) throw new IOException("LUT cannot be null");
    for (int i=0; i<table.length; i++) {
      if (table[i].length < size) {
        throw new IOException("LUT " + i + " too small");
      }
    }

    if (table.length > 0) redShort = table[0];
    if (table.length > 1) greenShort = table[1];
    if (table.length > 2) blueShort = table[2];
    if (table.length > 3) alphaShort = table[3];
    pixelBits = bits;
  }

  // -- ColorModel API methods --

  /* @see java.awt.image.ColorModel#getDataElements(int, Object) */
  public synchronized Object getDataElements(int rgb, Object pixel) {
    int red = (rgb >> 16) & 0xff;
    int green = (rgb >> 8) & 0xff;
    int blue = rgb & 0xff;
    //int alpha = (rgb >>> 24);

    short[] p = pixel == null ? new short[3] : (short[]) pixel;
    p[0] = (short) red;
    p[1] = (short) green;
    p[2] = (short) blue;
    return p;
  }

  /* @see java.awt.image.ColorModel#isCompatibleRaster(Raster) */
  public boolean isCompatibleRaster(Raster raster) {
    return raster.getNumBands() == 1;
  }

  /* @see java.awt.image.ColorModel#createCompatibleWritableRaster(int, int) */
  public WritableRaster createCompatibleWritableRaster(int w, int h) {
    return Raster.createInterleavedRaster(DataBuffer.TYPE_USHORT,
      w, h, 1, null);
  }

  /* @see java.awt.image.ColorModel#getAlpha(int) */
  public int getAlpha(int pixel) {
    if (alphaShort != null) return alphaShort[pixel] & 0xffff;
    return 0xffff;
  }

  /* @see java.awt.image.ColorModel#getBlue(int) */
  public int getBlue(int pixel) {
    return blueShort[pixel] & 0xffff;
  }

  /* @see java.awt.image.ColorModel#getGreen(int) */
  public int getGreen(int pixel) {
    return greenShort[pixel] & 0xffff;
  }

  /* @see java.awt.image.ColorModel#getRed(int) */
  public int getRed(int pixel) {
    return redShort[pixel] & 0xffff;
  }

}
