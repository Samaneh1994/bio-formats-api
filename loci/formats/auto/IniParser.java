//
// IniParser.java
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

package loci.formats.auto;

import java.io.*;
import java.util.*;

/**
 * A simple parser for INI configuration files. Supports pound (#) as comments,
 * and backslash (\) to continue values across multiple lines.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/auto/IniParser.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/auto/IniParser.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 */
public class IniParser {

  // -- Constants --

  /** Key to use for storing header value (in brackets). */
  public static final String HEADER_KEY = "name";

  // -- IniParser API methods --

  /** Parses the INI-style configuration data from the given resource. */
  public Vector<Hashtable<String, String>> parseINI(String path)
    throws IOException
  {
    return parseINI(openTextResource(path));
  }

  /** Parses the INI-style configuration data from the given input stream. */
  public Vector<Hashtable<String, String>> parseINI(BufferedReader in)
    throws IOException
  {
    Vector<Hashtable<String, String>> list =
      new Vector<Hashtable<String, String>>();
    Hashtable<String, String> attrs = null;
    int no = 1;
    StringBuffer sb = new StringBuffer();
    while (true) {
      int num = readLine(in, sb);
      if (num == 0) break; // eof
      String line = sb.toString();

      // ignore blank lines
      if (line.equals("")) {
        no += num;
        continue;
      }

      if (line.startsWith("[")) {
        // section header
        attrs = new Hashtable<String, String>();
        list.add(attrs);

        // strip brackets
        int end = line.length();
        if (line.endsWith("]")) end--;
        String header = line.substring(1, end);

        attrs.put(HEADER_KEY, header);
        no += num;
        continue;
      }

      // parse key/value pair
      int equals = line.indexOf("=");
      if (equals < 0) throw new IOException(no + ": bad line");
      String key = line.substring(0, equals).trim();
      String value = line.substring(equals + 1).trim();
      attrs.put(key, value);
      no += num;
    }
    return list;
  }

  // -- Utility methods --

  /** Opens a buffered reader for the given resource. */
  public static BufferedReader openTextResource(String path)
    throws IOException
  {
    return new BufferedReader(new InputStreamReader(
      IniParser.class.getResourceAsStream(path)));
  }

  // -- Helper methods --

  /**
   * Reads (at least) one line from the given input stream
   * into the specified string buffer.
   *
   * @return number of lines read
   */
  private int readLine(BufferedReader in, StringBuffer sb) throws IOException {
    int no = 0;
    sb.setLength(0);
    while (true) {
      String line = in.readLine();
      if (line == null) break;
      no++;

      // strip comments
      int hash = line.indexOf("#");
      if (hash >= 0) line = line.substring(0, hash);

      // kill whitespace
      line = line.trim();

      // backslash signifies data continues to next line
      boolean slash = line.endsWith("\\");
      if (slash) line = line.substring(0, line.length() - 1).trim() + " ";
      sb.append(line);
      if (!slash) break;
    }
    return no;
  }

}
