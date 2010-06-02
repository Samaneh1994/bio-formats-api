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
 * For indexed color data representing true color, factors out
 * the indices, replacing them with the color table values directly.
 *
 * For all other data (either non-indexed, or indexed with
 * "false color" tables), does nothing.
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

  /* @see IFormatReader#getSizeC() */
  @Override
  public int getSizeC() {
    if (passthrough()) return reader.getSizeC();
    return reader.getSizeC() * getLookupTableComponentCount();
  }

  /* @see IFormatReader#isRGB() */
  @Override
  public boolean isRGB() {
    if (passthrough()) return reader.isRGB();
    return getRGBChannelCount() > 1;
  }

  /* @see IFormatReader#isIndexed() */
  @Override
  public boolean isIndexed() {
    if (passthrough()) return reader.isIndexed();
    return false;
  }

  /* @see IFormatReader#get8BitLookupTable() */
  @Override
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    if (passthrough()) return reader.get8BitLookupTable();
    return null;
  }

  /* @see IFormatReader#get16BitLookupTable() */
  @Override
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    if (passthrough()) return reader.get16BitLookupTable();
    return null;
  }

  /* @see IFormatReader#getChannelDimLengths() */
  @Override
  public int[] getChannelDimLengths() {
    int[] cLengths = reader.getChannelDimLengths();
    if (passthrough()) return cLengths;

    // in the case of a single channel, replace rather than append
    if (cLengths.length == 1 && cLengths[0] == 1) cLengths = new int[0];

    // append filled dimension to channel dim lengths
    int[] newLengths = new int[1 + cLengths.length];
    newLengths[0] = getLookupTableComponentCount();
    System.arraycopy(cLengths, 0, newLengths, 1, cLengths.length);
    return newLengths;
  }

  /* @see IFormatReader#getChannelDimTypes() */
  @Override
  public String[] getChannelDimTypes() {
    String[] cTypes = reader.getChannelDimTypes();
    if (passthrough()) return cTypes;

    // in the case of a single channel, leave type unchanged
    int[] cLengths = reader.getChannelDimLengths();
    if (cLengths.length == 1 && cLengths[0] == 1) return cTypes;

    // append filled dimension to channel dim types
    String[] newTypes = new String[1 + cTypes.length];
    newTypes[0] = FormatTools.CHANNEL;
    System.arraycopy(cTypes, 0, newTypes, 1, cTypes.length);
    return newTypes;
  }

  /* @see IFormatReader#openBytes(int) */
  @Override
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  @Override
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return openBytes(no, buf, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  @Override
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    byte[] buf = new byte[w * h * getRGBChannelCount() *
      FormatTools.getBytesPerPixel(getPixelType())];
    return openBytes(no, buf, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (passthrough()) return reader.openBytes(no, buf, x, y, w, h);

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

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#setId(String) */
  @Override
  public void setId(String id) throws FormatException, IOException {
    super.setId(id);
    MetadataStore store = getMetadataStore();
    MetadataTools.populatePixels(store, this, false, false);
  }

  // -- Helper methods --

  /** Whether to hand off all method calls directly to the wrapped reader. */
  private boolean passthrough() {
    return !reader.isIndexed() || reader.isFalseColor();
  }

  /** Gets the number of color components in the lookup table. */
  private int getLookupTableComponentCount() {
    try {
      byte[][] lut8 = reader.get8BitLookupTable();
      if (lut8 != null) return lut8.length;
      short[][] lut16 = reader.get16BitLookupTable();
      if (lut16 != null) return lut16.length;
    }
    catch (FormatException exc) { }
    catch (IOException exc) { }
    return 3;
  }

}
