//
// GatanReader.java
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
import java.util.StringTokenizer;
import java.util.Vector;

import loci.common.DataTools;
import loci.common.RandomAccessInputStream;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * GatanReader is the file format reader for Gatan files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/GatanReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/GatanReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class GatanReader extends FormatReader {

  // -- Constants --

  public static final int DM3_MAGIC_BYTES = 3;

  /** Tag types. */
  private static final int GROUP = 20;
  private static final int VALUE = 21;

  /** Data types. */
  private static final int ARRAY = 15;
  private static final int SHORT = 2;
  private static final int USHORT = 4;
  private static final int INT = 3;
  private static final int UINT = 5;
  private static final int FLOAT = 6;
  private static final int DOUBLE = 7;
  private static final int BYTE = 8;
  private static final int UBYTE = 9;
  private static final int CHAR = 10;

  // -- Fields --

  /** Offset to pixel data. */
  private long pixelOffset;

  /** List of pixel sizes. */
  private Vector<String> pixelSizes;

  private int bytesPerPixel;

  private int pixelDataNum = 0;
  private int numPixelBytes;

  private boolean signed;
  private long timestamp;
  private double gamma, mag, voltage;
  private String info;

  // -- Constructor --

  /** Constructs a new Gatan reader. */
  public GatanReader() {
    super("Gatan Digital Micrograph", "dm3");
    domains = new String[] {FormatTools.EM_DOMAIN};
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    final int blockLen = 4;
    if (!FormatTools.validStream(stream, blockLen, false)) return false;
    return stream.readInt() == DM3_MAGIC_BYTES;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    in.seek(pixelOffset);
    readPlane(in, x, y, w, h, buf);

    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      pixelOffset = 0;
      bytesPerPixel = pixelDataNum = numPixelBytes = 0;
      pixelSizes = null;
      signed = false;
      timestamp = 0;
      gamma = mag = voltage = 0;
      info = null;
    }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    debug("GatanReader.initFile(" + id + ")");
    super.initFile(id);
    in = new RandomAccessInputStream(id);
    pixelOffset = 0;

    status("Verifying Gatan format");

    core[0].littleEndian = false;
    pixelSizes = new Vector<String>();

    in.order(false);

    // only support version 3
    if (in.readInt() != 3) {
      throw new FormatException("invalid header");
    }

    status("Reading tags");

    in.skipBytes(4);
    core[0].littleEndian = in.readInt() == 1;
    in.order(!isLittleEndian());

    // TagGroup instance

    in.skipBytes(2);
    int numTags = in.readInt();
    debug("tags (" + numTags + ") {");
    parseTags(numTags, null, "  ");
    debug("}");

    status("Populating metadata");

    core[0].littleEndian = true;

    if (getSizeX() == 0 || getSizeY() == 0) {
      throw new FormatException("Dimensions information not found");
    }
    int bytes = numPixelBytes / (getSizeX() * getSizeY());

    switch (bytes) {
      case 1:
        core[0].pixelType = signed ? FormatTools.INT8 : FormatTools.UINT8;
        break;
      case 2:
        core[0].pixelType = signed ? FormatTools.INT16 : FormatTools.UINT16;
        break;
      case 4:
        core[0].pixelType = signed ? FormatTools.INT32 : FormatTools.UINT32;
        break;
      default:
        throw new FormatException("Unsupported pixel type");
    }

    core[0].sizeZ = 1;
    core[0].sizeC = 1;
    core[0].sizeT = 1;
    core[0].dimensionOrder = "XYZTC";
    core[0].imageCount = 1;
    core[0].rgb = false;
    core[0].interleaved = false;
    core[0].metadataComplete = true;
    core[0].indexed = false;
    core[0].falseColor = false;

    // The metadata store we're working with.
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);
    MetadataTools.setDefaultCreationDate(store, id, 0);

    Double pixX = new Double(1);
    Double pixY = new Double(1);
    Double pixZ = new Double(1);

    if (pixelSizes.size() > 0) {
      pixX = new Double(pixelSizes.get(0));
    }

    if (pixelSizes.size() > 1) {
      pixY = new Double(pixelSizes.get(1));
    }

    if (pixelSizes.size() > 2) {
      pixZ = new Double(pixelSizes.get(2));
    }

    store.setDimensionsPhysicalSizeX(pixX, 0, 0);
    store.setDimensionsPhysicalSizeY(pixY, 0, 0);
    store.setDimensionsPhysicalSizeZ(pixZ, 0, 0);

    for (int i=0; i<getSizeC(); i++) {
      // CTR CHECK
//      store.setDisplayChannel(new Integer(i), null, null,
//        new Double(gamma), null);
    }

    // CTR CHECK
    //store.setObjectiveCalibratedMagnification(new Double(mag), 0, 0);
    //store.setDetectorVoltage(new Double(voltage), 0, 0);

    if (info == null) info = "";
    StringTokenizer scopeInfo = new StringTokenizer(info, "(");
    while (scopeInfo.hasMoreTokens()) {
      String token = scopeInfo.nextToken().trim();
      if (token.startsWith("Microscope")) {
        //token = token.substring(0, token.indexOf(" ")).trim();
        //store.setMicroscopeManufacturer(
        //  token.substring(token.indexOf(" ")).trim(), 0, 0);
        //store.setMicroscopeModel(token, 0, 0);
      }
      else if (token.startsWith("Mode")) {
        token = token.substring(token.indexOf(" ")).trim();
        String mode = token.substring(0, token.indexOf(" ")).trim();
        if (mode.equals("TEM")) mode = "Other";
        store.setLogicalChannelMode(mode, 0, 0);
      }
    }
  }

  // -- Helper methods --

  /**
   * Parses Gatan DM3 tags.
   * Information on the DM3 structure found at:
   * http://rsb.info.nih.gov/ij/plugins/DM3Format.gj.html and
   * http://www-hrem.msm.cam.ac.uk/~cbb/info/dmformat/
   *
   * The basic structure is this: the file is comprised of a list of tags.
   * Each tag is either a data tag or a group tag.  Group tags are simply
   * containers for more group and data tags, where data tags contain actual
   * metadata.  Each data tag is comprised of a type (byte, short, etc.),
   * a label, and a value.
   */
  private void parseTags(int numTags, String parent, String indent)
    throws FormatException, IOException
  {
    for (int i=0; i<numTags; i++) {
      byte type = in.readByte();  // can be 21 (data) or 20 (tag group)
      int length = in.readShort();

      // image data is in tag with type 21 and label 'Data'
      // image dimensions are in type 20 tag with 2 type 15 tags
      // bytes per pixel is in type 21 tag with label 'PixelDepth'

      String labelString = null;
      String value = null;

      if (type == VALUE) {
        labelString = in.readString(length);
        int skip = in.readInt(); // equal to '%%%%' / 623191333
        int n = in.readInt();
        int dataType = in.readInt();
        String sb = labelString;
        if (sb.length() > 32) {
          sb = sb.substring(0, 20) + "... (" + sb.length() + ")";
        }
        debug(indent + i + ": n=" + n +
          ", dataType=" + dataType + ", label=" + sb);
        if (skip != 623191333) warn("Skip mismatch: " + skip);
        if (n == 1) {
          if ("Dimensions".equals(parent) && labelString.length() == 0) {
            in.order(!in.isLittleEndian());
            if (i == 0) core[0].sizeX = in.readInt();
            else if (i == 1) core[0].sizeY = in.readInt();
            in.order(!in.isLittleEndian());
          }
          else value = String.valueOf(readValue(dataType));
        }
        else if (n == 2) {
          if (dataType == 18) { // this should always be true
            length = in.readInt();
          }
          else warn("dataType mismatch: " + dataType);
          value = in.readString(length);
        }
        else if (n == 3) {
          if (dataType == GROUP) {  // this should always be true
            dataType = in.readInt();
            length = in.readInt();
            if (labelString.equals("Data")) {
              pixelOffset = in.getFilePointer();
              in.skipBytes(getNumBytes(dataType) * length);
              numPixelBytes = (int) (in.getFilePointer() - pixelOffset);
            }
            else {
              if (dataType == 10) in.skipBytes(length);
              else value = DataTools.stripString(in.readString(length * 2));
            }
          }
          else warn("dataType mismatch: " + dataType);
        }
        else {
          // this is a normal struct of simple types
          if (dataType == ARRAY) {
            in.skipBytes(4);
            int numFields = in.readInt();
            StringBuffer s = new StringBuffer();
            in.skipBytes(4);
            for (int j=0; j<numFields; j++) {
              dataType = in.readInt();
              s.append(readValue(dataType));
              if (j < numFields - 1) s.append(", ");
            }
            value = s.toString();
            boolean lastTag = parent == null && i == numTags - 1;
            if (!lastTag) {
              // search for next tag
              // empirically, we need to skip 4, 12, 18 or 28 total bytes
              byte b = 0;
              final int[] jumps = {4, 7, 5, 9};
              for (int j=0; j<jumps.length; j++) {
                in.skipBytes(jumps[j]);
                b = in.readByte();
                if (b == GROUP || b == VALUE) break;
              }
              if (b != GROUP && b != VALUE) {
                throw new FormatException("Cannot find next tag (pos=" +
                  in.getFilePointer() + ", label=" + labelString + ")");
              }
              in.seek(in.getFilePointer() - 1); // reread tag type code
            }
          }
          else if (dataType == GROUP) {
            // this is an array of structs
            dataType = in.readInt();
            if (dataType == ARRAY) { // should always be true
              in.skipBytes(4);
              int numFields = in.readInt();
              int[] dataTypes = new int[numFields];
              for (int j=0; j<numFields; j++) {
                in.skipBytes(4);
                dataTypes[j] = in.readInt();
              }
              int len = in.readInt();

              double[][] values = new double[numFields][len];

              for (int k=0; k<len; k++) {
                for (int q=0; q<numFields; q++) {
                  values[q][k] = readValue(dataTypes[q]);
                }
              }
            }
            else warn("dataType mismatch: " + dataType);
          }
        }
      }
      else if (type == GROUP) {
        labelString = in.readString(length);
        in.skipBytes(2);
        int num = in.readInt();
        debug(indent + i + ": group (" + num + ") {");
        parseTags(num, labelString, indent + "  ");
        debug(indent + "}");
      }
      else debug(indent + i + ": unknown type: " + type);

      if (value != null) {
        addGlobalMeta(labelString, value);

        if (labelString.equals("Scale")) {
          if (value.indexOf(",") == -1) pixelSizes.add(value);
          else {
            double start =
              Double.parseDouble(value.substring(0, value.indexOf(",")));
            double end =
              Double.parseDouble(value.substring(value.indexOf(",") + 2));
            pixelSizes.add(String.valueOf(end - start));
          }
        }
        else if (labelString.equals("LowLimit")) {
          signed = Double.parseDouble(value) < 0;
        }
        else if (labelString.equals("Acquisition Start Time (epoch)")) {
          timestamp = (long) Double.parseDouble(value);
        }
        else if (labelString.equals("Voltage")) {
          voltage = Double.parseDouble(value);
        }
        else if (labelString.equals("Microscope Info")) info = value;
        else if (labelString.equals("Indicated Magnification")) {
          mag = Double.parseDouble(value);
        }
        else if (labelString.equals("Gamma")) gamma = Double.parseDouble(value);

        value = null;
      }
    }
  }

  private double readValue(int type) throws IOException {
    switch (type) {
      case SHORT:
      case USHORT:
        return in.readShort();
      case INT:
      case UINT:
        return in.readInt();
      case FLOAT:
        in.order(!in.isLittleEndian());
        float f = in.readFloat();
        in.order(!in.isLittleEndian());
        return f;
      case DOUBLE:
        in.order(!in.isLittleEndian());
        double dbl = in.readDouble();
        in.order(!in.isLittleEndian());
        return dbl;
      case BYTE:
      case UBYTE:
      case CHAR:
        return in.readByte();
    }
    return 0;
  }

  private int getNumBytes(int type) {
    switch (type) {
      case SHORT:
      case USHORT:
        return 2;
      case INT:
      case UINT:
      case FLOAT:
        return 4;
      case DOUBLE:
        return 8;
      case BYTE:
      case UBYTE:
      case CHAR:
        return 1;
    }
    return 0;
  }

}
