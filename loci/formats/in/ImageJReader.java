//
// ImageJReader.java
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

package loci.formats.in;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.swing.filechooser.FileFilter;
import loci.formats.*;

/**
 * ImageJReader is the file format reader for the image formats supported
 * by Wayne Rasband's excellent ImageJ program:
 *
 * DICOM, FITS, PGM, JPEG, GIF, LUT, BMP, TIFF, ZIP-compressed TIFF and ROI.
 */
public class ImageJReader extends FormatReader {

  // -- Static fields --

  /** Legal ImageJ suffixes. */
  private static final String[] SUFFIXES = {
    "tif", "tiff", "dcm", "dicom", "fits", "pgm", "jpg", "jpeg", "jpe",
    "gif", "png", "lut", "bmp", "zip", "roi"
  };

  /** Message produced when attempting to use ImageJ without it installed. */
  private static final String NO_IJ = "This feature requires ImageJ, " +
   "available online at http://rsb.info.nih.gov/ij/download.html";

  // -- Fields --

  /** Reflection tool for ImageJ calls. */
  private ReflectedUniverse r;

  /** Flag indicating ImageJ is not installed. */
  private boolean noImageJ = false;


  // -- Constructor --

  /** Constructs a new ImageJ reader. */
  public ImageJReader() {
    super("ImageJ", SUFFIXES);
    r = new ReflectedUniverse();

    try {
      r.exec("import ij.ImagePlus");
      r.exec("import ij.ImageStack");
      r.exec("import ij.io.Opener");
      r.exec("import ij.process.ImageProcessor");
      r.exec("opener = new Opener()");
    }
    catch (Exception exc) { noImageJ = true; }
  }


  // -- FormatReader API methods --

  /** Checks if the given block is a valid header. */
  public boolean isThisType(byte[] block) { return false; }

  /** Determines the number of images in the given file. */
  public int getImageCount(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return separated ? 3 : 1;
  }

  /** Checks if the images in the file are RGB. */
  public boolean isRGB(String id) throws FormatException, IOException {
    return true;
  }

  /** Get the size of the X dimension. */
  public int getSizeX(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return openImage(id, 0).getWidth();
  }

  /** Get the size of the Y dimension. */
  public int getSizeY(String id) throws FormatException, IOException {
    if (!id.equals(currentId)) initFile(id);
    return openImage(id, 0).getHeight();
  }

  /** Get the size of the Z dimension. */
  public int getSizeZ(String id) throws FormatException, IOException {
    return 1;
  }

  /** Get the size of the C dimension. */
  public int getSizeC(String id) throws FormatException, IOException {
    return 3;
  }

  /** Get the size of the T dimension. */
  public int getSizeT(String id) throws FormatException, IOException {
    return 1;
  }

  /** Return true if the data is in little-endian format. */
  public boolean isLittleEndian(String id) throws FormatException, IOException {
    return false;
  }

  /**
   * Return a five-character string representing the dimension order
   * within the file.
   */
  public String getDimensionOrder(String id)
    throws FormatException, IOException
  {
    return "XYCZT";
  }

  /** Obtains the specified image from the given file as a byte array. */
  public byte[] openBytes(String id, int no)
    throws FormatException, IOException
  {
    return ImageTools.getBytes(openImage(id, no), separated, no);
  }

  /** Obtains the specified image from the given file. */
  public BufferedImage openImage(String id, int no)
    throws FormatException, IOException
  {
    if (!id.equals(currentId)) initFile(id);

    if (no < 0 || no >= getImageCount(id)) {
      throw new FormatException("Invalid image number: " + no);
    }

    if (noImageJ) throw new FormatException(NO_IJ);

    try {
      File file = new File(id);
      r.setVar("dir", file.getParent() + System.getProperty("file.separator"));
      r.setVar("name", file.getName());
      r.exec("image = opener.openImage(dir, name)");
      r.exec("size = image.getStackSize()");
      Image img = (Image) r.exec("image.getImage()");

      if (!separated) {
        return ImageTools.makeBuffered(img);
      }
      else {
        return ImageTools.splitChannels(ImageTools.makeBuffered(img))[no];
      }
    }
    catch (Exception exc) {
      exc.printStackTrace();
    }
    return null;
  }

  /** Closes any open files. */
  public void close() throws FormatException, IOException {
    currentId = null;
  }

  /** Creates JFileChooser file filters for this file format. */
  protected void createFilters() {
    filters = new FileFilter[] {
      new ExtensionFileFilter(new String[] {"tif", "tiff"},
        "Tagged Image File Format"),
      new ExtensionFileFilter(new String[] {"dcm", "dicom"},
        "Digital Imaging and Communications in Medicine"),
      new ExtensionFileFilter("fits",
        "Flexible Image Transport System"),
      new ExtensionFileFilter("pgm", "PGM"),
      new ExtensionFileFilter(new String[] {"jpg", "jpeg", "jpe"},
        "Joint Photographic Experts Group"),
      new ExtensionFileFilter("gif", "Graphics Interchange Format"),
      new ExtensionFileFilter("png", "Portable Network Graphics"),
      new ExtensionFileFilter("lut", "ImageJ Lookup Table"),
      new ExtensionFileFilter("bmp", "Windows Bitmap"),
      new ExtensionFileFilter("zip", "Zip-compressed TIFF"),
      new ExtensionFileFilter("roi", "ImageJ Region of Interest")
    };
  }


  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    new ImageJReader().testRead(args);
  }

}
