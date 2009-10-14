//
// ImageReader.java
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import loci.common.LogTools;
import loci.common.RandomAccessInputStream;
import loci.formats.meta.MetadataStore;

/**
 * ImageReader is the master file format reader for all supported formats.
 * It uses one instance of each reader subclass (specified in readers.txt,
 * or other class list source) to identify file formats and read data.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/ImageReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/ImageReader.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class ImageReader implements IFormatReader {

  // -- Static fields --

  /** Default list of reader classes, for use with noargs constructor. */
  private static ClassList defaultClasses;

  // -- Static helper methods --

  private static ClassList getDefaultReaderClasses() {
    if (defaultClasses == null) {
      // load built-in reader classes from readers.txt file
      try {
        defaultClasses = new ClassList("readers.txt", IFormatReader.class);
      }
      catch (IOException exc) {
        defaultClasses = new ClassList(IFormatReader.class);
        LogTools.trace(exc);
      }
    }
    return defaultClasses;
  }

  // -- Fields --

  /** List of supported file format readers. */
  private IFormatReader[] readers;

  /**
   * Valid suffixes for this file format.
   * Populated the first time getSuffixes() is called.
   */
  private String[] suffixes;

  /** Name of current file. */
  private String currentId;

  /** Current form index. */
  private int current;

  // -- Constructors --

  /**
   * Constructs a new ImageReader with the default
   * list of reader classes from readers.txt.
   */
  public ImageReader() {
    this(getDefaultReaderClasses());
  }

  /** Constructs a new ImageReader from the given list of reader classes. */
  public ImageReader(ClassList classList) {
    // add readers to the list
    Vector v = new Vector();
    Class[] c = classList.getClasses();
    for (int i=0; i<c.length; i++) {
      IFormatReader reader = null;
      try {
        reader = (IFormatReader) c[i].newInstance();
      }
      catch (IllegalAccessException exc) { }
      catch (InstantiationException exc) { }
      if (reader == null) {
        LogTools.println("Error: " + c[i].getName() +
          " cannot be instantiated.");
        continue;
      }
      v.add(reader);
    }
    readers = new IFormatReader[v.size()];
    v.copyInto(readers);
  }

  // -- ImageReader API methods --

  /** Gets a string describing the file format for the given file. */
  public String getFormat(String id) throws FormatException, IOException {
    return getReader(id).getFormat();
  }

  /** Gets the reader used to open the given file. */
  public IFormatReader getReader(String id)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) {
      // initialize file
      boolean success = false;
      for (int i=0; i<readers.length; i++) {
        if (readers[i].isThisType(id)) {
          current = i;
          currentId = id;
          success = true;
          break;
        }
      }
      if (!success) {
        throw new UnknownFormatException("Unknown file format: " + id);
      }
    }
    return getReader();
  }

  /** Gets the reader used to open the current file. */
  public IFormatReader getReader() {
    FormatTools.assertId(currentId, true, 2);
    return readers[current];
  }

  /** Gets the file format reader instance matching the given class. */
  public IFormatReader getReader(Class c) {
    for (int i=0; i<readers.length; i++) {
      if (readers[i].getClass().equals(c)) return readers[i];
    }
    return null;
  }

  /** Gets all constituent file format readers. */
  public IFormatReader[] getReaders() {
    IFormatReader[] r = new IFormatReader[readers.length];
    System.arraycopy(readers, 0, r, 0, readers.length);
    return r;
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    for (int i=0; i<readers.length; i++) {
      if (readers[i].isThisType(name, open)) return true;
    }
    return false;
  }

  /* @see IFormatReader.isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    for (int i=0; i<readers.length; i++) {
      if (readers[i].isThisType(block)) return true;
    }
    return false;
  }

  /* @see IFormatReader.isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    for (int i=0; i<readers.length; i++) {
      if (readers[i].isThisType(stream)) return true;
    }
    return false;
  }

  /* @see IFormatReader#getImageCount() */
  public int getImageCount() {
    return getReader().getImageCount();
  }

  /* @see IFormatReader#isRGB() */
  public boolean isRGB() {
    return getReader().isRGB();
  }

  /* @see IFormatReader#getSizeX() */
  public int getSizeX() {
    return getReader().getSizeX();
  }

  /* @see IFormatReader#getSizeY() */
  public int getSizeY() {
    return getReader().getSizeY();
  }

  /* @see IFormatReader#getSizeC() */
  public int getSizeC() {
    return getReader().getSizeC();
  }

  /* @see IFormatReader#getSizeZ() */
  public int getSizeZ() {
    return getReader().getSizeZ();
  }

  /* @see IFormatReader#getSizeT() */
  public int getSizeT() {
    return getReader().getSizeT();
  }

  /* @see IFormatReader#getPixelType() */
  public int getPixelType() {
    return getReader().getPixelType();
  }

  /* @see IFormatReader#getEffectiveSizeC() */
  public int getEffectiveSizeC() {
    return getReader().getEffectiveSizeC();
  }

  /* @see IFormatReader#getRGBChannelCount() */
  public int getRGBChannelCount() {
    return getReader().getRGBChannelCount();
  }

  /* @see IFormatReader#isIndexed() */
  public boolean isIndexed() {
    return getReader().isIndexed();
  }

  /* @see IFormatReader#isFalseColor() */
  public boolean isFalseColor() {
    return getReader().isFalseColor();
  }

  /* @see IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    return getReader().get8BitLookupTable();
  }

  /* @see IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    return getReader().get16BitLookupTable();
  }

  /* @see IFormatReader#getChannelDimLengths() */
  public int[] getChannelDimLengths() {
    return getReader().getChannelDimLengths();
  }

  /* @see IFormatReader#getChannelDimTypes() */
  public String[] getChannelDimTypes() {
    return getReader().getChannelDimTypes();
  }

  /* @see IFormatReader#getThumbSizeX() */
  public int getThumbSizeX() {
    return getReader().getThumbSizeX();
  }

  /* @see IFormatReader#getThumbSizeY() */
  public int getThumbSizeY() {
    return getReader().getThumbSizeY();
  }

  /* @see IFormatReader#isLittleEndian() */
  public boolean isLittleEndian() {
    return getReader().isLittleEndian();
  }

  /* @see IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    return getReader().getDimensionOrder();
  }

  /* @see IFormatReader#isOrderCertain() */
  public boolean isOrderCertain() {
    return getReader().isOrderCertain();
  }

  /* @see IFormatReader#isThumbnailSeries() */
  public boolean isThumbnailSeries() {
    return getReader().isThumbnailSeries();
  }

  /* @see IFormatReader#isInterleaved() */
  public boolean isInterleaved() {
    return getReader().isInterleaved();
  }

  /* @see IFormatReader#isInterleaved(int) */
  public boolean isInterleaved(int subC) {
    return getReader().isInterleaved(subC);
  }

  /* @see IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    return getReader().openBytes(no);
  }

  /* @see IFormatReader#openBytes(int, int, int, int, int) */
  public byte[] openBytes(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return getReader().openBytes(no, x, y, w, h);
  }

  /* @see IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    return getReader().openBytes(no, buf);
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return getReader().openBytes(no, buf, x, y, w, h);
  }

  /* @see IFormatReader#openPlane(int, int, int, int, int) */
  public Object openPlane(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return getReader().openPlane(no, x, y, w, h);
  }

  /* @see IFormatReader#openThumbBytes(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    return getReader().openThumbBytes(no);
  }

  /* @see IFormatReader#getSeriesCount() */
  public int getSeriesCount() {
    return getReader().getSeriesCount();
  }

  /* @see IFormatReader#setSeries(int) */
  public void setSeries(int no) {
    getReader().setSeries(no);
  }

  /* @see IFormatReader#getSeries() */
  public int getSeries() {
    return getReader().getSeries();
  }

  /* @see IFormatReader#getUsedFiles() */
  public String[] getUsedFiles() {
    return getReader().getUsedFiles();
  }

  /* @see IFormatReader#getUsedFiles(boolean) */
  public String[] getUsedFiles(boolean noPixels) {
    return getReader().getUsedFiles(noPixels);
  }

  /* @see IFormatReader#getSeriesUsedFiles() */
  public String[] getSeriesUsedFiles() {
    return getReader().getSeriesUsedFiles();
  }

  /* @see IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    return getReader().getSeriesUsedFiles(noPixels);
  }

  /* @see IFormatReader#getAdvancedUsedFiles(boolean) */
  public FileInfo[] getAdvancedUsedFiles(boolean noPixels) {
    return getReader().getAdvancedUsedFiles(noPixels);
  }

  /* @see IFormatReader#getAdvancedSeriesUsedFiles(boolean) */
  public FileInfo[] getAdvancedSeriesUsedFiles(boolean noPixels) {
    return getReader().getAdvancedSeriesUsedFiles(noPixels);
  }

  /* @see IFormatReader#getIndex(int, int, int) */
  public int getIndex(int z, int c, int t) {
    return getReader().getIndex(z, c, t);
  }

  /* @see IFormatReader#getZCTCoords(int) */
  public int[] getZCTCoords(int index) {
    return getReader().getZCTCoords(index);
  }

  /* @see IFormatReader#getMetadataValue(String) */
  public Object getMetadataValue(String field) {
    return getReader().getMetadataValue(field);
  }

  /* @see IFormatReader#getGlobalMetadata() */
  public Hashtable getGlobalMetadata() {
    return getReader().getGlobalMetadata();
  }

  /* @see IFormatReader#getSeriesMetadata() */
  public Hashtable getSeriesMetadata() {
    return getReader().getSeriesMetadata();
  }

  /** @deprecated */
  public Hashtable getMetadata() {
    return getReader().getMetadata();
  }

  /* @see IFormatReader#getCoreMetadata() */
  public CoreMetadata[] getCoreMetadata() {
    return getReader().getCoreMetadata();
  }

  /* @see IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    for (int i=0; i<readers.length; i++) readers[i].close(fileOnly);
    if (!fileOnly) currentId = null;
  }

  /* @see IFormatReader#setGroupFiles(boolean) */
  public void setGroupFiles(boolean group) {
    FormatTools.assertId(currentId, false, 2);
    for (int i=0; i<readers.length; i++) readers[i].setGroupFiles(group);
  }

  /* @see IFormatReader#isGroupFiles() */
  public boolean isGroupFiles() {
    return getReader().isGroupFiles();
  }

  /* @see IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return getReader(id).fileGroupOption(id);
  }

  /* @see IFormatReader#isMetadataComplete() */
  public boolean isMetadataComplete() {
    return getReader().isMetadataComplete();
  }

  /* @see IFormatReader#setNormalized(boolean) */
  public void setNormalized(boolean normalize) {
    FormatTools.assertId(currentId, false, 2);
    for (int i=0; i<readers.length; i++) readers[i].setNormalized(normalize);
  }

  /* @see IFormatReader#isNormalized() */
  public boolean isNormalized() {
    // NB: all readers should have the same normalization setting
    return readers[0].isNormalized();
  }

  /* @see IFormatReader#setMetadataCollected(boolean) */
  public void setMetadataCollected(boolean collect) {
    FormatTools.assertId(currentId, false, 2);
    for (int i=0; i<readers.length; i++) {
      readers[i].setMetadataCollected(collect);
    }
  }

  /* @see IFormatReader#isMetadataCollected() */
  public boolean isMetadataCollected() {
    return readers[0].isMetadataCollected();
  }

  /* @see IFormatReader#setOriginalMetadataPopulated(boolean) */
  public void setOriginalMetadataPopulated(boolean populate) {
    FormatTools.assertId(currentId, false, 1);
    for (int i=0; i<readers.length; i++) {
      readers[i].setOriginalMetadataPopulated(populate);
    }
  }

  /* @see IFormatReader#isOriginalMetadataPopulated() */
  public boolean isOriginalMetadataPopulated() {
    return readers[0].isOriginalMetadataPopulated();
  }

  /* @see IFormatReader#getCurrentFile() */
  public String getCurrentFile() {
    return currentId;
  }

  /* @see IFormatReader#setMetadataFiltered(boolean) */
  public void setMetadataFiltered(boolean filter) {
    FormatTools.assertId(currentId, false, 2);
    for (int i=0; i<readers.length; i++) readers[i].setMetadataFiltered(filter);
  }

  /* @see IFormatReader#isMetadataFiltered() */
  public boolean isMetadataFiltered() {
    // NB: all readers should have the same metadata filtering setting
    return readers[0].isMetadataFiltered();
  }

  /* @see IFormatReader#setMetadataStore(MetadataStore) */
  public void setMetadataStore(MetadataStore store) {
    FormatTools.assertId(currentId, false, 2);
    for (int i=0; i<readers.length; i++) readers[i].setMetadataStore(store);
  }

  /* @see IFormatReader#getMetadataStore() */
  public MetadataStore getMetadataStore() {
    return getReader().getMetadataStore();
  }

  /* @see IFormatReader#getMetadataStoreRoot() */
  public Object getMetadataStoreRoot() {
    return getReader().getMetadataStoreRoot();
  }

  /* @see IFormatReader#getUnderlyingReaders() */
  public IFormatReader[] getUnderlyingReaders() {
    return getReaders();
  }

  /* @see IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return getReader(id).isSingleFile(id);
  }

  /* @see IFormatReader#getPossibleDomains(String) */
  public String[] getPossibleDomains(String id)
    throws FormatException, IOException
  {
    return getReader(id).getPossibleDomains(id);
  }

  /* @see IFormatReader#getDomains() */
  public String[] getDomains() {
    return getReader().getDomains();
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#isThisType(String) */
  public boolean isThisType(String name) {
    // if necessary, open the file for further analysis
    // but check isThisType(name, false) first, for efficiency
    return isThisType(name, false) || isThisType(name, true);
  }

  /* @see IFormatHandler#getFormat() */
  public String getFormat() { return getReader().getFormat(); }

  /* @see IFormatHandler#getSuffixes() */
  public String[] getSuffixes() {
    if (suffixes == null) {
      HashSet suffixSet = new HashSet();
      for (int i=0; i<readers.length; i++) {
        String[] suf = readers[i].getSuffixes();
        for (int j=0; j<suf.length; j++) suffixSet.add(suf[j]);
      }
      suffixes = new String[suffixSet.size()];
      suffixSet.toArray(suffixes);
      Arrays.sort(suffixes);
    }
    return suffixes;
  }

  /* @see IFormatHandler#getNativeDataType() */
  public Class getNativeDataType() {
    return getReader().getNativeDataType();
  }

  /* @see IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    getReader(id).setId(id);
  }

  /* @see IFormatHandler#close() */
  public void close() throws IOException { close(false); }

  // -- StatusReporter API methods --

  /* @see IFormatHandler#addStatusListener(StatusListener) */
  public void addStatusListener(StatusListener l) {
    for (int i=0; i<readers.length; i++) readers[i].addStatusListener(l);
  }

  /* @see IFormatHandler#removeStatusListener(StatusListener) */
  public void removeStatusListener(StatusListener l) {
    for (int i=0; i<readers.length; i++) readers[i].removeStatusListener(l);
  }

  /* @see IFormatHandler#getStatusListeners() */
  public StatusListener[] getStatusListeners() {
    // NB: all readers should have the same status listeners
    return readers[0].getStatusListeners();
  }

}
