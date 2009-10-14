//
// BufferedImageWriter.java
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

package loci.formats.gui;

import java.awt.image.BufferedImage;
import java.io.IOException;

import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.WriterWrapper;
import loci.formats.meta.MetadataRetrieve;

/**
 * A writer wrapper for writing image planes from BufferedImage objects.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/gui/BufferedImageWriter.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/gui/BufferedImageWriter.java">SVN</a></dd></dl>
 */
public class BufferedImageWriter extends WriterWrapper {

  // -- Utility methods --

  /**
   * Converts the given writer into a BufferedImageWriter, wrapping if needed.
   */
  public static BufferedImageWriter makeBufferedImageWriter(IFormatWriter w) {
    if (w instanceof BufferedImageWriter) return (BufferedImageWriter) w;
    return new BufferedImageWriter(w);
  }

  // -- Constructors --

  /** Constructs a BufferedImageWriter around a new image writer. */
  public BufferedImageWriter() { super(); }

  /** Constructs a BufferedImageWriter with the given writer. */
  public BufferedImageWriter(IFormatWriter r) { super(r); }

  // -- BufferedImageWriter methods --

  /**
   * Saves the given BufferedImage to the current file.
   * Note that this method will append the image plane to the file; it will not
   * overwrite previously saved image planes.
   * If this image plane is the last one in the file, the last flag must be set.
   */
  public void saveImage(BufferedImage image, boolean last)
    throws FormatException, IOException
  {
    saveImage(image, 0, last, last);
  }

  /**
   * Saves the given BufferedImage to the given series in the current file.
   * Note that this method will append the image plane to the file; it will not
   * overwrite previously saved image planes.
   * If this image plane is the last one in the series, the lastInSeries flag
   * must be set.
   * If this image plane is the last one in the file, the last flag must be set.
   */
  public void saveImage(BufferedImage image, int series,
    boolean lastInSeries, boolean last) throws FormatException, IOException
  {
    Class dataType = getNativeDataType();
    if (BufferedImage.class.isAssignableFrom(dataType)) {
      // native data type is compatible with BufferedImage
      savePlane(image, series, lastInSeries, last);
    }
    else {
      // must convert BufferedImage to byte array
      // TODO - move this code block into its own AWTImageTools method
      boolean littleEndian = false;
      int bpp = FormatTools.getBytesPerPixel(AWTImageTools.getPixelType(image));

      MetadataRetrieve r = getMetadataRetrieve();
      if (r != null) {
        Boolean bigEndian = r.getPixelsBigEndian(series, 0);
        if (bigEndian != null) littleEndian = !bigEndian.booleanValue();
      }

      byte[][] pixelBytes = AWTImageTools.getPixelBytes(image, littleEndian);
      byte[] buf = new byte[pixelBytes.length * pixelBytes[0].length];
      if (isInterleaved()) {
        for (int i=0; i<pixelBytes[0].length; i+=bpp) {
          for (int j=0; j<pixelBytes.length; j++) {
            System.arraycopy(pixelBytes[j], i, buf,
              i * pixelBytes.length + j * bpp, bpp);
          }
        }
      }
      else {
        for (int i=0; i<pixelBytes.length; i++) {
          System.arraycopy(pixelBytes[i], 0, buf,
            i * pixelBytes[0].length, pixelBytes[i].length);
        }
      }
      pixelBytes = null;

      saveBytes(buf, series, lastInSeries, last);
    }
  }

}
