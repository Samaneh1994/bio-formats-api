//
// GUITools.java
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

package loci.formats.gui;

import java.lang.reflect.InvocationTargetException;
import java.util.Vector;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import loci.formats.*;

/**
 * A utility class for working with graphical user interfaces.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/gui/GUITools.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/gui/GUITools.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public final class GUITools {

  // -- Fields --

  /** String to use for "all types" combination filter. */
  private static final String ALL_TYPES = "All supported file types";

  // -- Constructor --

  private GUITools() { }

  // -- File chooser --

  /** Constructs a list of file filters for the given file format handler. */
  public static FileFilter[] buildFileFilters(IFormatHandler handler) {
    FileFilter[] filters = null;

    // unwrap reader
    while (true) {
      if (handler instanceof ReaderWrapper) {
        handler = ((ReaderWrapper) handler).getReader();
      }
      else if (handler instanceof FileStitcher) {
        handler = ((FileStitcher) handler).getReader();
      }
      else break;
    }

    // handle special cases of ImageReader and ImageWriter
    if (handler instanceof ImageReader) {
      ImageReader imageReader = (ImageReader) handler;
      IFormatReader[] readers = imageReader.getReaders();
      Vector filterList = new Vector();
      Vector comboList = new Vector();
      for (int i=0; i<readers.length; i++) {
        filterList.add(new FormatFileFilter(readers[i]));
        // NB: Some readers need to open a file to determine if it is the
        // proper type, when the extension alone is insufficient to
        // distinguish. This behavior is fine for individual filters, but not
        // for ImageReader's combination filter, because it makes the combo
        // filter too slow. So rather than composing the combo filter from
        // FormatFileFilters, we use faster but less accurate
        // ExtensionFileFilters instead.
        String[] suffixes = readers[i].getSuffixes();
        String format = readers[i].getFormat();
        comboList.add(new ExtensionFileFilter(suffixes, format));
      }
      comboList.add(new NoExtensionFileFilter());
      FileFilter combo = makeComboFilter(sortFilters(comboList));
      if (combo != null) filterList.add(combo);

      filters = sortFilters(filterList);
    }
    else if (handler instanceof ImageWriter) {
      IFormatWriter[] writers = ((ImageWriter) handler).getWriters();
      Vector filterList = new Vector();
      for (int i=0; i<writers.length; i++) {
        String[] suffixes = writers[i].getSuffixes();
        String format = writers[i].getFormat();
        filterList.add(new ExtensionFileFilter(suffixes, format));
      }
      filters = sortFilters(filterList);
    }

    // handle default reader and writer cases
    else if (handler instanceof IFormatReader) {
      IFormatReader reader = (IFormatReader) handler;
      filters = new FileFilter[] {new FormatFileFilter(reader)};
    }
    else {
      String[] suffixes = handler.getSuffixes();
      String format = handler.getFormat();
      filters = new FileFilter[] {new ExtensionFileFilter(suffixes, format)};
    }
    return filters;
  }

  /** Constructs a file chooser for the given file format handler. */
  public static JFileChooser buildFileChooser(IFormatHandler handler) {
    return buildFileChooser(handler, true);
  }

  /**
   * Constructs a file chooser for the given file format handler.
   * If preview flag is set, chooser has an preview pane showing
   * a thumbnail and other information for the selected file.
   */
  public static JFileChooser buildFileChooser(IFormatHandler handler,
    boolean preview)
  {
    return buildFileChooser(buildFileFilters(handler), preview);
  }

  /**
   * Builds a file chooser with the given file filters,
   * as well as an "All supported file types" combo filter.
   */
  public static JFileChooser buildFileChooser(final FileFilter[] filters) {
    return buildFileChooser(filters, true);
  }

  /**
   * Builds a file chooser with the given file filters, as well as an "All
   * supported file types" combo filter, if one is not already specified.
   * @param preview If set, chooser has a preview pane showing a
   *   thumbnail and other information for the selected file.
   */
  public static JFileChooser buildFileChooser(final FileFilter[] filters,
    final boolean preview)
  {
    // NB: construct JFileChooser in the AWT worker thread, to avoid deadlocks
    final JFileChooser[] jfc = new JFileChooser[1];
    Runnable r = new Runnable() {
      public void run() {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        FileFilter[] ff = sortFilters(filters);

        FileFilter combo = null;
        if (ff.length > 0 && ff[0] instanceof ComboFileFilter) {
          // check for existing "All supported file types" filter
          ComboFileFilter cff = (ComboFileFilter) ff[0];
          if (ALL_TYPES.equals(cff.getDescription())) combo = cff;
        }
        // make an "All supported file types" filter if we don't have one yet
        if (combo == null) {
          combo = makeComboFilter(ff);
          if (combo != null) fc.addChoosableFileFilter(combo);
        }
        for (int i=0; i<ff.length; i++) fc.addChoosableFileFilter(ff[i]);
        if (combo != null) fc.setFileFilter(combo);
        if (preview) new PreviewPane(fc);
        jfc[0] = fc;
      }
    };
    if (Thread.currentThread().getName().startsWith("AWT-EventQueue")) {
      // current thread is the AWT event queue thread; just execute the code
      r.run();
    }
    else {
      // execute the code with the AWT event thread
      try {
        SwingUtilities.invokeAndWait(r);
      }
      catch (InterruptedException exc) { return null; }
      catch (InvocationTargetException exc) { return null; }
    }
    return jfc[0];
  }

  // -- Helper methods --

  /**
   * Creates an "All supported file types" combo filter
   * encompassing all of the given filters.
   */
  private static FileFilter makeComboFilter(FileFilter[] filters) {
    return filters.length > 1 ? new ComboFileFilter(filters, ALL_TYPES) : null;
  }

  /**
   * Sorts the given list of file filters, keeping the "All supported
   * file types" combo filter (if any) at the front of the list.
   */
  private static FileFilter[] sortFilters(FileFilter[] filters) {
    filters = ComboFileFilter.sortFilters(filters);
    shuffleAllTypesToFront(filters);
    return filters;
  }

  /**
   * Sorts the given list of file filters, keeping the "All supported
   * file types" combo filter (if any) at the front of the list.
   */
  private static FileFilter[] sortFilters(Vector filterList) {
    FileFilter[] filters = ComboFileFilter.sortFilters(filterList);
    shuffleAllTypesToFront(filters);
    return filters;
  }

  /**
   * Looks for an "All supported file types" combo filter
   * and shuffles it to the front of the list.
   */
  private static void shuffleAllTypesToFront(FileFilter[] filters) {
    for (int i=0; i<filters.length; i++) {
      if (filters[i] instanceof ComboFileFilter) {
        ComboFileFilter combo = (ComboFileFilter) filters[i];
        if (ALL_TYPES.equals(combo.getDescription())) {
          for (int j=i; j>=0; j--) filters[i] = filters[i - 1];
          filters[0] = combo;
          break;
        }
      }
    }
  }

}
