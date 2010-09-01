//
// SPWModelReaderTest.java
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

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.MinMaxCalculator;
import loci.formats.meta.IMetadata;
import loci.formats.ome.OMEXMLMetadataImpl;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Chris Allan <callan at blackcat dot ca>
 *
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/test/loci/formats/utests/SPWModelReaderTest.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/test/loci/formats/utests/SPWModelReaderTest.java">SVN</a></dd></dl>
 */
public class SPWModelReaderTest {

  private SPWModelMock mock;
  
  private SPWModelMock mockWithNoLightSources;

  private File temporaryFile;

  private File temporaryFileWithNoLightSources;
  
  private IFormatReader reader;

  private IFormatReader readerWithNoLightSources;

  private IMetadata metadata;

  private IMetadata metadataWithNoLightSources;

  @BeforeClass
  public void setUp() throws Exception {
    mock = new SPWModelMock(true);
    mockWithNoLightSources = new SPWModelMock(false);
    temporaryFile = File.createTempFile(this.getClass().getName(), ".ome");
    temporaryFileWithNoLightSources = 
      File.createTempFile(this.getClass().getName(), ".ome");
    writeMockToFile(mock, temporaryFile);
    writeMockToFile(mockWithNoLightSources, temporaryFileWithNoLightSources);
  }

  /**
   * Writes a model mock to a file as XML.
   * @param mock Mock to build a DOM tree of and serialize to XML.
   * @param file File to write serialized XML to.
   * @throws Exception If there is an error writing the XML to the file.
   */
  public static void writeMockToFile(ModelMock mock, File file)
  throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder parser = factory.newDocumentBuilder();
    Document document = parser.newDocument();
    // Produce a valid OME DOM element hierarchy
    Element root = mock.getRoot().asXMLElement(document);
    mock.postProcess(root, document);
    // Write the OME DOM to the requested file
    OutputStream stream = new FileOutputStream(file);
    stream.write(mock.asString(document).getBytes());
  }

  @AfterClass
  public void tearDown() throws Exception {
    temporaryFile.delete();
    temporaryFileWithNoLightSources.delete();
  }

  @Test
  public void testSetId() throws Exception {
    reader = new MinMaxCalculator(new ImageReader());
    metadata = new OMEXMLMetadataImpl();
    reader.setMetadataStore(metadata);
    reader.setId(temporaryFile.getAbsolutePath());
  }

  @Test
  public void testSetIdWithNoLightSources() throws Exception {
    readerWithNoLightSources = new MinMaxCalculator(new ImageReader());
    metadataWithNoLightSources = new OMEXMLMetadataImpl();
    readerWithNoLightSources.setMetadataStore(metadataWithNoLightSources);
    readerWithNoLightSources.setId(
      temporaryFileWithNoLightSources.getAbsolutePath());
  }

  @Test(dependsOnMethods={"testSetId"})
  public void testSeriesCount() {
    assertEquals(384, reader.getSeriesCount());
  }

  @Test(dependsOnMethods={"testSetId"})
  public void testCanReadEveryPlane() throws Exception {
    assertTrue(canReadEveryPlane(reader));
  }

  @Test(dependsOnMethods={"testSetIdWithNoLightSources"})
  public void testCanReadEveryPlaneWithNoLightSources() throws Exception {
    assertTrue(canReadEveryPlane(readerWithNoLightSources));
  }

  /**
   * Checks to see if every plane of an initialized reader can be read.
   * @param reader Reader to read all planes from.
   * @return <code>true</code> if all planes can be read, <code>false</code>
   * otherwise.
   * @throws Exception If there is an error reading data.
   */
  public static boolean canReadEveryPlane(IFormatReader reader)
  throws Exception {
    int sizeX = reader.getSizeX();
    int sizeY = reader.getSizeY();
    int pixelType = reader.getPixelType();
    int bytesPerPixel = getBytesPerPixel(pixelType);
    byte[] buf = new byte[sizeX * sizeY * bytesPerPixel];
    for (int i = 0; i < reader.getSeriesCount(); i++)
    {
      reader.setSeries(i);
      for (int j = 0; j < reader.getImageCount(); j++)
      {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    "Required SHA-1 message digest algorithm unavailable.");
        }
        buf = reader.openBytes(j, buf);
        try {
          md.update(buf);
        } catch (Exception e) {
          // This better not happen. :)
          throw new RuntimeException(e);
        }
        System.err.println(String.format("%d/%d", i, j));
      }
    }
    return true;
  }

  @Test(dependsOnMethods={"testSetId"})
  public void testHasLightSources() {
    assertEquals(1, metadata.getInstrumentCount());
    assertEquals(5, metadata.getLightSourceCount(0));
  }

  @Test(dependsOnMethods={"testSetIdWithNoLightSources"})
  public void testHasNoLightSources() {
    assertEquals(1, metadataWithNoLightSources.getInstrumentCount());
    assertEquals(0, metadataWithNoLightSources.getLightSourceCount(0));
  }

  /**
   * Retrieves how many bytes per pixel the current plane or section has.
   * @return the number of bytes per pixel.
   */
  public static int getBytesPerPixel(int type) {
    switch(type) {
    case 0:
    case 1:
      return 1;  // INT8 or UINT8
    case 2:
    case 3:
      return 2;  // INT16 or UINT16
    case 4:
    case 5:
    case 6:
      return 4;  // INT32, UINT32 or FLOAT
    case 7:
      return 8;  // DOUBLE
    }
    throw new RuntimeException("Unknown type with id: '" + type + "'");
  }

}
