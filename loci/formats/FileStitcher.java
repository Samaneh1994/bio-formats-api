//
// FileStitcher.java
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
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.*;
import loci.formats.meta.MetadataStore;

/**
 * Logic to stitch together files with similar names.
 * Assumes that all files have the same characteristics (e.g., dimensions).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/FileStitcher.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/FileStitcher.java">SVN</a></dd></dl>
 */
public class FileStitcher implements IFormatReader {

  // -- Fields --

  /**
   * FormatReader to use as a template for constituent readers.
   *
   * The constituent readers must be dimension swappers so that the
   * file stitcher can reorganize the dimension order as needed based on
   * the result of the axis guessing algorithm.
   *
   * @see AxisGuesser#getAdjustedOrder()
   */
  private DimensionSwapper reader;

  /**
   * Whether string ids given should be treated
   * as file patterns rather than single file paths.
   */
  private boolean patternIds = false;

  /** Current file pattern string. */
  private String currentId;

  /** File pattern object used to build the list of files. */
  private FilePattern fp;

  /** Axis guesser object used to guess which dimensional axes are which. */
  private AxisGuesser[] ag;

  /** The matching files. */
  private String[][] files;

  /** Used files list. Only initialized in certain cases, upon request. */
  private String[] usedFiles;

  /** Reader used for each file. */
  private DimensionSwapper[][] readers;

  /** Blank buffered image, for use when image counts vary between files. */
  private BufferedImage[] blankImage;

  /** Blank image bytes, for use when image counts vary between files. */
  private byte[][] blankBytes;

  /** Blank buffered thumbnail, for use when image counts vary between files. */
  private BufferedImage[] blankThumb;

  /** Blank thumbnail bytes, for use when image counts vary between files. */
  private byte[][] blankThumbBytes;

  /** Number of images per file. */
  private int[] imagesPerFile;

  /** Dimensional axis lengths per file. */
  private int[] sizeZ, sizeC, sizeT;

  /** Component lengths for each axis type. */
  private int[][] lenZ, lenC, lenT;

  /** Core metadata. */
  private CoreMetadata core;

  /** Current series number. */
  private int series;

  private String[] seriesBlocks;
  private Vector fileVector;
  private Vector seriesNames;
  private boolean seriesInFile;

  private boolean noStitch;

  private MetadataStore store;
  private String[] originalOrder;

  // -- Constructors --

  /** Constructs a FileStitcher around a new image reader. */
  public FileStitcher() { this(new ImageReader()); }

  /**
   * Constructs a FileStitcher around a new image reader.
   * @param patternIds Whether string ids given should be treated as file
   *    patterns rather than single file paths.
   */
  public FileStitcher(boolean patternIds) {
    this(new ImageReader(), patternIds);
  }

  /**
   * Constructs a FileStitcher with the given reader.
   * @param r The reader to use for reading stitched files.
   */
  public FileStitcher(IFormatReader r) { this(r, false); }

  /**
   * Constructs a FileStitcher with the given reader.
   * @param r The reader to use for reading stitched files.
   * @param patternIds Whether string ids given should be treated as file
   *   patterns rather than single file paths.
   */
  public FileStitcher(IFormatReader r, boolean patternIds) {
    if (r instanceof DimensionSwapper) reader = (DimensionSwapper) r;
    else reader = new DimensionSwapper(r);
    this.patternIds = patternIds;
  }

  // -- FileStitcher API methods --

  /** Gets the wrapped reader prototype. */
  public IFormatReader getReader() { return reader; }

  /**
   * Gets the axis type for each dimensional block.
   * @return An array containing values from the enumeration:
   *   <ul>
   *     <li>AxisGuesser.Z_AXIS: focal planes</li>
   *     <li>AxisGuesser.T_AXIS: time points</li>
   *     <li>AxisGuesser.C_AXIS: channels</li>
   *     <li>AxisGuesser.S_AXIS: series</li>
   *   </ul>
   */
  public int[] getAxisTypes() {
    FormatTools.assertId(currentId, true, 2);
    return ag[getSeries()].getAxisTypes();
  }

  /**
   * Sets the axis type for each dimensional block.
   * @param axes An array containing values from the enumeration:
   *   <ul>
   *     <li>AxisGuesser.Z_AXIS: focal planes</li>
   *     <li>AxisGuesser.T_AXIS: time points</li>
   *     <li>AxisGuesser.C_AXIS: channels</li>
   *     <li>AxisGuesser.S_AXIS: series</li>
   *   </ul>
   */
  public void setAxisTypes(int[] axes) throws FormatException {
    FormatTools.assertId(currentId, true, 2);
    ag[getSeries()].setAxisTypes(axes);
    computeAxisLengths();
  }

  /** Gets the file pattern object used to build the list of files. */
  public FilePattern getFilePattern() {
    FormatTools.assertId(currentId, true, 2);
    return fp;
  }

  /**
   * Gets the axis guesser object used to guess
   * which dimensional axes are which.
   */
  public AxisGuesser getAxisGuesser() {
    FormatTools.assertId(currentId, true, 2);
    return ag[getSeries()];
  }

  /**
   * Finds the file pattern for the given ID, based on the state of the file
   * stitcher. Takes both ID map entries and the patternIds flag into account.
   */
  public FilePattern findPattern(String id) {
    FormatTools.assertId(currentId, true, 2);
    if (!patternIds) {
      // find the containing pattern
      Hashtable map = Location.getIdMap();
      String pattern = null;
      if (map.containsKey(id)) {
        // search ID map for pattern, rather than files on disk
        String[] idList = new String[map.size()];
        Enumeration en = map.keys();
        for (int i=0; i<idList.length; i++) {
          idList[i] = (String) en.nextElement();
        }
        pattern = FilePattern.findPattern(id, null, idList);
      }
      else {
        // id is an unmapped file path; look to similar files on disk

        pattern = FilePattern.findPattern(new Location(id));
      }
      if (pattern != null) id = pattern;
    }
    return new FilePattern(id);
  }

  // -- IFormatReader API methods --

  /* @see IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    return reader.isThisType(name, open);
  }

  /* @see IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    return reader.isThisType(block);
  }

  /* @see IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    return reader.isThisType(stream);
  }

  /* @see IFormatReader#getImageCount() */
  public int getImageCount() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getImageCount() : core.imageCount[getSeries()];
  }

  /* @see IFormatReader#isRGB() */
  public boolean isRGB() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isRGB() : core.rgb[getSeries()];
  }

  /* @see IFormatReader#getSizeX() */
  public int getSizeX() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSizeX() : core.sizeX[getSeries()];
  }

  /* @see IFormatReader#getSizeY() */
  public int getSizeY() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSizeY() : core.sizeY[getSeries()];
  }

  /* @see IFormatReader#getSizeZ() */
  public int getSizeZ() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSizeZ() : core.sizeZ[getSeries()];
  }

  /* @see IFormatReader#getSizeC() */
  public int getSizeC() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSizeC() : core.sizeC[getSeries()];
  }

  /* @see IFormatReader#getSizeT() */
  public int getSizeT() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSizeT() : core.sizeT[getSeries()];
  }

  /* @see IFormatReader#getPixelType() */
  public int getPixelType() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getPixelType() : core.pixelType[getSeries()];
  }

  /* @see IFormatReader#getEffectiveSizeC() */
  public int getEffectiveSizeC() {
    FormatTools.assertId(currentId, true, 2);
    return getImageCount() / (getSizeZ() * getSizeT());
  }

  /* @see IFormatReader#getRGBChannelCount() */
  public int getRGBChannelCount() {
    FormatTools.assertId(currentId, true, 2);
    return getSizeC() / getEffectiveSizeC();
  }

  /* @see IFormatReader#isIndexed() */
  public boolean isIndexed() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isIndexed() : core.indexed[getSeries()];
  }

  /* @see IFormatReader#isFalseColor() */
  public boolean isFalseColor() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isFalseColor() : core.falseColor[getSeries()];
  }

  /* @see IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.get8BitLookupTable() :
      readers[seriesInFile ? 0 : getSeries()][0].get8BitLookupTable();
  }

  /* @see IFormatReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.get16BitLookupTable() :
      readers[seriesInFile ? 0 : getSeries()][0].get16BitLookupTable();
  }

  /* @see IFormatReader#getChannelDimLengths() */
  public int[] getChannelDimLengths() {
    FormatTools.assertId(currentId, true, 1);
    return noStitch ? reader.getChannelDimLengths() :
      core.cLengths[getSeries()];
  }

  /* @see IFormatReader#getChannelDimTypes() */
  public String[] getChannelDimTypes() {
    FormatTools.assertId(currentId, true, 1);
    return noStitch ? reader.getChannelDimTypes() : core.cTypes[getSeries()];
  }

  /* @see IFormatReader#getThumbSizeX() */
  public int getThumbSizeX() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getThumbSizeX() :
      readers[seriesInFile ? 0 : getSeries()][0].getThumbSizeX();
  }

  /* @see IFormatReader#getThumbSizeY() */
  public int getThumbSizeY() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getThumbSizeY() :
      readers[seriesInFile ? 0 : getSeries()][0].getThumbSizeY();
  }

  /* @see IFormatReader#isLittleEndian() */
  public boolean isLittleEndian() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isLittleEndian() :
      readers[seriesInFile ? 0 : getSeries()][0].isLittleEndian();
  }

  /* @see IFormatReader#getDimensionOrder() */
  public String getDimensionOrder() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getDimensionOrder() :
      core.currentOrder[getSeries()];
  }

  /* @see IFormatReader#isOrderCertain() */
  public boolean isOrderCertain() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isOrderCertain() : core.orderCertain[getSeries()];
  }

  /* @see IFormatReader#isInterleaved() */
  public boolean isInterleaved() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isInterleaved() :
      readers[seriesInFile ? 0 : getSeries()][0].isInterleaved();
  }

  /* @see IFormatReader#isInterleaved(int) */
  public boolean isInterleaved(int subC) {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.isInterleaved(subC) :
      readers[seriesInFile ? 0 : getSeries()][0].isInterleaved(subC);
  }

  /* @see IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    return openImage(no, 0, 0, getSizeX(), getSizeY());
  }

  /* @see IFormatReader#openImage(int, int, int, int, int) */
  public BufferedImage openImage(int no, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 2);
    if (noStitch) return reader.openImage(no, x, y, w, h);
    int[] q = computeIndices(no);
    int sno = seriesInFile ? 0 : getSeries();
    int fno = q[0], ino = q[1];
    if (seriesInFile) readers[sno][fno].setSeries(getSeries());
    if (ino < readers[sno][fno].getImageCount()) {
      return readers[sno][fno].openImage(ino, x, y, w, h);
    }

    sno = getSeries();

    // return a blank image to cover for the fact that
    // this file does not contain enough image planes
    if (blankImage[sno] == null) {
      blankImage[sno] = ImageTools.blankImage(w, h, sizeC[sno], getPixelType());
    }

    return blankImage[sno];
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
    FormatTools.assertId(currentId, true, 2);
    if (noStitch) return reader.openBytes(no, x, y, w, h);
    int[] q = computeIndices(no);
    int sno = seriesInFile ? 0 : getSeries();
    int fno = q[0], ino = q[1];
    if (seriesInFile) readers[sno][fno].setSeries(getSeries());
    if (ino < readers[sno][fno].getImageCount()) {
      return readers[sno][fno].openBytes(ino, x, y, w, h);
    }

    sno = getSeries();

    // return a blank image to cover for the fact that
    // this file does not contain enough image planes
    if (blankBytes[sno] == null) {
      int bytes = FormatTools.getBytesPerPixel(getPixelType());
      blankBytes[sno] = new byte[w * h * bytes * getRGBChannelCount()];
    }
    return blankBytes[sno];
  }

  /* @see IFormatReader#openBytes(int, byte[], int, int, int, int) */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 2);
    if (noStitch) return reader.openBytes(no, buf, x, y, w, h);
    int[] q = computeIndices(no);
    int sno = seriesInFile ? 0 : getSeries();
    int fno = q[0], ino = q[1];
    if (seriesInFile) readers[sno][fno].setSeries(getSeries());
    if (ino < readers[sno][fno].getImageCount()) {
      return readers[sno][fno].openBytes(ino, buf, x, y, w, h);
    }

    // return a blank image to cover for the fact that
    // this file does not contain enough image planes
    Arrays.fill(buf, (byte) 0);
    return buf;
  }

  /* @see IFormatReader#openThumbImage(int) */
  public BufferedImage openThumbImage(int no)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 2);
    if (noStitch) return reader.openThumbImage(no);
    int[] q = computeIndices(no);
    int sno = seriesInFile ? 0 : getSeries();
    int fno = q[0], ino = q[1];
    if (seriesInFile) readers[sno][fno].setSeries(getSeries());
    if (ino < readers[sno][fno].getImageCount()) {
      return readers[sno][fno].openThumbImage(ino);
    }

    sno = getSeries();

    // return a blank image to cover for the fact that
    // this file does not contain enough image planes
    if (blankThumb[sno] == null) {
      blankThumb[sno] = ImageTools.blankImage(getThumbSizeX(),
        getThumbSizeY(), sizeC[sno], getPixelType());
    }
    return blankThumb[sno];
  }

  /* @see IFormatReader#openThumbBytes(int) */
  public byte[] openThumbBytes(int no) throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 2);
    if (noStitch) return reader.openThumbBytes(no);
    int[] q = computeIndices(no);
    int sno = seriesInFile ? 0 : getSeries();
    int fno = q[0], ino = q[1];
    if (seriesInFile) readers[sno][fno].setSeries(getSeries());
    if (ino < readers[sno][fno].getImageCount()) {
      return readers[sno][fno].openThumbBytes(ino);
    }

    sno = getSeries();

    // return a blank image to cover for the fact that
    // this file does not contain enough image planes
    if (blankThumbBytes[sno] == null) {
      int bytes = FormatTools.getBytesPerPixel(getPixelType());
      blankThumbBytes[sno] = new byte[getThumbSizeX() * getThumbSizeY() *
        bytes * getRGBChannelCount()];
    }
    return blankThumbBytes[sno];
  }

  /* @see IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    if (readers == null) reader.close(fileOnly);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].close(fileOnly);
        }
      }
    }
    if (!fileOnly) {
      noStitch = false;
      readers = null;
      blankImage = null;
      blankBytes = null;
      currentId = null;
      fp = null;
      ag = null;
      files = null;
      usedFiles = null;
      blankThumb = null;
      blankThumbBytes = null;
      imagesPerFile = null;
      sizeZ = sizeC = sizeT = null;
      lenZ = lenC = lenT = null;
      core = null;
      series = 0;
      seriesBlocks = null;
      fileVector = seriesNames = null;
      seriesInFile = false;
      store = null;
    }
  }

  /* @see IFormatReader#getSeriesCount() */
  public int getSeriesCount() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getSeriesCount() : core.sizeX.length;
  }

  /* @see IFormatReader#setSeries(int) */
  public void setSeries(int no) {
    FormatTools.assertId(currentId, true, 2);
    int n = reader.getSeriesCount();
    if (n > 1) reader.setSeries(no);
    else series = no;
  }

  /* @see IFormatReader#getSeries() */
  public int getSeries() {
    FormatTools.assertId(currentId, true, 2);
    return seriesInFile || noStitch ? reader.getSeries() : series;
  }

  /* @see IFormatReader#setGroupFiles(boolean) */
  public void setGroupFiles(boolean group) {
    reader.setGroupFiles(group);
    for (int i=0; i<readers.length; i++) {
      for (int j=0; j<readers[i].length; j++) {
        readers[i][j].setGroupFiles(group);
      }
    }
  }

  /* @see IFormatReader#isGroupFiles() */
  public boolean isGroupFiles() {
    return reader.isGroupFiles();
  }

  /* @see IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return reader.fileGroupOption(id);
  }

  /* @see IFormatReader#isMetadataComplete() */
  public boolean isMetadataComplete() {
    return reader.isMetadataComplete();
  }

  /* @see IFormatReader#setNormalized(boolean) */
  public void setNormalized(boolean normalize) {
    FormatTools.assertId(currentId, false, 2);
    if (readers == null) reader.setNormalized(normalize);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].setNormalized(normalize);
        }
      }
    }
  }

  /* @see IFormatReader#isNormalized() */
  public boolean isNormalized() { return reader.isNormalized(); }

  /* @see IFormatReader#setMetadataCollected(boolean) */
  public void setMetadataCollected(boolean collect) {
    FormatTools.assertId(currentId, false, 2);
    if (readers == null) reader.setMetadataCollected(collect);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].setMetadataCollected(collect);
        }
      }
    }
  }

  /* @see IFormatReader#isMetadataCollected() */
  public boolean isMetadataCollected() {
    return reader.isMetadataCollected();
  }

  /* @see IFormatReader#setOriginalMetadataPopulated(boolean) */
  public void setOriginalMetadataPopulated(boolean populate) {
    FormatTools.assertId(currentId, false, 1);
    if (readers == null) reader.setOriginalMetadataPopulated(populate);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].setOriginalMetadataPopulated(populate);
        }
      }
    }
  }

  /* @see IFormatReader#isOriginalMetadataPopulated() */
  public boolean isOriginalMetadataPopulated() {
    return reader.isOriginalMetadataPopulated();
  }

  /* @see IFormatReader#getUsedFiles() */
  public String[] getUsedFiles() {
    FormatTools.assertId(currentId, true, 2);

    if (noStitch) return reader.getUsedFiles();

    // returning the files list directly here is fast, since we do not
    // have to call initFile on each constituent file; but we can only do so
    // when each constituent file does not itself have multiple used files

    if (reader.getUsedFiles().length > 1) {
      // each constituent file has multiple used files; we must build the list
      // this could happen with, e.g., a stitched collection of ICS/IDS pairs
      // we have no datasets structured this way, so this logic is untested
      if (usedFiles == null) {
        String[][][] used = new String[files.length][][];
        int total = 0;
        for (int i=0; i<files.length; i++) {
          used[i] = new String[files[i].length][];
          for (int j=0; j<files[i].length; j++) {
            try {
              initReader(i, j);
            }
            catch (FormatException exc) {
              LogTools.trace(exc);
              return null;
            }
            catch (IOException exc) {
              LogTools.trace(exc);
             return null;
            }
            used[i][j] = readers[i][j].getUsedFiles();
            total += used[i][j].length;
          }
        }
        usedFiles = new String[total];
        for (int i=0, off=0; i<used.length; i++) {
          for (int j=0; j<used[i].length; j++) {
            System.arraycopy(used[i][j], 0, usedFiles, off, used[i][j].length);
            off += used[i][j].length;
          }
        }
      }
      return usedFiles;
    }
    // assume every constituent file has no other used files
    // this logic could fail if the first constituent has no extra used files,
    // but later constituents do; in practice, this scenario seems unlikely
    Vector v = new Vector();
    for (int i=0; i<files.length; i++) {
      for (int j=0; j<files[i].length; j++) {
        v.add(files[i][j]);
      }
    }
    return (String[]) v.toArray(new String[0]);
  }

  /* @see IFormatReader#getCurrentFile() */
  public String getCurrentFile() { return currentId; }

  /* @see IFormatReader#getIndex(int, int, int) */
  public int getIndex(int z, int c, int t) {
    return FormatTools.getIndex(this, z, c, t);
  }

  /* @see IFormatReader#getZCTCoords(int) */
  public int[] getZCTCoords(int index) {
    return FormatTools.getZCTCoords(this, index);
  }

  /* @see IFormatReader#getMetadataValue(String) */
  public Object getMetadataValue(String field) {
    FormatTools.assertId(currentId, true, 2);
    return reader.getMetadataValue(field);
  }

  /* @see IFormatReader#getMetadata() */
  public Hashtable getMetadata() {
    FormatTools.assertId(currentId, true, 2);
    return reader.getMetadata();
  }

  /* @see IFormatReader#getCoreMetadata() */
  public CoreMetadata getCoreMetadata() {
    FormatTools.assertId(currentId, true, 2);
    return noStitch ? reader.getCoreMetadata() : core;
  }

  /* @see IFormatReader#setMetadataFiltered(boolean) */
  public void setMetadataFiltered(boolean filter) {
    FormatTools.assertId(currentId, false, 2);
    reader.setMetadataFiltered(filter);
  }

  /* @see IFormatReader#isMetadataFiltered() */
  public boolean isMetadataFiltered() {
    return reader.isMetadataFiltered();
  }

  /* @see IFormatReader#setMetadataStore(MetadataStore) */
  public void setMetadataStore(MetadataStore store) {
    FormatTools.assertId(currentId, false, 2);
    reader.setMetadataStore(store);
  }

  /* @see IFormatReader#getMetadataStore() */
  public MetadataStore getMetadataStore() {
    FormatTools.assertId(currentId, true, 2);
    return store;
  }

  /* @see IFormatReader#getMetadataStoreRoot() */
  public Object getMetadataStoreRoot() {
    FormatTools.assertId(currentId, true, 2);
    return store.getRoot();
  }

  /* @see IFormatReader#getUnderlyingReaders() */
  public IFormatReader[] getUnderlyingReaders() {
    Vector v = new Vector();
    for (int i=0; i<readers.length; i++) {
      for (int j=0; j<readers[i].length; j++) {
        v.add(readers[i][j]);
      }
    }
    return (IFormatReader[]) v.toArray(new IFormatReader[0]);
  }

  // -- IFormatHandler API methods --

  /* @see IFormatHandler#isThisType(String) */
  public boolean isThisType(String name) {
    return reader.isThisType(name);
  }

  /* @see IFormatHandler#getFormat() */
  public String getFormat() {
    FormatTools.assertId(currentId, true, 2);
    return reader.getFormat();
  }

  /* @see IFormatHandler#getSuffixes() */
  public String[] getSuffixes() {
    return reader.getSuffixes();
  }

  /* @see IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
  }

  /* @see IFormatHandler#close() */
  public void close() throws IOException { close(false); }

  // -- StatusReporter API methods --

  /* @see IFormatHandler#addStatusListener(StatusListener) */
  public void addStatusListener(StatusListener l) {
    if (readers == null) reader.addStatusListener(l);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].addStatusListener(l);
        }
      }
    }
  }

  /* @see IFormatHandler#removeStatusListener(StatusListener) */
  public void removeStatusListener(StatusListener l) {
    if (readers == null) reader.removeStatusListener(l);
    else {
      for (int i=0; i<readers.length; i++) {
        for (int j=0; j<readers[i].length; j++) {
          readers[i][j].removeStatusListener(l);
        }
      }
    }
  }

  /* @see IFormatHandler#getStatusListeners() */
  public StatusListener[] getStatusListeners() {
    return reader.getStatusListeners();
  }

  // -- Internal FormatReader API methods --

  /** Initializes the given file or file pattern. */
  protected void initFile(String id) throws FormatException, IOException {
    if (FormatHandler.debug) {
      LogTools.println("calling FileStitcher.initFile(" + id + ")");
    }

    close();
    currentId = id;

    fp = findPattern(currentId);

    reader.setId(fp.getFiles()[0]);
    if (reader.fileGroupOption(fp.getFiles()[0]) == FormatTools.MUST_GROUP) {
      // reader subclass is handling file grouping
      noStitch = true;
      return;
    }

    AxisGuesser guesser = new AxisGuesser(fp, reader.getDimensionOrder(),
      reader.getSizeZ(), reader.getSizeT(), reader.getEffectiveSizeC(),
      reader.isOrderCertain());

    // use the dimension order recommended by the axis guesser
    reader.swapDimensions(guesser.getAdjustedOrder());

    // if this is a multi-series dataset, we need some special logic
    int seriesCount = reader.getSeriesCount();
    seriesInFile = true;
    if (guesser.getAxisCountS() > 0) {
      int[] axes = guesser.getAxisTypes();

      seriesInFile = false;

      String[] blockPrefixes = fp.getPrefixes();
      Vector sBlock = new Vector();

      for (int i=0; i<axes.length; i++) {
        if (axes[i] == AxisGuesser.S_AXIS) sBlock.add(blockPrefixes[i]);
      }

      seriesBlocks = (String[]) sBlock.toArray(new String[0]);
      fileVector = new Vector();
      seriesNames = new Vector();

      String file = fp.getFiles()[0];
      Location dir = new Location(file).getAbsoluteFile().getParentFile();
      String dpath = dir.getAbsolutePath();
      String[] fs = dir.list();

      String ext = "";
      if (file.indexOf(".") != -1) {
        ext = file.substring(file.lastIndexOf(".") + 1);
      }

      Vector tmpFiles = new Vector();
      for (int i=0; i<fs.length; i++) {
        if (fs[i].endsWith(ext)) tmpFiles.add(fs[i]);
      }

      setFiles((String[]) tmpFiles.toArray(new String[0]), seriesBlocks[0],
        fp.getFirst()[0], fp.getLast()[0], fp.getStep()[0], dpath, 0);

      seriesCount = fileVector.size();
      files = new String[seriesCount][];

      for (int i=0; i<seriesCount; i++) {
        files[i] = (String[]) fileVector.get(i);
      }
    }

    // verify that file pattern is valid and matches existing files
    String msg = " Please rename your files or disable file stitching.";
    if (!fp.isValid()) {
      throw new FormatException("Invalid " +
        (patternIds ? "file pattern" : "filename") +
        " (" + currentId + "): " + fp.getErrorMessage() + msg);
    }
    if (files == null) {
      files = new String[1][];
      files[0] = fp.getFiles();
    }

    if (files == null) {
      throw new FormatException("No files matching pattern (" +
        fp.getPattern() + "). " + msg);
    }
    for (int i=0; i<files.length; i++) {
      for (int j=0; j<files[i].length; j++) {
        if (!new Location(files[i][j]).exists()) {
          throw new FormatException("File #" + i +
            " (" + files[i][j] + ") does not exist.");
        }
      }
    }

    // determine reader type for these files; assume all are the same type
    Vector classes = new Vector();
    IFormatReader r = reader;
    while (r instanceof ReaderWrapper) {
      classes.add(r.getClass());
      r = ((ReaderWrapper) r).getReader();
    }
    if (r instanceof ImageReader) r = ((ImageReader) r).getReader(files[0][0]);
    classes.add(r.getClass());

    // construct list of readers for all files
    readers = new DimensionSwapper[files.length][];
    for (int i=0; i<readers.length; i++) {
      readers[i] = new DimensionSwapper[files[i].length];
    }

    for (int i=0; i<readers.length; i++) {
      for (int j=0; j<readers[i].length; j++) {
        if (i == 0 && j == 0) {
          readers[i][j] = reader;
          continue;
        }
        // use crazy reflection to instantiate a reader of the proper type
        try {
          r = null;
          for (int k=classes.size()-1; k>=0; k--) {
            Class c = (Class) classes.elementAt(k);
            if (r == null) r = (IFormatReader) c.newInstance();
            else {
              r = (IFormatReader) c.getConstructor(new Class[]
                {IFormatReader.class}).newInstance(new Object[] {r});
            }
          }
          readers[i][j] = (DimensionSwapper) r;
        }
        catch (InstantiationException exc) { LogTools.trace(exc); }
        catch (IllegalAccessException exc) { LogTools.trace(exc); }
        catch (NoSuchMethodException exc) { LogTools.trace(exc); }
        catch (InvocationTargetException exc) { LogTools.trace(exc); }
      }
    }

    // sync reader configurations with original reader
    boolean normalized = reader.isNormalized();
    boolean metadataFiltered = reader.isMetadataFiltered();
    boolean metadataCollected = reader.isMetadataCollected();
    StatusListener[] statusListeners = reader.getStatusListeners();
    for (int i=0; i<readers.length; i++) {
      for (int j=0; j<readers[i].length; j++) {
        if (i == 0 && j == 0) continue;
        readers[i][j].setNormalized(normalized);
        readers[i][j].setMetadataFiltered(metadataFiltered);
        readers[i][j].setMetadataCollected(metadataCollected);
        for (int k=0; k<statusListeners.length; k++) {
          readers[i][j].addStatusListener(statusListeners[k]);
        }
      }
    }

    ag = new AxisGuesser[seriesCount];
    blankImage = new BufferedImage[seriesCount];
    blankBytes = new byte[seriesCount][];
    blankThumb = new BufferedImage[seriesCount];
    blankThumbBytes = new byte[seriesCount][];
    imagesPerFile = new int[seriesCount];
    sizeZ = new int[seriesCount];
    sizeC = new int[seriesCount];
    sizeT = new int[seriesCount];
    boolean[] certain = new boolean[seriesCount];
    lenZ = new int[seriesCount][];
    lenC = new int[seriesCount][];
    lenT = new int[seriesCount][];

    // analyze first file; assume each file has the same parameters
    core = new CoreMetadata(seriesCount);
    int oldSeries = reader.getSeries();
    IFormatReader rr = reader;
    for (int i=0; i<seriesCount; i++) {
      if (seriesInFile) rr.setSeries(i);
      else {
        initReader(i, 0);
        rr = readers[i][0];
      }

      core.sizeX[i] = rr.getSizeX();
      core.sizeY[i] = rr.getSizeY();
      // NB: core.sizeZ populated in computeAxisLengths below
      // NB: core.sizeC populated in computeAxisLengths below
      // NB: core.sizeT populated in computeAxisLengths below
      core.pixelType[i] = rr.getPixelType();
      imagesPerFile[i] = rr.getImageCount();
      core.imageCount[i] =
        imagesPerFile[i] * files[seriesInFile ? 0 : i].length;
      core.thumbSizeX[i] = rr.getThumbSizeX();
      core.thumbSizeY[i] = rr.getThumbSizeY();
      // NB: core.cLengths[i] populated in computeAxisLengths below
      // NB: core.cTypes[i] populated in computeAxisLengths below
      core.currentOrder[i] = rr.getDimensionOrder();
      // NB: core.orderCertain[i] populated below
      core.rgb[i] = rr.isRGB();
      core.littleEndian[i] = rr.isLittleEndian();
      core.interleaved[i] = rr.isInterleaved();
      core.seriesMetadata[i] = rr.getMetadata();
      sizeZ[i] = rr.getSizeZ();
      sizeC[i] = rr.getSizeC();
      sizeT[i] = rr.getSizeT();
      certain[i] = rr.isOrderCertain();
    }
    reader.setSeries(oldSeries);

    // guess at dimensions corresponding to file numbering
    for (int i=0; i<seriesCount; i++) {
      ag[i] = new AxisGuesser(fp, core.currentOrder[i],
        sizeZ[i], sizeT[i], sizeC[i], certain[i]);
    }

    // order may need to be adjusted
    for (int i=0; i<seriesCount; i++) {
      setSeries(i);
      core.currentOrder[i] = ag[i].getAdjustedOrder();
      core.orderCertain[i] = ag[i].isCertain();
      computeAxisLengths();
    }
    setSeries(oldSeries);
    originalOrder = new String[seriesCount];
    System.arraycopy(core.currentOrder, 0, originalOrder, 0, seriesCount);
  }

  // -- Helper methods --

  /** Computes axis length arrays, and total axis lengths. */
  protected void computeAxisLengths() throws FormatException {
    int sno = seriesInFile ? 0 : getSeries();

    FilePattern p = new FilePattern(FilePattern.findPattern(files[sno][0],
      new Location(files[sno][0]).getAbsoluteFile().getParentFile().getPath(),
      files[sno]));

    int[] count = p.getCount();

    try {
      initReader(sno, 0);
    }
    catch (IOException e) {
      throw new FormatException(e);
    }

    ag[getSeries()] = new AxisGuesser(p, readers[sno][0].getDimensionOrder(),
      readers[sno][0].getSizeZ(), readers[sno][0].getSizeT(),
      readers[sno][0].getSizeC(), readers[sno][0].isOrderCertain());
    sno = getSeries();
    int[] axes = ag[sno].getAxisTypes();
    int numZ = ag[sno].getAxisCountZ();
    int numC = ag[sno].getAxisCountC();
    int numT = ag[sno].getAxisCountT();

    core.sizeZ[sno] = sizeZ[sno];
    core.sizeC[sno] = sizeC[sno];
    core.sizeT[sno] = sizeT[sno];
    lenZ[sno] = new int[numZ + 1];
    lenC[sno] = new int[numC + 1];
    lenT[sno] = new int[numT + 1];
    lenZ[sno][0] = sizeZ[sno];
    lenC[sno][0] = sizeC[sno];
    lenT[sno][0] = sizeT[sno];

    for (int i=0, z=1, c=1, t=1; i<count.length; i++) {
      switch (axes[i]) {
        case AxisGuesser.Z_AXIS:
          core.sizeZ[sno] *= count[i];
          lenZ[sno][z++] = count[i];
          break;
        case AxisGuesser.C_AXIS:
          core.sizeC[sno] *= count[i];
          lenC[sno][c++] = count[i];
          break;
        case AxisGuesser.T_AXIS:
          core.sizeT[sno] *= count[i];
          lenT[sno][t++] = count[i];
          break;
        case AxisGuesser.S_AXIS:
          break;
        default:
          throw new FormatException("Unknown axis type for axis #" +
            i + ": " + axes[i]);
      }
    }

    int[] cLengths = reader.getChannelDimLengths();
    String[] cTypes = reader.getChannelDimTypes();
    int cCount = 0;
    for (int i=0; i<cLengths.length; i++) {
      if (cLengths[i] > 1) cCount++;
    }
    for (int i=1; i<lenC[sno].length; i++) {
      if (lenC[sno][i] > 1) cCount++;
    }
    if (cCount == 0) {
      core.cLengths[sno] = new int[] {1};
      core.cTypes[sno] = new String[] {FormatTools.CHANNEL};
    }
    else {
      core.cLengths[sno] = new int[cCount];
      core.cTypes[sno] = new String[cCount];
    }
    int c = 0;
    for (int i=0; i<cLengths.length; i++) {
      if (cLengths[i] == 1) continue;
      core.cLengths[sno][c] = cLengths[i];
      core.cTypes[sno][c] = cTypes[i];
      c++;
    }
    for (int i=1; i<lenC[sno].length; i++) {
      if (lenC[sno][i] == 1) continue;
      core.cLengths[sno][c] = lenC[sno][i];
      core.cTypes[sno][c] = FormatTools.CHANNEL;
    }

    // populate metadata store
    store = reader.getMetadataStore();
    for (int i=0; i<core.sizeX.length; i++) {
      if (seriesNames != null) {
        store.setImageName((String) seriesNames.get(i), i);
      }
    }
    MetadataTools.populatePixels(store, this);
  }

  /**
   * Gets the file index, and image index into that file,
   * corresponding to the given global image index.
   *
   * @return An array of size 2, dimensioned {file index, image index}.
   */
  protected int[] computeIndices(int no) throws FormatException, IOException {
    int sno = getSeries();

    int[] axes = ag[sno].getAxisTypes();
    int[] count = fp.getCount();

    // get Z, C and T positions
    int[] zct = getZCTCoords(no);
    int[] posZ = FormatTools.rasterToPosition(lenZ[sno], zct[0]);
    int[] posC = FormatTools.rasterToPosition(lenC[sno], zct[1]);
    int[] posT = FormatTools.rasterToPosition(lenT[sno], zct[2]);

    int[] tmpZ = new int[posZ.length];
    System.arraycopy(posZ, 0, tmpZ, 0, tmpZ.length);
    int[] tmpC = new int[posC.length];
    System.arraycopy(posC, 0, tmpC, 0, tmpC.length);
    int[] tmpT = new int[posT.length];
    System.arraycopy(posT, 0, tmpT, 0, tmpT.length);

    for (int i=0; i<3; i++) {
      char originalAxis = originalOrder[sno].charAt(i + 2);
      char newAxis = getDimensionOrder().charAt(i + 2);

      if (newAxis != originalAxis) {
        int src = -1;
        if (originalAxis == 'Z') src = tmpZ[tmpZ.length - 1];
        else if (originalAxis == 'C') src = tmpC[tmpC.length - 1];
        else if (originalAxis == 'T') src = tmpT[tmpT.length - 1];

        if (newAxis == 'Z') posZ[posZ.length - 1] = src;
        else if (newAxis == 'C') posC[posC.length - 1] = src;
        else if (newAxis == 'T') posT[posT.length - 1] = src;
      }
    }

    // convert Z, C and T position lists into file index and image index
    int[] pos = new int[axes.length];
    int z = 1, c = 1, t = 1;
    for (int i=0; i<axes.length; i++) {
      if (axes[i] == AxisGuesser.Z_AXIS) pos[i] = posZ[z++];
      else if (axes[i] == AxisGuesser.C_AXIS) pos[i] = posC[c++];
      else if (axes[i] == AxisGuesser.T_AXIS) pos[i] = posT[t++];
      else {
        throw new FormatException("Unknown axis type for axis #" +
          i + ": " + axes[i]);
      }
    }

    int fno = FormatTools.positionToRaster(count, pos);

    if (seriesInFile) sno = 0;

    // configure the reader, in case we haven't done this one yet
    initReader(sno, fno);

    int ino;
    if (posZ[0] < readers[sno][fno].getSizeZ() &&
      posC[0] < readers[sno][fno].getSizeC() &&
      posT[0] < readers[sno][fno].getSizeT())
    {
      if (readers[sno][fno].isRGB() &&
        (posC[0] * readers[sno][fno].getRGBChannelCount() >= lenC[sno][0]))
      {
        posC[0] /= lenC[sno][0];
      }
      ino = FormatTools.getIndex(readers[sno][fno], posZ[0], posC[0], posT[0]);
    }
    else ino = Integer.MAX_VALUE; // coordinates out of range

    return new int[] {fno, ino};
  }

  /**
   * Gets a list of readers to include in relation to the given C position.
   * @return Array with indices corresponding to the list of readers, and
   *   values indicating the internal channel index to use for that reader.
   */
  protected int[] getIncludeList(int theC) throws FormatException, IOException {
    int[] include = new int[readers.length];
    Arrays.fill(include, -1);
    for (int t=0; t<sizeT[getSeries()]; t++) {
      for (int z=0; z<sizeZ[getSeries()]; z++) {
        int no = getIndex(z, theC, t);
        int[] q = computeIndices(no);
        int fno = q[0], ino = q[1];
        include[fno] = ino;
      }
    }
    return include;
  }

  protected void initReader(int sno, int fno)
    throws FormatException, IOException
  {
    readers[sno][fno].setId(files[sno][fno]);
    readers[sno][fno].setSeries(seriesInFile ? getSeries() : 0);
    readers[sno][fno].swapDimensions(reader.getDimensionOrder());
    if (getSizeC() > 0) MetadataTools.populatePixels(store, this);
  }

  private FilePattern getPattern(String[] f, String dir, String block) {
    Vector v = new Vector();
    for (int i=0; i<f.length; i++) {
      if (f[i].indexOf(File.separator) != -1) {
        f[i] = f[i].substring(f[i].lastIndexOf(File.separator) + 1);
      }
      if (dir.endsWith(File.separator)) f[i] = dir + f[i];
      else f[i] = dir + File.separator + f[i];
      if (f[i].indexOf(block) != -1 && new Location(f[i]).exists()) {
        v.add(f[i].substring(f[i].lastIndexOf(File.separator) + 1));
      }
    }
    f = (String[]) v.toArray(new String[0]);
    return new FilePattern(FilePattern.findPattern(f[0], dir, f));
  }

  private void setFiles(String[] list, String prefix, BigInteger first,
    BigInteger last, BigInteger step, String dir, int blockNum)
  {
    long f = first.longValue();
    long l = last.longValue();
    long s = step.longValue();
    for (long i=f; i<=l; i+=s) {
      FilePattern newPattern = getPattern(list, dir, prefix + i);
      if (blockNum == seriesBlocks.length - 1) {
        fileVector.add(newPattern.getFiles());
        String name = newPattern.getPattern();
        if (name.indexOf(File.separator) != -1) {
          name = name.substring(name.lastIndexOf(File.separator) + 1);
        }
        seriesNames.add(name);
      }
      else {
        String next = seriesBlocks[blockNum + 1];
        String[] blocks = newPattern.getPrefixes();
        BigInteger fi = null;
        BigInteger la = null;
        BigInteger st = null;
        for (int q=0; q<blocks.length; q++) {
          if (blocks[q].indexOf(next) != -1) {
            fi = newPattern.getFirst()[q];
            la = newPattern.getLast()[q];
            st = newPattern.getStep()[q];
            break;
          }
        }

        setFiles(newPattern.getFiles(), next, fi, la, st, dir, blockNum + 1);
      }
    }
  }

}
