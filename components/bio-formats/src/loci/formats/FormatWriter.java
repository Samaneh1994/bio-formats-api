//
// FormatWriter.java
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

import java.awt.image.ColorModel;
import java.io.IOException;
import java.util.HashMap;

import ome.xml.model.primitives.PositiveInteger;

import loci.common.DataTools;
import loci.common.RandomAccessOutputStream;
import loci.formats.codec.CodecOptions;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.MetadataRetrieve;

/**
 * Abstract superclass of all biological file format writers.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/FormatWriter.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/FormatWriter.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public abstract class FormatWriter extends FormatHandler
  implements IFormatWriter
{

  // -- Fields --

  /** Frame rate to use when writing in frames per second, if applicable. */
  protected int fps = 10;

  /** Default color model. */
  protected ColorModel cm;

  /** Available compression types. */
  protected String[] compressionTypes;

  /** Current compression type. */
  protected String compression;
  
  /** The options if required. */
  protected CodecOptions options;

  /**
   * Whether each plane in each series of the current file has been
   * prepped for writing.
   */
  protected boolean[][] initialized;

  /** Whether the channels in an RGB image are interleaved. */
  protected boolean interleaved;

  /** The number of valid bits per pixel. */
  protected int validBits;

  /** Current series. */
  protected int series;

  /** Whether or not we are writing planes sequentially. */
  protected boolean sequential;

  /**
   * Current metadata retrieval object. Should <b>never</b> be accessed
   * directly as the semantics of {@link #getMetadataRetrieve()}
   * prevent "null" access.
   */
  protected MetadataRetrieve metadataRetrieve = new DummyMetadata();

  /**
   * Next plane index for each series.  This is only used by deprecated methods.
   */
  private HashMap<Integer, Integer> planeIndices =
    new HashMap<Integer, Integer>();

  /** Current file. */
  protected RandomAccessOutputStream out;

  // -- Constructors --

  /** Constructs a format writer with the given name and default suffix. */
  public FormatWriter(String format, String suffix) { super(format, suffix); }

  /** Constructs a format writer with the given name and default suffixes. */
  public FormatWriter(String format, String[] suffixes) {
    super(format, suffixes);
  }

  // -- IFormatWriter API methods --

  /* @see IFormatWriter#changeOutputFile(String) */
  public void changeOutputFile(String id) throws FormatException, IOException {
    setId(id);
  }

  /* @see IFormatWriter#saveBytes(int, byte[]) */
  public void saveBytes(int no, byte[] buf) throws FormatException, IOException
  {
    int width = metadataRetrieve.getPixelsSizeX(getSeries()).getValue();
    int height = metadataRetrieve.getPixelsSizeY(getSeries()).getValue();
    saveBytes(no, buf, 0, 0, width, height);
  }

  /* @see IFormatWriter#savePlane(int, Object) */
  public void savePlane(int no, Object plane)
    throws FormatException, IOException
  {
    int width = metadataRetrieve.getPixelsSizeX(getSeries()).getValue();
    int height = metadataRetrieve.getPixelsSizeY(getSeries()).getValue();
    savePlane(no, plane, 0, 0, width, height);
  }

  /* @see IFormatWriter#savePlane(int, Object, int, int, int, int) */
  public void savePlane(int no, Object plane, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    // NB: Writers use byte arrays by default as the native type.
    if (!(plane instanceof byte[])) {
      throw new IllegalArgumentException("Object to save must be a byte[]");
    }
    saveBytes(no, (byte[]) plane, x, y, w, h);
  }

  /* @see IFormatWriter#setSeries(int) */
  public void setSeries(int series) throws FormatException {
    if (series < 0) throw new FormatException("Series must be > 0.");
    if (series >= metadataRetrieve.getImageCount()) {
      throw new FormatException("Series is '" + series +
        "' but MetadataRetrieve only defines " +
        metadataRetrieve.getImageCount() + " series.");
    }
    this.series = series;
  }

  /* @see IFormatWriter#getSeries() */
  public int getSeries() {
    return series;
  }

  /* @see IFormatWriter#setInterleaved(boolean) */
  public void setInterleaved(boolean interleaved) {
    this.interleaved = interleaved;
  }

  /* @see IFormatWriter#isInterleaved() */
  public boolean isInterleaved() {
    return interleaved;
  }

  /* @see IFormatWriter#setValidBitsPerPixel(int) */
  public void setValidBitsPerPixel(int bits) {
    validBits = bits;
  }

  /* @see IFormatWriter#canDoStacks() */
  public boolean canDoStacks() { return false; }

  /* @see IFormatWriter#setMetadataRetrieve(MetadataRetrieve) */
  public void setMetadataRetrieve(MetadataRetrieve retrieve) {
    FormatTools.assertId(currentId, false, 1);
    if (retrieve == null) {
      throw new IllegalArgumentException("Metadata object is null");
    }
    metadataRetrieve = retrieve;
  }

  /* @see IFormatWriter#getMetadataRetrieve() */
  public MetadataRetrieve getMetadataRetrieve() {
    return metadataRetrieve;
  }

  /* @see IFormatWriter#setColorModel(ColorModel) */
  public void setColorModel(ColorModel model) { cm = model; }

  /* @see IFormatWriter#getColorModel() */
  public ColorModel getColorModel() { return cm; }

  /* @see IFormatWriter#setFramesPerSecond(int) */
  public void setFramesPerSecond(int rate) { fps = rate; }

  /* @see IFormatWriter#getFramesPerSecond() */
  public int getFramesPerSecond() { return fps; }

  /* @see IFormatWriter#getCompressionTypes() */
  public String[] getCompressionTypes() { return compressionTypes; }

  /* @see IFormatWriter#setCompression(compress) */
  public void setCompression(String compress) throws FormatException {
    // check that this is a valid type
    for (int i=0; i<compressionTypes.length; i++) {
      if (compressionTypes[i].equals(compress)) {
        compression = compress;
        return;
      }
    }
    throw new FormatException("Invalid compression type: " + compress);
  }

  /* @see IFormatWriter#getCompression() */
  public String getCompression() {
    return compression;
  }

  /* @see IFormatWriter#getPixelTypes() */
  public int[] getPixelTypes() {
    return getPixelTypes(getCompression());
  }

  /* @see IFormatWriter#getPixelTypes(String) */
  public int[] getPixelTypes(String codec) {
    return new int[] {FormatTools.INT8, FormatTools.UINT8, FormatTools.INT16,
      FormatTools.UINT16, FormatTools.INT32, FormatTools.UINT32,
      FormatTools.FLOAT};
  }

  /* @see IFormatWriter#isSupportedType(int) */
  public boolean isSupportedType(int type) {
    int[] types = getPixelTypes();
    for (int i=0; i<types.length; i++) {
      if (type == types[i]) return true;
    }
    return false;
  }

  /* @see IFormatWriter#setWriteSequentially(boolean) */
  public void setWriteSequentially(boolean sequential) {
    this.sequential = sequential;
  }

  // -- Deprecated IFormatWriter API methods --

  /**
   * @deprecated
   * @see IFormatWriter#saveBytes(byte[], boolean)
   */
  public void saveBytes(byte[] bytes, boolean last)
    throws FormatException, IOException
  {
    saveBytes(bytes, 0, last, last);
  }

  /**
   * @deprecated
   * @see IFormatWriter#saveBytes(byte[], int, boolean, boolean)
   */
  public void saveBytes(byte[] bytes, int series, boolean lastInSeries,
    boolean last) throws FormatException, IOException
  {
    setSeries(series);
    Integer planeIndex = planeIndices.get(series);
    if (planeIndex == null) planeIndex = 0;
    saveBytes(planeIndex, bytes);
    planeIndex++;
    planeIndices.put(series, planeIndex);
  }

  /**
   * @deprecated
   * @see IFormatWriter#savePlane(Object, boolean)
   */
  public void savePlane(Object plane, boolean last)
    throws FormatException, IOException
  {
    savePlane(plane, 0, last, last);
  }

  /**
   * @deprecated
   * @see IFormatWriter#savePlane(Object, int, boolean, boolean)
   */
  public void savePlane(Object plane, int series, boolean lastInSeries,
    boolean last) throws FormatException, IOException
  {
    // NB: Writers use byte arrays by default as the native type.
    if (!(plane instanceof byte[])) {
      throw new IllegalArgumentException("Object to save must be a byte[]");
    }
    saveBytes((byte[]) plane, series, lastInSeries, last);
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    if (id.equals(currentId)) return;
    close();
    currentId = id;
    out = new RandomAccessOutputStream(currentId);

    MetadataRetrieve r = getMetadataRetrieve();
    initialized = new boolean[r.getImageCount()][];
    int oldSeries = series;
    for (int i=0; i<r.getImageCount(); i++) {
      setSeries(i);
      initialized[i] = new boolean[getPlaneCount()];
    }
    setSeries(oldSeries);
  }

  /* @see IFormatHandler#close() */
  public void close() throws IOException {
    if (out != null) out.close();
    out = null;
    currentId = null;
    initialized = null;
  }

  /**
   * Sets the codec options.
   * @param options The options to set.
   */
  public void setCodecOptions(CodecOptions options) {
    this.options = options;
  }
  
  // -- Helper methods --

  /**
   * Ensure that the arguments that are being passed to saveBytes(...) are
   * valid.
   * @throws FormatException if any of the arguments is invalid.
   */
  protected void checkParams(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException
  {
    MetadataRetrieve r = getMetadataRetrieve();
    MetadataTools.verifyMinimumPopulated(r, series);

    if (buf == null) throw new FormatException("Buffer cannot be null.");
    int z = r.getPixelsSizeZ(series).getValue().intValue();
    int t = r.getPixelsSizeT(series).getValue().intValue();
    int c = r.getChannelCount(series);
    int planes = z * c * t;

    if (no < 0) throw new FormatException("Plane index must be >= 0");
    if (no >= planes) {
      throw new FormatException("Plane index must be < " + planes);
    }

    int sizeX = r.getPixelsSizeX(series).getValue().intValue();
    int sizeY = r.getPixelsSizeY(series).getValue().intValue();
    if (x < 0) throw new FormatException("X coordinate must be >= 0");
    if (y < 0) throw new FormatException("Y coordinate must be >= 0");
    if (x >= sizeX) {
      throw new FormatException("X coordinate must be < " + sizeX);
    }
    if (y >= sizeY) {
      throw new FormatException("Y coordinate must be < " + sizeY);
    }
    if (w <= 0) throw new FormatException("Width must be > 0");
    if (h <= 0) throw new FormatException("Height must be > 0");
    if (x + w > sizeX) throw new FormatException("(w + x) must be <= " + sizeX);
    if (y + h > sizeY) throw new FormatException("(h + y) must be <= " + sizeY);

    int pixelType =
      FormatTools.pixelTypeFromString(r.getPixelsType(series).toString());
    int bpp = FormatTools.getBytesPerPixel(pixelType);
    PositiveInteger samples = r.getChannelSamplesPerPixel(series, 0);
    if (samples == null) samples = new PositiveInteger(1);
    int minSize = bpp * w * h * samples.getValue();
    if (buf.length < minSize) {
      throw new FormatException("Buffer is too small; expected " + minSize +
        " bytes, got " + buf.length + " bytes.");
    }

    if (!DataTools.containsValue(getPixelTypes(compression), pixelType)) {
      throw new FormatException("Unsupported image type '" +
        FormatTools.getPixelTypeString(pixelType) + "'.");
    }
  }

  /**
   * Seek to the given (x, y) coordinate of the image that starts at
   * the given offset.
   */
  protected void seekToPlaneOffset(long baseOffset, int x, int y)
    throws IOException
  {
    out.seek(baseOffset);

    MetadataRetrieve r = getMetadataRetrieve();
    int samples = getSamplesPerPixel();
    int pixelType =
      FormatTools.pixelTypeFromString(r.getPixelsType(series).toString());
    int bpp = FormatTools.getBytesPerPixel(pixelType);

    if (interleaved) bpp *= samples;

    int sizeX = r.getPixelsSizeX(series).getValue().intValue();

    out.skipBytes(bpp * (y * sizeX + x));
  }

  /**
   * Returns true if the given rectangle coordinates correspond to a full
   * image in the given series.
   */
  protected boolean isFullPlane(int x, int y, int w, int h) {
    MetadataRetrieve r = getMetadataRetrieve();
    int sizeX = r.getPixelsSizeX(series).getValue().intValue();
    int sizeY = r.getPixelsSizeY(series).getValue().intValue();
    return x == 0 && y == 0 && w == sizeX && h == sizeY;
  }

  /** Retrieve the number of samples per pixel for the current series. */
  protected int getSamplesPerPixel() {
    MetadataRetrieve r = getMetadataRetrieve();
    PositiveInteger samples = r.getChannelSamplesPerPixel(series, 0);
    if (samples == null) {
      LOGGER.warn("SamplesPerPixel #0 is null. It is assumed to be 1.");
    }
    return samples == null ? 1 : samples.getValue();
  }

  /** Retrieve the total number of planes in the current series. */
  protected int getPlaneCount() {
    MetadataRetrieve r = getMetadataRetrieve();
    int z = r.getPixelsSizeZ(series).getValue().intValue();
    int t = r.getPixelsSizeT(series).getValue().intValue();
    int c = r.getPixelsSizeC(series).getValue().intValue();
    c /= r.getChannelSamplesPerPixel(series, 0).getValue().intValue();
    return z * c * t;
  }

}
