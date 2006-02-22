//
// LociPlugin.java
//

/*
LOCI Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-2006 Melissa Linkert, Curtis Rueden and Eric Kjellman.

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

import ij.*;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;
import java.awt.Dimension;
import java.awt.Image;
import loci.formats.ImageReader;
import loci.formats.ImageTools;

/**
 * ImageJ plugin for the LOCI Bio-Formats package.
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class LociPlugin implements PlugIn {

  // -- PlugIn API methods --

  /** Executes the plugin. */
  public void run(String arg) {
    OpenDialog od = new OpenDialog("Open...", arg);
    String directory = od.getDirectory();
    String fileName = od.getFileName();
    if (fileName == null) return;
    String id = directory + fileName;

    IJ.showStatus("Opening " + fileName);
    ImageReader reader = new ImageReader();
    try {
      int num = reader.getImageCount(id);
      ImageStack stack = null;
      for (int i=0; i<num; i++) {
        if (i % 5 == 4) IJ.showStatus("Reading plane " + (i + 1) + "/" + num);
        IJ.showProgress((double) i / num);
        Image img = reader.open(id, i);
        if (stack == null) {
          Dimension dim = ImageTools.getSize(img);
          stack = new ImageStack(dim.width, dim.height);
        }
        stack.addSlice(fileName + ":" + (i + 1),
          new ImagePlus(null, img).getProcessor());
      }
      IJ.showStatus("Creating image");
      IJ.showProgress(1);
      new ImagePlus(fileName, stack).show();
    }
    catch (Exception exc) {
      IJ.showStatus("");
      IJ.showMessage("LOCI Bio-Formats", "" + exc);
    }
  }

}
