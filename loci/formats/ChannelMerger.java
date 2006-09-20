//
// ChannelMerger.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ Melissa Linkert, Curtis Rueden, Chris Allan
and Eric Kjellman.

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

package loci.formats;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Hashtable;

/** Logic to automatically merge channels in a file. */
public class ChannelMerger extends FormatReader {

  // -- Fields --

  /** FormatReader used to read the file. */
  private IFormatReader reader;

  /** The dimension order. */
  private String order;

  /** The Z, C, and T dimensions. */
  private int[] dimensions;

  /** Number of planes between channels. */
  private int between;

  // -- Constructors --

  /** Constructs a ChannelMerger with the given reader. */
  public ChannelMerger(IFormatReader r) throws FormatException {
    super("any", "*");
    if (r == null) throw new FormatException("FormatReader cannot be null");
    reader = r;
  }

  // -- FormatReader API methods --

  /** Checks if the given block is a valid header for this file format. */
  public boolean isThisType(byte[] block) {
    return reader.isThisType(block);
  }

  /** Determines the number of images in the given file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    int originalImages = reader.getImageCount(id);

    if (canMerge(id)) {
      int channels = getSizeC(id);
      originalImages /= channels;
    }
    return originalImages;
  }

  /**
   * Obtains the hashtable containing the metadata field/value pairs from
   * the given file.
   */
  public Hashtable getMetadata(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getMetadata(id);
  }

  /**
   * Obtains the specified metadata field's value for the given file.
   *
   * @param field the name associated with the metadata field
   * @return the value, or null if the field doesn't exit
   */
  public Object getMetadataValue(String id, String field)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);
    return reader.getMetadataValue(id, field);
  }

  /**
   * Retrieves the current metadata store for this reader. You can be
   * assured that this method will <b>never</b> return a <code>null</code>
   * metadata store.
   * @return a metadata store implementation.
   */
  public MetadataStore getMetadataStore(String id)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);
    return reader.getMetadataStore(id);
  }

  /**
   * Sets the default metadata store for this reader.
   *
   * @param store a metadata store implementation.
   */
  public void setMetadataStore(MetadataStore store) {
    metadataStore = store;
    reader.setMetadataStore(store);
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return canMerge(id) || reader.isRGB(id);
  }

  /** Get the size of the X dimension. */
  public int getSizeX(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSizeX(id);
  }

  /** Get the size of the Y dimension. */
  public int getSizeY(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSizeY(id);
  }

  /** Get the size of the Z dimension. */
  public int getSizeZ(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSizeZ(id);
  }

  /** Get the size of the C dimension. */
  public int getSizeC(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSizeC(id);
  }

  /** Get the size of the T dimension. */
  public int getSizeT(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSizeT(id);
  }

  /** Return true if the data is in little-endian format. */
  public boolean isLittleEndian(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.isLittleEndian(id);
  }

  /**
   * Return a five-character string representing the dimension order
   * within the file.
   */
  public String getDimensionOrder(String id)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);
    return reader.getDimensionOrder(id);
  }

  /** Returns whether or not the channels are interleaved. */
  public boolean isInterleaved(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.isInterleaved(id);
  }

  /** Return the number of series in this file. */
  public int getSeriesCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return reader.getSeriesCount(id);
  }

  /** Activates the specified series. */
  public void setSeries(String id, int no) throws FormatException, IOException {
    if (no < 0 || no >= getSeriesCount(id)) {
      throw new FormatException("Invalid series: " + no);
    }
    series = no;
    reader.setSeries(id, no);
  }

  /** Obtains the specified image from the given file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);
    if (!canMerge(id)) return reader.openImage(id, no);
    int[] nos = translateVirtual(no, id);

    int size = nos.length;
    if (size != 1 && size != 3 && size != 4) size = 3;
    BufferedImage[] img = new BufferedImage[size];

    int width = 1;
    int height = 1;

    for (int i=0; i<img.length; i++) {
      if (i < nos.length) {
        img[i] = reader.openImage(id, nos[i]);
        width = img[i].getWidth();
        height = img[i].getHeight();
      }
      else {
        Object o = ImageTools.getPixels(img[nos.length - 1]);
        int mul = 0;
        if (o instanceof byte[][]) mul = 1;
        else if (o instanceof short[][]) mul = 2;
        else if (o instanceof int[][]) mul = 4;
        else if (o instanceof float[][]) mul = 4;
        else mul = 8;

        byte[] b = new byte[width * height * mul];
        img[i] = ImageTools.makeImage(b, width, height, 1,
          false, mul, true);
      }
    }

    BufferedImage image = ImageTools.mergeChannels(img);
    return image;
  }

  /**
   * Obtains the specified image from the given file as a byte array.
   * For convenience, the channels are sequential, i.e. "RRR...GGG...BBB".
   */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);
    if (!canMerge(id)) return reader.openBytes(id, no);
    int[] nos = translateVirtual(no, id);

    ByteVector v = new ByteVector(1000);
    for (int i=0; i<nos.length; i++) {
      v.add(reader.openBytes(id, nos[i]));
    }

    return v.toByteArray();
  }

  /** Closes the currently open file. */
  public void close() throws FormatException, IOException {
    if (reader != null) reader.close();
    //reader = null;
    currentId = null;
  }

  // -- ChannelMerger API methods --

  /** Determines whether the channels in the file can be merged. */
  public boolean canMerge(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);

    int channels = getSizeC(id);
    boolean multipleChannels = channels > 1;
    return multipleChannels && !(channels == 3 && reader.isRGB(id));
  }

  // -- Helper methods --

  /** Initializes the given file. */
  protected void initFile(String id) throws FormatException, IOException {
    currentId = id;

    order = getDimensionOrder(id);
    dimensions = new int[3];
    dimensions[0] = getSizeZ(id);
    dimensions[1] = getSizeC(id);
    dimensions[2] = getSizeT(id);

    currentId = id;
  }

  /**
   * Translate a virtual plane number in a k-tuple of "real" plane numbers,
   * one per channel.
   */
  private int[] translateVirtual(int plane, String id)
    throws FormatException, IOException
  {
    int[] real = new int[reader.getImageCount(id) / getImageCount(id)];

    while (order.length() > 3) order = order.substring(1);

    if (order.charAt(0) == 'C') {
      for (int i=0; i<real.length; i++) {
        real[i] = plane * real.length + i;
      }
    }
    else {
      // determine the number of planes between channels

      if (between == 0) {
        between = 1;
        if (order.charAt(0) == 'Z' || order.charAt(1) == 'Z') {
          between *= dimensions[0];
        }
        if (order.charAt(0) == 'T' || order.charAt(1) == 'T') {
          between *= dimensions[2];
        }
      }

      for (int i=0; i<real.length; i++) {
        real[i] = plane + between * i;
      }
    }

    return real;
  }

}
