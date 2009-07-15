//
// XMLTools.java
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

package loci.formats;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import loci.common.LogTools;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A utility class for working with XML (not necessarily OME-XML).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/XMLTools.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/XMLTools.java">SVN</a></dd></dl>
 */
public final class XMLTools {

  // -- Constructor --

  private XMLTools() { }

  // -- Utility methods --

  /**
   * Attempts to validate the given XML string using
   * Java's XML validation facility. Requires Java 1.5+.
   * @param xml The XML string to validate.
   */
  public static void validateXML(String xml) { validateXML(xml, null); }

  /**
   * Attempts to validate the given XML string using
   * Java's XML validation facility. Requires Java 1.5+.
   * @param xml The XML string to validate.
   * @param label String describing the type of XML being validated.
   */
  public static void validateXML(String xml, String label) {
    if (label == null) label = "XML";

    // get path to schema from root element using SAX
    LogTools.println("Parsing schema path");
    ValidationSAXHandler saxHandler = new ValidationSAXHandler();
    SAXParserFactory saxFactory = SAXParserFactory.newInstance();
    Exception exception = null;
    try {
      SAXParser saxParser = saxFactory.newSAXParser();
      InputStream is = new ByteArrayInputStream(xml.getBytes());
      saxParser.parse(is, saxHandler);
    }
    catch (ParserConfigurationException exc) { exception = exc; }
    catch (SAXException exc) { exception = exc; }
    catch (IOException exc) { exception = exc; }
    if (exception != null) {
      LogTools.println("Error parsing schema path from " + label + ":");
      LogTools.trace(exception);
      return;
    }
    String schemaPath = saxHandler.getSchemaPath();
    if (schemaPath == null) {
      LogTools.println("No schema path found. Validation cannot continue.");
      return;
    }
    else LogTools.println(schemaPath);

    LogTools.println("Validating " + label);

    // look up a factory for the W3C XML Schema language
    String xmlSchemaPath = "http://www.w3.org/2001/XMLSchema";
    SchemaFactory factory = SchemaFactory.newInstance(xmlSchemaPath);

    // compile the schema
    URL schemaLocation = null;
    try {
      schemaLocation = new URL(schemaPath);
    }
    catch (MalformedURLException exc) {
      LogTools.println("Error accessing schema at " + schemaPath + ":");
      LogTools.trace(exc);
      return;
    }
    Schema schema = null;
    try {
      schema = factory.newSchema(schemaLocation);
    }
    catch (SAXException exc) {
      LogTools.println("Error parsing schema at " + schemaPath + ":");
      LogTools.trace(exc);
      return;
    }

    // get a validator from the schema
    Validator validator = schema.newValidator();

    // prepare the XML source
    StringReader reader = new StringReader(xml);
    InputSource is = new InputSource(reader);
    SAXSource source = new SAXSource(is);

    // validate the XML
    ValidationErrorHandler errorHandler = new ValidationErrorHandler();
    validator.setErrorHandler(errorHandler);
    try {
      validator.validate(source);
    }
    catch (IOException exc) { exception = exc; }
    catch (SAXException exc) { exception = exc; }
    if (exception != null) {
      LogTools.println("Error validating document:");
      LogTools.trace(exception);
      return;
    }
    if (errorHandler.ok()) LogTools.println("No validation errors found.");
  }

  /** Indents XML to be more readable. */
  public static String indentXML(String xml) {
    return indentXML(xml, 3, false);
  }

  /** Indents XML by the given spacing to be more readable. */
  public static String indentXML(String xml, int spacing) {
    return indentXML(xml, spacing, false);
  }

  /**
   * Indents XML by the given spacing to be more readable, avoiding any
   * whitespace injection into CDATA if the preserveCData flag is set.
   */
  public static String indentXML(String xml, int spacing,
    boolean preserveCData)
  {
    if (xml == null) return null; // garbage in, garbage out
    StringBuffer sb = new StringBuffer();
    StringTokenizer st = new StringTokenizer(xml, "<>", true);
    int indent = 0, noSpace = 0;
    boolean first = true, element = false;
    while (st.hasMoreTokens()) {
      String token = st.nextToken().trim();
      if (token.equals("")) continue;
      if (token.equals("<")) {
        element = true;
        continue;
      }
      if (element && token.equals(">")) {
        element = false;
        continue;
      }

      if (!element && preserveCData) noSpace = 2;

      if (noSpace == 0) {
        // advance to next line
        if (first) first = false;
        else sb.append("\n");
      }

      // adjust indent backwards
      if (element && token.startsWith("/")) indent -= spacing;

      if (noSpace == 0) {
        // apply indent
        for (int j=0; j<indent; j++) sb.append(" ");
      }

      // output element contents
      if (element) sb.append("<");
      sb.append(token);
      if (element) sb.append(">");

      if (noSpace == 0) {
        // adjust indent forwards
        if (element &&
          !token.startsWith("?") && // ?xml tag, probably
          !token.startsWith("/") && // end element
          !token.endsWith("/") && // standalone element
          !token.startsWith("!")) // comment
        {
          indent += spacing;
        }
      }

      if (noSpace > 0) noSpace--;
    }
    sb.append("\n");
    return sb.toString();
  }

  // -- Helper classes --

  /** Used by validateXML to parse the XML block's schema path using SAX. */
  private static class ValidationSAXHandler extends DefaultHandler {
    private String schemaPath;
    private boolean first;
    public String getSchemaPath() { return schemaPath; }
    public void startDocument() {
      schemaPath = null;
      first = true;
    }
    public void startElement(String uri,
      String localName, String qName, Attributes attributes)
    {
      if (!first) return;
      first = false;

      int len = attributes.getLength();
      String xmlns = null, xsiSchemaLocation = null;
      for (int i=0; i<len; i++) {
        String name = attributes.getQName(i);
        if (name.equals("xmlns")) xmlns = attributes.getValue(i);
        else if (name.equals("schemaLocation") ||
          name.endsWith(":schemaLocation"))
        {
          xsiSchemaLocation = attributes.getValue(i);
        }
      }
      if (xmlns == null || xsiSchemaLocation == null) return; // not found

      StringTokenizer st = new StringTokenizer(xsiSchemaLocation);
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        if (xmlns.equals(token)) {
          // next token is the actual schema path
          if (st.hasMoreTokens()) schemaPath = st.nextToken();
          break;
        }
      }
    }
  }

  /** Used by validateXML to handle XML validation errors. */
  private static class ValidationErrorHandler implements ErrorHandler {
    private boolean ok = true;
    public boolean ok() { return ok; }
    public void error(SAXParseException e) {
      LogTools.println("error: " + e.getMessage());
      ok = false;
    }
    public void fatalError(SAXParseException e) {
      LogTools.println("fatal error: " + e.getMessage());
      ok = false;
    }
    public void warning(SAXParseException e) {
      LogTools.println("warning: " + e.getMessage());
      ok = false;
    }
  }

}
