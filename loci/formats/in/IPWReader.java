//
// IPWReader.java
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
import java.text.*;
import java.util.*;
import loci.formats.*;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * IPWReader is the file format reader for Image-Pro Workspace (IPW) files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/IPWReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/IPWReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class IPWReader extends FormatReader {

  // -- Fields --

  private Vector imageFiles;

  private POITools poi;

  // -- Constructor --

  /** Constructs a new IPW reader. */
  public IPWReader() { super("Image-Pro Workspace", "ipw"); }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    return block[0] == 0xd0 && block[1] == 0xcf &&
      block[2] == 0x11 && block[3] == 0xe0;
  }

  /* @see loci.formats.IFormatReader#get8BitLookupTable() */
  public byte[][] get8BitLookupTable() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    RandomAccessStream stream =
      poi.getDocumentStream((String) imageFiles.get(0));
    Hashtable[] ifds = TiffTools.getIFDs(stream);
    int[] bits = TiffTools.getBitsPerSample(ifds[0]);
    if (bits[0] <= 8) {
      int[] colorMap =
        (int[]) TiffTools.getIFDValue(ifds[0], TiffTools.COLOR_MAP);
      if (colorMap == null) return null;

      byte[][] table = new byte[3][colorMap.length / 3];
      int next = 0;
      for (int j=0; j<table.length; j++) {
        for (int i=0; i<table[0].length; i++) {
          table[j][i] = (byte) (colorMap[next++] >> 8);
        }
      }

      return table;
    }
    return null;
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

    RandomAccessStream stream =
      poi.getDocumentStream((String) imageFiles.get(no));
    Hashtable[] ifds = TiffTools.getIFDs(stream);
    TiffTools.getSamples(ifds[0], stream, buf, x, y, w, h);
    stream.close();
    return buf;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();

    if (poi != null) poi.close();
    poi = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("IPWReader.initFile(" + id + ")");
    super.initFile(id);

    in = new RandomAccessStream(id);
    poi = new POITools(Location.getMappedId(currentId));

    imageFiles = new Vector();

    Vector fileList = poi.getDocumentList();

    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);

    for (int i=0; i<fileList.size(); i++) {
      String name = (String) fileList.get(i);
      String relativePath =
        name.substring(name.lastIndexOf(File.separator) + 1);

      if (relativePath.equals("CONTENTS")) {
        addMeta("Version", new String(poi.getDocumentBytes(name)).trim());
      }
      else if (relativePath.equals("FrameRate")) {
        byte[] b = poi.getDocumentBytes(name, 4);
        addMeta("Frame Rate", new Integer(DataTools.bytesToInt(b, true)));
      }
      else if (relativePath.equals("FrameInfo")) {
        byte[] b = poi.getDocumentBytes(name);
        for (int q=0; q<b.length/2; q++) {
          addMeta("FrameInfo " + q,
            new Short(DataTools.bytesToShort(b, q*2, 2, true)));
        }
      }
      else if (relativePath.equals("ImageInfo")) {
        String description = new String(poi.getDocumentBytes(name)).trim();
        addMeta("Image Description", description);

        String timestamp = null;

        // parse the description to get channels/slices/times where applicable
        // basically the same as in SEQReader
        if (description != null) {
          StringTokenizer tokenizer = new StringTokenizer(description, "\n");
          while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            String label = "Timestamp";
            String data;
            if (token.indexOf("=") != -1) {
              label = token.substring(0, token.indexOf("=")).trim();
              data = token.substring(token.indexOf("=") + 1).trim();
            }
            else data = token.trim();
            addMeta(label, data);
            if (label.equals("frames")) core.sizeZ[0] = Integer.parseInt(data);
            else if (label.equals("slices")) {
              core.sizeT[0] = Integer.parseInt(data);
            }
            else if (label.equals("channels")) {
              core.sizeC[0] = Integer.parseInt(data);
            }
            else if (label.equals("Timestamp")) timestamp = data;
          }
        }
        store.setImageDescription(description, 0);

        if (timestamp != null) {
          if (timestamp.length() > 26) {
            timestamp = timestamp.substring(timestamp.length() - 26);
          }
          SimpleDateFormat fmt =
            new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS aa");
          Date d = fmt.parse(timestamp, new ParsePosition(0));
          fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
          store.setImageCreationDate(fmt.format(d), 0);
        }
        else {
          MetadataTools.setDefaultCreationDate(store, id, 0);
        }
      }
      else if (relativePath.equals("ImageTIFF")) {
        // pixel data
        String idx = "0";
        if (!name.substring(0,
          name.lastIndexOf(File.separator)).equals("Root Entry"))
        {
          idx = name.substring(21, name.indexOf(File.separator, 22));
        }

        int n = Integer.parseInt(idx);
        if (n < imageFiles.size()) imageFiles.setElementAt(name, n);
        else {
          int diff = n - imageFiles.size();
          for (int q=0; q<diff; q++) {
            imageFiles.add("");
          }
          imageFiles.add(name);
        }
        core.imageCount[0]++;
      }
    }

    status("Populating metadata");

    RandomAccessStream stream =
      poi.getDocumentStream((String) imageFiles.get(0));
    Hashtable[] ifds = TiffTools.getIFDs(stream);
    stream.close();

    core.rgb[0] = (TiffTools.getIFDIntValue(ifds[0],
      TiffTools.SAMPLES_PER_PIXEL, false, 1) > 1);

    if (!isRGB()) {
      core.indexed[0] = TiffTools.getIFDIntValue(ifds[0],
        TiffTools.PHOTOMETRIC_INTERPRETATION, false, 1) ==
        TiffTools.RGB_PALETTE;
    }
    if (isIndexed()) {
      core.sizeC[0] = 1;
      core.rgb[0] = false;
    }

    core.littleEndian[0] = TiffTools.isLittleEndian(ifds[0]);

    // retrieve axis sizes

    addMeta("slices", "1");
    addMeta("channels", "1");
    addMeta("frames", new Integer(getImageCount()));

    Hashtable h = ifds[0];
    core.sizeX[0] = TiffTools.getIFDIntValue(h, TiffTools.IMAGE_WIDTH);
    core.sizeY[0] = TiffTools.getIFDIntValue(h, TiffTools.IMAGE_LENGTH);
    core.currentOrder[0] = isRGB() ? "XYCTZ" : "XYTCZ";

    if (getSizeZ() == 0) core.sizeZ[0] = 1;
    if (getSizeC() == 0) core.sizeC[0] = 1;
    if (getSizeT() == 0) core.sizeT[0] = 1;

    if (getSizeZ() * getSizeC() * getSizeT() == 1 && getImageCount() != 1) {
      core.sizeZ[0] = getImageCount();
    }

    if (isRGB()) core.sizeC[0] *= 3;

    int bitsPerSample = TiffTools.getIFDIntValue(ifds[0],
      TiffTools.BITS_PER_SAMPLE);
    int bitFormat = TiffTools.getIFDIntValue(ifds[0], TiffTools.SAMPLE_FORMAT);

    while (bitsPerSample % 8 != 0) bitsPerSample++;
    if (bitsPerSample == 24 || bitsPerSample == 48) bitsPerSample /= 3;

    core.pixelType[0] = FormatTools.UINT8;

    if (bitFormat == 3) core.pixelType[0] = FormatTools.FLOAT;
    else if (bitFormat == 2) {
      switch (bitsPerSample) {
        case 8:
          core.pixelType[0] = FormatTools.INT8;
          break;
        case 16:
          core.pixelType[0] = FormatTools.INT16;
          break;
        case 32:
          core.pixelType[0] = FormatTools.INT32;
          break;
      }
    }
    else {
      switch (bitsPerSample) {
        case 8:
          core.pixelType[0] = FormatTools.UINT8;
          break;
        case 16:
          core.pixelType[0] = FormatTools.UINT16;
          break;
        case 32:
          core.pixelType[0] = FormatTools.UINT32;
          break;
      }
    }

    MetadataTools.populatePixels(store, this);
  }

}
