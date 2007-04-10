//
// TiffReader.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan,
Eric Kjellman and Brian Loranger.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Library General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Library General Public License for more details.

You should have received a copy of the GNU Library General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.in;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import javax.xml.parsers.*;
import loci.formats.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

/**
 * TiffReader is the file format reader for TIFF files, including OME-TIFF.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 */
public class TiffReader extends BaseTiffReader {

  // -- Constructor --

  /** Constructs a new Tiff reader. */
  public TiffReader() {
    super("Tagged Image File Format", new String[] {"tif", "tiff"});
  }

  // -- Internal TiffReader API methods --

  /**
   * Allows a class which is delegating parsing responsibility to
   * <code>TiffReader</code> the ability to affect the <code>sizeZ</code> value
   * that is inserted into the metadata store.
   * @param zSize the number of optical sections to use when making a call to
   * {@link loci.formats.MetadataStore#setPixels(Integer, Integer, Integer,
   *   Integer, Integer, Integer, Boolean, String, Integer, Integer)}.
   */
  protected void setSizeZ(int zSize) {
    if (core.sizeZ == null) core.sizeZ = new int[1];
    core.sizeZ[0] = zSize;
  }

  // -- Internal BaseTiffReader API methods --

  /* @see BaseTiffReader#initStandardMetadata() */ 
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();
    String comment = (String) getMeta("Comment");

    status("Checking comment style");

    // check for OME-XML in TIFF comment (OME-TIFF format)
    boolean omeTiff = comment != null && comment.indexOf("ome.xsd") >= 0;
    put("OME-TIFF", omeTiff ? "yes" : "no");
    if (omeTiff) {
      status("Found OME-TIFF: parsing OME-XML");

      // convert string to DOM
      ByteArrayInputStream is = new ByteArrayInputStream(comment.getBytes());
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      Document doc = null;
      try {
        DocumentBuilder builder = factory.newDocumentBuilder();
        doc = builder.parse(is);
      }
      catch (ParserConfigurationException exc) { }
      catch (SAXException exc) { }
      catch (IOException exc) { }

      // extract TiffData elements from XML
      Element[] pixels = null;
      Element[][] tiffData = null;
      if (doc != null) {
        NodeList pixelsList = doc.getElementsByTagName("Pixels");
        int numSeries = pixelsList.getLength();
        Vector v = new Vector();
        pixels = new Element[numSeries];
        tiffData = new Element[numSeries][];
        for (int i=0; i<numSeries; i++) {
          pixels[i] = (Element) pixelsList.item(i);
          NodeList list = pixels[i].getChildNodes();
          int size = list.getLength();
          v.clear();
          for (int j=0; j<size; j++) {
            Node node = list.item(j);
            if (!(node instanceof Element)) continue;
            if ("TiffData".equals(node.getNodeName())) v.add(node);
          }
          tiffData[i] = new Element[v.size()];
          v.copyInto(tiffData[i]);
        }
      }

      // MAJOR HACK : check for OME-XML in the comment of the second IFD
      // There is a version of WiscScan which writes OME-XML to every IFD,
      // but with SizeZ and SizeT equal to 1.

      String s = 
        (String) TiffTools.getIFDValue(ifds[1], TiffTools.IMAGE_DESCRIPTION);
      boolean isWiscScan = s != null && s.indexOf("ome.xsd") != -1;

      // extract SizeZ, SizeC and SizeT from XML block
      if (tiffData != null) {
        boolean rgb = false;
        try {
          rgb = isRGB();
        }
        catch (IOException exc) {
          throw new FormatException(exc);
        }
        
        core = new CoreMetadata(tiffData.length);
        Arrays.fill(core.orderCertain, true);

        for (int i=0; i<tiffData.length; i++) {
          core.sizeX[i] = Integer.parseInt(pixels[i].getAttribute("SizeX"));
          core.sizeY[i] = Integer.parseInt(pixels[i].getAttribute("SizeY"));
          core.sizeZ[i] = Integer.parseInt(pixels[i].getAttribute("SizeZ"));
          core.sizeC[i] = Integer.parseInt(pixels[i].getAttribute("SizeC"));
          core.imageCount[i] = ifds.length; 
          int sc = core.sizeC[i];
          if (rgb) sc /= 3;
          core.sizeT[i] = Integer.parseInt(pixels[i].getAttribute("SizeT"));
          core.pixelType[i] = FormatTools.pixelTypeFromString(
            pixels[i].getAttribute("PixelType"));
          if (core.pixelType[i] == FormatTools.INT8 ||
            core.pixelType[i] == FormatTools.INT16 ||
            core.pixelType[i] == FormatTools.INT32)
          {
            core.pixelType[i]++;
          }

          // MAJOR HACK : adjust SizeT to match the number of IFDs, if this
          // file was written by a buggy version of WiscScan
          if (isWiscScan) core.sizeT[i] = core.imageCount[0];

          core.currentOrder[i] = pixels[i].getAttribute("DimensionOrder");
          core.orderCertain[i] = true;

          boolean[][][] zct = new boolean[core.sizeZ[i]][sc][core.sizeT[i]];

          for (int j=0; j<tiffData[i].length; j++) {
            String aIfd = tiffData[i][j].getAttribute("IFD");
            String aFirstZ = tiffData[i][j].getAttribute("FirstZ");
            String aFirstT = tiffData[i][j].getAttribute("FirstT");
            String aFirstC = tiffData[i][j].getAttribute("FirstC");
            String aNumPlanes = tiffData[i][j].getAttribute("NumPlanes");
            boolean nullIfd = aIfd == null || "".equals(aIfd);
            boolean nullFirstZ = aFirstZ == null || "".equals(aFirstZ);
            boolean nullFirstT = aFirstT == null || "".equals(aFirstT);
            boolean nullFirstC = aFirstC == null || "".equals(aFirstC);
            boolean nullNumPlanes = aNumPlanes == null || "".equals(aNumPlanes);
            int ifd = nullIfd ? 0 : Integer.parseInt(aIfd);
            int firstZ = nullFirstZ ? 0 : Integer.parseInt(aFirstZ);
            int firstT = nullFirstT ? 0 : Integer.parseInt(aFirstT);
            int firstC = nullFirstC ? 0 : Integer.parseInt(aFirstC);
            int numPlanes = nullNumPlanes ?
              (nullIfd ? core.imageCount[0] : 1) : Integer.parseInt(aNumPlanes);

            // populate ZCT matrix
            char d1st = core.currentOrder[i].charAt(2);
            char d2nd = core.currentOrder[i].charAt(3);
            int z = firstZ, t = firstT, c = firstC;
            
            for (int k=0; k<numPlanes; k++) {
              zct[z][c][t] = true;
              switch (d1st) {
                case 'Z':
                  z++;
                  if (z >= core.sizeZ[i]) {
                    z = 0;
                    switch (d2nd) {
                      case 'T':
                        t++;
                        if (t >= core.sizeT[i]) {
                          t = 0;
                          c++;
                        }
                        break;
                      case 'C':
                        c++;
                        if (c >= sc) {
                          c = 0;
                          t++;
                        }
                        break;
                    }
                  }
                  break;
                case 'T':
                  t++;
                  if (t >= core.sizeT[i]) {
                    t = 0;
                    switch (d2nd) {
                      case 'Z':
                        z++;
                        if (z >= core.sizeZ[i]) {
                          z = 0;
                          c++;
                        }
                        break;
                      case 'C':
                        c++;
                        if (c >= sc) {
                          c = 0;
                          z++;
                        }
                        break;
                    }
                  }
                  break;
                case 'C':
                  c++;
                  if (c >= sc) {
                    c = 0;
                    switch (d2nd) {
                      case 'Z':
                        z++;
                        if (z >= core.sizeZ[i]) {
                          z = 0;
                          t++;
                        }
                        break;
                      case 'T':
                        t++;
                        if (t >= core.sizeT[i]) {
                          t = 0;
                          z++;
                        }
                        break;
                    }
                  }
                  break;
              }
            }
          }

          // analyze ZCT matrix to determine best SizeZ, SizeC and SizeT
          // for now, we only handle certain special cases:
          boolean success = false;
          int theZ, theT, theC;

          // 1) all Z, all T, all C
          success = true;
          for (int z=0; z<core.sizeZ[i] && success; z++) {
            for (int t=0; t<core.sizeT[i] && success; t++) {
              for (int c=0; c<sc && success; c++) {
                if (!zct[z][c][t]) success = false;
              }
            }
          }
          if (success) {
            // NB: sizes are already correct; no corrections necessary
            continue;
          }

          // 2) single Z, all T, all C
          success = true;
          theZ = -1;
          for (int z=0; z<core.sizeZ[i] && success; z++) {
            if (zct[z][0][0]) {
              if (theZ < 0) theZ = z;
              else success = false;
            }
            boolean state = theZ == z;
            for (int t=0; t<core.sizeT[i] && success; t++) {
              for (int c=0; c<sc && success; c++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeZ[i] = 1;
            continue;
          }

          // 3) all Z, single T, all C
          success = true;
          theT = -1;
          for (int t=0; t<core.sizeT[i] && success; t++) {
            if (zct[0][0][t]) {
              if (theT < 0) theT = t;
              else success = false;
            }
            boolean state = theT == t;
            for (int z=0; z<core.sizeZ[i] && success; z++) {
              for (int c=0; c<sc && success; c++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeT[i] = 1;
            continue;
          }

          // 4) all Z, all T, single C
          success = true;
          theC = -1;
          for (int c=0; c<sc && success; c++) {
            if (zct[0][c][0]) {
              if (theC < 0) theC = c;
              else success = false;
            }
            boolean state = theC == c;
            for (int z=0; z<core.sizeZ[i] && success; z++) {
              for (int t=0; t<core.sizeT[i] && success; t++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeC[i] = 1;
            continue;
          }

          // 5) single Z, single T, all C
          success = true;
          theZ = -1;
          theT = -1;
          for (int z=0; z<core.sizeZ[i] && success; z++) {
            for (int t=0; t<core.sizeT[i] && success; t++) {
              if (zct[z][0][t]) {
                if (theZ < 0 && theT < 0) {
                  theZ = z;
                  theT = t;
                }
                else success = false;
              }
              boolean state = theZ == z && theT == t;
              for (int c=0; c<sc && success; c++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeZ[i] = core.sizeT[i] = 1;
            continue;
          }

          // 6) single Z, all T, single C
          success = true;
          theZ = -1;
          theC = -1;
          for (int z=0; z<core.sizeZ[i] && success; z++) {
            for (int c=0; c<sc && success; c++) {
              if (zct[z][c][0]) {
                if (theZ < 0 && theC < 0) {
                  theZ = z;
                  theC = c;
                }
                else success = false;
              }
              boolean state = theZ == z && theC == c;
              for (int t=0; t<core.sizeT[i] && success; t++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeZ[i] = core.sizeC[i] = 1;
            continue;
          }

          // 7) all Z, single T, single C
          success = true;
          theT = -1;
          theC = -1;
          for (int t=0; t<core.sizeT[i] && success; t++) {
            for (int c=0; c<sc && success; c++) {
              if (zct[0][c][t]) {
                if (theC < 0 && theT < 0) {
                  theC = c;
                  theT = t;
                }
                else success = false;
              }
              boolean state = theC == c && theT == t;
              for (int z=0; z<core.sizeZ[i] && success; z++) {
                if (zct[z][c][t] != state) success = false;
              }
            }
          }
          if (success) {
            core.sizeT[i] = core.sizeC[i] = 1;
            continue;
          }

          // 8) single Z, single T, single C
          success = true;
          theZ = -1;
          theT = -1;
          theC = -1;
          int count = 0;
          for (int z=0; z<core.sizeZ[i] && success; z++) {
            for (int t=0; t<core.sizeT[i] && success; t++) {
              for (int c=0; c<sc && success; c++) {
                if (zct[z][c][t]) {
                  count++;
                  if (count > 1) success = false;
                }
              }
            }
          }
          if (success) {
            core.sizeZ[i] = core.sizeT[i] = core.sizeC[i] = 1;
            continue;
          }

          // no easy way to chop up TiffData mappings into regular ZCT dims
          throw new FormatException("Unsupported ZCT index mapping");
        }
      }
    }
    else if (ifds.length > 1) core.orderCertain[0] = false;

    // check for ImageJ-style TIFF comment
    boolean ij = comment != null && comment.startsWith("ImageJ=");
    if (ij) {
      int nl = comment.indexOf("\n");
      put("ImageJ", nl < 0 ? comment.substring(7) : comment.substring(7, nl));
      metadata.remove("Comment");
    }

    // check for Improvision-style TIFF comment
    boolean iv = comment != null && comment.startsWith("[Improvision Data]\n");
    put("Improvision", iv ? "yes" : "no");
    if (iv) {
      // parse key/value pairs
      StringTokenizer st = new StringTokenizer(comment, "\n");
      while (st.hasMoreTokens()) {
        String line = st.nextToken();
        int equals = line.indexOf("=");
        if (equals < 0) continue;
        String key = line.substring(0, equals);
        String value = line.substring(equals + 1);
        addMeta(key, value);
      }
      metadata.remove("Comment");
    }
  
    // check for MetaMorph-style TIFF comment 
    boolean metamorph = comment != null && getMeta("Software") != null && 
      ((String) getMeta("Software")).indexOf("MetaMorph") != -1; 
    put("MetaMorph", metamorph ? "yes" : "no"); 
    
    if (metamorph) {
      // parse key/value pairs   
      StringTokenizer st = new StringTokenizer(comment, "\n");
      while (st.hasMoreTokens()) {
        String line = st.nextToken();
        int colon = line.indexOf(":");
        if (colon < 0) {
          addMeta("Comment", line);
          continue; 
        } 
        String key = line.substring(0, colon);
        String value = line.substring(colon + 1);
        addMeta(key, value);
      }
    }
  }

  /* @see BaseTiffReader#initMetadataStore() */ 
  protected void initMetadataStore() {
    // check for OME-XML in TIFF comment (OME-TIFF format)
    // we need an extra check to make sure that any XML we find is indeed
    // OME-XML (and not some other XML variant)

    String comment = (String) getMeta("Comment");
    if (comment != null && comment.indexOf("ome.xsd") >= 0) {
      metadata.remove("Comment");
      boolean isOMEXML;
      try {
        Class omexmlMeta =
          Class.forName("loci.formats.ome.OMEXMLMetadataStore");
        isOMEXML = omexmlMeta.isAssignableFrom(metadataStore.getClass());
      }
      catch (Throwable t) {
        isOMEXML = false;
      }
      if (isOMEXML) {
        ReflectedUniverse r = new ReflectedUniverse();
        try {
          r.exec("import loci.formats.ome.OMEXMLMetadataStore");
          r.setVar("xmlStore", metadataStore);
          r.setVar("comment", comment);
          r.exec("xmlStore.createRoot(comment)");
          return;
        }
        catch (ReflectException exc) {
          // OME Java probably not available; ignore this error
        }
      }
    }
    super.initMetadataStore();
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#getSeriesCount() */
  public int getSeriesCount() throws FormatException, IOException {
    return core.currentOrder.length;
  }

}
