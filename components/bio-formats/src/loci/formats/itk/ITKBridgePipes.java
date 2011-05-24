//
// ITKBridgePipes.java
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

package loci.formats.itk;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataStore;

import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.enums.EnumerationException;
/**
 * ITKBridgePipes is a Java console application that listens for "commands"
 * on stdin and issues results on stdout. It is used by the pipes version of
 * the ITK Bio-Formats plugin to read image files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/itk/ITKBridgePipes.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/itk/ITKBridgePipes.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author Mark Hiner
 * @author Curtis Rueden
 */
public class ITKBridgePipes {

  private static final String HASH_PREFIX = "hash:";

  private IFormatReader reader = null;
  private String readerPath = "";

  /** Enters an input loop, waiting for commands, until EOF is reached. */
  public void waitForInput() throws FormatException, IOException {
    final BufferedReader in =
      new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      final String line = in.readLine(); // blocks until a line is read
      if (line == null) break; // eof
      executeCommand(line);
    }
    in.close();
  }

  /**
   * Executes the given command line. The following commands are supported:
   * <ul>
   * <li>info</li> - Dumps image metadata
   * <li>read</li> - Dumps image pixels
   * <li>canRead</li> - Tests whether the given file path can be parsed
   * </ul>
   */
  public boolean executeCommand(String commandLine)
    throws FormatException, IOException
  {
    System.err.println(commandLine);
    String[] args = commandLine.split("\t");

    final String command = args[0].trim();

    if (command.equals("info")) {
       final String filePath = args[1].trim();
       boolean res = readImageInfo(filePath);
       // add an extra \n to mark the end of the output
       System.out.println();
       return res;
    }
    else if (command.equals("read")) {
      final String filePath = args[1].trim();
      int xBegin = Integer.parseInt( args[2] );
      int xEnd =   Integer.parseInt( args[3] ) + xBegin - 1;
      int yBegin = Integer.parseInt( args[4] );
      int yEnd =   Integer.parseInt( args[5] ) + yBegin - 1;
      int zBegin = Integer.parseInt( args[6] );
      int zEnd =   Integer.parseInt( args[7] ) + zBegin - 1;
      int tBegin = Integer.parseInt( args[8] );
      int tEnd =   Integer.parseInt( args[9] ) + tBegin - 1;
      int cBegin = Integer.parseInt( args[10] );
      int cEnd =   Integer.parseInt( args[11] ) + cBegin - 1;
      return read(filePath, xBegin, xEnd, yBegin, yEnd, zBegin, zEnd, tBegin, tEnd, cBegin, cEnd);
    }
    else if (command.equals("canRead")) {
      final String filePath = args[1].trim();
      boolean res = canRead(filePath);
      // add an extra \n to mark the end of the output
      System.out.println();
      return res;
    }
    else if (command.equals("exit")) {
      boolean res = exit();
      // add an extra \n to mark the end of the output
      System.out.println();
      return res;
    }
    else if(command.equals("write")) {
    	int byteOrder = Integer.parseInt( args[2] );
    	int dims = Integer.parseInt( args[3] );
    	int dim0 = Integer.parseInt( args[4] );
    	int dim1 = Integer.parseInt( args[5] );
    	int dim2 = Integer.parseInt( args[6] );
    	int dim3 = Integer.parseInt( args[7] );
    	int dim4 = Integer.parseInt( args[8] );
    	int pixelType = Integer.parseInt( args[9] );
    	int rgbCCount = Integer.parseInt( args[10] );
    	int xStart = Integer.parseInt( args[11] );
    	int yStart = Integer.parseInt( args[13] );
    	int zStart = Integer.parseInt( args[15] );
    	int cStart = Integer.parseInt( args[17] );
    	int tStart = Integer.parseInt( args[19] );
    	int xCount = Integer.parseInt( args[12] );
    	int yCount = Integer.parseInt( args[14] );
    	int zCount = Integer.parseInt( args[16] );
    	int cCount = Integer.parseInt( args[18] );
    	int tCount = Integer.parseInt( args[20] );
    	return write(args[1], byteOrder, dims, dim0, dim1, dim2, dim3, dim4, pixelType, rgbCCount, xStart, yStart, zStart, cStart, tStart, xCount, yCount, zCount, cCount, tCount);
    }
    else {
      System.err.println("Unknown command: " + command);
    }
    return false;
  }

  /**
   * Reads image metadata from the given file path, dumping the resultant
   * values to stdout in a specific order (which we have not documented here
   * because we are lazy).
   *
   * @param filePath a path to a file on disk, or a hash token for an
   *   initialized reader (beginning with "hash:") as given by a call to "info"
   *   earlier.
   */
  public boolean readImageInfo(String filePath)
    throws FormatException, IOException
  {
    createReader(filePath);

    final MetadataStore store = reader.getMetadataStore();
    IMetadata meta = (IMetadata) store;
    
    
   // now print the informations

   // little endian?
    System.out.println( "LittleEndian(bool): " + (reader.isLittleEndian()? 1:0) );

    // component type
    // set ITK component type
    int pixelType = reader.getPixelType();
    int itkComponentType;
    switch (pixelType) {
      case FormatTools.UINT8:
        itkComponentType = 1;
        break;
      case FormatTools.INT8:
        itkComponentType = 2;
        break;
      case FormatTools.UINT16:
        itkComponentType = 3;
        break;
      case FormatTools.INT16:
        itkComponentType = 4;
        break;
      case FormatTools.UINT32:
        itkComponentType = 5;
        break;
      case FormatTools.INT32:
        itkComponentType = 6;
        break;
      case FormatTools.FLOAT:
        itkComponentType = 9;
        break;
      case FormatTools.DOUBLE:
        itkComponentType = 10;
        break;
      default:
        itkComponentType = 0;
    }
    System.out.println( "PixelType(enum): " + itkComponentType );

    // x, y, z, t, c
    System.out.println( "SizeX(int): " + reader.getSizeX() );
    System.out.println( "SizeY(int): " + reader.getSizeY() );
    System.out.println( "SizeZ(int): " + reader.getSizeZ() );
    System.out.println( "SizeT(int): " + reader.getSizeT() );
    System.out.println( "SizeC(int): " + reader.getEffectiveSizeC() ); // reader.getSizeC()

    // number of components
    System.out.println( "RGBChannelCount(int): " + reader.getRGBChannelCount() );

    // spacing
    System.out.println( "PixelsPhysicalSizeX(real): " + (meta.getPixelsPhysicalSizeX(0)==null? 1.0: meta.getPixelsPhysicalSizeX(0)) );
    System.out.println( "PixelsPhysicalSizeY(real): " + (meta.getPixelsPhysicalSizeY(0)==null? 1.0: meta.getPixelsPhysicalSizeY(0)) );
    System.out.println( "PixelsPhysicalSizeZ(real): " + (meta.getPixelsPhysicalSizeZ(0)==null? 1.0: meta.getPixelsPhysicalSizeZ(0)) );
    System.out.println( "PixelsPhysicalSizeT(real): " + (meta.getPixelsTimeIncrement(0)==null? 1.0: meta.getPixelsTimeIncrement(0)) );
    // should we give something more useful for this one?
    System.out.println( "PixelsPhysicalSizeC(real): " + 1.0 );

    HashMap<String, Object> metadata = new HashMap<String, Object>();
    metadata.putAll( reader.getGlobalMetadata() );
    metadata.putAll( reader.getSeriesMetadata() );
    Set entries = metadata.entrySet();
    Iterator it = entries.iterator();
    while (it.hasNext()) {
      Map.Entry entry = (Map.Entry) it.next();

      String key = (String)entry.getKey();
      Object value = entry.getValue();

      // clean up the key name
      key = key.replace('(', ' ');
      key = key.replace(')', ' ');
      key = key.replace(':', ' ');

      String type;
      if( value instanceof Double ) {
        type = "real";
      }
      else if( value instanceof Long ) {
        type = "int";
      }
      else if( value instanceof Integer ) {
        type = "int";
      }
      else if( value instanceof Boolean ) {
        type = "bool";
        // don't print false or true, but 0 or 1
        if( ((Boolean)value).booleanValue() ) {
          value = new Integer(1);
        }
        else {
          value = new Integer(0);
        }
      }
      else if( value instanceof String ) {
        // remove the line return
        value = ((String)value).replace("\\", "\\\\").replace("\n", "\\n");
        type = "string";
      }
      else {
        // defaults to string
        type = "string";
      }
      System.out.println( entry.getKey() + "("+type+"): " + value );
    }
    System.out.flush();

    return true;
  }

  /**
   * Reads image pixels from the given file path, dumping the resultant binary
   * stream to stdout.
   *
   * @param filePath a path to a file on disk, or a hash token for an
   *   initialized reader (beginning with "hash:") as given by a call to "info"
   *   earlier. Using a hash token eliminates the need to initialize the file a
   *   second time with a fresh reader object. Regardless, after reading the
   *   file, the reader closes the file handle, and invalidates its hash token.
   */
  public boolean read(String filePath,
       int xBegin, int xEnd,
       int yBegin, int yEnd,
       int zBegin, int zEnd,
       int tBegin, int tEnd,
       int cBegin, int cEnd)
    throws FormatException, IOException
  {
    createReader(filePath);

    int rgbChannelCount = reader.getRGBChannelCount();
    int bpp = FormatTools.getBytesPerPixel( reader.getPixelType() );
    int xCount = reader.getSizeX();
    int yCount = reader.getSizeY();
    boolean isInterleaved = reader.isInterleaved();
    boolean canDoDirect = xBegin == 0 && yBegin == 0 && xEnd == xCount-1 && yEnd == yCount-1 && rgbChannelCount == 1;

    BufferedOutputStream out = new BufferedOutputStream(System.out, 100*1024*1024);
    // System.err.println("canDoDirect = "+canDoDirect);

    for( int c=cBegin; c<=cEnd; c++ )
      {
      for( int t=tBegin; t<=tEnd; t++ )
        {
        for( int z=zBegin; z<=zEnd; z++ )
          {
          byte[] image = reader.openBytes( reader.getIndex(z, c, t) );
          if( canDoDirect )
            {
            out.write(image);
            }
          else
            {
            for( int y=yBegin; y<=yEnd; y++ )
              {
              for( int x=xBegin; x<=xEnd; x++ )
                {
                for( int i=0; i<rgbChannelCount; i++ )
                  {
                  for( int b=0; b<bpp; b++ )
                    {
                    int index = xCount * (yCount * (rgbChannelCount * b + i) + y) + x;
                    out.write( image[index] );
                    }
                  }
                }
              }
            }
          }
        }
      }
    out.flush();
    return true;
  }
  
  /**
   * 
   */
  public boolean write ( String fileName, int byteOrder, int dims,
		  int dim0, int dim1, int dim2, int dim3, int dim4,
		  int pixelType, int rgbCCount, int xStart, int yStart,
		  int zStart, int cStart, int tStart, int xCount, int yCount,
		  int zCount, int cCount, int tCount) throws IOException, FormatException
  {
	  IMetadata meta = MetadataTools.createOMEXMLMetadata();
	  meta.createRoot();
	  meta.setPixelsID("Pixels:0", 0);
	  meta.setPixelsDimensionOrder(DimensionOrder.XYZCT, 0);

	  try {
		  meta.setPixelsType(PixelType.fromString(FormatTools.getPixelTypeString(pixelType)), 0);

	  } catch (EnumerationException e) {
		  throw new IOException(e.getMessage(), e.getCause());
	  }
	  
	  if(byteOrder == 0)
		  meta.setPixelsBinDataBigEndian(new Boolean("true"), 0, 0);
	  else
		  meta.setPixelsBinDataBigEndian(new Boolean("false"), 0, 0);
	  
	  meta.setPixelsSizeX(new PositiveInteger(new Integer(dim0)), 0);
	  meta.setPixelsSizeY(new PositiveInteger(new Integer(dim1)), 0);
	  meta.setPixelsSizeZ(new PositiveInteger(new Integer(dim2)), 0);
	  meta.setPixelsSizeC(new PositiveInteger(new Integer(dim3)), 0);
	  meta.setPixelsSizeT(new PositiveInteger(new Integer(dim4)), 0);
	  meta.setChannelID("Channel:0:0", 0, 0);
	  meta.setChannelSamplesPerPixel(new PositiveInteger(new Integer(1)), 0, 0);
	  
	  ImageWriter writer = new ImageWriter();
	  writer.setMetadataRetrieve(meta);
	  writer.setId(fileName);
	  
	  int bpp = FormatTools.getBytesPerPixel(pixelType);
	  
	  int bytesPerPlane = xCount * yCount * bpp * rgbCCount;
	  
	  int numIters = (cCount - cStart) * (tCount - tStart) * (zCount - zStart);
	  
	  // tell native code how many times to iterate & how big each iteration is
	  System.out.println(bytesPerPlane + "\n" + numIters + "\n");
	  System.out.flush();
	  
	  byte[] buf = new byte[bytesPerPlane];
	  
	  final BufferedReader in =
		  new BufferedReader(new InputStreamReader(System.in));
	  
	  String line = "";
	  int no = 0;
	  for(int c=cStart; c<cStart+cCount; c++) {
		  for(int t=tStart; t<tStart+tCount; t++) {
			  for(int z=zStart; z<zStart+zCount; z++) {
				  line = in.readLine(); // blocks for input, waiting for next set of bytes
				  buf = line.getBytes();
				  writer.saveBytes(no++, buf, xStart, yStart, xCount, yCount);
			  }
		  }
	  }
	  
	  in.close();
	  writer.close();
	  return true;
  }

  /** Tests whether the given file path can be parsed by Bio-Formats. */
  public boolean canRead(String filePath)
    throws FormatException, IOException
  {
    createReader(null);
    final boolean canRead = reader.isThisType(filePath);
    System.out.println(canRead);
    System.out.flush();
    return true;
  }

  private IFormatReader createReader(final String filePath)
    throws FormatException, IOException
  {
    if( readerPath == null ) {
      // use the not yet used reader
      reader.setId(filePath);
      reader.setSeries(0);
      return reader;
      }

    if(readerPath.equals( filePath )) {
      // just use the existing reader
      return reader;
    }

    if (reader != null) {
      reader.close();
    }
    System.err.println("Creating new reader for "+filePath);
    // initialize a fresh reader
    reader = new ImageReader();
    readerPath = filePath;

    reader.setMetadataFiltered(true);
    reader.setOriginalMetadataPopulated(true);
    final MetadataStore store = MetadataTools.createOMEXMLMetadata();
    if (store == null) System.err.println("OME-Java library not found.");
    else reader.setMetadataStore(store);

    // avoid grouping all the .lsm when a .mdb is there
    reader.setGroupFiles(false);

    if (filePath != null) {
      reader.setId(filePath);
      reader.setSeries(0);
    }

    return reader;
  }

  public boolean exit()
    throws FormatException, IOException
  {
    reader.close();
    System.exit(0);
    return true;
  }

  // -- Main method --

  public static void main(String[] args) throws FormatException, IOException {
    if(args[0].equals("info")) {
      if (!new ITKBridgePipes().readImageInfo(args[1])) System.exit(1);
    }
    else if(args[0].equals("read")) {
      int xBegin = Integer.parseInt( args[2] );
      int xEnd =   Integer.parseInt( args[3] ) + xBegin - 1;
      int yBegin = Integer.parseInt( args[4] );
      int yEnd =   Integer.parseInt( args[5] ) + yBegin - 1;
      int zBegin = Integer.parseInt( args[6] );
      int zEnd =   Integer.parseInt( args[7] ) + zBegin - 1;
      int tBegin = Integer.parseInt( args[8] );
      int tEnd =   Integer.parseInt( args[9] ) + tBegin - 1;
      int cBegin = Integer.parseInt( args[10] );
      int cEnd =   Integer.parseInt( args[11] ) + cBegin - 1;
      if (!new ITKBridgePipes().read(args[1], xBegin, xEnd, yBegin, yEnd, zBegin, zEnd, tBegin, tEnd, cBegin, cEnd)) System.exit(1);
    }
    else if(args[0].equals("canRead")) {
      if (!new ITKBridgePipes().canRead(args[1])) System.exit(1);
    }
    else if(args[0].equals("waitForInput")) {
      new ITKBridgePipes().waitForInput();
    }
    else if(args[0].equals("write")) {
    	int byteOrder = Integer.parseInt( args[2] );
    	int dims = Integer.parseInt( args[3] );
    	int dim0 = Integer.parseInt( args[4] );
    	int dim1 = Integer.parseInt( args[5] );
    	int dim2 = Integer.parseInt( args[6] );
    	int dim3 = Integer.parseInt( args[7] );
    	int dim4 = Integer.parseInt( args[8] );
    	int pixelType = Integer.parseInt( args[9] );
    	int rgbCCount = Integer.parseInt( args[10] );
    	int xStart = Integer.parseInt( args[11] );
    	int yStart = Integer.parseInt( args[13] );
    	int zStart = Integer.parseInt( args[15] );
    	int cStart = Integer.parseInt( args[17] );
    	int tStart = Integer.parseInt( args[19] );
    	int xCount = Integer.parseInt( args[12] );
    	int yCount = Integer.parseInt( args[14] );
    	int zCount = Integer.parseInt( args[16] );
    	int cCount = Integer.parseInt( args[18] );
    	int tCount = Integer.parseInt( args[20] );
    	if(!new ITKBridgePipes().write(args[1], byteOrder, dims, dim0, dim1, dim2, dim3, dim4, pixelType, rgbCCount, xStart, yStart, zStart, cStart, tStart, xCount, yCount, zCount, cCount, tCount)) System.exit(1);
    }
    else {
      System.err.println("Error: unknown command: "+args[0]);
      System.exit(1);
    }
  }

}
