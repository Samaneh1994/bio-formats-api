//
// ImarisReader.java
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

import java.io.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * ImarisReader is the file format reader for Bitplane Imaris files.
 * Specifications available at
 * http://flash.bitplane.com/support/faqs/faqsview.cfm?inCat=6&inQuestionID=104
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/ImarisReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/ImarisReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class ImarisReader extends FormatReader {

  // -- Constants --

  /** Magic number; present in all files. */
  private static final int IMARIS_MAGIC_NUMBER = 5021964;

  /** Specifies endianness. */
  private static final boolean IS_LITTLE = false;

  // -- Fields --

  /** Offsets to each image. */
  private int[] offsets;

  // -- Constructor --

  /** Constructs a new Imaris reader. */
  public ImarisReader() {
    super("Bitplane Imaris", "ims");
    blockCheckLen = 4;
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    if (!FormatTools.validStream(stream, blockCheckLen, IS_LITTLE)) {
      return false;
    }
    return stream.readInt() == IMARIS_MAGIC_NUMBER;
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

    in.seek(offsets[no] + core.sizeX[0] * (core.sizeY[0] - y - h));

    for (int row=h-1; row>=0; row--) {
      in.skipBytes(x);
      in.read(buf, row*w, w);
      in.skipBytes(core.sizeX[0] - w - x);
    }
    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    offsets = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("ImarisReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessStream(id);

    status("Verifying Imaris RAW format");

    in.order(IS_LITTLE);

    long magic = in.readInt();
    if (magic != IMARIS_MAGIC_NUMBER) {
      throw new FormatException("Imaris magic number not found.");
    }

    status("Reading header");

    addMeta("Version", new Integer(in.readInt()));
    in.skipBytes(4);

    addMeta("Image name", in.readString(128));

    core.sizeX[0] = in.readShort();
    core.sizeY[0] = in.readShort();
    core.sizeZ[0] = in.readShort();

    in.skipBytes(2);

    core.sizeC[0] = in.readInt();
    in.skipBytes(2);

    addMeta("Original date", in.readString(32));

    float dx = in.readFloat();
    float dy = in.readFloat();
    float dz = in.readFloat();
    int mag = in.readShort();

    addMeta("Image comment", in.readString(128));
    int isSurvey = in.readInt();
    addMeta("Survey performed", String.valueOf(isSurvey == 0));

    status("Calculating image offsets");

    core.imageCount[0] = getSizeZ() * getSizeC();
    offsets = new int[getImageCount()];

    float[] gains = new float[getSizeC()];
    float[] detectorOffsets = new float[getSizeC()];
    float[] pinholes = new float[getSizeC()];

    for (int i=0; i<getSizeC(); i++) {
      addMeta("Channel #" + i + " Comment", in.readString(128));
      gains[i] = in.readFloat();
      detectorOffsets[i] = in.readFloat();
      pinholes[i] = in.readFloat();
      in.skipBytes(24);
      int offset = 336 + (164 * getSizeC()) +
        (i * getSizeX() * getSizeY() * getSizeZ());
      for (int j=0; j<getSizeZ(); j++) {
        offsets[i*getSizeZ() + j] = offset + (j * getSizeX() * getSizeY());
      }
    }

    status("Populating metadata");

    core.sizeT[0] = getImageCount() / (getSizeC() * getSizeZ());
    core.currentOrder[0] = "XYZCT";
    core.rgb[0] = false;
    core.interleaved[0] = false;
    core.littleEndian[0] = IS_LITTLE;
    core.indexed[0] = false;
    core.falseColor[0] = false;
    core.metadataComplete[0] = true;

    // The metadata store we're working with.
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);
    MetadataTools.setDefaultCreationDate(store, id, 0);
    core.pixelType[0] = FormatTools.UINT8;
    MetadataTools.populatePixels(store, this);

    store.setDimensionsPhysicalSizeX(new Float(dx), 0, 0);
    store.setDimensionsPhysicalSizeY(new Float(dy), 0, 0);
    store.setDimensionsPhysicalSizeZ(new Float(dz), 0, 0);
    store.setDimensionsTimeIncrement(new Float(1), 0, 0);
    store.setDimensionsWaveIncrement(new Integer(1), 0, 0);

    // CTR CHECK
    for (int i=0; i<getSizeC(); i++) {
      if (pinholes[i] > 0) {
        store.setLogicalChannelPinholeSize(new Integer((int) pinholes[i]),
          0, i);
      }
      //if (gains[i] > 0) {
      //  store.setDetectorSettingsGain(new Float(gains[i]), 0, i);
      //}
      //store.setDetectorSettingsOffset(new Float(offsets[i]), i, 0);
    }

    // CTR CHECK
    //store.setObjectiveCalibratedMagnification(new Float(mag), 0, 0);
  }

}
