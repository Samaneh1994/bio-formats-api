//
// CellWorxReader.java
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
import java.util.Vector;

import loci.common.DataTools;
import loci.common.DateTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;

/**
 * CellWorxReader is the file format reader for CellWorx .pnl files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/CellWorxReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/CellWorxReader.java">SVN</a></dd></dl>
 */
public class CellWorxReader extends FormatReader {

  // -- Constants --

  private static final String DATE_FORMAT = "EEE MMM dd HH:mm:ss yyyy";

  // -- Fields --

  private boolean[][] fieldMap;
  private String[][] wellFiles;
  private String[][] logFiles;
  private int fieldCount = 0;

  private String plateLogFile;
  private String zMapFile;

  // -- Constructor --

  /** Constructs a new CellWorx reader. */
  public CellWorxReader() {
    super("CellWorx", new String[] {"pnl", "htd"});
    domains = new String[] {FormatTools.HCS_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    Vector<String> files = new Vector<String>();
    files.add(currentId);
    if (plateLogFile != null) files.add(plateLogFile);
    if (zMapFile != null) files.add(zMapFile);

    int row = getWellRow(getSeries());
    int col = getWellColumn(getSeries());

    files.add(logFiles[row][col]);
    if (!noPixels) files.add(wellFiles[row][col]);
    return files.toArray(new String[files.size()]);
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);
    int planeSize = FormatTools.getPlaneSize(this);
    int fieldSize = getImageCount() * planeSize;
    int fieldIndex = getSeries() % fieldCount;

    String file = getPNLFile(getSeries());
    DeltavisionReader pnl = new DeltavisionReader();
    pnl.setId(file);
    pnl.openBytes(fieldIndex * fieldCount + no, buf, x, y, w, h);
    pnl.close();
    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      fieldMap = null;
      wellFiles = null;
      logFiles = null;
      fieldCount = 0;
      plateLogFile = null;
      zMapFile = null;
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    // first, make sure that we have the .htd file

    if (!checkSuffix(id, "htd")) {
      LOGGER.info("Searching for .htd file");
      String base = new Location(id).getAbsolutePath();
      base = base.substring(0, base.lastIndexOf("_"));
      id = base + ".HTD";
    }

    super.initFile(id);

    String plateData = DataTools.readFile(id);
    String[] lines = plateData.split("\n");
    int xWells = 0, yWells = 0;
    int xFields = 0, yFields = 0;
    String[] wavelengths = null;

    // determine dataset dimensions
    for (String line : lines) {
      int split = line.indexOf("\",");
      if (split < 1) continue;
      String key = line.substring(1, split).trim();
      String value = line.substring(split + 2).trim();

      if (key.equals("XWells")) {
        xWells = Integer.parseInt(value);
      }
      else if (key.equals("YWells")) {
        yWells = Integer.parseInt(value);
        wellFiles = new String[yWells][xWells];
        logFiles = new String[yWells][xWells];
      }
      else if (key.startsWith("WellsSelection")) {
        int row = Integer.parseInt(key.substring(14)) - 1;
        String[] mapping = value.split(",");
        for (int col=0; col<xWells; col++) {
          if (new Boolean(mapping[col].trim()).booleanValue()) {
            wellFiles[row][col] = "";
          }
        }
      }
      else if (key.equals("XSites")) {
        xFields = Integer.parseInt(value);
      }
      else if (key.equals("YSites")) {
        yFields = Integer.parseInt(value);
        fieldMap = new boolean[yFields][xFields];
      }
      else if (key.startsWith("SiteSelection")) {
        int row = Integer.parseInt(key.substring(13)) - 1;
        String[] mapping = value.split(",");
        for (int col=0; col<xFields; col++) {
          fieldMap[row][col] = new Boolean(mapping[col].trim()).booleanValue();
        }
      }
      else if (key.equals("NWavelengths")) {
        wavelengths = new String[Integer.parseInt(value)];
      }
      else if (key.startsWith("WaveName")) {
        int index = Integer.parseInt(key.substring(8)) - 1;
        wavelengths[index] = value.replaceAll("\"", "");
      }
    }

    for (int row=0; row<fieldMap.length; row++) {
      for (int col=0; col<fieldMap[row].length; col++) {
        if (fieldMap[row][col]) fieldCount++;
      }
    }

    // find pixels files
    String plateName = new Location(id).getAbsolutePath();
    plateName = plateName.substring(0, plateName.lastIndexOf(".")) + "_";
    int wellCount = 0;
    for (int row=0; row<wellFiles.length; row++) {
      for (int col=0; col<wellFiles[row].length; col++) {
        if (wellFiles[row][col] != null) {
          wellCount++;
          char rowLetter = (char) (row + 'A');
          String base = plateName + rowLetter + String.format("%02d", col + 1);
          wellFiles[row][col] = base + ".pnl";
          logFiles[row][col] = base + "_scan.log";
        }
      }
    }

    plateLogFile = plateName + "scan.log";

    if (new Location(plateLogFile).exists()) {
      String[] f = DataTools.readFile(plateLogFile).split("\n");
      for (String line : f) {
        if (line.trim().startsWith("Z Map File")) {
          String file = line.substring(line.indexOf(":") + 1);
          file = file.substring(file.lastIndexOf("/") + 1);
          String parent = new Location(id).getAbsoluteFile().getParent();
          zMapFile = new Location(parent, file).getAbsolutePath();
        }
      }
    }

    core = new CoreMetadata[fieldCount * wellCount];

    String file = getPNLFile(0);
    DeltavisionReader pnl = new DeltavisionReader();
    pnl.setId(file);

    for (int i=0; i<core.length; i++) {
      setSeries(i);
      core[i] = new CoreMetadata();
      core[i].littleEndian = pnl.isLittleEndian();
      core[i].sizeX = pnl.getSizeX();
      core[i].sizeY = pnl.getSizeY();
      core[i].pixelType = pnl.getPixelType();
      core[i].sizeZ = 1;
      core[i].sizeT = 1;
      core[i].sizeC = wavelengths.length;
      core[i].imageCount = getSizeZ() * getSizeC() * getSizeT();
      core[i].dimensionOrder = "XYCZT";
      core[i].rgb = false;
      core[i].interleaved = pnl.isInterleaved();
    }

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);

    store.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
    for (int i=0; i<core.length; i++) {
      store.setImageID(MetadataTools.createLSID("Image", i), i);
    }

    int nextImage = 0;
    for (int row=0; row<wellFiles.length; row++) {
      for (int col=0; col<wellFiles[row].length; col++) {
        int wellIndex = row * wellFiles[row].length + col;
        String wellID = MetadataTools.createLSID("Well", 0, wellIndex);
        store.setWellID(wellID, 0, wellIndex);
        store.setWellColumn(new NonNegativeInteger(col), 0, wellIndex);
        store.setWellRow(new NonNegativeInteger(row), 0, wellIndex);

        for (int fieldRow=0; fieldRow<fieldMap.length; fieldRow++) {
          for (int fieldCol=0; fieldCol<fieldMap[fieldRow].length; fieldCol++) {
            if (fieldMap[fieldRow][fieldCol] && wellFiles[row][col] != null) {
              int fieldIndex = fieldRow * fieldMap[fieldRow].length + fieldCol;

              String wellSampleID =
                MetadataTools.createLSID("WellSample", 0, wellIndex, fieldIndex);
              store.setWellSampleID(wellSampleID, 0, wellIndex, fieldIndex);
              String imageID = MetadataTools.createLSID("Image", nextImage);
              store.setWellSampleImageRef(imageID, 0, wellIndex, fieldIndex);
              store.setWellSampleIndex(
                new NonNegativeInteger(fieldIndex), 0, wellIndex, fieldIndex);

              String well = (char) (row + 'A') + String.format("%02d", col + 1);
              store.setImageName(
                "Well " + well + " Field #" + (fieldIndex + 1), nextImage);
              nextImage++;
            }
          }
        }
      }
    }

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      for (int well=0; well<wellCount; well++) {
        parseWellLogFile(well, store);
      }
      for (int i=0; i<core.length; i++) {
        for (int c=0; c<getSizeC(); c++) {
          if (c < wavelengths.length) {
            store.setChannelName(wavelengths[c], i, c);
          }
        }
      }
    }
  }

  // -- Helper methods --

  /** Retrieve the well index corresponding to the given series. */
  private int getWell(int seriesIndex) {
    int wellIndex = seriesIndex / fieldCount;
    int counter = -1;
    for (int row=0; row<wellFiles.length; row++) {
      for (int col=0; col<wellFiles[row].length; col++) {
        if (wellFiles[row][col] != null) counter++;
        if (counter == wellIndex) return row * wellFiles[row].length + col;
      }
    }
    return -1;
  }

  /** Retrieve the well row corresponding to the given series. */
  private int getWellRow(int seriesIndex) {
    int well = getWell(seriesIndex);
    return well / wellFiles[0].length;
  }

  /** Retrieve the well column corresponding to the given series. */
  private int getWellColumn(int seriesIndex) {
    int well = getWell(seriesIndex);
    return well % wellFiles[0].length;
  }

  /** Retrieve the .pnl file corresponding to the given series. */
  private String getPNLFile(int seriesIndex) {
    return wellFiles[getWellRow(seriesIndex)][getWellColumn(seriesIndex)];
  }

  /** Parse metadata from a well log file. */
  private void parseWellLogFile(int wellIndex, MetadataStore store)
    throws IOException
  {
    int seriesIndex = wellIndex * fieldCount;
    int row = getWellRow(seriesIndex);
    int col = getWellColumn(seriesIndex);
    String logFile = logFiles[row][col];
    LOGGER.debug("Parsing log file for well {}{}", (char) (row + 'A'), col + 1);

    int oldSeries = getSeries();
    setSeries(seriesIndex);

    String data = DataTools.readFile(logFile);
    String[] lines = data.split("\n");
    for (String line : lines) {
      line = line.trim();
      int separator = line.indexOf(":");
      if (separator < 0) continue;
      String key = line.substring(0, separator).trim();
      String value = line.substring(separator + 1).trim();

      addSeriesMeta(key, value);

      if (key.equals("Date")) {
        String date = DateTools.formatDate(value, DATE_FORMAT);
        for (int field=0; field<fieldCount; field++) {
          store.setImageAcquiredDate(date, seriesIndex + field);
        }
      }
      else if (key.equals("Scan Origin")) {
        String[] axes = value.split(",");
        for (int field=0; field<fieldCount; field++) {
          store.setWellSamplePositionX(new Double(axes[0]), 0, wellIndex, field);
          store.setWellSamplePositionY(new Double(axes[1]), 0, wellIndex, field);
        }
      }
      else if (key.equals("Scan Area")) {
        int s = value.indexOf("x");
        if (s > 0) {
          int end = value.indexOf(" ", s + 2);
          Double xSize = new Double(value.substring(0, s).trim());
          Double ySize = new Double(value.substring(s + 1, end).trim());
          for (int field=0; field<fieldCount; field++) {
            store.setPixelsPhysicalSizeX(xSize / getSizeX(), seriesIndex + field);
            store.setPixelsPhysicalSizeY(ySize / getSizeY(), seriesIndex + field);
          }
        }
      }
      else if (key.startsWith("Channel")) {
        int start = key.indexOf(" ") + 1;
        int end = key.indexOf(" ", start);
        if (end < 0) end = key.length();
        int index = Integer.parseInt(key.substring(start, end)) - 1;

        String[] tokens = value.split(",");
        for (String token : tokens) {
          token = token.trim();
          if (token.startsWith("gain")) {
            String instrumentID = MetadataTools.createLSID("Instrument", 0);
            Double gain = new Double(token.replaceAll("gain ", ""));
            String detectorID = MetadataTools.createLSID("Detector", 0, 0);

            store.setInstrumentID(instrumentID, 0);
            store.setDetectorID(detectorID, 0, 0);

            for (int field=0; field<fieldCount; field++) {
              store.setImageInstrumentRef(instrumentID, seriesIndex + field);
              store.setDetectorSettingsGain(gain, seriesIndex + field, index);
              store.setDetectorSettingsID(detectorID, seriesIndex + field, index);
            }
          }
          else if (token.startsWith("EX")) {
            int slash = token.indexOf("/");
            if (slash > 0) {
              String ex = token.substring(0, slash).trim();
              String em = token.substring(slash + 1).trim();

              if (ex.indexOf(" ") > 0) ex = ex.substring(ex.indexOf(" ") + 1);
              if (em.indexOf(" ") > 0) {
                em = em.substring(em.indexOf(" ") + 1);
                if (em.indexOf(" ") > 0) {
                  em = em.substring(0, em.indexOf(" "));
                }
              }

              PositiveInteger exWave = new PositiveInteger(new Integer(ex));
              PositiveInteger emWave = new PositiveInteger(new Integer(em));
              for (int field=0; field<fieldCount; field++) {
                store.setChannelExcitationWavelength(
                  exWave, seriesIndex + field, index);
                store.setChannelEmissionWavelength(
                  emWave, seriesIndex + field, index);
              }
            }
          }
        }
      }
    }

    setSeries(oldSeries);
  }

}
