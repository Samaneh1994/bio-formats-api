//
// RandomAccessStream.java
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

package loci.formats;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import loci.formats.codec.CBZip2InputStream;

/**
 * RandomAccessStream provides methods for "intelligent" reading of files and
 * byte arrays.  It also automagically deals with closing and reopening files
 * to prevent an IOException caused by too many open files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/RandomAccessStream.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/RandomAccessStream.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class RandomAccessStream extends InputStream implements DataInput {

  // -- Constants --

  /** Maximum size of the buffer used by the DataInputStream. */
  // 256 KB - please don't change this
  protected static final int MAX_OVERHEAD = 262144;

  /** Maximum number of open files. */
  protected static final int MAX_FILES = 100;

  /** Indicators for most efficient method of reading. */
  protected static final int DIS = 0;
  protected static final int RAF = 1;
  protected static final int ARRAY = 2;

  // -- Static fields --

  /** Hashtable of all files that have been opened at some point. */
  private static Hashtable fileCache = new Hashtable();

  /** Number of currently open files. */
  private static int openFiles = 0;

  // -- Fields --

  protected IRandomAccess raf;
  protected DataInputStream dis;

  /** Length of the file. */
  protected long length;

  /** The file pointer within the DIS. */
  protected long fp;

  /** The "absolute" file pointer. */
  protected long afp;

  /** Most recent mark. */
  protected long mark;

  /** Next place to mark. */
  protected long nextMark;

  /** The file name. */
  protected String file;

  /** Starting buffer. */
  protected byte[] buf;

  /** Endianness of the stream. */
  protected boolean littleEndian = false;

  /** Number of bytes by which to extend the stream. */
  protected int ext = 0;

  /** Flag indicating this file has been compressed. */
  protected boolean compressed = false;

  // -- Constructors --

  /**
   * Constructs a hybrid RandomAccessFile/DataInputStream
   * around the given file.
   */
  public RandomAccessStream(String file) throws IOException {
    File f = new File(Location.getMappedId(file));
    f = f.getAbsoluteFile();
    if (Location.getMappedFile(file) != null) {
      raf = Location.getMappedFile(file);
      length = raf.length();
    }
    else if (f.exists()) {
      raf = new RAFile(f, "r");

      BufferedInputStream bis = new BufferedInputStream(
        new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);

      String path = f.getPath().toLowerCase();

      if (path.endsWith(".gz")) {
        dis = new DataInputStream(new GZIPInputStream(bis));
        compressed = true;

        length = 0;
        while (true) {
          int skip = dis.skipBytes(1024);
          if (skip <= 0) break;
          length += skip;
        }

        bis = new BufferedInputStream(
          new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);
        dis = new DataInputStream(new GZIPInputStream(bis));
      }
      else if (path.endsWith(".zip")) {
        ZipFile zf = new ZipFile(Location.getMappedId(file));

        // strip off .zip extension and directory prefix
        String innerId = path.substring(0, path.length() - 4);
        int slash = innerId.lastIndexOf(File.separator);
        if (slash < 0) slash = innerId.lastIndexOf("/");
        if (slash >= 0) innerId = innerId.substring(slash + 1);

        // look for zip entry with same prefix as the ZIP file
        ZipEntry entry = null;
        Enumeration en = zf.entries();
        while (en.hasMoreElements()) {
          ZipEntry ze = (ZipEntry) en.nextElement();
          if (ze.getName().startsWith(innerId)) {
            // found entry with matching name
            entry = ze;
            break;
          }
          else if (entry == null) entry = ze; // default to first entry
        }
        if (entry == null) {
          throw new IOException("Zip file '" + file + "' has no entries");
        }

        compressed = true;

        length = entry.getSize();

        dis = new DataInputStream(new BufferedInputStream(
          zf.getInputStream(entry), MAX_OVERHEAD));
      }
      else if (path.endsWith(".bz2")) {
        bis.skip(2);
        dis = new DataInputStream(new CBZip2InputStream(bis));
        compressed = true;

        length = 0;
        while (true) {
          int skip = dis.skipBytes(1024);
          if (skip <= 0) break;
          length += skip;
        }

        bis = new BufferedInputStream(
          new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);
        bis.skip(2);
        dis = new DataInputStream(new CBZip2InputStream(bis));
      }
      else dis = new DataInputStream(bis);

      if (!compressed) {
        length = raf.length();
        buf = new byte[(int) (length < MAX_OVERHEAD ? length : MAX_OVERHEAD)];
        raf.readFully(buf);
        raf.seek(0);
        nextMark = MAX_OVERHEAD;
      }
    }
    else if (file.startsWith("http")) {
      raf = new RAUrl(Location.getMappedId(file), "r");
      length = raf.length();
    }
    else throw new IOException("File not found : " + file);
    this.file = file;
    fp = 0;
    afp = 0;
    fileCache.put(this, Boolean.TRUE);
    openFiles++;
    if (openFiles > MAX_FILES) cleanCache();
  }

  /** Constructs a random access stream around the given byte array. */
  public RandomAccessStream(byte[] array) throws IOException {
    // this doesn't use a file descriptor, so we don't need to add it to the
    // file cache
    raf = new RABytes(array);
    fp = 0;
    afp = 0;
    length = raf.length();
  }

  // -- RandomAccessStream API methods --

  /** Returns the underlying InputStream. */
  public DataInputStream getInputStream() {
    try {
      if (Boolean.FALSE.equals(fileCache.get(this))) reopen();
    }
    catch (IOException e) {
      return null;
    }
    return dis;
  }

  /**
   * Sets the number of bytes by which to extend the stream.  This only applies
   * to InputStream API methods.
   */
  public void setExtend(int extend) { ext = extend; }

  /** Seeks to the given offset within the stream. */
  public void seek(long pos) throws IOException { afp = pos; }

  /** Alias for readByte(). */
  public int read() throws IOException {
    int b = (int) readByte();
    if (b == -1 && (afp >= length()) && ext > 0) return 0;
    return b;
  }

  /** Gets the number of bytes in the file. */
  public long length() throws IOException {
    if (Boolean.FALSE.equals(fileCache.get(this))) reopen();
    return length;
  }

  /** Gets the current (absolute) file pointer. */
  public long getFilePointer() { return afp; }

  /** Closes the streams. */
  public void close() throws IOException {
    if (raf != null) raf.close();
    raf = null;
    if (dis != null) dis.close();
    dis = null;
    buf = null;
    if (Boolean.TRUE.equals(fileCache.get(this))) {
      fileCache.put(this, Boolean.FALSE);
      openFiles--;
    }
  }

  /** Sets the endianness of the stream. */
  public void order(boolean little) { littleEndian = little; }

  /** Gets the endianness of the stream. */
  public boolean isLittleEndian() { return littleEndian; }

  // -- DataInput API methods --

  /** Read an input byte and return true if the byte is nonzero. */
  public boolean readBoolean() throws IOException {
    return (readByte() != 0);
  }

  /** Read one byte and return it. */
  public byte readByte() throws IOException {
    int status = checkEfficiency(1);

    long oldAFP = afp;
    if (afp < length - 1) afp++;

    if (status == DIS) {
      byte b = dis.readByte();
      fp++;
      return b;
    }
    else if (status == ARRAY) {
      return buf[(int) oldAFP];
    }
    else {
      byte b = raf.readByte();
      return b;
    }
  }

  /** Read an input char. */
  public char readChar() throws IOException {
    return (char) readByte();
  }

  /** Read eight bytes and return a double value. */
  public double readDouble() throws IOException {
    return Double.longBitsToDouble(readLong());
  }

  /** Read four bytes and return a float value. */
  public float readFloat() throws IOException {
    return Float.intBitsToFloat(readInt());
  }

  /** Read four input bytes and return an int value. */
  public int readInt() throws IOException {
    return DataTools.read4SignedBytes(this, littleEndian);
  }

  /** Read the next line of text from the input stream. */
  public String readLine() throws IOException {
    StringBuffer sb = new StringBuffer();
    char c = readChar();
    while (c != '\n') {
      sb = sb.append(c);
      c = readChar();
    }
    return sb.toString();
  }

  /** Read a string of arbitrary length, terminated by a null char. */
  public String readCString() throws IOException {
    StringBuffer sb = new StringBuffer();
    char achar = readChar();
    while (achar != 0) {
      sb = sb.append(achar);
      achar = readChar();
    }
    return sb.toString();
  }

  /** Read a string of length n. */
  public String readString(int n) throws IOException {
    byte[] b = new byte[n];
    read(b);
    return new String(b);
  }

  /** Read eight input bytes and return a long value. */
  public long readLong() throws IOException {
    return DataTools.read8SignedBytes(this, littleEndian);
  }

  /** Read two input bytes and return a short value. */
  public short readShort() throws IOException {
    return DataTools.read2SignedBytes(this, littleEndian);
  }

  /** Read an input byte and zero extend it appropriately. */
  public int readUnsignedByte() throws IOException {
    return DataTools.readUnsignedByte(this);
  }

  /** Read two bytes and return an int in the range 0 through 65535. */
  public int readUnsignedShort() throws IOException {
    return DataTools.read2UnsignedBytes(this, littleEndian);
  }

  /** Read a string that has been encoded using a modified UTF-8 format. */
  public String readUTF() throws IOException {
    return null;  // not implemented yet...we don't really need this
  }

  /** Skip n bytes within the stream. */
  public int skipBytes(int n) throws IOException {
    afp += n;
    return n;
  }

  /** Read bytes from the stream into the given array. */
  public int read(byte[] array) throws IOException {
    int status = checkEfficiency(array.length);
    int n = 0;

    if (status == DIS) {
      return read(array, 0, array.length);
    }
    else if (status == ARRAY) {
      n = array.length;
      if ((buf.length - afp) < array.length) {
        n = buf.length - (int) afp;
      }
      System.arraycopy(buf, (int) afp, array, 0, n);
    }
    else n = raf.read(array);

    afp += n;
    if (status == DIS) fp += n;
    if (n < array.length && ext > 0) {
      while (n < array.length && ext > 0) {
        n++;
        ext--;
      }
    }
    return n;
  }

  /**
   * Read n bytes from the stream into the given array at the specified offset.
   */
  public int read(byte[] array, int offset, int n) throws IOException {
    int toRead = n;
    int status = checkEfficiency(n);

    if (status == DIS) {
      int p = dis.read(array, offset, n);
      if (p == -1) return -1;
      if ((p >= 0) && ((fp + p) < length)) {
        int k = p;
        while ((k >= 0) && (p < n) && ((afp + p) <= length) &&
          ((offset + p) < array.length))
        {
          k = dis.read(array, offset + p, n - p);
          if (k >= 0) p += k;
        }
      }
      n = p;
    }
    else if (status == ARRAY) {
      if ((buf.length - afp) < n) n = buf.length - (int) afp;
      System.arraycopy(buf, (int) afp, array, offset, n);
    }
    else {
      n = raf.read(array, offset, n);
    }
    afp += n;
    if (status == DIS) fp += n;
    if (n < toRead && ext > 0) {
      while (n < array.length && ext > 0) {
        n++;
        ext--;
      }
    }

    return n;
  }

  /** Read bytes from the stream into the given array. */
  public void readFully(byte[] array) throws IOException {
    readFully(array, 0, array.length);
  }

  /**
   * Read n bytes from the stream into the given array at the specified offset.
   */
  public void readFully(byte[] array, int offset, int n) throws IOException {
    int status = checkEfficiency(n);

    if (status == DIS) {
      dis.readFully(array, offset, n);
    }
    else if (status == ARRAY) {
      System.arraycopy(buf, (int) afp, array, offset, n);
    }
    else {
      raf.readFully(array, offset, n);
    }
    afp += n;
    if (status == DIS) fp += n;
  }

  // -- InputStream API methods --

  public int available() throws IOException {
    if (Boolean.FALSE.equals(fileCache.get(this))) reopen();
    int available = dis != null ? dis.available() + ext :
      (int) (length() - getFilePointer());
    if (available < 0) available = Integer.MAX_VALUE;
    return available;
  }

  public void mark(int readLimit) {
    try {
      if (Boolean.FALSE.equals(fileCache.get(this))) reopen();
    }
    catch (IOException e) { }
    if (!compressed) dis.mark(readLimit);
  }

  public boolean markSupported() { return !compressed; }

  public void reset() throws IOException {
    if (Boolean.FALSE.equals(fileCache.get(this))) reopen();
    dis.reset();
    fp = length() - dis.available();
  }

  // -- Helper methods - I/O --

  /**
   * Determine whether it is more efficient to use the DataInputStream or
   * RandomAccessFile for reading (based on the current file pointers).
   * Returns 0 if we should use the DataInputStream, 1 if we should use the
   * RandomAccessFile, and 2 for a direct array access.
   */
  protected int checkEfficiency(int toRead) throws IOException {
    if (Boolean.FALSE.equals(fileCache.get(this))) reopen();

    if (compressed) {
      // can only read from the input stream

      if (afp < fp) {
        dis.close();

        BufferedInputStream bis = new BufferedInputStream(
          new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);

        String path = Location.getMappedId(file).toLowerCase();

        if (path.endsWith(".gz")) {
          dis = new DataInputStream(new GZIPInputStream(bis));
        }
        else if (path.endsWith(".zip")) {
          ZipFile zf = new ZipFile(Location.getMappedId(file));
          InputStream zip = new BufferedInputStream(zf.getInputStream(
            (ZipEntry) zf.entries().nextElement()), MAX_OVERHEAD);
          dis = new DataInputStream(zip);
        }
        else if (path.endsWith(".bz2")) {
          bis.skip(2);
          dis = new DataInputStream(new CBZip2InputStream(bis));
        }
        fp = 0;
      }

      while (fp < afp) {
        fp += dis.skipBytes((int) (afp - fp));
      }

      return DIS;
    }

    if (dis != null && raf != null &&
      afp + toRead < MAX_OVERHEAD && afp + toRead < length() &&
      afp + toRead < buf.length)
    {
      // this is a really special case that allows us to read directly from
      // an array when working with the first MAX_OVERHEAD bytes of the file
      // ** also note that it doesn't change the stream
      return ARRAY;
    }

    if (dis != null) {
      if (fp < length()) {
        while (fp > (length() - dis.available())) {
          while (fp - length() + dis.available() > Integer.MAX_VALUE) {
            dis.skipBytes(Integer.MAX_VALUE);
          }
          dis.skipBytes((int) (fp - (length() - dis.available())));
        }
      }
      else {
        fp = afp;
        BufferedInputStream bis = new BufferedInputStream(
          new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);
        dis = new DataInputStream(bis);
        while (fp > (length() - dis.available())) {
          while (fp - length() + dis.available() > Integer.MAX_VALUE) {
            dis.skipBytes(Integer.MAX_VALUE);
          }
          dis.skipBytes((int) (fp - length() + dis.available()));
        }
      }
    }

    if (afp >= fp && dis != null) {
      while (fp < afp) {
        while (afp - fp > Integer.MAX_VALUE) {
          fp += dis.skipBytes(Integer.MAX_VALUE);
        }
        int skip = dis.skipBytes((int) (afp - fp));
        if (skip == 0) break;
        fp += skip;
      }

      if (fp >= nextMark) {
        dis.mark(MAX_OVERHEAD);
      }
      nextMark = fp + MAX_OVERHEAD;
      mark = fp;

      return DIS;
    }
    else {
      if (dis != null && afp >= mark && fp < mark) {
        boolean valid = true;

        try {
          dis.reset();
        }
        catch (IOException io) {
          valid = false;
        }

        if (valid) {
          dis.mark(MAX_OVERHEAD);

          fp = length() - dis.available();
          while (fp < afp) {
            while (afp - fp > Integer.MAX_VALUE) {
              fp += dis.skipBytes(Integer.MAX_VALUE);
            }
            int skip = dis.skipBytes((int) (afp - fp));
            if (skip == 0) break;
            fp += skip;
          }

          if (fp >= nextMark) {
            dis.mark(MAX_OVERHEAD);
          }
          nextMark = fp + MAX_OVERHEAD;
          mark = fp;

          return DIS;
        }
        else {
          raf.seek(afp);
          return RAF;
        }
      }
      else {
        // we don't want this to happen very often
        raf.seek(afp);
        return RAF;
      }
    }
  }

  // -- Helper methods - cache management --

  /** Re-open a file that has been closed */
  private void reopen() throws IOException {
    File f = new File(Location.getMappedId(file));
    f = f.getAbsoluteFile();
    if (Location.getMappedFile(file) != null) {
      raf = Location.getMappedFile(file);
      length = raf.length();
    }
    else if (f.exists()) {
      raf = new RAFile(f, "r");

      BufferedInputStream bis = new BufferedInputStream(
        new FileInputStream(Location.getMappedId(file)), MAX_OVERHEAD);

      String path = f.getPath().toLowerCase();

      if (path.endsWith(".gz")) {
        dis = new DataInputStream(new GZIPInputStream(bis));
        compressed = true;
      }
      else if (path.endsWith(".zip")) {
        ZipFile zf = new ZipFile(Location.getMappedId(file));
        InputStream zip = new BufferedInputStream(zf.getInputStream(
          (ZipEntry) zf.entries().nextElement()), MAX_OVERHEAD);
        dis = new DataInputStream(zip);
        compressed = true;
      }
      else if (path.endsWith(".bz2")) {
        bis.skip(2);
        dis = new DataInputStream(new CBZip2InputStream(bis));
        compressed = true;
      }
      else dis = new DataInputStream(bis);

      if (!compressed) {
        length = raf.length();
        buf = new byte[(int) (length < MAX_OVERHEAD ? length : MAX_OVERHEAD)];
        raf.readFully(buf);
        raf.seek(0);
      }
    }
    else if (file.startsWith("http")) {
      raf = new RAUrl(Location.getMappedId(file), "r");
      length = raf.length();
    }
    fileCache.put(this, Boolean.TRUE);
    openFiles++;
    if (openFiles > MAX_FILES) cleanCache();
  }

  /** If we have too many open files, close most of them. */
  private void cleanCache() {
    int toClose = MAX_FILES - 10;
    RandomAccessStream[] files = (RandomAccessStream[])
      fileCache.keySet().toArray(new RandomAccessStream[0]);
    int closed = 0;
    int ndx = 0;

    while (closed < toClose) {
      if (!this.equals(files[ndx]) &&
        !fileCache.get(files[ndx]).equals(Boolean.FALSE) &&
        files[ndx].file != null)
      {
        try { files[ndx].close(); }
        catch (IOException exc) { LogTools.trace(exc); }
        closed++;
      }
      ndx++;
    }
  }

}
