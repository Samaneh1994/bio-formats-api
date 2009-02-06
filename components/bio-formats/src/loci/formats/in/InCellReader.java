//
// InCellReader.java
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

package loci.formats.in;

import java.awt.Point;
import java.io.*;
import java.util.*;
import loci.common.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * InCellReader is the file format reader for InCell 1000 datasets.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/InCellReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/InCellReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class InCellReader extends FormatReader {

  // -- Fields --

  private Image[][][] imageFiles;
  private MinimalTiffReader tiffReader;
  private int seriesCount;
  private Vector emWaves, exWaves;
  private int totalImages;
  private String creationDate;
  private int startRow, startCol;
  private int fieldCount;

  private int wellRows, wellCols;
  private Hashtable wellCoordinates;
  private Vector posX, posY;

  private int firstRow, firstCol;
  private int lastCol;

  // -- Constructor --

  /** Constructs a new InCell 1000 reader. */
  public InCellReader() { super("InCell 1000", "xdce"); }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    return tiffReader.get8BitLookupTable();
  }

  /* @see loci.formats.IForamtReader#get16BitLookupTable() */
  public short[][] get16BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    return tiffReader.get16BitLookupTable();
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length, w, h);

    int well = getWellFromSeries(getSeries());
    int field = getFieldFromSeries(getSeries());
    tiffReader.setId(imageFiles[well][field][no].filename);
    return tiffReader.openBytes(0, buf, x, y, w, h);
  }

  /* @see loci.formats.IFormatReader#getUsedFiles() */
  public String[] getUsedFiles() {
    FormatTools.assertId(currentId, true, 1);
    String[] files = new String[getSeriesCount() * getImageCount() + 1];
    int nextFile = 0;
    if (imageFiles != null) {
      for (int series=0; series<getSeriesCount(); series++) {
        int well = getWellFromSeries(series);
        int field = getFieldFromSeries(series);
        for (int plane=0; plane<getImageCount(); plane++) {
          files[nextFile++] = imageFiles[well][field][plane].filename;
        }
      }
    }
    files[files.length - 1] = currentId;
    return files;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    imageFiles = null;
    if (tiffReader != null) tiffReader.close();
    tiffReader = null;
    seriesCount = 0;
    totalImages = 0;

    emWaves = exWaves = null;
    wellCoordinates = null;
    posX = null;
    posY = null;
    creationDate = null;
    wellRows = wellCols = 0;
    startRow = startCol = 0;
    fieldCount = 0;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("InCellReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);

    firstRow = Integer.MAX_VALUE;
    firstCol = Integer.MAX_VALUE;
    lastCol = Integer.MIN_VALUE;

    emWaves = new Vector();
    exWaves = new Vector();

    wellCoordinates = new Hashtable();
    posX = new Vector();
    posY = new Vector();

    byte[] b = new byte[(int) in.length()];
    in.read(b);

    core[0].dimensionOrder = "XYZCT";

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    DefaultHandler handler = new MinimalInCellHandler();
    DataTools.parseXML(b, handler);

    if (getSizeZ() == 0) core[0].sizeZ = 1;
    if (getSizeC() == 0) core[0].sizeC = 1;
    if (getSizeT() == 0) core[0].sizeT = 1;

    seriesCount = totalImages / (getSizeZ() * getSizeC() * getSizeT());

    int z = getSizeZ();
    int c = getSizeC();
    int t = getSizeT();

    core = new CoreMetadata[seriesCount];
    for (int i=0; i<seriesCount; i++) {
      core[i] = new CoreMetadata();
      core[i].sizeZ = z;
      core[i].sizeC = c;
      core[i].sizeT = t;
      core[i].imageCount = z * c * t;
      core[i].dimensionOrder = "XYZCT";
    }

    tiffReader = new MinimalTiffReader();
    tiffReader.setId(
      imageFiles[getWellFromSeries(0)][getFieldFromSeries(0)][0].filename);

    int nextTiming = 0;
    for (int i=0; i<seriesCount; i++) {
      core[i].sizeX = tiffReader.getSizeX();
      core[i].sizeY = tiffReader.getSizeY();
      core[i].interleaved = tiffReader.isInterleaved();
      core[i].indexed = tiffReader.isIndexed();
      core[i].rgb = tiffReader.isRGB();
      core[i].pixelType = tiffReader.getPixelType();
      core[i].littleEndian = tiffReader.isLittleEndian();
    }

    MetadataTools.populatePixels(store, this, true);

    handler = new InCellHandler(store);
    DataTools.parseXML(b, handler);

    // populate Image data

    store.setInstrumentID("Instrument:0", 0);

    for (int i=0; i<seriesCount; i++) {
      int well = getWellFromSeries(i);
      int field = getFieldFromSeries(i);
      store.setImageID("Image:" + i, i);
      store.setImageInstrumentRef("Instrument:0", i);
      store.setImageName("Well #" + well + ", Field #" + field, i);
      store.setImageCreationDate(creationDate, i);
    }

    // populate PlaneTiming data

    for (int i=0; i<seriesCount; i++) {
      int well = getWellFromSeries(i);
      int field = getFieldFromSeries(i);
      for (int q=0; q<core[i].imageCount; q++) {
        Image img = imageFiles[well][field][q];
        store.setPlaneTimingDeltaT(img.deltaT, i, 0, q);
        store.setPlaneTimingExposureTime(img.exposure, i, 0, q);
      }
    }

    // populate LogicalChannel data

    for (int i=0; i<seriesCount; i++) {
      for (int q=0; q<emWaves.size(); q++) {
        store.setLogicalChannelEmWave((Integer) emWaves.get(q), i, q);
        store.setLogicalChannelExWave((Integer) exWaves.get(q), i, q);
      }
    }

    // populate Well data

    int[][] plate = new int[wellRows][wellCols];

    for (int i=0; i<seriesCount; i++) {
      int well = getWellFromSeries(i);
      int field = getFieldFromSeries(i);

      store.setWellSampleIndex(new Integer(i), 0, well, field);
      store.setWellSampleImageRef("Image:" + i, 0, well, field);
      store.setWellSamplePosX((Float) posX.get(field), 0, well, field);
      store.setWellSamplePosY((Float) posY.get(field), 0, well, field);
    }
  }

  // -- Helper methods --

  private int getFieldFromSeries(int series) {
    return series % fieldCount;
  }

  private int getWellFromSeries(int series) {
    int well = series / fieldCount;
    int wellRow = well / (lastCol - firstCol + 1);
    int wellCol = well % (lastCol - firstCol + 1);
    return (wellRow + firstRow) * wellCols + wellCol + firstCol;
  }

  // -- Helper classes --

  class MinimalInCellHandler extends DefaultHandler {
    private String currentImageFile;
    private int wellRow, wellCol;

    public void endElement(String uri, String localName, String qName) {
      if (qName.equals("PlateMap")) {
        int imageCount = getSizeZ() * getSizeC() * getSizeT();
        imageFiles = new Image[wellRows * wellCols][fieldCount][imageCount];
      }
    }

    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      if (qName.equals("Plate")) {
        wellRows = Integer.parseInt(attributes.getValue("rows"));
        wellCols = Integer.parseInt(attributes.getValue("columns"));
      }
      else if (qName.equals("Images")) {
        totalImages = Integer.parseInt(attributes.getValue("number"));
      }
      else if (qName.equals("Image")) {
        String file = attributes.getValue("filename");
        String thumb = attributes.getValue("thumbnail");
        Location current = new Location(currentId).getAbsoluteFile();

        Location imageFile = new Location(current.getParentFile(), file);
        if (imageFile.exists()) {
          currentImageFile = imageFile.getAbsolutePath();
        }
        else currentImageFile = file;
      }
      else if (qName.equals("Identifier")) {
        int field = Integer.parseInt(attributes.getValue("field_index"));
        int z = Integer.parseInt(attributes.getValue("z_index"));
        int c = Integer.parseInt(attributes.getValue("wave_index"));
        int t = Integer.parseInt(attributes.getValue("time_index"));
        core[0].imageCount = getSizeZ() * getSizeC() * getSizeT();
        int index = getIndex(z, c, t);

        Image img = new Image();
        img.filename = currentImageFile;
        imageFiles[wellRow * wellCols + wellCol][field][index] = img;
      }
      else if (qName.equals("offset_point")) {
        fieldCount++;
      }
      else if (qName.equals("TimePoint")) {
        core[0].sizeT++;
      }
      else if (qName.equals("Wavelength")) {
        core[0].sizeC++;
      }
      else if (qName.equals("ZDimensionParameters")) {
        String nz = attributes.getValue("number_of_slices");
        if (nz != null) {
          core[0].sizeZ = Integer.parseInt(nz);
        }
        else core[0].sizeZ = 1;
      }
      else if (qName.equals("Row")) {
        wellRow = Integer.parseInt(attributes.getValue("number")) - 1;
        firstRow = (int) Math.min(firstRow, wellRow);
      }
      else if (qName.equals("Column")) {
        wellCol = Integer.parseInt(attributes.getValue("number")) - 1;
        firstCol = (int) Math.min(firstCol, wellCol);
        lastCol = (int) Math.max(lastCol, wellCol);
      }
    }
  }

  /** SAX handler for parsing XML. */
  class InCellHandler extends DefaultHandler {
    private String currentQName;
    private boolean openImage;
    private int nextEmWave = 0;
    private int nextExWave = 0;
    private MetadataStore store;
    private int nextPlate = 0;
    private int currentRow = -1, currentCol = -1;
    private int currentField = 0;
    private int currentImage;
    private Float timestamp, exposure;

    public InCellHandler(MetadataStore store) {
      this.store = store;
    }

    public void characters(char[] ch, int start, int length) {
      String value = new String(ch, start, length);
      if (currentQName.equals("UserComment")) {
        store.setImageDescription(value, 0);
      }
    }

    public void endElement(String uri, String localName, String qName) {
      if (qName.equals("Image")) {
        Point p = new Point(currentRow, currentCol);
        wellCoordinates.put(new Integer(currentField), p);
        openImage = false;

        int well = currentRow * wellCols + currentCol;
        imageFiles[well][currentField][currentImage].deltaT = timestamp;
        imageFiles[well][currentField][currentImage].exposure = exposure;
      }
    }

    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      currentQName = qName;
      for (int i=0; i<attributes.getLength(); i++) {
        addMeta(qName + " - " + attributes.getQName(i), attributes.getValue(i));
      }

      if (qName.equals("Image")) {
        openImage = true;
        float time =
          Float.parseFloat(attributes.getValue("acquisition_time_ms"));
        timestamp = new Float(time / 1000);
      }
      else if (qName.equals("Identifier")) {
        currentField = Integer.parseInt(attributes.getValue("field_index"));
        int z = Integer.parseInt(attributes.getValue("z_index"));
        int c = Integer.parseInt(attributes.getValue("wave_index"));
        int t = Integer.parseInt(attributes.getValue("time_index"));
        currentImage = getIndex(z, c, t);
      }
      else if (qName.equals("Creation")) {
        String date = attributes.getValue("date"); // yyyy-mm-dd
        String time = attributes.getValue("time"); // hh:mm:ss
        creationDate = date + "T" + time;
      }
      else if (qName.equals("ObjectiveCalibration")) {
        store.setObjectiveNominalMagnification(new Integer(
          (int) Float.parseFloat(attributes.getValue("magnification"))), 0, 0);
        store.setObjectiveModel(attributes.getValue("objective_name"), 0, 0);
        store.setObjectiveLensNA(new Float(
          attributes.getValue("numerical_aperture")), 0, 0);
        store.setObjectiveCorrection("Unknown", 0, 0);
        store.setObjectiveImmersion("Unknown", 0, 0);

        Float pixelSizeX = new Float(attributes.getValue("pixel_width"));
        Float pixelSizeY = new Float(attributes.getValue("pixel_height"));
        Float refractive = new Float(attributes.getValue("refractive_index"));

        // link Objective to Image
        store.setObjectiveID("Objective:0", 0, 0);
        for (int i=0; i<getSeriesCount(); i++) {
          store.setObjectiveSettingsObjective("Objective:0", i);
          store.setObjectiveSettingsRefractiveIndex(refractive, i);
          store.setDimensionsPhysicalSizeX(pixelSizeX, i, 0);
          store.setDimensionsPhysicalSizeY(pixelSizeY, i, 0);
        }
      }
      else if (qName.equals("ExcitationFilter")) {
        String wave = attributes.getValue("wavelength");
        if (wave != null) exWaves.add(new Integer(wave));
      }
      else if (qName.equals("EmissionFilter")) {
        String wave = attributes.getValue("wavelength");
        if (wave != null) emWaves.add(new Integer(wave));
      }
      else if (qName.equals("Camera")) {
        store.setDetectorModel(attributes.getValue("name"), 0, 0);
        store.setDetectorType("Unknown", 0, 0);
        store.setDetectorID("Detector:0", 0, 0);
        for (int i=0; i<getSeriesCount(); i++) {
          for (int q=0; q<getSizeC(); q++) {
            store.setDetectorSettingsDetector("Detector:0", i, q);
          }
        }
      }
      else if (qName.equals("Binning")) {
        String binning = attributes.getValue("value");
        for (int i=0; i<getSeriesCount(); i++) {
          for (int q=0; q<getSizeC(); q++) {
            store.setDetectorSettingsBinning(binning, i, q);
          }
        }
      }
      else if (qName.equals("Gain")) {
        Float gain = new Float(attributes.getValue("value"));
        for (int i=0; i<getSeriesCount(); i++) {
          for (int q=0; q<getSizeC(); q++) {
            store.setDetectorSettingsGain(gain, i, q);
          }
        }
      }
      else if (qName.equals("PlateTemperature")) {
        Float temperature = new Float(attributes.getValue("value"));
        for (int i=0; i<getSeriesCount(); i++) {
          store.setImagingEnvironmentTemperature(temperature, i);
        }
      }
      else if (qName.equals("Plate")) {
        store.setPlateName(new Location(getCurrentFile()).getName(), nextPlate);

        for (int r=0; r<wellRows; r++) {
          for (int c=0; c<wellCols; c++) {
            store.setWellRow(new Integer(r), nextPlate, r*wellCols + c);
            store.setWellColumn(new Integer(c), nextPlate, r*wellCols + c);
          }
        }
        nextPlate++;
      }
      else if (qName.equals("Row")) {
        currentRow = Integer.parseInt(attributes.getValue("number")) - 1;
      }
      else if (qName.equals("Column")) {
        currentCol = Integer.parseInt(attributes.getValue("number")) - 1;
      }
      else if (qName.equals("Exposure") && openImage) {
        float exp = Float.parseFloat(attributes.getValue("time"));
        exposure = new Float(exp / 1000);
      }
      else if (qName.equals("NamingRows")) {
        String row = attributes.getValue("begin");
        try {
          startRow = Integer.parseInt(row);
        }
        catch (NumberFormatException e) {
          startRow = row.charAt(0) - 'A' + 1;
        }
      }
      else if (qName.equals("NamingColumns")) {
        String col = attributes.getValue("begin");
        try {
          startCol = Integer.parseInt(col);
        }
        catch (NumberFormatException e) {
          startCol = col.charAt(0) - 'A' + 1;
        }
      }
      else if (qName.equals("offset_point")) {
        posX.add(new Float(attributes.getValue("x")));
        posY.add(new Float(attributes.getValue("y")));
      }
    }
  }

  class Image {
    public String filename;
    public Float deltaT, exposure;
  }

}
