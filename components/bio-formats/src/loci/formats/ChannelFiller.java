//
// ChannelFiller.java
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
 * Expands indexed color images to RGB.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/ChannelFiller.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/ChannelFiller.java">SVN</a></dd></dl>
 */
public class ChannelFiller extends ReaderWrapper {

  // -- Utility methods --

  /** Converts the given reader into a ChannelFiller, wrapping if needed. */
  public static ChannelFiller makeChannelFiller(IFormatReader r) {
    if (r instanceof ChannelFiller) return (ChannelFiller) r;
    return new ChannelFiller(r);
  }

  // -- Constructors --

  /** Constructs a ChannelFiller around a new image reader. */
  public ChannelFiller() { super(); }

  /** Constructs a ChannelFiller with a given reader. */
  public ChannelFiller(IFormatReader r) { super(r); }

  // -- IFormatReader API methods --

  /* @see IFormatReader#isIndexed() */
  public boolean isIndexed() {
    return false;
  }

  /* @see IFormatReader#getSizeC() */
  public int getSizeC() {
    return reader.getSizeC() *
      ((reader.isIndexed() && !reader.isFalseColor()) ? 3 : 1);
  }

  /* @see IFormatReader#getRGBChannelCount() */
  public int getRGBChannelCount() {
    return (reader.isIndexed() && !reader.isFalseColor()) ? 3 :
      reader.getRGBChannelCount();
  }

  /* @see IFormatReader#isRGB() */
  public boolean isRGB() {
    return (reader.isIndexed() && !reader.isFalseColor()) || reader.isRGB();
  }

  /* @see IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() {
    try {
      return reader.isFalseColor() ? reader.get8BitLookupTable() : null;
    }
    catch (FormatException e) { }
    catch (IOException e) { }
    return null;
  }

  /* @see IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() {
    try {
      return reader.isFalseColor() ? reader.get16BitLookupTable() : null;
    }
    catch (FormatException e) { }
    catch (IOException e) { }
    return null;
  }

  /* @see IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return openBytes(no, buf, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    byte[] buf = new byte[w * h * getRGBChannelCount() *
      FormatTools.getBytesPerPixel(getPixelType())];
    return openBytes(no, buf, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (reader.isIndexed() && !reader.isFalseColor()) {
      byte[] pix = reader.openBytes(no, x, y, w, h);
      if (getPixelType() == FormatTools.UINT8) {
        byte[][] b = ImageTools.indexedToRGB(reader.get8BitLookupTable(), pix);
        if (isInterleaved()) {
          int pt = 0;
          for (int i=0; i<b[0].length; i++) {
            for (int j=0; j<b.length; j++) {
              buf[pt++] = b[j][i];
            }
          }
        }
        else {
          for (int i=0; i<b.length; i++) {
            System.arraycopy(b[i], 0, buf, i*b[i].length, b[i].length);
          }
        }
        return buf;
      }
      else {
        short[][] s = ImageTools.indexedToRGB(reader.get16BitLookupTable(),
          pix, isLittleEndian());

        if (isInterleaved()) {
          int pt = 0;
          for (int i=0; i<s[0].length; i++) {
            for (int j=0; j<s.length; j++) {
              buf[pt++] = (byte) (isLittleEndian() ?
                (s[j][i] & 0xff) : (s[j][i] >> 8));
              buf[pt++] = (byte) (isLittleEndian() ?
                (s[j][i] >> 8) : (s[j][i] & 0xff));
            }
          }
        }
        else {
          int pt = 0;
          for (int i=0; i<s.length; i++) {
            for (int j=0; j<s[i].length; j++) {
              buf[pt++] = (byte) (isLittleEndian() ?
                (s[i][j] & 0xff) : (s[i][j] >> 8));
              buf[pt++] = (byte) (isLittleEndian() ?
                (s[i][j] >> 8) : (s[i][j] & 0xff));
            }
          }
        }
        return buf;
      }
    }
    return reader.openBytes(no, buf, x, y, w, h);
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    super.setId(id);
    MetadataStore store = getMetadataStore();
    MetadataTools.populatePixels(store, this, false, false);
  }

}
