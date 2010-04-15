//
// L2DReader.java
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

import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.DateTools;
import loci.common.Location;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * L2DReader is the file format reader for Li-Cor L2D datasets.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/L2DReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/L2DReader.java">SVN</a></dd></dl>
 */
public class L2DReader extends FormatReader {

  // -- Constants --

  public static final String DATE_FORMAT = "yyyy, m, d";

  // -- Fields --

  /** List of constituent TIFF files. */
  private String[][] tiffs;

  /** List of all metadata files in the dataset. */
  private Vector[] metadataFiles;

  private MinimalTiffReader reader;

  // -- Constructor --

  /** Construct a new L2D reader. */
  public L2DReader() {
    super("Li-Cor L2D", new String[] {"l2d", "scn", "tif"});
    domains = new String[] {FormatTools.GEL_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    if (checkSuffix(name, "l2d") || checkSuffix(name, "scn")) return true;
    if (!open) return false;

    Location parent = new Location(name).getAbsoluteFile().getParentFile();
    String[] list = parent.list();
    if (list == null) return false;

    for (String file : list) {
      if (checkSuffix(file, "scn")) return true;
    }
    return false;
  }

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    reader.setId(tiffs[series][no]);
    return reader.openBytes(0, buf, x, y, w, h);
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    Vector<String> files = new Vector<String>();
    files.add(currentId);
    if (metadataFiles != null && getSeries() < metadataFiles.length) {
      files.addAll(metadataFiles[getSeries()]);
    }
    if (!noPixels) {
      for (String tiff : tiffs[series]) {
        files.add(tiff);
      }
    }
    return files.toArray(new String[files.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (reader != null) reader.close(fileOnly);
    if (!fileOnly) {
      tiffs = null;
      reader = null;
      metadataFiles = null;
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    // NB: This format cannot be imported using omebf.
    // See Trac ticket #266 for details.

    if (!checkSuffix(id, "l2d") && isGroupFiles()) {
      // find the corresponding .l2d file
      Location parent = new Location(id).getAbsoluteFile().getParentFile();
      parent = parent.getParentFile();
      String[] list = parent.list();
      for (String file : list) {
        if (checkSuffix(file, "l2d")) {
          initFile(new Location(parent, file).getAbsolutePath());
          return;
        }
      }
      throw new FormatException("Could not find .l2d file");
    }
    else if (!isGroupFiles()) {
      super.initFile(id);

      tiffs = new String[][] {{id}};

      TiffReader r = new TiffReader();
      r.setMetadataStore(getMetadataStore());
      r.setId(id);
      core = r.getCoreMetadata();
      metadataStore = r.getMetadataStore();

      Hashtable globalMetadata = r.getGlobalMetadata();
      for (Object key : globalMetadata.keySet()) {
        addGlobalMeta(key.toString(), globalMetadata.get(key));
      }
      r.close();
      reader = new MinimalTiffReader();
      return;
    }

    super.initFile(id);

    String[] scans = getScanNames();

    // read metadata from each scan

    tiffs = new String[scans.length][];
    metadataFiles = new Vector[scans.length];

    core = new CoreMetadata[scans.length];

    String[] comments = new String[scans.length];
    String[] wavelengths = new String[scans.length];
    String[] dates = new String[scans.length];
    String model = null;

    Location parent = new Location(id).getAbsoluteFile().getParentFile();
    for (int i=0; i<scans.length; i++) {
      setSeries(i);
      core[i] = new CoreMetadata();
      metadataFiles[i] = new Vector();
      String scanName = scans[i] + ".scn";
      Location scanDir = new Location(parent, scans[i]);

      // read .scn file from each scan

      String scanPath = new Location(scanDir, scanName).getAbsolutePath();
      addDirectory(scanDir.getAbsolutePath(), i);
      String scanData = DataTools.readFile(scanPath);
      String[] lines = scanData.split("\n");
      for (String line : lines) {
        if (!line.startsWith("#")) {
          String key = line.substring(0, line.indexOf("="));
          String value = line.substring(line.indexOf("=") + 1);
          addSeriesMeta(key, value);

          if (key.equals("ExperimentNames")) {
            // TODO : parse experiment metadata - this is typically a list of
            //        overlay shapes, or analysis data
          }
          else if (key.equals("ImageNames")) {
            tiffs[i] = value.split(",");
            for (int t=0; t<tiffs[i].length; t++) {
              tiffs[i][t] =
                new Location(scanDir, tiffs[i][t].trim()).getAbsolutePath();
            }
          }
          else if (key.equals("Comments")) {
            comments[i] = value;
          }
          else if (key.equals("ScanDate")) {
            dates[i] = value;
          }
          else if (key.equals("ScannerName")) {
            model = value;
          }
          else if (key.equals("ScanChannels")) {
            wavelengths[i] = value;
          }
        }
      }
    }
    setSeries(0);

    reader = new MinimalTiffReader();

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());

    for (int i=0; i<getSeriesCount(); i++) {
      core[i].imageCount = tiffs[i].length;
      core[i].sizeC = tiffs[i].length;
      core[i].sizeT = 1;
      core[i].sizeZ = 1;
      core[i].dimensionOrder = "XYCZT";

      reader.setId(tiffs[i][0]);
      core[i].sizeX = reader.getSizeX();
      core[i].sizeY = reader.getSizeY();
      core[i].sizeC *= reader.getSizeC();
      core[i].rgb = reader.isRGB();
      core[i].indexed = reader.isIndexed();
      core[i].littleEndian = reader.isLittleEndian();
      core[i].pixelType = reader.getPixelType();
    }

    MetadataTools.populatePixels(store, this);

    for (int i=0; i<getSeriesCount(); i++) {
      store.setImageName(scans[i], i);
      if (dates[i] != null) {
        dates[i] = DateTools.formatDate(dates[i], DATE_FORMAT);
        store.setImageCreationDate(dates[i], i);
      }
      else MetadataTools.setDefaultCreationDate(store, id, i);
    }

    if (getMetadataOptions().getMetadataLevel() == MetadataLevel.ALL) {
      String instrumentID = MetadataTools.createLSID("Instrument", 0);
      store.setInstrumentID(instrumentID, 0);

      for (int i=0; i<getSeriesCount(); i++) {
        store.setImageInstrumentRef(instrumentID, i);

        store.setImageDescription(comments[i], i);

        if (wavelengths[i] != null) {
          String[] waves = wavelengths[i].split("[, ]");
          if (waves.length < getEffectiveSizeC()) {
            LOGGER.debug("Expected {} wavelengths; got {} wavelengths.",
              getEffectiveSizeC(), waves.length);
          }
          for (int q=0; q<waves.length; q++) {
            String lightSource = MetadataTools.createLSID("LightSource", 0, q);
            store.setLightSourceID(lightSource, 0, q);
            store.setLaserWavelength(new Integer(waves[q].trim()), 0, q);
            store.setLaserType("Unknown", 0, q);
            store.setLaserLaserMedium("Unknown", 0, q);
            store.setLogicalChannelLightSource(lightSource, i, q);
          }
        }
      }

      store.setMicroscopeModel(model, 0);
      store.setMicroscopeType("Unknown", 0);
    }
  }

  // -- Helper methods --

  /**
   * Recursively add all of the files in the given directory to the
   * used file list.
   */
  private void addDirectory(String path, int series) {
    Location dir = new Location(path);
    String[] files = dir.list();
    if (files == null) return;
    for (String f : files) {
      Location file = new Location(path, f);
      if (file.isDirectory()) {
        addDirectory(file.getAbsolutePath(), series);
      }
      else if (checkSuffix(f, "scn")) {
        metadataFiles[series].add(file.getAbsolutePath());
      }
    }
  }

  /** Return a list of scan names. */
  private String[] getScanNames() throws IOException {
    String[] scans = null;

    String data = DataTools.readFile(currentId);
    String[] lines = data.split("\n");
    for (String line : lines) {
      if (!line.startsWith("#")) {
        String key = line.substring(0, line.indexOf("=")).trim();
        String value = line.substring(line.indexOf("=") + 1).trim();
        addGlobalMeta(key, value);

        if (key.equals("ScanNames")) {
          scans = value.split(",");
          for (int i=0; i<scans.length; i++) {
            scans[i] = scans[i].trim();
          }
        }
      }
    }
    return scans;
  }

}
