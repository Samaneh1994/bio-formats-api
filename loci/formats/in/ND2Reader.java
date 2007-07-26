//
// ND2Reader.java
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

import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ServiceRegistry;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.xml.parsers.*;
import loci.formats.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * ND2Reader is the file format reader for Nikon ND2 files.
 * The JAI ImageIO library is required to use this reader; it is available from
 * http://jai-imageio.dev.java.net. Note that JAI ImageIO is bundled with a
 * version of the JJ2000 library, so it is important that either:
 * (1) the JJ2000 jar file is *not* in the classpath; or
 * (2) the JAI jar file precedes JJ2000 in the classpath.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/ND2Reader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/ND2Reader.java">SVN</a></dd></dl>
 */
public class ND2Reader extends FormatReader {

  // -- Constants --

  private static final String NO_J2K_MSG =
    "The JAI Image I/O Tools are required to read ND2 files. Please " +
    "obtain jai_imageio.jar from http://loci.wisc.edu/ome/formats.html";

  private static final String J2K_READER =
    "com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReader";

  /** Factory for generating SAX parsers. */
  public static final SAXParserFactory SAX_FACTORY =
    SAXParserFactory.newInstance();

  // -- Static fields --

  private static boolean noJ2k = false;
  private static ReflectedUniverse r = createReflectedUniverse();

  private static ReflectedUniverse createReflectedUniverse() {
    // NB: ImageJ does not access the jai_imageio classes with the normal
    // class loading scheme, and thus the necessary service provider stuff is
    // not automatically registered. Instead, we register the J2KImageReader
    // with the IIORegistry manually, merely so that we can obtain a
    // J2KImageReaderSpi object from the IIORegistry's service provider
    // lookup function, then use it to construct a J2KImageReader object
    // directly, which we can use to process ND2 files one plane at a time.

    ReflectedUniverse ru = null;
    try {
      // register J2KImageReader with IIORegistry
      String j2kReaderSpi = J2K_READER + "Spi";
      Class j2kSpiClass = null;
      try {
        j2kSpiClass = Class.forName(j2kReaderSpi);
      }
      catch (ClassNotFoundException exc) {
        if (debug) LogTools.trace(exc);
        noJ2k = true;
      }
      catch (NoClassDefFoundError err) {
        if (debug) LogTools.trace(err);
        noJ2k = true;
      }
      catch (RuntimeException exc) {
        // HACK: workaround for bug in Apache Axis2
        Throwable cause = exc.getCause();
        if (!(cause instanceof ClassNotFoundException)) throw exc;
        if (debug) LogTools.trace(exc);
        noJ2k = true;
      }
      IIORegistry registry = IIORegistry.getDefaultInstance();
      if (j2kSpiClass != null) {
        Iterator providers = ServiceRegistry.lookupProviders(j2kSpiClass);
        registry.registerServiceProviders(providers);
      }

      // obtain J2KImageReaderSpi instance from IIORegistry
      Object j2kSpi = registry.getServiceProviderByClass(j2kSpiClass);

      ru = new ReflectedUniverse();

      // for computing offsets in initFile
      ru.exec("import jj2000.j2k.fileformat.reader.FileFormatReader");
      ru.exec("import jj2000.j2k.io.BEBufferedRandomAccessFile");
      ru.exec("import jj2000.j2k.util.ISRandomAccessIO");

      // for reading pixel data in openImage
      ru.exec("import " + J2K_READER);
      ru.setVar("j2kSpi", j2kSpi);
      ru.exec("j2kReader = new J2KImageReader(j2kSpi)");
    }
    catch (Throwable t) {
      noJ2k = true;
      if (debug) LogTools.trace(t);
    }
    return ru;
  }

  // -- Fields --

  /** Array of image offsets. */
  private long[] offsets;

  /** Whether or not the pixel data is compressed using JPEG 2000. */
  private boolean isJPEG;

  private Vector zs = new Vector();
  private Vector ts = new Vector();

  // -- Constructor --

  /** Constructs a new ND2 reader. */
  public ND2Reader() { super("Nikon ND2", new String[] {"nd2", "jp2"}); }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    if (block.length < 8) return false;
    return block[4] == 0x6a && block[5] == 0x50 && block[6] == 0x20 &&
      block[7] == 0x20;
  }

  /* @see loci.formats.IFormatReader#openBytes(int) */
  public byte[] openBytes(int no) throws FormatException, IOException {
    byte[] b = new byte[core.sizeX[0] * core.sizeY[0] * core.sizeC[0] *
      FormatTools.getBytesPerPixel(core.pixelType[0])];
    return openBytes(no, b);
  }

  /* @see loci.formats.IFormatReader#openBytes(int, byte[]) */
  public byte[] openBytes(int no, byte[] buf)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    if (no < 0 || no >= core.imageCount[0]) {
      throw new FormatException("Invalid image number: " + no);
    }
    if (buf.length < core.sizeX[0] * core.sizeY[0] * core.sizeC[0] *
      FormatTools.getBytesPerPixel(core.pixelType[0]))
    {
      throw new FormatException("Buffer too small.");
    }

    in.seek(offsets[no]);

    if (isJPEG) {
      byte[][] pixels = ImageTools.getPixelBytes(openImage(no), false);

      for (int i=0; i<core.sizeC[0]; i++) {
        System.arraycopy(pixels[i], 0, buf, i*pixels[i].length,
          pixels[i].length);
      }
      pixels = null;
    }
    else {
      in.read(buf);
    }

    return buf;
  }

  /* @see loci.formats.IFormatReader#openImage(int) */
  public BufferedImage openImage(int no) throws FormatException, IOException {
    if (!isJPEG) {
      return ImageTools.makeImage(openBytes(no), core.sizeX[0], core.sizeY[0],
        core.sizeC[0], core.interleaved[0],
        FormatTools.getBytesPerPixel(core.pixelType[0]), core.littleEndian[0]);
    }

    FormatTools.assertId(currentId, true, 1);
    if (no < 0 || no >= getImageCount()) {
      throw new FormatException("Invalid image number: " + no);
    }

    in.seek(offsets[no]);

    long len = no < core.imageCount[0] - 1 ? offsets[no + 1] - offsets[no] :
      in.length() - offsets[no];

    byte[] b = new byte[(int) len];
    in.readFully(b);

    ByteArrayInputStream bis = new ByteArrayInputStream(b);
    // NB: Even after registering J2KImageReader with
    // IIORegistry manually, the following still does not work:
    //BufferedImage img = ImageIO.read(bis);
    MemoryCacheImageInputStream mciis = new MemoryCacheImageInputStream(bis);
    BufferedImage img = null;
    try {
      r.setVar("mciis", mciis);
      r.exec("j2kReader.setInput(mciis)");
      r.setVar("zero", 0);
      r.setVar("param", null);
      img = (BufferedImage) r.exec("j2kReader.read(zero, param)");
    }
    catch (ReflectException exc) {
      throw new FormatException(exc);
    }
    bis.close();
    mciis.close();
    b = null;

    return img;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("ND2Reader.initFile(" + id + ")");
    if (noJ2k) throw new FormatException(NO_J2K_MSG);
    super.initFile(id);

    in = new RandomAccessStream(id);

    if (in.read() == -38 && in.read() == -50) {
      // newer version of ND2 - doesn't use JPEG2000

      isJPEG = false;
      in.seek(0);
      in.order(true);

      while (in.getFilePointer() < in.length()) {
        if (in.read() == -38 && in.read() == -50 && in.read() == -66 &&
          in.read() == 10)
        {
          // found a data chunk
          int len = in.readInt() + in.readInt();
          in.skipBytes(4);

          byte[] b = new byte[len];
          in.read(b);

          String s = new String(b);

          if (s.startsWith("ImageDataSeq")) {
            // found pixel data
            int ndx = Integer.parseInt(s.substring(13, s.indexOf("!")));
            if (core.sizeC[0] == 0) {
              core.sizeC[0] = len / (core.sizeX[0] * core.sizeY[0] *
                FormatTools.getBytesPerPixel(core.pixelType[0]));
            }
            offsets[ndx] = in.getFilePointer() - len + s.indexOf("!") + 9;
          }
          else if (s.startsWith("Image")) {
            // XML metadata

            ND2Handler handler = new ND2Handler();

            s = s.substring(s.indexOf("!") + 1);

            // strip out invalid characters
            for (int i=0; i<s.length(); i++) {
              char c = s.charAt(i);
              if (Character.isISOControl(c) || !Character.isDefined(c)) {
                s = s.replace(c, ' ');
              }
            }

            try {
              SAXParser parser = SAX_FACTORY.newSAXParser();
              parser.parse(new ByteArrayInputStream(s.getBytes()), handler);
            }
            catch (ParserConfigurationException exc) {
              throw new FormatException(exc);
            }
            catch (SAXException exc) {
              throw new FormatException(exc);
            }
          }

          if (core.imageCount[0] > 0 && offsets == null) {
            offsets = new long[core.imageCount[0]];
          }

          if (in.getFilePointer() < in.length() - 1) {
            if (in.read() != -38) in.skipBytes(15);
            else in.seek(in.getFilePointer() - 1);
          }
        }
      }

      core.sizeC[0] = 1;
      core.currentOrder[0] = "XYCZT";
      core.rgb[0] = false;
      core.littleEndian[0] = true;

      return;
    }
    else in.seek(0);

    isJPEG = true;

    status("Calculating image offsets");

    Vector vs = new Vector();

    long pos = in.getFilePointer();
    boolean lastBoxFound = false;
    int length = 0;
    int box = 0;

    while (!lastBoxFound) {
      pos = in.getFilePointer();
      length = in.readInt();
      if (pos + length >= in.length() || length == 0) lastBoxFound = true;
      box = in.readInt();
      pos = in.getFilePointer();
      length -= 8;

      if (box == 0x6a703263) {
        vs.add(new Long(in.getFilePointer()));
      }
      if (!lastBoxFound) in.seek(pos + length);
    }

    offsets = new long[vs.size()];
    for (int i=0; i<offsets.length; i++) {
      offsets[i] = ((Long) vs.get(i)).longValue();
    }
    vs.clear();
    vs = null;

    status("Finding XML metadata");

    core.imageCount[0] = offsets.length;

    core.pixelType[0] = FormatTools.UINT8;

    // read XML metadata from the end of the file

    in.seek(offsets[offsets.length - 1]);

    boolean found = false;
    long off = -1;
    byte[] buf = new byte[2048];
    while (!found && in.getFilePointer() < in.length()) {
      int read = 0;
      if (in.getFilePointer() == offsets[offsets.length - 1]) {
        read = in.read(buf);
      }
      else {
        System.arraycopy(buf, buf.length - 10, buf, 0, 10);
        read = in.read(buf, 10, buf.length - 10);
      }

      if (read == buf.length) read -= 10;
      for (int i=0; i<read+9; i++) {
        if (buf[i] == (byte) 0xff && buf[i+1] == (byte) 0xd9) {
          found = true;
          off = in.getFilePointer() - (read + 10) + i;
          i = buf.length;
          break;
        }
      }
    }

    buf = null;

    status("Parsing XML");

    if (off > 0 && off < in.length() - 5) {
      in.seek(off + 5);
      byte[] b = new byte[(int) (in.length() - off - 5)];
      in.readFully(b);
      String xml = new String(b);

      // assume that this XML string will be malformed, since that's how both
      // sample files are; this means we need to manually parse it :-(

      // strip out binary data at the end - this is irrelevant for our purposes
      xml = xml.substring(0, xml.lastIndexOf("</MetadataSeq>") + 14);

      // strip out all comments
      xml = xml.replaceAll("<!--*-->", "");

      // each chunk appears on a separate line, so split up the chunks

      StringTokenizer st = new StringTokenizer(xml, "\r\n");
      while (st.hasMoreTokens()) {
        String token = st.nextToken().trim();
        if (token.indexOf("<") != -1) {
          String prefix = token.substring(1, token.indexOf(">")).trim();
          token = token.substring(token.indexOf(">") + 1);

          while (token.indexOf("<") != -1) {
            int start = token.indexOf("<");
            String s = token.substring(start + 1, token.indexOf(">", start));
            token = token.substring(token.indexOf(">", start));

            // get the prefix for this tag
            if (s.indexOf(" ") != -1) {
              String pre = s.substring(0, s.indexOf(" ")).trim();
              s = s.substring(s.indexOf(" ") + 1);

              // get key/value pairs
              while (s.indexOf("=") != -1) {
                int eq = s.indexOf("=");
                String key = s.substring(0, eq).trim();
                String value =
                  s.substring(eq + 2, s.indexOf("\"", eq + 2)).trim();

                // strip out the data types
                if (key.indexOf("runtype") == -1) {
                  if (prefix.startsWith("Metadata_V1.2")) {
                    prefix = "";
                  }
                  String effectiveKey = prefix + " " + pre + " " + key;
                  if (!metadata.containsKey(effectiveKey)) {
                    addMeta(effectiveKey, value);

                    if (effectiveKey.equals(
                      "MetadataSeq _SEQUENCE_INDEX=\"0\" uiCompCount value"))
                    {
                      if (value != null) {
                        core.sizeC[0] = Integer.parseInt(value);
                      }
                    }
                    else if (effectiveKey.endsWith("dTimeAbsolute value")) {
                      long v = (long) Double.parseDouble(value);
                      if (!ts.contains(new Long(v))) {
                        core.sizeT[0]++;
                        ts.add(new Long(v));
                      }
                    }
                    else if (effectiveKey.endsWith("dZPos value")) {
                      long v = (long) Double.parseDouble(value);
                      if (!zs.contains(new Long(v))) {
                        core.sizeZ[0]++;
                        zs.add(new Long(v));
                      }
                    }
                  }
                  else {
                    String v = (String) getMeta(effectiveKey);
                    boolean parse = v != null;
                    if (parse) {
                      for (int i=0; i<v.length(); i++) {
                        if (Character.isLetter(v.charAt(i)) ||
                          Character.isWhitespace(v.charAt(i)))
                        {
                          parse = false;
                          break;
                        }
                      }
                    }
                    if (parse) {
                      addMeta(effectiveKey, value);
                    }
                  }
                }
                s = s.substring(s.indexOf("\"", eq + 2) + 1);
              }
            }
          }
        }
      }
      b = null;
      xml = null;
      st = null;
    }

    status("Populating metadata");

    BufferedImage img = openImage(0);
    core.sizeX[0] = img.getWidth();
    core.sizeY[0] = img.getHeight();
    core.sizeC[0] = img.getRaster().getNumBands();
    core.rgb[0] = core.sizeC[0] > 1;

    int numInvalid = 0;

    for (int i=1; i<offsets.length; i++) {
      if (offsets[i] - offsets[i - 1] < (core.sizeX[0] * core.sizeY[0] / 3)) {
        offsets[i - 1] = 0;
        numInvalid++;
      }
    }

    long[] tempOffsets = new long[core.imageCount[0] - numInvalid];
    int pt = 0;
    for (int i=0; i<offsets.length; i++) {
      if (offsets[i] != 0) {
        tempOffsets[pt] = offsets[i];
        pt++;
      }
    }
    offsets = tempOffsets;
    core.imageCount[0] = offsets.length;

    String sigBits =
      (String) getMeta("AdvancedImageAttributes SignificantBits value");
    int bits = 0;
    if (sigBits != null && sigBits.length() > 0) {
      bits = Integer.parseInt(sigBits.trim());
    }

    // determine the pixel size
    String pixX = (String)
      getMeta("CalibrationSeq _SEQUENCE_INDEX=\"0\" dCalibration value");
    String pixZ = (String)
      getMeta("CalibrationSeq _SEQUENCE_INDEX=\"0\" dAspect value");

    float pixSizeX = 0f;
    float pixSizeZ = 0f;

    if (pixX != null && pixX.length() > 0) {
      pixSizeX = Float.parseFloat(pixX.trim());
    }
    if (pixZ != null && pixZ.length() > 0) {
      pixSizeZ = Float.parseFloat(pixZ.trim());
    }

    if (core.sizeC[0] == 2) core.sizeC[0] = 1;

    core.currentOrder[0] = "XY";
    long deltaT = ts.size() > 1 ?
      ((Long) ts.get(1)).longValue() - ((Long) ts.get(0)).longValue() : 1;
    long deltaZ = zs.size() > 1 ?
      ((Long) zs.get(1)).longValue() - ((Long) zs.get(0)).longValue() : 1;

    if (deltaT < deltaZ || deltaZ == 0) core.currentOrder[0] += "CTZ";
    else core.currentOrder[0] += "CZT";

    // we calculate this directly (instead of calling getEffectiveSizeC) because
    // sizeZ and sizeT have not been accurately set yet
    int effectiveC = ((core.sizeC[0] - 1) / 3) + 1;

    if (core.imageCount[0] < core.sizeZ[0] * core.sizeT[0]) {
      if (core.sizeT[0] == core.imageCount[0]) {
        core.sizeT[0] /= core.sizeZ[0] * effectiveC;
        while (core.imageCount[0] > core.sizeZ[0] * core.sizeT[0] * effectiveC)
        {
          core.sizeT[0]++;
        }
      }
      else if (core.sizeZ[0] == core.imageCount[0]) {
        core.sizeZ[0] /= core.sizeT[0] * effectiveC;
        while (core.imageCount[0] > core.sizeZ[0] * core.sizeT[0] * effectiveC)
        {
          core.sizeZ[0]++;
        }
      }

      if (core.imageCount[0] < core.sizeZ[0] * core.sizeT[0] * effectiveC) {
        if (core.sizeZ[0] < core.sizeT[0]) {
          core.sizeZ[0]--;
          while (core.imageCount[0] >
            core.sizeZ[0] * core.sizeT[0] * effectiveC)
          {
            core.sizeT[0]++;
          }
          while (core.imageCount[0] <
            core.sizeZ[0] * core.sizeT[0] * effectiveC)
          {
            core.sizeT[0]--;
          }
        }
        else {
          core.sizeT[0]--;
          while (core.imageCount[0] >
            core.sizeZ[0] * core.sizeT[0] * effectiveC)
          {
            core.sizeZ[0]++;
          }
          if (core.imageCount[0] < core.sizeZ[0] * core.sizeT[0] * effectiveC) {
            core.sizeZ[0]--;
          }
        }
        while (core.imageCount[0] > core.sizeZ[0] * core.sizeT[0] * effectiveC)
        {
          core.imageCount[0]--;
        }
      }
    }

    if (bits == 0) core.pixelType[0] = FormatTools.UINT8;
    else {
      int bpp = bits;
      while (bpp % 8 != 0) bpp++;
      switch (bpp) {
        case 8:
          core.pixelType[0] = FormatTools.UINT8;
          break;
        case 16:
          core.pixelType[0] = FormatTools.UINT16;
          break;
        case 32:
          core.pixelType[0] = FormatTools.UINT32;
          break;
        default:
          throw new FormatException("Unsupported bits per pixel: " + bpp);
      }

    }

    if (core.sizeZ[0] == 0) core.sizeZ[0] = 1;
    if (core.sizeT[0] == 0) core.sizeT[0] = 1;

    if (core.imageCount[0] < core.sizeZ[0] * core.sizeT[0] * core.sizeC[0]) {
      core.sizeT[0] = core.imageCount[0];
      core.sizeZ[0] = 1;
    }

    if (core.sizeZ[0] * core.sizeT[0] * core.sizeC[0] < core.imageCount[0]) {
      core.sizeT[0] = 1;
      core.sizeZ[0] = core.imageCount[0];
    }

    core.rgb[0] = core.sizeC[0] == 3;
    core.interleaved[0] = true;
    core.littleEndian[0] = false;

    MetadataStore store = getMetadataStore();
    store.setPixels(
      new Integer(core.sizeX[0]),
      new Integer(core.sizeY[0]),
      new Integer(core.sizeZ[0]),
      new Integer(core.sizeC[0]),
      new Integer(core.sizeT[0]),
      new Integer(core.pixelType[0]),
      new Boolean(!isLittleEndian()),
      core.currentOrder[0],
      null,
      null);

    store.setDimensions(new Float(pixSizeX), new Float(pixSizeX),
      new Float(pixSizeZ), null, null, null);
    for (int i=0; i<core.sizeC[0]; i++) {
      store.setLogicalChannel(i, null, null, null, null, null, null, null);
    }

    String prefix = "MetadataSeq _SEQUENCE_INDEX=\"0\" ";

    String gain = (String) getMeta(prefix + "dGain value");
    String voltage = (String) getMeta(prefix + "dLampVoltage value");
    String mag = (String) getMeta(prefix + "dObjectiveMag value");
    String na = (String) getMeta(prefix + "dObjectiveNA value");

    store.setDetector(null, null, null, null,
      gain == null ? null : new Float(gain),
      voltage == null ? null : new Float(voltage), null, null, null);
    store.setObjective(null, null, null, na == null ? null : new Float(na),
      mag == null ? null : new Float(mag), null, null);
  }

  // -- Helper class --

  /** SAX handler for parsing XML. */
  class ND2Handler extends DefaultHandler {
    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      if (qName.equals("uiWidth")) {
        core.sizeX[0] = Integer.parseInt(attributes.getValue("value"));
      }
      else if (qName.equals("uiWidthBytes")) {
        int bytes =
          Integer.parseInt(attributes.getValue("value")) / core.sizeX[0];
        switch (bytes) {
          case 2:
            core.pixelType[0] = FormatTools.UINT16;
            break;
          case 4:
            core.pixelType[0] = FormatTools.UINT32;
            break;
          default: core.pixelType[0] = FormatTools.UINT8;
        }
      }
      else if (qName.equals("uiHeight")) {
        core.sizeY[0] = Integer.parseInt(attributes.getValue("value"));
      }
      else if (qName.equals("uiCount")) {
        int n = Integer.parseInt(attributes.getValue("value"));
        if (core.imageCount[0] == 0) {
          core.imageCount[0] = n;
          core.sizeZ[0] = n;
        }
        core.sizeT[0] = 1;
      }
      else {
        addMeta(qName, attributes.getValue("value"));
      }
    }
  }

}
