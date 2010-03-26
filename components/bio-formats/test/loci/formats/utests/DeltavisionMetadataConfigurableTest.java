//
// DeltavisionMetadataConfigurableTest.java
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

package loci.formats.utests;

import static org.testng.AssertJUnit.*;

import java.io.IOException;
import java.security.MessageDigest;

import loci.formats.FormatException;
import loci.formats.in.DeltavisionReader;
import loci.formats.in.MetadataLevel;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author callan
 *
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/test/loci/formats/utests/DeltavisionMetadataConfigurableTest.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/test/loci/formats/utests/DeltavisionMetadataConfigurableTest.java">SVN</a></dd></dl>
 */
public class DeltavisionMetadataConfigurableTest {

  private static final String ID = "/Users/callan/testimages/tinyTest.d3d.dv";

  private static final String ALL_ONLY_KEY = "Image Type";

  private static final String ALL_ONLY_VALUE = "normal";

  private DeltavisionReader pixelsOnly;

  private DeltavisionReader all;

  @BeforeClass
  public void setUp() {
    pixelsOnly = new DeltavisionReader();
    pixelsOnly.getMetadataOptions().setMetadataLevel(MetadataLevel.PIXELS_ONLY);
    all = new DeltavisionReader();
    all.getMetadataOptions().setMetadataLevel(MetadataLevel.ALL);
  }

  @Test
  public void testSetId() throws FormatException, IOException {
    long t0 = System.currentTimeMillis();
    pixelsOnly.setId(ID);
    assertEquals(MetadataLevel.PIXELS_ONLY,
                 pixelsOnly.getMetadataOptions().getMetadataLevel());
    assertNull(pixelsOnly.getSeriesMetadata().get(ALL_ONLY_KEY));
    long t1 = System.currentTimeMillis();
    all.setId(ID);
    assertEquals(MetadataLevel.ALL,
                 all.getMetadataOptions().getMetadataLevel());
    assertEquals(ALL_ONLY_VALUE, all.getGlobalMetadata().get(ALL_ONLY_KEY));
    long t2 = System.currentTimeMillis();
    System.err.println(String.format("Pixels only: %d -- All: %d",
        t1 - t0, t2 - t1));
  }

  @Test(dependsOnMethods={"testSetId"})
  public void testDimensions() {
    assertEquals(all.getSeriesCount(), pixelsOnly.getSeriesCount());
    assertEquals(all.getSizeX(), pixelsOnly.getSizeX());
    assertEquals(all.getSizeY(), pixelsOnly.getSizeY());
    assertEquals(all.getSizeZ(), pixelsOnly.getSizeZ());
    assertEquals(all.getSizeC(), pixelsOnly.getSizeC());
    assertEquals(all.getSizeT(), pixelsOnly.getSizeT());
    assertEquals(all.getPixelType(), pixelsOnly.getPixelType());
  }

  @Test(dependsOnMethods={"testSetId"})
  public void testPlaneData() throws FormatException, IOException {
    for (int i = 0; i < pixelsOnly.getSeriesCount(); i++) {
      pixelsOnly.setSeries(i);
      all.setSeries(i);
      assertEquals(all.getImageCount(), pixelsOnly.getImageCount());
      for (int j = 0; j < pixelsOnly.getImageCount(); j++) {
        byte[] pixelsOnlyPlane = pixelsOnly.openBytes(j);
        String sha1PixelsOnlyPlane = sha1(pixelsOnlyPlane);
        byte[] allPlane = all.openBytes(j);
        String sha1AllPlane = sha1(allPlane);
        if (!sha1PixelsOnlyPlane.equals(sha1AllPlane)) {
          fail(String.format(
              "MISMATCH: Series:%d Image:%d PixelsOnly:%s All:%s",
              i, j, sha1PixelsOnlyPlane, sha1AllPlane));
        }
      }
    }
  }

  private String sha1(byte[] buf) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1"); 
      return toHex(md.digest(buf));
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String toHex(byte[] data) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < data.length; i++) {
      int halfbyte = (data[i] >>> 4) & 0x0F;
      int two_halfs = 0;
      do {
        if ((0 <= halfbyte) && (halfbyte <= 9)) {
          buf.append((char) ('0' + halfbyte));
        }
        else {
          buf.append((char) ('a' + (halfbyte - 10)));
        }
        halfbyte = data[i] & 0x0F;
      } while(two_halfs++ < 1);
    }
    return buf.toString();
  }

}
