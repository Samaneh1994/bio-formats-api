//
// MakeLZW.java
//

import loci.formats.MetadataTools;
import loci.formats.ImageReader;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;

/**
 * Converts the given image file to an LZW-compressed TIFF.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/utils/MakeLZW.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/utils/MakeLZW.java">SVN</a></dd></dl>
 */
public class MakeLZW {

  public static void main(String[] args) throws Exception {
    ImageReader reader = new ImageReader();
    IMetadata omexmlMeta = MetadataTools.createOMEXMLMetadata();
    reader.setMetadataStore(omexmlMeta);
    TiffWriter writer = new TiffWriter();
    for (int i=0; i<args.length; i++) {
      String inFile = args[i];
      String outFile = "lzw-" + inFile;
      System.out.print("Converting " + inFile + " to " + outFile);
      reader.setId(inFile);
      writer.setMetadataRetrieve(omexmlMeta);
      writer.setCompression("LZW");
      writer.setId(outFile);
      int planeCount = reader.getImageCount();
      for (int p=0; p<planeCount; p++) {
        System.out.print(".");
        byte[] plane = reader.openBytes(p);
        writer.saveBytes(plane, p == planeCount - 1);
      }
      System.out.println(" [done]");
    }
  }

}
