//
// FormatReader.java
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
import java.util.*;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.MetadataStore;

/**
 * Abstract superclass of all biological file format readers.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/FormatReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/FormatReader.java">SVN</a></dd></dl>
 */
public abstract class FormatReader extends FormatHandler
  implements IFormatReader
{

  // -- Constants --

  /** Default thumbnail width and height. */
  protected static final int THUMBNAIL_DIMENSION = 128;

  // -- Fields --

  /** Current file. */
  protected RandomAccessStream in;

  /** Hashtable containing metadata key/value pairs. */
  protected Hashtable metadata;

  /** The number of the current series. */
  protected int series = 0;

  /** Core metadata values. */
  protected CoreMetadata[] core;

  /**
   * Maximum number of bytes to check for header information.
   * If blockCheckLen is zero, the file is never opened for file type analysis.
   */
  protected int blockCheckLen = 0;

  /**
   * Whether the file extension matching one of the reader's suffixes
   * is necessary to identify the file as an instance of this format.
   */
  protected boolean suffixNecessary = true;

  /**
   * Whether the file extension matching one of the reader's suffixes
   * is sufficient to identify the file as an instance of this format.
   */
  protected boolean suffixSufficient = true;

  /** Whether or not to normalize float data. */
  protected boolean normalizeData;

  /** Whether or not to filter out invalid metadata. */
  protected boolean filterMetadata;

  /** Whether or not to collect metadata. */
  protected boolean collectMetadata = true;

  /** Whether or not to save proprietary metadata in the MetadataStore. */
  protected boolean saveOriginalMetadata = false;

  /** Whether or not MetadataStore sets C = 3 for indexed color images. */
  protected boolean indexedAsRGB = false;

  /** Whether or not to group multi-file formats. */
  protected boolean group = true;

  /**
   * Current metadata store. Should never be accessed directly as the
   * semantics of {@link #getMetadataStore()} prevent "null" access.
   */
  protected MetadataStore metadataStore = new DummyMetadata();

  // -- Constructors --

  /** Constructs a format reader with the given name and default suffix. */
  public FormatReader(String format, String suffix) { super(format, suffix); }

  /** Constructs a format reader with the given name and default suffixes. */
  public FormatReader(String format, String[] suffixes) {
    super(format, suffixes);
  }

  // -- Internal FormatReader API methods --

  /**
   * Initializes the given file (parsing header information, etc.).
   * Most subclasses should override this method to perform
   * initialization operations such as parsing metadata.
   */
  protected void initFile(String id) throws FormatException, IOException {
    if (currentId != null) {
      String[] s = getUsedFiles();
      for (int i=0; i<s.length; i++) {
        if (id.equals(s[i])) return;
      }
    }

    series = 0;
    close();
    currentId = id;
    metadata = new Hashtable();

    core = new CoreMetadata[1];
    core[0] = new CoreMetadata();
    core[0].orderCertain = true;

    // reinitialize the MetadataStore
    // NB: critical for metadata conversion to work properly!
    getMetadataStore().createRoot();
  }

  /**
   * Opens the given file, reads in the first few KB and calls
   * isThisType(byte[]) to check whether it matches this format.
   */
  protected boolean checkBytes(String name, int maxLen) {
    try {
      RandomAccessStream ras = new RandomAccessStream(name);
      boolean isThisType = isThisType(ras);
      ras.close();
      return isThisType;
    }
    catch (IOException exc) {
      if (debug) trace(exc);
      return false;
    }
  }

  /** Returns true if the given file name is in the used files list. */
  protected boolean isUsedFile(String file) {
    String[] usedFiles = getUsedFiles();
    for (int i=0; i<usedFiles.length; i++) {
      if (usedFiles[i].equals(file) ||
        usedFiles[i].equals(new Location(file).getAbsolutePath()))
      {
        return true;
      }
    }
    return false;
  }

  /** Adds an entry to the metadata table. */
  protected void addMeta(String key, Object value) {
    if (key == null || value == null || !collectMetadata) return;
    String val = value.toString();
    if (filterMetadata) {
      // verify key & value are not empty
      if (key.length() == 0) return;
      if (val.length() == 0) return;

      // verify key & value are reasonable length
      int maxLen = 8192;
      if (key.length() > maxLen) return;
      if (val.length() > maxLen) return;

      // remove all non-printable characters
      key = DataTools.sanitize(key);
      val = DataTools.sanitize(val);

      // verify key contains at least one alphabetic character
      if (!key.matches(".*[a-zA-Z].*")) return;
    }

    if (saveOriginalMetadata) {
      MetadataStore store = getMetadataStore();
      if (MetadataTools.isOMEXMLMetadata(store)) {
        MetadataTools.populateOriginalMetadata(store, key, val);
      }
    }

    metadata.put(key, val);
  }

  /** Gets a value from the metadata table. */
  protected Object getMeta(String key) {
    return metadata.get(key);
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
  }

  /**
   * Checks if a file matches the type of this format reader.
   * Checks filename suffixes against those known for this format.
   * If the suffix check is inconclusive, the open parameter is true, and the
   * blockCheckLen variable is set to a value greater than zero, the first
   * blockCheckLen bytes of the file are read and tested with
   * {@link #isThisType(byte[])}.
   * @param open If true, and the file extension is insufficient to determine
   *   the file type, the (existing) file is opened for further analysis.
   */
  public boolean isThisType(String name, boolean open) {
    // if file extension ID is insufficient and we can't open the file, give up
    if (!suffixSufficient && !open) return false;

    if (suffixNecessary || suffixSufficient) {
      // it's worth checking the file extension
      boolean suffixMatch = super.isThisType(name);

      // if suffix match is required but it doesn't match, failure
      if (suffixNecessary && !suffixMatch) return false;

      // if suffix matches and that's all we need, green light it
      if (suffixSufficient && suffixMatch) return true;
    }

    // suffix matching was inconclusive; we need to analyze the file contents
    if (!open || blockCheckLen == 0) return false;
    return checkBytes(name, blockCheckLen);
  }

  /* @see IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    try {
      RandomAccessStream stream = new RandomAccessStream(block);
      boolean isThisType = isThisType(stream);
      stream.close();
      return isThisType;
    }
    catch (IOException e) {
      if (debug) LogTools.trace(e);
    }
    return false;
  }

  /* @see IFormatReader#getImageCount() */
  public int getImageCount() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].imageCount;
  }

  /* @see IFormatReader#isRGB() */
  public boolean isRGB() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].rgb;
  }

  /* @see IFormatReader#getSizeX() */
  public int getSizeX() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].sizeX;
  }

  /* @see IFormatReader#getSizeY() */
  public int getSizeY() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].sizeY;
  }

  /* @see IFormatReader#getSizeZ() */
  public int getSizeZ() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].sizeZ;
  }

  /* @see IFormatReader#getSizeC() */
  public int getSizeC() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].sizeC;
  }

  /* @see IFormatReader#getSizeT() */
  public int getSizeT() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].sizeT;
  }

  /* @see IFormatReader#getPixelType() */
  public int getPixelType() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].pixelType;
  }

  /* @see IFormatReader#getEffectiveSizeC() */
  public int getEffectiveSizeC() {
    // NB: by definition, imageCount == effectiveSizeC * sizeZ * sizeT
    return getImageCount() / (getSizeZ() * getSizeT());
  }

  /* @see IFormatReader#getRGBChannelCount() */
  public int getRGBChannelCount() {
    return getSizeC() / getEffectiveSizeC();
  }

  /* @see IFormatReader#isIndexed() */
  public boolean isIndexed() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].indexed;
  }

  /* @see IFormatReader#isFalseColor() */
  public boolean isFalseColor() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].falseColor;
  }

  /* @see IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    return null;
  }

  /* @see IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    return null;
  }

  /* @see IFormatReader#getChannelDimLengths() */
  public int[] getChannelDimLengths() {
    FormatTools.assertId(currentId, true, 1);
    if (core[series].cLengths == null) return new int[] {core[series].sizeC};
    return core[series].cLengths;
  }

  /* @see IFormatReader#getChannelDimTypes() */
  public String[] getChannelDimTypes() {
    FormatTools.assertId(currentId, true, 1);
    if (core[series].cTypes == null) return new String[] {FormatTools.CHANNEL};
    return core[series].cTypes;
  }

  /* @see IFormatReader#getThumbSizeX() */
  public int getThumbSizeX() {
    FormatTools.assertId(currentId, true, 1);
    if (core[series].thumbSizeX == 0) {
      int sx = getSizeX();
      int sy = getSizeY();
      int thumbSizeX =
        sx > sy ? THUMBNAIL_DIMENSION : sx * THUMBNAIL_DIMENSION / sy;
      if (thumbSizeX == 0) thumbSizeX = 1;
      return thumbSizeX;
    }
    return core[series].thumbSizeX;
  }

  /* @see IFormatReader#getThumbSizeY() */
  public int getThumbSizeY() {
    FormatTools.assertId(currentId, true, 1);
    if (core[series].thumbSizeY == 0) {
      int sx = getSizeX();
      int sy = getSizeY();
      int thumbSizeY =
        sy > sx ? THUMBNAIL_DIMENSION : sy * THUMBNAIL_DIMENSION / sx;
      if (thumbSizeY == 0) thumbSizeY = 1;
      return thumbSizeY;
    }
    return core[series].thumbSizeY;
  }

  /* @see IFormatReader.isLittleEndian() */
  public boolean isLittleEndian() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].littleEndian;
  }

  /* @see IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    FormatTools.assertId(currentId, true, 1);
    // by default, the input order and output order are the same
    if (core[series].outputOrder != null) return core[series].outputOrder;
    return getInputOrder();
  }

  /* @see IFormatReader#getInputOrder() */
  public String getInputOrder() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].inputOrder;
  }

  /* @see IFormatReader#isOrderCertain() */
  public boolean isOrderCertain() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].orderCertain;
  }

  /* @see IFormatReader#isInterleaved() */
  public boolean isInterleaved() {
    return isInterleaved(0);
  }

  /* @see IFormatReader#isInterleaved(int) */
  public boolean isInterleaved(int subC) {
    FormatTools.assertId(currentId, true, 1);
    return core[series].interleaved;
  }

  /* @see IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return openBytes(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    return openImage(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openImage(int, int, int, int, int) */
  public BufferedImage openImage(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return ImageTools.openImage(openBytes(no, x, y, w, h), this, w, h);
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
    int bpp = FormatTools.getBytesPerPixel(getPixelType());
    int ch = getRGBChannelCount();
    byte[] newBuffer = new byte[w * h * ch * bpp];
    return openBytes(no, newBuffer, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public abstract byte[] openBytes(int no, byte[] buf, int x, int y,
    int w, int h) throws FormatException, IOException;

  /* @see IFormatReader#openThumbImage(int) */
  public BufferedImage openThumbImage(int no)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    return ImageTools.scale(openImage(no), getThumbSizeX(),
      getThumbSizeY(), false);
  }

  /* @see IFormatReader#openThumbBytes(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    BufferedImage img = openThumbImage(no);
    byte[][] bytes = ImageTools.getPixelBytes(img, isLittleEndian());
    if (bytes.length == 1) return bytes[0];
    byte[] rtn = new byte[getRGBChannelCount() * bytes[0].length];
    for (int i=0; i<getRGBChannelCount(); i++) {
      System.arraycopy(bytes[i], 0, rtn, bytes[0].length * i, bytes[i].length);
    }
    return rtn;
  }

  /* @see IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    if (fileOnly) {
      if (in != null) in.close();
    }
    else close();
  }

  /* @see IFormatReader#getSeriesCount() */
  public int getSeriesCount() {
    FormatTools.assertId(currentId, true, 1);
    return core.length;
  }

  /* @see IFormatReader#setSeries(int) */
  public void setSeries(int no) {
    if (no < 0 || no >= getSeriesCount()) {
      throw new IllegalArgumentException("Invalid series: " + no);
    }
    series = no;
  }

  /* @see IFormatReader#getSeries() */
  public int getSeries() {
    return series;
  }

  /* @see IFormatReader#setGroupFiles(boolean) */
  public void setGroupFiles(boolean groupFiles) {
    FormatTools.assertId(currentId, false, 1);
    group = groupFiles;
  }

  /* @see IFormatReader#isGroupFiles() */
  public boolean isGroupFiles() {
    return group;
  }

  /* @see IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id)
    throws FormatException, IOException
  {
    return FormatTools.CANNOT_GROUP;
  }

  /* @see IFormatReader#isMetadataComplete() */
  public boolean isMetadataComplete() {
    FormatTools.assertId(currentId, true, 1);
    return core[series].metadataComplete;
  }

  /* @see IFormatReader#setNormalized(boolean) */
  public void setNormalized(boolean normalize) {
    FormatTools.assertId(currentId, false, 1);
    normalizeData = normalize;
  }

  /* @see IFormatReader#isNormalized() */
  public boolean isNormalized() {
    return normalizeData;
  }

  /* @see IFormatReader#setMetadataCollected(boolean) */
  public void setMetadataCollected(boolean collect) {
    FormatTools.assertId(currentId, false, 1);
    collectMetadata = collect;
  }

  /* @see IFormatReader#isMetadataCollected() */
  public boolean isMetadataCollected() {
    return collectMetadata;
  }

  /* @see IFormatReader#setOriginalMetadataPopulated(boolean) */
  public void setOriginalMetadataPopulated(boolean populate) {
    FormatTools.assertId(currentId, false, 1);
    saveOriginalMetadata = populate;
  }

  /* @see IFormatReader#isOriginalMetadataPopulated() */
  public boolean isOriginalMetadataPopulated() {
    return saveOriginalMetadata;
  }

  /* @see IFormatReader#getUsedFiles() */
  public String[] getUsedFiles() {
    FormatTools.assertId(currentId, true, 1);
    return new String[] {currentId};
  }

  /* @see IFormatReader#getCurrentFile() */
  public String getCurrentFile() {
    return currentId;
  }

  /* @see IFormatReader#getIndex(int, int, int) */
  public int getIndex(int z, int c, int t) {
    FormatTools.assertId(currentId, true, 1);
    return FormatTools.getIndex(this, z, c, t);
  }

  /* @see IFormatReader#getZCTCoords(int) */
  public int[] getZCTCoords(int index) {
    FormatTools.assertId(currentId, true, 1);
    return FormatTools.getZCTCoords(this, index);
  }

  /* @see IFormatReader#getMetadataValue(String) */
  public Object getMetadataValue(String field) {
    FormatTools.assertId(currentId, true, 1);
    return getMeta(field);
  }

  /* @see IFormatReader#getMetadata() */
  public Hashtable getMetadata() {
    FormatTools.assertId(currentId, true, 1);
    return metadata;
  }

  /* @see IFormatReader#getCoreMetadata() */
  public CoreMetadata[] getCoreMetadata() {
    FormatTools.assertId(currentId, true, 1);
    return core;
  }

  /* @see IFormatReader#setMetadataFiltered(boolean) */
  public void setMetadataFiltered(boolean filter) {
    FormatTools.assertId(currentId, false, 1);
    filterMetadata = filter;
  }

  /* @see IFormatReader#isMetadataFiltered() */
  public boolean isMetadataFiltered() {
    return filterMetadata;
  }

  /* @see IFormatReader#setMetadataStore(MetadataStore) */
  public void setMetadataStore(MetadataStore store) {
    FormatTools.assertId(currentId, false, 1);
    if (store == null) {
      throw new IllegalArgumentException("Metadata object cannot be null; " +
        "use loci.formats.meta.DummyMetadata instead");
    }
    metadataStore = store;
  }

  /* @see IFormatReader#getMetadataStore() */
  public MetadataStore getMetadataStore() {
    return metadataStore;
  }

  /* @see IFormatReader#getMetadataStoreRoot() */
  public Object getMetadataStoreRoot() {
    FormatTools.assertId(currentId, true, 1);
    return getMetadataStore().getRoot();
  }

  /* @see IFormatReader#getUnderlyingReaders() */
  public IFormatReader[] getUnderlyingReaders() {
    return null;
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#isThisType(String) */
  public boolean isThisType(String name) {
    // if necessary, open the file for further analysis
    return isThisType(name, true);
  }

  /* @see IFormatHandler#close() */
  public void close() throws IOException {
    if (in != null) in.close();
    in = null;
    currentId = null;
  }

}
