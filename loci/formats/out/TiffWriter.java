//
// TiffWriter.java
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

package loci.formats.out;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import loci.formats.*;
//import org.openmicroscopy.xml.*;
//import org.openmicroscopy.xml.st.*;
//import loci.formats.ome.OMEXMLMetadataStore;

/** TiffWriter is the file format writer for TIFF files. */
public class TiffWriter extends FormatWriter {

  // -- Fields --

  /** The last offset written to. */
  private int lastOffset;

  /** Current output stream. */
  private BufferedOutputStream out;

  // -- Constructor --

  public TiffWriter() {
    super("Tagged Image File Format", new String[] {"tif", "tiff"});
    lastOffset = 0;
    compressionTypes = new String[] {"Uncompressed", "LZW"};
  }

  // -- TiffWriter API methods --

  /**
   * Saves the given image to the specified (possibly already open) file.
   * The IFD hashtable allows specification of TIFF parameters such as bit
   * depth, compression and units.  If this image is the last one in the file,
   * the last flag must be set.
   */
  public void saveImage(Image image, Hashtable ifd, boolean last)
    throws IOException, FormatException
  {
    if (!initialized) {
      initialized = true;
      out =
        new BufferedOutputStream(new FileOutputStream(currentId, true), 4096);

      RandomAccessStream tmp = new RandomAccessStream(currentId);
      if (tmp.length() == 0) {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeByte(TiffTools.BIG);
        dataOut.writeByte(TiffTools.BIG);
        dataOut.writeShort(TiffTools.MAGIC_NUMBER);
        dataOut.writeInt(8); // offset to first IFD
        lastOffset = 8;
      }
      else {
        // compute the offset to the last IFD
        TiffTools.checkHeader(tmp);
        long offset = TiffTools.getFirstOffset(tmp);
        long ifdMax = (tmp.length() - 8) / 18;

        for (long ifdNum=0; ifdNum<ifdMax; ifdNum++) {
          TiffTools.getIFD(tmp, ifdNum, offset);
          offset = tmp.readInt();
          if (offset <= 0 || offset >= tmp.length()) break;
        }
        lastOffset = (int) offset;
      }
      tmp.close();
    }

    BufferedImage img = (cm == null) ?
      ImageTools.makeBuffered(image) : ImageTools.makeBuffered(image, cm);

    lastOffset += TiffTools.writeImage(img, ifd, out, lastOffset, last);
    if (last) close();
  }

  // -- IFormatWriter API methods --

  /* @see loci.formats.IFormatWriter#save(String, Image, boolean) */
  public void saveImage(Image image, boolean last)
    throws FormatException, IOException
  {
    Hashtable h = new Hashtable();
    if (compression == null) compression = "";
    h.put(new Integer(TiffTools.COMPRESSION), compression.equals("LZW") ?
      new Integer(TiffTools.LZW) : new Integer(TiffTools.UNCOMPRESSED));
    saveImage(image, h, last);
  }

  /* @see loci.formats.IFormatWriter#canDoStacks(String) */
  public boolean canDoStacks() { return true; }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    // write the metadata, if enabled

    /*
    if (metadataEnabled && store != null && currentId != null) {
      // TODO : use reflection to access the OMEXMLMetadataStore
      if (store instanceof OMEXMLMetadataStore) {
        try {
          // writes valid OME-TIFF
          RandomAccessFile raf = new RandomAccessFile(currentId, "rw");
          RandomAccessStream in = new RandomAccessStream(currentId);
          OMENode xml = (OMENode) ((OMEXMLMetadataStore) store).getRoot();
          Vector images = xml.getChildNodes("Image");
          for (int p=0; p<images.size(); p++) {
            PixelsNode pix =
              (PixelsNode) ((ImageNode) images.get(p)).getDefaultPixels();
            DOMUtil.createChild(pix.getDOMElement(), "TiffData");
          }

          Hashtable[] ifds = TiffTools.getIFDs(in);
          TiffTools.overwriteIFDValue(raf, 0, TiffTools.IMAGE_DESCRIPTION,
            xml.writeOME(true));
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
      else {
        throw new FormatException("Expecting an OMEXMLMetadataStore; got a " +
          store.getClass());
      }
    }
    */

    if (out != null) out.close();
    out = null;
    currentId = null;
    initialized = false;
    lastOffset = 0;
  }

  // -- Deprecated API methods --

  /** @deprecated Replaced by {@link #saveImage(Image, Hashtable, boolean)} */
  public void saveImage(String id, Image image, Hashtable ifd, boolean last)
    throws IOException, FormatException
  {
    setId(id);
    saveImage(image, ifd, last);
  }

}
