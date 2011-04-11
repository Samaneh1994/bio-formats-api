//
// SISReader.java
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

import loci.common.DateTools;
import loci.common.RandomAccessInputStream;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffParser;

import ome.xml.model.primitives.PositiveInteger;

/**
 * SISReader is the file format reader for Olympus Soft Imaging Solutions
 * TIFF files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/SISReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/SISReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class SISReader extends BaseTiffReader {

  // -- Constants --

  private static final int SIS_TAG = 33560;

  // -- Fields --

  private String imageName;
  private double magnification;
  private String channelName;
  private String cameraName;
  private double physicalSizeX, physicalSizeY;
  private String acquisitionDate;

  // -- Constructor --

  public SISReader() {
    super("Olympus SIS TIFF", new String[] {"tif", "tiff"});
    suffixSufficient = false;
    domains = new String[] {FormatTools.UNKNOWN_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    TiffParser tp = new TiffParser(stream);
    IFD ifd = tp.getFirstIFD();
    if (ifd == null) return false;
    return ifd.get(SIS_TAG) != null;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      imageName = null;
      channelName = null;
      cameraName = null;
      magnification = 0d;
      physicalSizeX = physicalSizeY = 0d;
    }
  }

  // -- Internal BaseTiffReader API methods --

  /* @see BaseTiffReader#initStandardMetadata() */
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();

    IFD ifd = ifds.get(0);
    long metadataPointer = ifd.getIFDLongValue(SIS_TAG, 0);

    in.seek(metadataPointer);

    in.skipBytes(4);

    in.skipBytes(6);
    int minute = in.readShort();
    int hour = in.readShort();
    int day = in.readShort();
    int month = in.readShort() + 1;
    int year = 1900 + in.readShort();

    acquisitionDate =
      year + "-" + month + "-" + day + " " + hour + ":" + minute;
    acquisitionDate = DateTools.formatDate(acquisitionDate, "yyyy-M-d H:m");

    in.skipBytes(6);

    imageName = in.readCString();
    in.skipBytes(18);

    in.seek(in.readInt());

    in.skipBytes(12);

    physicalSizeX = in.readDouble();
    physicalSizeY = in.readDouble();

    in.skipBytes(8);

    magnification = in.readDouble();
    int cameraNameLength = in.readShort();
    channelName = in.readCString();
    cameraName = channelName.substring(0, cameraNameLength);

    addGlobalMeta("Nanometers per pixel (X)", physicalSizeX);
    addGlobalMeta("Nanometers per pixel (Y)", physicalSizeY);
    addGlobalMeta("Magnification", magnification);
    addGlobalMeta("Channel name", channelName);
    addGlobalMeta("Camera name", cameraName);
    addGlobalMeta("Image name", imageName);
    addGlobalMeta("Acquisition date", acquisitionDate);
  }

  /* @see BaseTiffReader#initMetadataStore() */
  protected void initMetadataStore() throws FormatException {
    super.initMetadataStore();
    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);

    store.setImageName(imageName, 0);
    store.setImageAcquiredDate(acquisitionDate, 0);

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      String instrument = MetadataTools.createLSID("Instrument", 0);
      store.setInstrumentID(instrument, 0);
      store.setImageInstrumentRef(instrument, 0);

      String objective = MetadataTools.createLSID("Objective", 0, 0);
      store.setObjectiveID(objective, 0, 0);
      store.setObjectiveNominalMagnification(
        new PositiveInteger((int) magnification), 0, 0);
      store.setObjectiveCorrection(getCorrection("Other"), 0, 0);
      store.setObjectiveImmersion(getImmersion("Other"), 0, 0);
      store.setImageObjectiveSettingsID(objective, 0);

      String detector = MetadataTools.createLSID("Detector", 0, 0);
      store.setDetectorID(detector, 0, 0);
      store.setDetectorModel(cameraName, 0, 0);
      store.setDetectorType(getDetectorType("Other"), 0, 0);
      store.setDetectorSettingsID(detector, 0, 0);

      store.setPixelsPhysicalSizeX(physicalSizeX / 1000, 0);
      store.setPixelsPhysicalSizeY(physicalSizeY / 1000, 0);
      store.setChannelName(channelName, 0, 0);
    }
  }

}
