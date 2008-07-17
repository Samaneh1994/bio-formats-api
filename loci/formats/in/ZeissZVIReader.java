//
// ZeissZVIReader.java
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
import loci.formats.codec.JPEGCodec;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;

/**
 * ZeissZVIReader is the file format reader for Zeiss ZVI files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/ZeissZVIReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/ZeissZVIReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class ZeissZVIReader extends FormatReader {

  // -- Fields --

  /** Number of bytes per pixel. */
  private int bpp;

  private Vector imageFiles;

  /** Vector containing Z indices. */
  private Vector zIndices;

  /** Vector containing C indices. */
  private Vector cIndices;

  /** Vector containing T indices. */
  private Vector tIndices;

  private Hashtable offsets;
  private Hashtable coordinates;

  private int zIndex = -1, cIndex = -1, tIndex = -1;

  private boolean isTiled;
  private int tileRows, tileColumns;
  private boolean isJPEG;

  private String firstImageTile, secondImageTile;
  private float pixelSizeX, pixelSizeY, pixelSizeZ;
  private String microscopeName;
  private Vector emWave, exWave;
  private Hashtable channelNames;
  private Vector whiteValue, blackValue, gammaValue, exposureTime, timestamps;
  private String userName, userCompany, mag, na;

  private int tileWidth, tileHeight;
  private int realWidth, realHeight;

  private POITools poi;

  // -- Constructor --

  /** Constructs a new ZeissZVI reader. */
  public ZeissZVIReader() {
    super("Zeiss Vision Image (ZVI)", "zvi");
    blockCheckLen = 4;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessStream) */
  public boolean isThisType(RandomAccessStream stream) throws IOException {
    if (!FormatTools.validStream(stream, blockCheckLen, false)) return false;
    return stream.readInt() == 0xd0cf11e0;
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

    int bytes = FormatTools.getBytesPerPixel(getPixelType());

    if (tileRows * tileColumns == 0 || tileWidth * tileHeight == 0) {
      RandomAccessStream s = poi.getDocumentStream((String) imageFiles.get(no));
      s.seek(((Integer) offsets.get(new Integer(no))).intValue());

      int len = w * bytes * getRGBChannelCount();

      if (isJPEG) {
        byte[] t = new byte[(int) (s.length() - s.getFilePointer())];
        s.read(t);
        t = new JPEGCodec().decompress(t, new Object[] {
          new Boolean(isLittleEndian()), new Boolean(isInterleaved())});

        int row = getSizeX() * bytes * getRGBChannelCount();

        for (int yy=0; yy<h; yy++) {
          System.arraycopy(t, (yy + y) * row + x * bytes * getRGBChannelCount(),
            buf, yy*len, len);
        }
      }
      else {
        DataTools.readPlane(s, x, y, w, h, this, buf);
      }
      s.close();
    }
    else {
      // determine which tiles to read

      int firstTile = no * tileRows * tileColumns;

      int rowOffset = 0;
      int colOffset = 0;

      byte[] tile = new byte[tileWidth * tileHeight * bytes];

      for (int row=0; row<tileRows; row++) {
        for (int col=0; col<tileColumns; col++) {
          int rowIndex = row * tileHeight;
          int colIndex = col * tileWidth;

          if ((rowIndex <= y && rowIndex + tileHeight >= y) ||
            (rowIndex > y && rowIndex <= y + h))
          {
            if ((colIndex <= x && colIndex + tileWidth >= x) ||
              (colIndex > x && colIndex <= x + w))
            {
              // read this tile
              int tileX = colIndex < x ? x - colIndex : 0;
              int tileY = rowIndex < y ? y - rowIndex : 0;
              int tileW = colIndex + tileWidth <= x + w ?
                tileWidth - tileX : x + w - colIndex -  tileX;
              int tileH = rowIndex + tileHeight <= y + h ?
                tileHeight - tileY : y + h - rowIndex - tileY;

              int count = getSizeZ() * getSizeT() * getSizeC();
              int ii = no + (row*tileColumns + col)*count;
              if ((row % 2) == 1) {
                ii = no + (row*tileColumns + (tileColumns - col - 1))*count;
              }

              RandomAccessStream s =
                poi.getDocumentStream((String) imageFiles.get(ii));
              s.skipBytes(
                ((Integer) offsets.get(new Integer(ii))).intValue());
              s.read(tile);
              if (isJPEG) {
                tile = new JPEGCodec().decompress(tile,
                  new Object[] {new Boolean(isLittleEndian()),
                  new Boolean(isInterleaved())});
              }
              s.close();

              for (int r=tileY; r<tileY + tileH; r++) {
                int src = bytes * (r * tileWidth + tileX);
                int dest = bytes * (w * (rowOffset + r - tileY) + colOffset);
                int len = tileW * bytes;
                System.arraycopy(tile, src, buf, dest, len);
              }

              colOffset += tileW;
              if (colOffset >= w) {
                colOffset = 0;
                rowOffset += tileH;
              }
            }
          }
        }
      }
    }

    if (bpp > 6) bpp = 1;
    if (bpp == 3 || bpp == 6) {
      // reverse bytes in groups of 3 to account for BGR storage
      byte[] bb = new byte[bpp / 3];
      int bp = bpp / 3;
      for (int i=0; i<buf.length; i+=bpp) {
        System.arraycopy(buf, i + 2*bp, bb, 0, bp);
        System.arraycopy(buf, i, buf, i + 2*bp, bp);
        System.arraycopy(bb, 0, buf, i, bp);
      }
    }
    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    if (fileOnly) {
      if (in != null) in.close();
    }
    else close();
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();

    offsets = coordinates = null;
    imageFiles = zIndices = cIndices = tIndices = null;
    bpp = tileRows = tileColumns = 0;
    zIndex = cIndex = tIndex = -1;
    isTiled = isJPEG = false;
    if (poi != null) poi.close();
    poi = null;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("ZeissZVIReader.initFile(" + id + ")");
    super.initFile(id);

    imageFiles = new Vector();
    offsets = new Hashtable();
    coordinates = new Hashtable();
    zIndices = new Vector();
    cIndices = new Vector();
    tIndices = new Vector();

    emWave = new Vector();
    exWave = new Vector();
    channelNames = new Hashtable();
    whiteValue = new Vector();
    blackValue = new Vector();
    gammaValue = new Vector();
    exposureTime = new Vector();
    timestamps = new Vector();

    poi = new POITools(Location.getMappedId(id));

    // parse each embedded file

    Vector files = poi.getDocumentList();

    for (int i=0; i<files.size(); i++) {
      String name = (String) files.get(i);
      String relPath = name.substring(name.lastIndexOf(File.separator) + 1);
      String dirName = name.substring(0, name.lastIndexOf(File.separator));
      if (dirName.indexOf(File.separator) != -1) {
        dirName = dirName.substring(dirName.lastIndexOf(File.separator) + 1);
      }

      boolean isContents = relPath.toUpperCase().equals("CONTENTS");

      RandomAccessStream s = poi.getDocumentStream(name);
      s.order(true);

      if (name.indexOf("Scaling") == -1 && dirName.equals("Tags") &&
        isContents)
      {
        int imageNum = -1;
        if (name.toUpperCase().indexOf("ITEM") != -1) {
          String num = name.substring(name.indexOf("(") + 1);
          num = num.substring(0, num.indexOf(")"));
          imageNum = Integer.parseInt(num);
        }
        try { parseTags(imageNum, s); }
        catch (EOFException e) { }
      }
      else if (isContents && (dirName.equals("Image") ||
        dirName.toUpperCase().indexOf("ITEM") != -1) &&
        (s.length() > 1024))
      {
        // found a valid image stream
        try {
          s.seek(6);

          int vt = s.readShort();
          if (vt == 3) {
            s.skipBytes(6);
          }
          else if (vt == 8) {
            int l = s.readShort();
            s.skipBytes(l + 2);
          }
          int len = s.readShort();
          if (s.readShort() != 0) s.seek(s.getFilePointer() - 2);

          if (s.getFilePointer() + len <= s.length()) {
            s.skipBytes(len);
          }
          else break;

          vt = s.readShort();
          if (vt == 8) {
            len = s.readInt();
            s.skipBytes(len + 2);
          }

          int tw = s.readInt();
          if (getSizeX() == 0 || (tw < getSizeX() && tw > 0)) {
            core.sizeX[0] = tw;
          }
          s.skipBytes(2);
          int th = s.readInt();
          if (getSizeY() == 0 || (th < getSizeY() && th > 0)) {
            core.sizeY[0] = th;
          }

          s.skipBytes(14);

          int numImageContainers = s.readInt();
          s.skipBytes(6);

          // VT_CLSID - PluginCLSID
          while (s.readShort() != 65);

          // VT_BLOB - Others
          len = s.readInt();
          s.skipBytes(len);

          // VT_STORED_OBJECT - Layers
          s.skipBytes(2);
          long old = s.getFilePointer();
          len = s.readInt();

          s.skipBytes(8);

          int zidx = s.readInt();
          int cidx = s.readInt();
          int tidx = s.readInt();

          Integer zndx = new Integer(zidx);
          Integer cndx = new Integer(cidx);
          Integer tndx = new Integer(tidx);

          if (!zIndices.contains(zndx)) zIndices.add(zndx);
          if (!cIndices.contains(cndx)) cIndices.add(cndx);
          if (!tIndices.contains(tndx)) tIndices.add(tndx);

          s.seek(old + len + 4);

          boolean foundWidth = s.readInt() == getSizeX();
          boolean foundHeight = s.readInt() == getSizeY();
          boolean findFailed = false;
          while ((!foundWidth || !foundHeight) &&
            s.getFilePointer() + 1 < s.length())
          {
            s.seek(s.getFilePointer() - 7);
            foundWidth = s.readInt() == getSizeX();
            foundHeight = s.readInt() == getSizeY();
          }
          s.seek(s.getFilePointer() - 16);
          findFailed = !foundWidth && !foundHeight;

          // image header and data

          if (dirName.toUpperCase().indexOf("ITEM") != -1 ||
            (dirName.equals("Image") && numImageContainers == 0))
          {
            if (findFailed) s.seek(old + len + 92);
            long fp = s.getFilePointer();
            byte[] o = new byte[(int) (s.length() - fp)];
            s.read(o);

            int imageNum = 0;
            if (dirName.toUpperCase().indexOf("ITEM") != -1) {
              String num = dirName.substring(dirName.indexOf("(") + 1);
              num = num.substring(0, num.length() - 1);
              imageNum = Integer.parseInt(num);
            }

            coordinates.put(new Integer(imageNum),
              new int[] {zidx, cidx, tidx});

            offsets.put(new Integer(imageNum), new Integer((int) fp + 32));
            parsePlane(o, imageNum, name);
          }
        }
        catch (EOFException exc) { }
      }
      else {
        try { parseTags(-1, s); }
        catch (IOException e) { }
      }
    }

    status("Populating metadata");

    core.rgb[0] = getSizeC() > 1 &&
      (getSizeZ() * getSizeC() * getSizeT() != getImageCount());
    core.littleEndian[0] = true;
    core.interleaved[0] = !isJPEG;
    core.indexed[0] = false;
    core.falseColor[0] = false;
    core.metadataComplete[0] = true;

    core.sizeZ[0] = zIndices.size();
    core.sizeT[0] = tIndices.size();

    if (channelNames.size() > 0) core.sizeC[0] *= channelNames.size();
    else core.sizeC[0] *= cIndices.size();

    core.imageCount[0] = getSizeZ() * getSizeT() *
      (isRGB() ? getSizeC() / 3 : getSizeC());

    if (isTiled) {
      // calculate tile dimensions and number of tiles
      int totalTiles = offsets.size() / (getSizeZ() * getSizeC() * getSizeT());

      tileRows = (realHeight / getSizeY()) + 1;
      tileColumns = (realWidth / getSizeX()) + 1;

      if (totalTiles <= 1) {
        tileRows = 1;
        tileColumns = 1;
        totalTiles = 1;
        isTiled = false;
      }

      while (tileRows * tileColumns > totalTiles) {
        totalTiles = offsets.size() / (getSizeZ() * getSizeC() * getSizeT());
        core.imageCount[0] -= getSizeZ() * getSizeT();
      }

      if (tileRows == 0) tileRows = 1;
      if (tileColumns == 0) tileColumns = 1;

      if (tileColumns == 1 && tileRows == 1) isTiled = false;
      else {
        tileWidth = getSizeX();
        tileHeight = getSizeY();
        core.sizeX[0] = tileWidth * tileColumns;
        core.sizeY[0] = tileHeight * tileRows;
      }
    }

    core.currentOrder[0] = "XY";
    for (int i=0; i<coordinates.size()-1; i++) {
      int[] zct1 = (int[]) coordinates.get(new Integer(i));
      int[] zct2 = (int[]) coordinates.get(new Integer(i + 1));
      int deltaZ = zct2[0] - zct1[0];
      int deltaC = zct2[1] - zct1[1];
      int deltaT = zct2[2] - zct1[2];
      if (deltaZ > 0 && getDimensionOrder().indexOf("Z") == -1) {
        core.currentOrder[0] += "Z";
      }
      if (deltaC > 0 && getDimensionOrder().indexOf("C") == -1) {
        core.currentOrder[0] += "C";
      }
      if (deltaT > 0 && getDimensionOrder().indexOf("T") == -1) {
        core.currentOrder[0] += "T";
      }
    }
    if (getDimensionOrder().indexOf("C") == -1) {
      core.currentOrder[0] += "C";
    }
    if (getDimensionOrder().indexOf("Z") == -1) {
      core.currentOrder[0] += "Z";
    }
    if (getDimensionOrder().indexOf("T") == -1) {
      core.currentOrder[0] += "T";
    }

    // rearrange axis sizes, if necessary

    int lastZ = zIndices.size() == 0 ? Integer.MAX_VALUE :
      ((Integer) zIndices.get(zIndices.size() - 1)).intValue();
    int lastT = tIndices.size() == 0 ? Integer.MAX_VALUE :
      ((Integer) tIndices.get(tIndices.size() - 1)).intValue();

    if ((zIndex > lastZ || tIndex > lastT) && (zIndex == getSizeC() - 1 ||
      tIndex == getSizeC() - 1 ||
      (zIndex != 0 && zIndex % getSizeC() == 0) ||
      (tIndex != 0 && tIndex % getSizeC() == 0)) && zIndex != lastT)
    {
      if (zIndex >= getSizeZ() || tIndex >= getSizeT()) {
        int tmp = getSizeZ();
        core.sizeZ[0] = getSizeT();
        core.sizeT[0] = tmp;
      }
    }

    try {
      initMetadata();
    }
    catch (FormatException exc) {
      if (debug) trace(exc);
    }
    catch (IOException exc) {
      if (debug) trace(exc);
    }
  }

  // -- Helper methods --

  /** Initialize metadata hashtable and OME-XML structure. */
  private void initMetadata() throws FormatException, IOException {
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    store.setImageName("", 0);
    MetadataTools.setDefaultCreationDate(store, getCurrentFile(), 0);

    if (bpp == 1 || bpp == 3) core.pixelType[0] = FormatTools.UINT8;
    else if (bpp == 2 || bpp == 6) core.pixelType[0] = FormatTools.UINT16;

    MetadataTools.populatePixels(store, this);

    store.setDimensionsPhysicalSizeX(new Float(pixelSizeX), 0, 0);
    store.setDimensionsPhysicalSizeY(new Float(pixelSizeY), 0, 0);
    store.setDimensionsPhysicalSizeZ(new Float(pixelSizeZ), 0, 0);

    // CTR CHECK
//    store.setInstrumentModel(microscopeName, 0);

    // trim metadata arrays
    String[] s = (String[]) exposureTime.toArray(new String[0]);
    exposureTime.clear();
    for (int i=0; i<s.length; i++) {
      if (!s[i].trim().equals("")) exposureTime.add(s[i]);
    }

    s = (String[]) timestamps.toArray(new String[0]);
    timestamps.clear();
    for (int i=0; i<s.length; i++) {
      if (!s[i].trim().equals("")) timestamps.add(s[i]);
    }

    s = (String[]) emWave.toArray(new String[0]);
    emWave.clear();
    for (int i=0; i<s.length; i++) {
      if (!s[i].trim().equals("")) emWave.add(s[i]);
    }

    s = (String[]) exWave.toArray(new String[0]);
    exWave.clear();
    for (int i=0; i<s.length; i++) {
      if (!s[i].trim().equals("")) exWave.add(s[i]);
    }

    String[] channelNumbers =
      (String[]) channelNames.keySet().toArray(new String[0]);
    Arrays.sort(channelNumbers);

    for (int i=0; i<getSizeC(); i++) {
      int idx = i % getEffectiveSizeC();
      String em = idx < emWave.size() ? (String) emWave.get(idx) : null;
      String ex = idx < exWave.size() ? (String) exWave.get(idx) : null;

      if (em != null && em.indexOf(".") != -1) {
        em = em.substring(0, em.indexOf("."));
      }
      if (ex != null && ex.indexOf(".") != -1) {
        ex = ex.substring(0, ex.indexOf("."));
      }
      if (em != null && !em.trim().equals("")) {
        store.setLogicalChannelEmWave(new Integer(em), 0, idx);
      }
      if (ex != null && !ex.trim().equals("")) {
        store.setLogicalChannelExWave(new Integer(ex), 0, idx);
      }
      if (idx < channelNumbers.length) {
        store.setLogicalChannelName(
          (String) channelNames.get(channelNumbers[idx]), 0, idx);
      }

      String black =
        idx < blackValue.size() ? (String) blackValue.get(idx) : null;
      String white =
        idx < whiteValue.size() ? (String) whiteValue.get(idx) : null;
      String gamma =
        idx < gammaValue.size() ? (String) gammaValue.get(idx) : null;

      Double blackVal = null, whiteVal = null;
      Float gammaVal = null;

      try { blackVal = new Double(black); }
      catch (NumberFormatException e) { }
      catch (NullPointerException e) { }
      try { whiteVal = new Double(white); }
      catch (NumberFormatException e) { }
      catch (NullPointerException e) { }
      try { gammaVal = new Float(gamma); }
      catch (NumberFormatException e) { }
      catch (NullPointerException e) { }

      // CTR CHECK
//      store.setDisplayChannel(new Integer(i), blackVal, whiteVal,
//        gammaVal, null);
    }

    long firstStamp = 0;
    if (timestamps.size() > 0) {
      firstStamp = Long.parseLong((String) timestamps.get(0));
      store.setImageCreationDate(DataTools.convertDate(
        (long) (firstStamp / 1600), DataTools.ZVI), 0);
    }
    else {
      MetadataTools.setDefaultCreationDate(store, getCurrentFile(), 0);
    }

    for (int plane=0; plane<getImageCount(); plane++) {
      int[] zct = FormatTools.getZCTCoords(this, plane);
      String exposure =
        zct[1] < exposureTime.size() ? (String) exposureTime.get(zct[1]) : null;
      Float exp = new Float(0.0);
      try { exp = new Float(exposure); }
      catch (NumberFormatException e) { }
      catch (NullPointerException e) { }
      store.setPlaneTheZ(new Integer(zct[0]), 0, 0, plane);
      store.setPlaneTheC(new Integer(zct[1]), 0, 0, plane);
      store.setPlaneTheT(new Integer(zct[2]), 0, 0, plane);
      store.setPlaneTimingExposureTime(exp, 0, 0, plane);

      if (plane < timestamps.size()) {
        long stamp = Long.parseLong((String) timestamps.get(plane));
        stamp -= firstStamp;
        store.setPlaneTimingDeltaT(new Float(stamp / 1600000), 0, 0, plane);
      }
    }

    // set experimenter data
    if (userName != null) {
      int space = userName.indexOf(" ");
      String firstName = space == -1 ? "": userName.substring(0, space);
      String lastName = userName.substring(space + 1);
      if (firstName.trim().equals("")) firstName = null;
      if (lastName.trim().equals("")) lastName = null;
      if (firstName != null || lastName != null) {
        store.setExperimenterFirstName(firstName, 0);
        store.setExperimenterLastName(lastName, 0);
        if (userCompany != null) {
          store.setExperimenterInstitution(userCompany, 0);
        }
      }
    }

    // TODO : Objective Working Distance

    // CTR CHECK - use Objective, combined w/ ObjectiveSettings once it exists?
//    String objectiveName = (String) getMeta("Objective Name 0");
//
//    if (mag == null) mag = "1.0";
//    if (na == null) na = "1.0";
//
//    store.setObjective(null, objectiveName, null, new Float(na),
//      new Float(mag), null, null);
  }

  /** Parse a plane of data. */
  private void parsePlane(byte[] data, int num, String name) throws IOException
  {
    RandomAccessStream s = new RandomAccessStream(data);
    s.order(true);

    if (s.readInt() == 0) s.skipBytes(4);

    core.sizeX[0] = s.readInt();
    core.sizeY[0] = s.readInt();
    s.skipBytes(4);
    bpp = s.readInt();
    s.skipBytes(4);
    int valid = s.readInt();
    isJPEG = valid == 0 || valid == 1;

    if (num < imageFiles.size()) imageFiles.setElementAt(name, num);
    else {
      int diff = num - imageFiles.size();
      for (int i=0; i<diff; i++) {
        imageFiles.add("");
      }
      imageFiles.add(name);
    }
    core.imageCount[0]++;
    if (bpp % 3 == 0) core.sizeC[0] = 3;
    else core.sizeC[0] = 1;
  }

  /** Parse all of the tags in a stream. */
  private void parseTags(int image, RandomAccessStream s) throws IOException {
    s.skipBytes(8);

    int count = s.readInt();

    // limit count to 4096

    for (int i=0; i<count; i++) {
      if (s.getFilePointer() + 2 >= s.length()) break;
      int type = s.readShort();

      String value = "";
      switch (type) {
        case 0:
          break;
        case 1:
          break;
        case 2:
          value = "" + s.readShort();
          break;
        case 3:
        case 22:
        case 23:
          value = "" + s.readInt();
          break;
        case 4:
          value = "" + s.readFloat();
          break;
        case 5:
          value = "" + s.readDouble();
          break;
        case 7:
        case 20:
        case 21:
          value = "" + s.readLong();
          break;
        case 69:
        case 8:
          int len = s.readInt();
          if (s.getFilePointer() + len < s.length()) {
            value = s.readString(len);
          }
          else return;
          break;
        case 66:
          int l = s.readShort();
          s.seek(s.getFilePointer() - 2);
          value = s.readString(l + 2);
          break;
        default:
          long old = s.getFilePointer();
          while (s.readShort() != 3 &&
            s.getFilePointer() + 2 < s.length());
          long fp = s.getFilePointer() - 2;
          s.seek(old - 2);
          value = s.readString((int) (fp - old + 2));
      }

      s.skipBytes(2);
      int tagID = 0;

      try { tagID = s.readInt(); }
      catch (IOException e) { }

      s.skipBytes(6);

      String key = getKey(tagID);
      value = DataTools.stripString(value);
      if (key.equals("Image Index Z")) {
        try {
          zIndex = Integer.parseInt(value);
        }
        catch (NumberFormatException f) { }
      }
      else if (key.equals("Image Index T")) {
        try {
          tIndex = Integer.parseInt(value);
        }
        catch (NumberFormatException f) { }
      }
      else if (key.equals("Image Channel Index")) {
        try {
          cIndex = Integer.parseInt(value);
        }
        catch (NumberFormatException f) { }
      }
      else if (key.equals("ImageWidth")) {
        try {
          if (getSizeX() == 0) Integer.parseInt(value);
          if (realWidth == 0 && Integer.parseInt(value) > realWidth) {
            realWidth = Integer.parseInt(value);
          }
        }
        catch (NumberFormatException f) { }
      }
      else if (key.equals("ImageHeight")) {
        try {
          if (getSizeY() == 0) core.sizeY[0] = Integer.parseInt(value);
          if (realHeight == 0 || Integer.parseInt(value) > realHeight) {
            realHeight = Integer.parseInt(value);
          }
        }
        catch (NumberFormatException f) { }
      }

      if (key.indexOf("ImageTile") != -1) isTiled = true;
      if (cIndex != -1) key += " " + cIndex;
      addMeta(key, value);

      if (key.equals("ImageTile Index") || key.equals("ImageTile Index 0")) {
        firstImageTile = value;
      }
      else if (key.equals("ImageTile Index 1")) secondImageTile = value;
      else if (key.startsWith("Scale Factor for X")) {
        try {
          pixelSizeX = Float.parseFloat(value);
        }
        catch (NumberFormatException e) { }
      }
      else if (key.startsWith("Scale Factor for Y")) {
        try {
          pixelSizeY = Float.parseFloat(value);
        }
        catch (NumberFormatException e) { }
      }
      else if (key.startsWith("Scale Factor for Z")) {
        try {
          pixelSizeZ = Float.parseFloat(value);
        }
        catch (NumberFormatException e) { }
      }
      else if (key.startsWith("Scale Unit for X")) {
        int v = 0;
        try {
          v = Integer.parseInt(value);
        }
        catch (NumberFormatException e) { }
        switch (v) {
          case 72:
            // meters
            pixelSizeX *= 1000000;
            break;
          case 77:
            // nanometers
            pixelSizeX /= 1000;
            break;
          case 81:
            // inches
            pixelSizeX *= 25400;
            break;
        }
      }
      else if (key.startsWith("Scale Unit for Y")) {
        int v = 0;
        try {
          v = Integer.parseInt(value);
        }
        catch (NumberFormatException e) { }
        switch (v) {
          case 72:
            // meters
            pixelSizeY *= 1000000;
            break;
          case 77:
            // nanometers
            pixelSizeY /= 1000;
            break;
          case 81:
            // inches
            pixelSizeY *= 25400;
            break;
        }
      }
      else if (key.startsWith("Scale Unit for Z")) {
        int v = 0;
        try {
          v = Integer.parseInt(value);
        }
        catch (NumberFormatException e) { }
        switch (v) {
          case 72:
            // meters
            pixelSizeZ *= 1000000;
            break;
          case 77:
            // nanometers
            pixelSizeZ /= 1000;
            break;
          case 81:
            // inches
            pixelSizeZ *= 25400;
            break;
        }
      }
      else if (key.equals("Microscope Name") || key.equals("Microscope Name 0"))
      {
        microscopeName = value;
      }
      else if (key.startsWith("Emission Wavelength")) {
        if (cIndex != -1) {
          while (cIndex >= emWave.size()) emWave.add("");
          emWave.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("Excitation Wavelength")) {
        if (cIndex != -1) {
          while (cIndex >= exWave.size()) exWave.add("");
          exWave.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("Channel Name")) {
        if (cIndex != -1) {
          channelNames.put(String.valueOf(cIndex), value);
        }
      }
      else if (key.startsWith("BlackValue")) {
        if (cIndex != -1) {
          while (cIndex >= blackValue.size()) blackValue.add("");
          blackValue.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("WhiteValue")) {
        if (cIndex != -1) {
          while (cIndex >= whiteValue.size()) whiteValue.add("");
          whiteValue.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("GammaValue")) {
        if (cIndex != -1) {
          while (cIndex >= gammaValue.size()) gammaValue.add("");
          gammaValue.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("Exposure Time [ms]")) {
        if (cIndex != -1) {
          while (cIndex >= exposureTime.size()) exposureTime.add("");
          exposureTime.setElementAt(value, cIndex);
        }
      }
      else if (key.startsWith("User Name")) {
        if (value != null && !value.trim().equals("")) userName = value;
      }
      else if (key.equals("User company")) userCompany = value;
      else if (key.startsWith("Objective Magnification")) mag = value;
      else if (key.startsWith("Objective N.A.")) na = value;
      else if (key.startsWith("Acquisition Date")) {
        if (image >= 0) {
          if (image < timestamps.size()) timestamps.setElementAt(value, image);
          else {
            int diff = image - timestamps.size();
            for (int q=0; q<diff; q++) {
              timestamps.add("");
            }
            timestamps.add(value);
          }
          addMeta("Timestamp " + image, value);
        }
      }
    }
  }

  /** Return the string corresponding to the given ID. */
  private String getKey(int tagID) {
    switch (tagID) {
      case 222: return "Compression";
      case 258: return "BlackValue";
      case 259: return "WhiteValue";
      case 260: return "ImageDataMappingAutoRange";
      case 261: return "Thumbnail";
      case 262: return "GammaValue";
      case 264: return "ImageOverExposure";
      case 265: return "ImageRelativeTime1";
      case 266: return "ImageRelativeTime2";
      case 267: return "ImageRelativeTime3";
      case 268: return "ImageRelativeTime4";
      case 333: return "RelFocusPosition1";
      case 334: return "RelFocusPosition2";
      case 513: return "ObjectType";
      case 515: return "ImageWidth";
      case 516: return "ImageHeight";
      case 517: return "Number Raw Count";
      case 518: return "PixelType";
      case 519: return "NumberOfRawImages";
      case 520: return "ImageSize";
      case 523: return "Acquisition pause annotation";
      case 530: return "Document Subtype";
      case 531: return "Acquisition Bit Depth";
      case 532: return "Image Memory Usage (RAM)";
      case 534: return "Z-Stack single representative";
      case 769: return "Scale Factor for X";
      case 770: return "Scale Unit for X";
      case 771: return "Scale Width";
      case 772: return "Scale Factor for Y";
      case 773: return "Scale Unit for Y";
      case 774: return "Scale Height";
      case 775: return "Scale Factor for Z";
      case 776: return "Scale Unit for Z";
      case 777: return "Scale Depth";
      case 778: return "Scaling Parent";
      case 1001: return "Date";
      case 1002: return "code";
      case 1003: return "Source";
      case 1004: return "Message";
      case 1025: return "Acquisition Date";
      case 1026: return "8-bit acquisition";
      case 1027: return "Camera Bit Depth";
      case 1029: return "MonoReferenceLow";
      case 1030: return "MonoReferenceHigh";
      case 1031: return "RedReferenceLow";
      case 1032: return "RedReferenceHigh";
      case 1033: return "GreenReferenceLow";
      case 1034: return "GreenReferenceHigh";
      case 1035: return "BlueReferenceLow";
      case 1036: return "BlueReferenceHigh";
      case 1041: return "FrameGrabber Name";
      case 1042: return "Camera";
      case 1044: return "CameraTriggerSignalType";
      case 1045: return "CameraTriggerEnable";
      case 1046: return "GrabberTimeout";
      case 1281: return "MultiChannelEnabled";
      case 1282: return "MultiChannel Color";
      case 1283: return "MultiChannel Weight";
      case 1284: return "Channel Name";
      case 1536: return "DocumentInformationGroup";
      case 1537: return "Title";
      case 1538: return "Author";
      case 1539: return "Keywords";
      case 1540: return "Comments";
      case 1541: return "SampleID";
      case 1542: return "Subject";
      case 1543: return "RevisionNumber";
      case 1544: return "Save Folder";
      case 1545: return "FileLink";
      case 1546: return "Document Type";
      case 1547: return "Storage Media";
      case 1548: return "File ID";
      case 1549: return "Reference";
      case 1550: return "File Date";
      case 1551: return "File Size";
      case 1553: return "Filename";
      case 1792: return "ProjectGroup";
      case 1793: return "Acquisition Date";
      case 1794: return "Last modified by";
      case 1795: return "User company";
      case 1796: return "User company logo";
      case 1797: return "Image";
      case 1800: return "User ID";
      case 1801: return "User Name";
      case 1802: return "User City";
      case 1803: return "User Address";
      case 1804: return "User Country";
      case 1805: return "User Phone";
      case 1806: return "User Fax";
      case 2049: return "Objective Name";
      case 2050: return "Optovar";
      case 2051: return "Reflector";
      case 2052: return "Condenser Contrast";
      case 2053: return "Transmitted Light Filter 1";
      case 2054: return "Transmitted Light Filter 2";
      case 2055: return "Reflected Light Shutter";
      case 2056: return "Condenser Front Lens";
      case 2057: return "Excitation Filter Name";
      case 2060: return "Transmitted Light Fieldstop Aperture";
      case 2061: return "Reflected Light Aperture";
      case 2062: return "Condenser N.A.";
      case 2063: return "Light Path";
      case 2064: return "HalogenLampOn";
      case 2065: return "Halogen Lamp Mode";
      case 2066: return "Halogen Lamp Voltage";
      case 2068: return "Fluorescence Lamp Level";
      case 2069: return "Fluorescence Lamp Intensity";
      case 2070: return "LightManagerEnabled";
      case 2071: return "tag_ID_2071";
      case 2072: return "Focus Position";
      case 2073: return "Stage Position X";
      case 2074: return "Stage Position Y";
      case 2075: return "Microscope Name";
      case 2076: return "Objective Magnification";
      case 2077: return "Objective N.A.";
      case 2078: return "MicroscopeIllumination";
      case 2079: return "External Shutter 1";
      case 2080: return "External Shutter 2";
      case 2081: return "External Shutter 3";
      case 2082: return "External Filter Wheel 1 Name";
      case 2083: return "External Filter Wheel 2 Name";
      case 2084: return "Parfocal Correction";
      case 2086: return "External Shutter 4";
      case 2087: return "External Shutter 5";
      case 2088: return "External Shutter 6";
      case 2089: return "External Filter Wheel 3 Name";
      case 2090: return "External Filter Wheel 4 Name";
      case 2103: return "Objective Turret Position";
      case 2104: return "Objective Contrast Method";
      case 2105: return "Objective Immersion Type";
      case 2107: return "Reflector Position";
      case 2109: return "Transmitted Light Filter 1 Position";
      case 2110: return "Transmitted Light Filter 2 Position";
      case 2112: return "Excitation Filter Position";
      case 2113: return "Lamp Mirror Position";
      case 2114: return "External Filter Wheel 1 Position";
      case 2115: return "External Filter Wheel 2 Position";
      case 2116: return "External Filter Wheel 3 Position";
      case 2117: return "External Filter Wheel 4 Position";
      case 2118: return "Lightmanager Mode";
      case 2119: return "Halogen Lamp Calibration";
      case 2120: return "CondenserNAGoSpeed";
      case 2121: return "TransmittedLightFieldstopGoSpeed";
      case 2122: return "OptovarGoSpeed";
      case 2123: return "Focus calibrated";
      case 2124: return "FocusBasicPosition";
      case 2125: return "FocusPower";
      case 2126: return "FocusBacklash";
      case 2127: return "FocusMeasurementOrigin";
      case 2128: return "FocusMeasurementDistance";
      case 2129: return "FocusSpeed";
      case 2130: return "FocusGoSpeed";
      case 2131: return "FocusDistance";
      case 2132: return "FocusInitPosition";
      case 2133: return "Stage calibrated";
      case 2134: return "StagePower";
      case 2135: return "StageXBacklash";
      case 2136: return "StageYBacklash";
      case 2137: return "StageSpeedX";
      case 2138: return "StageSpeedY";
      case 2139: return "StageSpeed";
      case 2140: return "StageGoSpeedX";
      case 2141: return "StageGoSpeedY";
      case 2142: return "StageStepDistanceX";
      case 2143: return "StageStepDistanceY";
      case 2144: return "StageInitialisationPositionX";
      case 2145: return "StageInitialisationPositionY";
      case 2146: return "MicroscopeMagnification";
      case 2147: return "ReflectorMagnification";
      case 2148: return "LampMirrorPosition";
      case 2149: return "FocusDepth";
      case 2150: return "MicroscopeType";
      case 2151: return "Objective Working Distance";
      case 2152: return "ReflectedLightApertureGoSpeed";
      case 2153: return "External Shutter";
      case 2154: return "ObjectiveImmersionStop";
      case 2155: return "Focus Start Speed";
      case 2156: return "Focus Acceleration";
      case 2157: return "ReflectedLightFieldstop";
      case 2158: return "ReflectedLightFieldstopGoSpeed";
      case 2159: return "ReflectedLightFilter 1";
      case 2160: return "ReflectedLightFilter 2";
      case 2161: return "ReflectedLightFilter1Position";
      case 2162: return "ReflectedLightFilter2Position";
      case 2163: return "TransmittedLightAttenuator";
      case 2164: return "ReflectedLightAttenuator";
      case 2165: return "Transmitted Light Shutter";
      case 2166: return "TransmittedLightAttenuatorGoSpeed";
      case 2167: return "ReflectedLightAttenuatorGoSpeed";
      case 2176: return "TransmittedLightVirtualFilterPosition";
      case 2177: return "TransmittedLightVirtualFilter";
      case 2178: return "ReflectedLightVirtualFilterPosition";
      case 2179: return "ReflectedLightVirtualFilter";
      case 2180: return "ReflectedLightHalogenLampMode";
      case 2181: return "ReflectedLightHalogenLampVoltage";
      case 2182: return "ReflectedLightHalogenLampColorTemperature";
      case 2183: return "ContrastManagerMode";
      case 2184: return "Dazzle Protection Active";
      case 2195: return "Zoom";
      case 2196: return "ZoomGoSpeed";
      case 2197: return "LightZoom";
      case 2198: return "LightZoomGoSpeed";
      case 2199: return "LightZoomCoupled";
      case 2200: return "TransmittedLightHalogenLampMode";
      case 2201: return "TransmittedLightHalogenLampVoltage";
      case 2202: return "TransmittedLightHalogenLampColorTemperature";
      case 2203: return "Reflected Coldlight Mode";
      case 2204: return "Reflected Coldlight Intensity";
      case 2205: return "Reflected Coldlight Color Temperature";
      case 2206: return "Transmitted Coldlight Mode";
      case 2207: return "Transmitted Coldlight Intensity";
      case 2208: return "Transmitted Coldlight Color Temperature";
      case 2209: return "Infinityspace Portchanger Position";
      case 2210: return "Beamsplitter Infinity Space";
      case 2211: return "TwoTv VisCamChanger Position";
      case 2212: return "Beamsplitter Ocular";
      case 2213: return "TwoTv CamerasChanger Position";
      case 2214: return "Beamsplitter Cameras";
      case 2215: return "Ocular Shutter";
      case 2216: return "TwoTv CamerasChangerCube";
      case 2218: return "Ocular Magnification";
      case 2219: return "Camera Adapter Magnification";
      case 2220: return "Microscope Port";
      case 2221: return "Ocular Total Magnification";
      case 2222: return "Field of View";
      case 2223: return "Ocular";
      case 2224: return "CameraAdapter";
      case 2225: return "StageJoystickEnabled";
      case 2226: return "ContrastManager Contrast Method";
      case 2229: return "CamerasChanger Beamsplitter Type";
      case 2235: return "Rearport Slider Position";
      case 2236: return "Rearport Source";
      case 2237: return "Beamsplitter Type Infinity Space";
      case 2238: return "Fluorescence Attenuator";
      case 2239: return "Fluorescence Attenuator Position";
      case 2261: return "Objective ID";
      case 2262: return "Reflector ID";
      case 2307: return "Camera Framestart Left";
      case 2308: return "Camera Framestart Top";
      case 2309: return "Camera Frame Width";
      case 2310: return "Camera Frame Height";
      case 2311: return "Camera Binning";
      case 2312: return "CameraFrameFull";
      case 2313: return "CameraFramePixelDistance";
      case 2318: return "DataFormatUseScaling";
      case 2319: return "CameraFrameImageOrientation";
      case 2320: return "VideoMonochromeSignalType";
      case 2321: return "VideoColorSignalType";
      case 2322: return "MeteorChannelInput";
      case 2323: return "MeteorChannelSync";
      case 2324: return "WhiteBalanceEnabled";
      case 2325: return "CameraWhiteBalanceRed";
      case 2326: return "CameraWhiteBalanceGreen";
      case 2327: return "CameraWhiteBalanceBlue";
      case 2331: return "CameraFrameScalingFactor";
      case 2562: return "Meteor Camera Type";
      case 2564: return "Exposure Time [ms]";
      case 2568: return "CameraExposureTimeAutoCalculate";
      case 2569: return "Meteor Gain Value";
      case 2571: return "Meteor Gain Automatic";
      case 2572: return "MeteorAdjustHue";
      case 2573: return "MeteorAdjustSaturation";
      case 2574: return "MeteorAdjustRedLow";
      case 2575: return "MeteorAdjustGreenLow";
      case 2576: return "Meteor Blue Low";
      case 2577: return "MeteorAdjustRedHigh";
      case 2578: return "MeteorAdjustGreenHigh";
      case 2579: return "MeteorBlue High";
      case 2582: return "CameraExposureTimeCalculationControl";
      case 2585: return "AxioCamFadingCorrectionEnable";
      case 2587: return "CameraLiveImage";
      case 2588: return "CameraLiveEnabled";
      case 2589: return "LiveImageSyncObjectName";
      case 2590: return "CameraLiveSpeed";
      case 2591: return "CameraImage";
      case 2592: return "CameraImageWidth";
      case 2593: return "CameraImageHeight";
      case 2594: return "CameraImagePixelType";
      case 2595: return "CameraImageShMemoryName";
      case 2596: return "CameraLiveImageWidth";
      case 2597: return "CameraLiveImageHeight";
      case 2598: return "CameraLiveImagePixelType";
      case 2599: return "CameraLiveImageShMemoryName";
      case 2600: return "CameraLiveMaximumSpeed";
      case 2601: return "CameraLiveBinning";
      case 2602: return "CameraLiveGainValue";
      case 2603: return "CameraLiveExposureTimeValue";
      case 2604: return "CameraLiveScalingFactor";
      case 2819: return "Image Index Z";
      case 2820: return "Image Channel Index";
      case 2821: return "Image Index T";
      case 2822: return "ImageTile Index";
      case 2823: return "Image acquisition Index";
      case 2827: return "Image IndexS";
      case 2841: return "Original Stage Position X";
      case 2842: return "Original Stage Position Y";
      case 3088: return "LayerDrawFlags";
      case 3334: return "RemainingTime";
      case 3585: return "User Field 1";
      case 3586: return "User Field 2";
      case 3587: return "User Field 3";
      case 3588: return "User Field 4";
      case 3589: return "User Field 5";
      case 3590: return "User Field 6";
      case 3591: return "User Field 7";
      case 3592: return "User Field 8";
      case 3593: return "User Field 9";
      case 3594: return "User Field 10";
      case 3840: return "ID";
      case 3841: return "Name";
      case 3842: return "Value";
      case 5501: return "PvCamClockingMode";
      case 8193: return "Autofocus Status Report";
      case 8194: return "Autofocus Position";
      case 8195: return "Autofocus Position Offset";
      case 8196: return "Autofocus Empty Field Threshold";
      case 8197: return "Autofocus Calibration Name";
      case 8198: return "Autofocus Current Calibration Item";
      case 20478: return "tag_ID_20478";
      case 65537: return "CameraFrameFullWidth";
      case 65538: return "CameraFrameFullHeight";
      case 65541: return "AxioCam Shutter Signal";
      case 65542: return "AxioCam Delay Time";
      case 65543: return "AxioCam Shutter Control";
      case 65544: return "AxioCam BlackRefIsCalculated";
      case 65545: return "AxioCam Black Reference";
      case 65547: return "Camera Shading Correction";
      case 65550: return "AxioCam Enhance Color";
      case 65551: return "AxioCam NIR Mode";
      case 65552: return "CameraShutterCloseDelay";
      case 65553: return "CameraWhiteBalanceAutoCalculate";
      case 65556: return "AxioCam NIR Mode Available";
      case 65557: return "AxioCam Fading Correction Available";
      case 65559: return "AxioCam Enhance Color Available";
      case 65565: return "MeteorVideoNorm";
      case 65566: return "MeteorAdjustWhiteReference";
      case 65567: return "MeteorBlackReference";
      case 65568: return "MeteorChannelInputCountMono";
      case 65570: return "MeteorChannelInputCountRGB";
      case 65571: return "MeteorEnableVCR";
      case 65572: return "Meteor Brightness";
      case 65573: return "Meteor Contrast";
      case 65575: return "AxioCam Selector";
      case 65576: return "AxioCam Type";
      case 65577: return "AxioCam Info";
      case 65580: return "AxioCam Resolution";
      case 65581: return "AxioCam Color Model";
      case 65582: return "AxioCam MicroScanning";
      case 65585: return "Amplification Index";
      case 65586: return "Device Command";
      case 65587: return "BeamLocation";
      case 65588: return "ComponentType";
      case 65589: return "ControllerType";
      case 65590: return "CameraWhiteBalanceCalculationRedPaint";
      case 65591: return "CameraWhiteBalanceCalculationBluePaint";
      case 65592: return "CameraWhiteBalanceSetRed";
      case 65593: return "CameraWhiteBalanceSetGreen";
      case 65594: return "CameraWhiteBalanceSetBlue";
      case 65595: return "CameraWhiteBalanceSetTargetRed";
      case 65596: return "CameraWhiteBalanceSetTargetGreen";
      case 65597: return "CameraWhiteBalanceSetTargetBlue";
      case 65598: return "ApotomeCamCalibrationMode";
      case 65599: return "ApoTome Grid Position";
      case 65600: return "ApotomeCamScannerPosition";
      case 65601: return "ApoTome Full Phase Shift";
      case 65602: return "ApoTome Grid Name";
      case 65603: return "ApoTome Staining";
      case 65604: return "ApoTome Processing Mode";
      case 65605: return "ApotomeCamLiveCombineMode";
      case 65606: return "ApoTome Filter Name";
      case 65607: return "Apotome Filter Strength";
      case 65608: return "ApotomeCamFilterHarmonics";
      case 65609: return "ApoTome Grating Period";
      case 65610: return "ApoTome Auto Shutter Used";
      case 65611: return "Apotome Cam Status";
      case 65612: return "ApotomeCamNormalize";
      case 65613: return "ApotomeCamSettingsManager";
      case 65614: return "DeepviewCamSupervisorMode";
      case 65615: return "DeepView Processing";
      case 65616: return "DeepviewCamFilterName";
      case 65617: return "DeepviewCamStatus";
      case 65618: return "DeepviewCamSettingsManager";
      case 65619: return "DeviceScalingName";
      case 65620: return "CameraShadingIsCalculated";
      case 65621: return "CameraShadingCalculationName";
      case 65622: return "CameraShadingAutoCalculate";
      case 65623: return "CameraTriggerAvailable";
      case 65626: return "CameraShutterAvailable";
      case 65627: return "AxioCam ShutterMicroScanningEnable";
      case 65628: return "ApotomeCamLiveFocus";
      case 65629: return "DeviceInitStatus";
      case 65630: return "DeviceErrorStatus";
      case 65631: return "ApotomeCamSliderInGridPosition";
      case 65632: return "Orca NIR Mode Used";
      case 65633: return "Orca Analog Gain";
      case 65634: return "Orca Analog Offset";
      case 65635: return "Orca Binning";
      case 65636: return "Orca Bit Depth";
      case 65637: return "ApoTome Averaging Count";
      case 65638: return "DeepView DoF";
      case 65639: return "DeepView EDoF";
      case 65643: return "DeepView Slider Name";
      case 65655: return "DeepView Slider Name";
      case 5439491: return "Acquisition Sofware";
      case 16777488: return "Excitation Wavelength";
      case 16777489: return "Emission Wavelength";
      case 101515267: return "File Name";
      case 101253123:
      case 101777411:
        return "Image Name";
      default: return "" + tagID;
    }
  }

}
