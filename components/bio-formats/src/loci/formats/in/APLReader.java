//
// APLReader.java
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

package loci.formats.in;

import java.io.File;
import java.io.IOException;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.Location;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * APLReader is the file format reader for Olympus APL files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/APLReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/APLReader.java">SVN</a></dd></dl>
 */
public class APLReader extends FormatReader {

  // -- Fields --

  private String[] tiffFiles;
  private String[] xmlFiles;
  private MinimalTiffReader[] tiffReaders;
  private Vector<String> used;

  // -- Constructor --

  /** Constructs a new APL reader. */
  public APLReader() {
    super("Olympus APL", new String[] {"apl", "tnb", "mtb"});
    domains = new String[] {FormatTools.LM_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    Vector<String> files = new Vector<String>();
    files.addAll(used);
    if (getSeries() < xmlFiles.length) files.add(xmlFiles[getSeries()]);
    if (!noPixels && getSeries() < tiffFiles.length) {
      files.add(tiffFiles[getSeries()]);
    }
    return files.toArray(new String[files.size()]);
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    return tiffReaders[series].openBytes(no, buf, x, y, w, h);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (tiffReaders != null) {
      for (MinimalTiffReader reader : tiffReaders) {
        reader.close(fileOnly);
      }
    }
    if (!fileOnly) {
      tiffReaders = null;
      tiffFiles = null;
      xmlFiles = null;
      used = null;
    }
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    debug("APLReader.initFile(" + id + ")");
    super.initFile(id);

    // find the corresponding .mtb file
    String name = id.toLowerCase();
    if (!name.endsWith(".mtb")) {
      int separator = id.lastIndexOf(File.separator);
      if (separator < 0) separator = 0;
      int underscore = id.lastIndexOf("_");
      if (underscore < separator) underscore = id.lastIndexOf(".");
      String mtbFile = id.substring(0, underscore) + "_d.mtb";
      if (!new Location(mtbFile).exists()) {
        throw new FormatException(".mtb file not found");
      }
      currentId = new Location(mtbFile).getAbsolutePath();
    }

    String mtb = new Location(currentId).getAbsolutePath();

    Vector<String[]> rows = MDBParser.parseDatabase(mtb)[0];
    String[] columnNames = rows.get(0);
    String[] tmpNames = columnNames;
    columnNames = new String[tmpNames.length - 1];
    System.arraycopy(tmpNames, 1, columnNames, 0, columnNames.length);

    // add full table to metadata hashtable

    for (int i=1; i<rows.size(); i++) {
      String[] row = rows.get(i);
      for (int q=0; q<row.length; q++) {
        addGlobalMeta(columnNames[q] + " " + i, row[q]);
      }
    }

    used = new Vector<String>();
    used.add(id);
    if (!id.equals(mtb)) used.add(mtb);
    String tnb = id.substring(0, id.lastIndexOf("."));
    if (tnb.lastIndexOf("_") > tnb.lastIndexOf(File.separator)) {
      tnb = tnb.substring(0, tnb.lastIndexOf("_"));
    }
    used.add(tnb + "_1.tnb");

    // calculate indexes to relevant metadata

    int calibrationUnit = DataTools.indexOf(columnNames, "Calibration Unit");
    int colorChannels = DataTools.indexOf(columnNames, "Color Channels");
    int frames = DataTools.indexOf(columnNames, "Frames");
    int calibratedHeight = DataTools.indexOf(columnNames, "Height");
    int calibratedWidth = DataTools.indexOf(columnNames, "Width");
    int path = DataTools.indexOf(columnNames, "Image Path");
    int filename = DataTools.indexOf(columnNames, "File Name");
    int magnification = DataTools.indexOf(columnNames, "Magnification");
    int width = DataTools.indexOf(columnNames, "X-Resolution");
    int height = DataTools.indexOf(columnNames, "Y-Resolution");
    int imageName = DataTools.indexOf(columnNames, "Image Name");
    int zLayers = DataTools.indexOf(columnNames, "Z-Layers");

    String parentDirectory = mtb.substring(0, mtb.lastIndexOf(File.separator));

    // look for the directory that contains TIFF and XML files

    Location dir = new Location(parentDirectory);
    String[] list = dir.list(true);
    String topDirectory = null;
    for (String f : list) {
      Location file = new Location(dir, f);
      if (file.isDirectory() && f.endsWith("_DocumentFiles")) {
        topDirectory = file.getAbsolutePath();
      }
    }
    if (topDirectory == null) {
      throw new FormatException("Could not find a directory with TIFF files.");
    }

    Vector<Integer> seriesIndexes = new Vector<Integer>();

    for (int i=1; i<rows.size(); i++) {
      String file = rows.get(i)[filename].trim();
      if (file.equals("")) continue;
      file = topDirectory + File.separator + file;
      if (new Location(file).exists() && file.toLowerCase().endsWith(".tif")) {
        seriesIndexes.add(new Integer(i));
      }
    }
    int seriesCount = seriesIndexes.size();

    core = new CoreMetadata[seriesCount];
    for (int i=0; i<seriesCount; i++) {
      core[i] = new CoreMetadata();
    }
    tiffFiles = new String[seriesCount];
    xmlFiles = new String[seriesCount];
    tiffReaders = new MinimalTiffReader[seriesCount];

    for (int i=0; i<seriesCount; i++) {
      int secondRow = seriesIndexes.get(i).intValue();
      int firstRow = secondRow - 1;
      String[] row2 = rows.get(firstRow);
      String[] row3 = rows.get(secondRow);

      try {
        core[i].sizeT = Integer.parseInt(row3[frames]);
      }
      catch (NumberFormatException e) {
        core[i].sizeT = 1;
      }
      try {
        core[i].sizeZ = Integer.parseInt(row3[zLayers]);
      }
      catch (NumberFormatException e) {
        core[i].sizeZ = 1;
      }
      try {
        core[i].sizeC = Integer.parseInt(row3[colorChannels]);
      }
      catch (NumberFormatException e) {
        core[i].sizeC = 1;
      }
      core[i].dimensionOrder = "XYCZT";

      if (core[i].sizeZ == 0) core[i].sizeZ = 1;
      if (core[i].sizeC == 0) core[i].sizeC = 1;
      if (core[i].sizeT == 0) core[i].sizeT = 1;

      xmlFiles[i] = topDirectory + File.separator + row2[filename];
      tiffFiles[i] = topDirectory + File.separator + row3[filename];

      tiffReaders[i] = new MinimalTiffReader();
      tiffReaders[i].setId(tiffFiles[i]);

      // get core metadata from TIFF file

      core[i].sizeX = tiffReaders[i].getSizeX();
      core[i].sizeY = tiffReaders[i].getSizeY();
      core[i].rgb = tiffReaders[i].isRGB();
      core[i].pixelType = tiffReaders[i].getPixelType();
      core[i].littleEndian = tiffReaders[i].isLittleEndian();
      core[i].indexed = tiffReaders[i].isIndexed();
      core[i].falseColor = tiffReaders[i].isFalseColor();
      core[i].imageCount = tiffReaders[i].getImageCount();
      if (core[i].sizeZ * core[i].sizeT * (core[i].rgb ? 1 : core[i].sizeC) !=
        core[i].imageCount)
      {
        core[i].sizeT = core[i].imageCount / (core[i].rgb ? 1 : core[i].sizeC);
        core[i].sizeZ = 1;
      }
    }

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);

    for (int i=0; i<seriesCount; i++) {
      String[] row = rows.get(seriesIndexes.get(i).intValue());

      // populate Image data
      MetadataTools.setDefaultCreationDate(store, mtb, i);
      store.setImageName(row[imageName], i);

      // populate Dimensions data

      // calculate physical X and Y sizes

      float realWidth = Float.parseFloat(row[calibratedWidth]);
      float realHeight = Float.parseFloat(row[calibratedHeight]);

      String units = row[calibrationUnit];

      float px = realWidth / core[i].sizeX;
      float py = realHeight / core[i].sizeY;

      if (units.equals("mm")) {
        px *= 1000;
        py *= 1000;
      }
      // TODO : add cases for other units

      store.setDimensionsPhysicalSizeX(new Float(px), i, 0);
      store.setDimensionsPhysicalSizeY(new Float(py), i, 0);
    }
  }

}
