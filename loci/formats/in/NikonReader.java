//
// NikonReader.java
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

package loci.formats.in;

import java.io.*;
import java.util.*;
import loci.formats.*;

/**
 * NikonReader is the file format reader for
 * Nikon NEF (TIFF) files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/loci/formats/in/NikonReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/loci/formats/in/NikonReader.java">SVN</a></dd></dl>
 *
 * @author Melissa Linkert linkert at wisc.edu
 */
public class NikonReader extends BaseTiffReader {

  // -- Constants --

  public static final String[] NEF_SUFFIX = {"nef"};

  // Tags that give a good indication of whether this is an NEF file.
  private static final int EXIF_IFD_POINTER = 34665;
  private static final int TIFF_EPS_STANDARD = 37398;

  // EXIF IFD tags.
  private static final int CFA_REPEAT_DIM = 33421;
  private static final int EXPOSURE_TIME = 33434;
  private static final int APERTURE = 33437;
  private static final int EXPOSURE_PROGRAM = 34850;
  private static final int DATE_TIME_DIGITIZED = 36867;
  private static final int DATE_TIME_ORIGINAL = 36868;
  private static final int EXPOSURE_BIAS_VALUE = 37380;
  private static final int MAX_APERTURE_VALUE = 37381;
  private static final int METERING_MODE = 37383;
  private static final int LIGHT_SOURCE = 37384;
  private static final int FLASH = 37385;
  private static final int FOCAL_LENGTH = 37386;
  private static final int SENSING_METHOD = 37399;
  private static final int MAKER_NOTE = 37500;
  private static final int USER_COMMENT = 37510;
  private static final int SUBSEC_TIME = 37520;
  private static final int SUBSEC_TIME_ORIGINAL = 37521;
  private static final int SUBSEC_TIME_DIGITIZED = 37522;
  private static final int COLOR_SPACE = 40961;
  private static final int FILE_SOURCE = 41728;
  private static final int SCENE_TYPE = 41729;
  private static final int CFA_PATTERN = 41730;
  private static final int CUSTOM_RENDERED = 41985;
  private static final int EXPOSURE_MODE = 41986;
  private static final int WHITE_BALANCE = 41987;
  private static final int DIGITAL_ZOOM_RATIO = 41988;
  private static final int FOCAL_LENGTH_35MM_FILM = 41989;
  private static final int SCENE_CAPTURE_TYPE = 41990;
  private static final int GAIN_CONTROL = 41991;
  private static final int CONTRAST = 41992;
  private static final int SATURATION = 41993;
  private static final int SHARPNESS = 41994;
  private static final int SUBJECT_DISTANCE_RANGE = 41996;

  // Maker Note tags.
  private static final int FIRMWARE_VERSION = 1;
  private static final int ISO = 2;
  private static final int QUALITY = 4;
  private static final int MAKER_WHITE_BALANCE = 5;
  private static final int SHARPENING = 6;
  private static final int FOCUS_MODE = 7;
  private static final int FLASH_SETTING = 8;
  private static final int FLASH_MODE = 9;
  private static final int WHITE_BALANCE_FINE = 11;
  private static final int WHITE_BALANCE_RGB_COEFFS = 12;
  private static final int FLASH_COMPENSATION = 18;
  private static final int TONE_COMPENSATION = 129;
  private static final int LENS_TYPE = 131;
  private static final int LENS = 132;
  private static final int FLASH_USED = 135;
  private static final int CURVE = 140;
  private static final int COLOR_MODE = 141;
  private static final int LIGHT_TYPE = 144;
  private static final int HUE = 146;
  private static final int CAPTURE_EDITOR_DATA = 3585;

  // -- Fields --

  /** Offset to the Nikon Maker Note. */
  protected int makerNoteOffset;

  /** The original IFD. */
  protected Hashtable original;

  private Object cfaPattern;

  // -- Constructor --

  /** Constructs a new Nikon reader. */
  public NikonReader() {
    super("Nikon NEF", new String[] {"nef", "tif", "tiff"});
    blockCheckLen = 16384;
    suffixSufficient = false;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    // extension is sufficient as long as it is NEF
    if (checkSuffix(name, NEF_SUFFIX)) return true;

    if (!open) return false;
    try {
      RandomAccessStream stream = new RandomAccessStream(name);
      boolean isThisType = isThisType(stream);
      stream.close();
      return isThisType;
    }
    catch (IOException e) {
      if (debug) trace(e);
    }
    return false;
  }

  /* @see loci.formats.IFormatReader#isThisType(byte[]) */
  public boolean isThisType(byte[] block) {
    try {
      RandomAccessStream stream = new RandomAccessStream(block);
      boolean isThisType = isThisType(stream);
      stream.close();
      return isThisType;
    }
    catch (IOException e) {
      if (debug) trace(e);
    }
    return false;
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  public void close() throws IOException {
    super.close();
    makerNoteOffset = 0;
    original = null;
  }

  // -- Internal BaseTiffReader API methods --

  /* @see BaseTiffReader#initStandardMetadata() */
  protected void initStandardMetadata() throws FormatException, IOException {
    super.initStandardMetadata();

    // look for the TIFF_EPS_STANDARD tag
    // it should contain version information

    try {
      short[] version = (short[])
        TiffTools.getIFDValue(original, TIFF_EPS_STANDARD);
      String v = "";
      for (int i=0; i<version.length; i++) v += version[i];
      addMeta("Version", v);
    }
    catch (NullPointerException e) { }

    core.littleEndian[0] = true;
    try {
      core.littleEndian[0] = TiffTools.isLittleEndian(ifds[0]);
    }
    catch (FormatException f) { }

    // now look for the EXIF IFD pointer

    try {
      int exif = TiffTools.getIFDIntValue(original, EXIF_IFD_POINTER);
      if (exif != -1) {
        Hashtable exifIFD = TiffTools.getIFD(in, 0, exif);

        // put all the EXIF data in the metadata hashtable

        if (exifIFD != null) {
          Enumeration e = exifIFD.keys();
          Integer key;
          while (e.hasMoreElements()) {
            key = (Integer) e.nextElement();
            int tag = key.intValue();
            if (tag == CFA_PATTERN) {
              byte[] cfa = (byte[]) exifIFD.get(key);
              int[] colorMap = new int[cfa.length];
              for (int i=0; i<cfa.length; i++) colorMap[i] = (int) cfa[i];
              addMeta(getTagName(tag), colorMap);
              cfaPattern = colorMap;
            }
            else {
              addMeta(getTagName(tag), exifIFD.get(key));
              if (getTagName(tag).equals("Offset to maker note")) {
                byte[] b = (byte[]) exifIFD.get(key);
                if (b != null) makerNoteOffset = b[0];
                try {
                  if (makerNoteOffset >= in.length() || makerNoteOffset == 0) {
                    return;
                  }
                  Hashtable makerNote =
                    TiffTools.getIFD(in, 0, makerNoteOffset);
                  if (makerNote != null) {
                    Enumeration en = makerNote.keys();
                    Integer nextKey;
                    while (en.hasMoreElements()) {
                      nextKey = (Integer) en.nextElement();
                      int nextTag = nextKey.intValue();
                      if (makerNote.containsKey(nextKey)) {
                        addMeta(getTagName(nextTag), makerNote.get(nextKey));
                      }
                    }
                  }
                }
                catch (IOException exc) {
                  if (debug) LogTools.trace(exc);
                }
              }
            }
          }
        }
      }
    }
    catch (IOException io) { }
    catch (NullPointerException e) { }
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (debug) debug("NikonReader.initFile(" + id + ")");
    super.initFile(id);

    in = new RandomAccessStream(id);
    if (in.readShort() == 0x4949) in.order(true);

    ifds = TiffTools.getIFDs(in);
    if (ifds == null) throw new FormatException("No IFDs found");

    // look for the SubIFD tag (330);

    int offset = 0;
    try {
      offset = TiffTools.getIFDIntValue(ifds[0], 330, false, 0);
    }
    catch (FormatException exc) {
      if (debug) trace(exc);
      long[] array = TiffTools.getIFDLongArray(ifds[0], 330, false);
      offset = (int) array[array.length - 1];
    }

    Hashtable realImage = TiffTools.getIFD(in, 1, offset);

    original = ifds[0];
    ifds[0] = realImage;
    core.imageCount[0] = 1;

    if (cfaPattern != null) {
      realImage.put(new Integer(TiffTools.COLOR_MAP), (int[]) cfaPattern);
    }
  }

  // -- Helper methods --

  /** Gets the name of the IFD tag encoded by the given number. */
  private String getTagName(int tag) {
    switch (tag) {
      case CFA_REPEAT_DIM:
        return "CFA Repeat Dimensions";
      case EXPOSURE_TIME:
        return "Exposure Time";
      case APERTURE:
        return "Aperture";
      case EXPOSURE_PROGRAM:
        return "Exposure Program";
      case DATE_TIME_DIGITIZED:
        return "Date/Time Digitized";
      case DATE_TIME_ORIGINAL:
        return "Date/Time Original";
      case EXPOSURE_BIAS_VALUE:
        return "Exposure Bias Value";
      case MAX_APERTURE_VALUE:
        return "Max Aperture Value";
      case METERING_MODE:
        return "Metering Mode";
      case LIGHT_SOURCE:
        return "Light Source";
      case FLASH:
        return "Flash Enabled?";
      case FOCAL_LENGTH:
        return "Focal length of lens";
      case SENSING_METHOD:
        return "Sensing Method";
      case MAKER_NOTE:
        return "Offset to maker note";
      case USER_COMMENT:
        return "User comment";
      case SUBSEC_TIME:
        return "Subsec. Sampling for Date/Time field";
      case SUBSEC_TIME_ORIGINAL:
        return "Subsec. Sampling for original date";
      case SUBSEC_TIME_DIGITIZED:
        return "Subsec. Sampling for digitized date";
      case COLOR_SPACE:
        return "Color space";
      case FILE_SOURCE:
        return "File source";
      case SCENE_TYPE:
        return "Scene type";
      case CFA_PATTERN:
        return "CFA pattern";
      case CUSTOM_RENDERED:
        return "Custom Rendered?";
      case EXPOSURE_MODE:
        return "Exposure mode";
      case WHITE_BALANCE:
        return "White Balance";
      case DIGITAL_ZOOM_RATIO:
        return "Digital Zoom Ratio";
      case FOCAL_LENGTH_35MM_FILM:
        return "Focal Length of 35mm lens";
      case SCENE_CAPTURE_TYPE:
        return "Scene Capture Type";
      case GAIN_CONTROL:
        return "Gain Control";
      case CONTRAST:
        return "Contrast";
      case SATURATION:
        return "Saturation";
      case SHARPNESS:
        return "Sharpness";
      case SUBJECT_DISTANCE_RANGE:
        return "Subject Distance Range";
      case FIRMWARE_VERSION:
        return "Firmware version";
      case ISO:
        return "ISO";
      case QUALITY:
        return "Quality";
      case MAKER_WHITE_BALANCE:
        return "White Balance (Maker)";
      case SHARPENING:
        return "Sharpening";
      case FOCUS_MODE:
        return "Focus Mode";
      case FLASH_SETTING:
        return "Flash Setting";
      case FLASH_MODE:
        return "Flash Mode";
      case WHITE_BALANCE_FINE:
        return "White Balance Fine";
      case WHITE_BALANCE_RGB_COEFFS:
        return "White Balance (RGB coefficients)";
      case FLASH_COMPENSATION:
        return "Flash compensation";
      case TONE_COMPENSATION:
        return "Tone compensation";
      case LENS_TYPE:
        return "Lens type";
      case LENS:
        return "Lens";
      case FLASH_USED:
        return "Flash used?";
      case CURVE:
        return "Curve";
      case COLOR_MODE:
        return "Color mode";
      case LIGHT_TYPE:
        return "Light type";
      case HUE:
        return "Hue";
      case CAPTURE_EDITOR_DATA:
        return "Capture Editor Data";
    }
    return "" + tag;
  }

  private boolean isThisType(RandomAccessStream stream) throws IOException {
    Hashtable ifd = TiffTools.getFirstIFD(stream);
    return ifd != null && ifd.containsKey(new Integer(TIFF_EPS_STANDARD));
  }

}
