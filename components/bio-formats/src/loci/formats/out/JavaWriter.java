//
// JavaWriter.java
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

package loci.formats.out;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import loci.common.DataTools;
import loci.common.RandomAccessOutputStream;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.FormatWriter;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataRetrieve;

/**
 * JavaWriter is the file format writer for Java source code.
 * At the moment, this code is just a very simple container for pixel data.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/out/JavaWriter.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/out/JavaWriter.java">SVN</a></dd></dl>
 */
public class JavaWriter extends FormatWriter {

  // -- Constructor --

  public JavaWriter() { super("Java source code", "java"); }

  // -- IFormatWriter API methods --

  /**
   * @see loci.formats.IFormatWriter#saveBytes(int, byte[], int, int, int, int)
   */
  public void saveBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    checkParams(no, buf, x, y, w, h);

    // check pixel type
    MetadataRetrieve meta = getMetadataRetrieve();
    String pixelType = meta.getPixelsType(series).toString();
    int type = FormatTools.pixelTypeFromString(pixelType);
    if (!DataTools.containsValue(getPixelTypes(), type)) {
      throw new FormatException("Unsupported image type '" + pixelType + "'.");
    }
    int bpp = FormatTools.getBytesPerPixel(type);
    boolean fp = FormatTools.isFloatingPoint(type);
    boolean little =
      Boolean.FALSE.equals(meta.getPixelsBinDataBigEndian(series, 0));

    // write array
    String varName = "series" + series + "Plane" + no;
    Object array = DataTools.makeDataArray(buf, bpp, fp, little);
    int sizeX = meta.getPixelsSizeX(series).getValue().intValue();
    int sizeY = meta.getPixelsSizeY(series).getValue().intValue();

    out.seek(out.length());
    if (array instanceof byte[]) {
      writePlane(varName, (byte[]) array, w, h);
    }
    else if (array instanceof short[]) {
      writePlane(varName, (short[]) array, w, h);
    }
    else if (array instanceof int[]) {
      writePlane(varName, (int[]) array, w, h);
    }
    else if (array instanceof long[]) {
      writePlane(varName, (long[]) array, w, h);
    }
    else if (array instanceof float[]) {
      writePlane(varName, (float[]) array, w, h);
    }
    else if (array instanceof double[]) {
      writePlane(varName, (double[]) array, w, h);
    }

    if (no == getPlaneCount() - 1) writeFooter();
  }

  /* @see loci.formats.IFormatWriter#canDoStacks() */
  public boolean canDoStacks() { return true; }

  /* @see loci.formats.IFormatWriter#getPixelTypes() */
  public int[] getPixelTypes() {
    return new int[] {
      FormatTools.INT8,
      FormatTools.UINT8,
      FormatTools.UINT16,
      FormatTools.UINT32,
      FormatTools.INT32,
      FormatTools.FLOAT,
      FormatTools.DOUBLE
    };
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#setId(String) */
  public void setId(String id) throws FormatException, IOException {
    super.setId(id);
    if (out.length() == 0) writeHeader();
  }

  // -- Helper methods --

  protected void writeHeader() throws IOException {
    String className = currentId.substring(0, currentId.length() - 5);
    className = className.substring(className.lastIndexOf(File.separator) + 1);

    out.writeLine("//");
    out.writeLine("// " + className + ".java");
    out.writeLine("//");
    out.writeLine("");
    out.writeLine("// Generated by Bio-Formats v" + FormatTools.VERSION);
    out.writeLine("// Generated on " + new Date());
    out.writeLine("");
    out.writeLine("public class " + className + " {");
    out.writeLine("");
  }

  protected void writePlane(String varName, byte[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public byte[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writePlane(String varName, short[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public short[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writePlane(String varName, int[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public int[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writePlane(String varName, long[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public long[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writePlane(String varName, float[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public float[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writePlane(String varName, double[] array, int w, int h)
    throws IOException
  {
    int i = 0;
    out.writeLine("  public double[][] " + varName + " = {");
    for (int y=0; y<h; y++) {
      out.writeBytes("    {");
      for (int x=0; x<w; x++) {
        out.writeBytes(String.valueOf(array[i++]));
        if (x < w - 1) out.writeBytes(", ");
        else out.writeBytes("}");
      }
      if (y < h - 1) out.writeLine(",");
      else out.writeLine("");
    }
    out.writeLine("  };");
    out.writeLine("");
  }

  protected void writeFooter() throws IOException {
    out.writeLine("}");
  }

}
