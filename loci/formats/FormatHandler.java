//
// FormatHandler.java
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

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

/** Abstract superclass of all biological file format readers and writers. */
public abstract class FormatHandler implements IFormatHandler {

  // -- Fields --

  /** Name of this file format. */
  protected String format;

  /** Valid suffixes for this file format. */
  protected String[] suffixes;

  /** File filters for this file format, for use with a JFileChooser. */
  protected FileFilter[] filters;

  /** File chooser for this file format. */
  protected JFileChooser chooser;

  /** Name of current file. */
  protected String currentId;

  // -- Constructors --

  /** Constructs a format handler with the given name and default suffix. */
  public FormatHandler(String format, String suffix) {
    this(format, suffix == null ? null : new String[] {suffix});
  }

  /** Constructs a format handler with the given name and default suffixes. */
  public FormatHandler(String format, String[] suffixes) {
    this.format = format;
    this.suffixes = suffixes == null ? new String[0] : suffixes;
  }

  // -- FormatHandler API methods --

  /** Creates JFileChooser file filters for this file format. */
  protected void createFilters() {
    filters = new FileFilter[] {new ExtensionFileFilter(suffixes, format)};
  }

  /**
   * This default implementation checks filename suffixes against those known
   * for this format.
   */
  public boolean isThisType(String name) {
    String lname = name.toLowerCase();
    for (int i=0; i<suffixes.length; i++) {
      if (lname.endsWith("." + suffixes[i])) return true;
    }
    return false;
  }

  /* (non-Javadoc)
   * @see loci.formats.IFormatHandler#getFormat()
   */
  public String getFormat() { return format; }

  /* (non-Javadoc)
   * @see loci.formats.IFormatHandler#getSuffixes()
   */
  public String[] getSuffixes() { return suffixes; }

  /* (non-Javadoc)
   * @see loci.formats.IFormatHandler#getFileFilters()
   */
  public FileFilter[] getFileFilters() {
    if (filters == null) createFilters();
    return filters;
  }

  /* (non-Javadoc)
   * @see loci.formats.IFormatHandler#getFileChooser()
   */
  public JFileChooser getFileChooser() {
    if (chooser == null) chooser = buildFileChooser(getFileFilters());
    return chooser;
  }

  // -- Utility methods --

  /**
   * Builds a file chooser with the given file filters,
   * as well as an "All supported file types" combo filter.
   */
  public static JFileChooser buildFileChooser(FileFilter[] ff) {
    JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
    ff = ComboFileFilter.sortFilters(ff);
    FileFilter combo = null;
    if (ff.length > 1) {
      combo = new ComboFileFilter(ff, "All supported file types");
      fc.addChoosableFileFilter(combo);
    }
    for (int i=0; i<ff.length; i++) fc.addChoosableFileFilter(ff[i]);
    if (combo != null) fc.setFileFilter(combo);
    return fc;
  }

}
