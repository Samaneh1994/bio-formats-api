/*
 * loci.formats.enums.handler.DetectorTypeHandler
 *
 *-----------------------------------------------------------------------------
 *
 *  Copyright (C) 2005-@year@ Open Microscopy Environment
 *      Massachusetts Institute of Technology,
 *      National Institutes of Health,
 *      University of Dundee,
 *      University of Wisconsin-Madison
 *
 *
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation; either
 *    version 2.1 of the License, or (at your option) any later version.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 *
 *    You should have received a copy of the GNU Lesser General Public
 *    License along with this library; if not, write to the Free Software
 *    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *-----------------------------------------------------------------------------
 */

/*-----------------------------------------------------------------------------
 *
 * THIS IS AUTOMATICALLY GENERATED CODE.  DO NOT MODIFY.
 * Created by callan via xsd-fu on 2009-10-28 16:52:37+0000
 *
 *-----------------------------------------------------------------------------
 */

package loci.formats.enums.handler;

import java.util.Hashtable;
import java.util.List;

import loci.formats.enums.Enumeration;
import loci.formats.enums.EnumerationException;
import loci.formats.enums.DetectorType;

/**
 * Enumeration handler for DetectorType.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/enums/handler/DetectorTypeHandler.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/enums/handler/DetectorTypeHandler.java">SVN</a></dd></dl>
 */
public class DetectorTypeEnumHandler implements IEnumerationHandler {

  // -- Fields --

  /** Every DetectorType value must match one of these patterns. */
  private static final Hashtable<String, String> patterns = makePatterns();

  private static Hashtable<String, String> makePatterns() {
    Hashtable<String, String> p = new Hashtable<String, String>();
    p.put("^\\s*CCD", "CCD");
    p.put("^\\s*IntensifiedCCD", "IntensifiedCCD");
    p.put("^\\s*AnalogVideo", "AnalogVideo");
    p.put("^\\s*PMT", "PMT");
    p.put("^\\s*Photodiode", "Photodiode");
    p.put("^\\s*Spectroscopy", "Spectroscopy");
    p.put("^\\s*LifetimeImaging", "LifetimeImaging");
    p.put("^\\s*CorrelationSpectroscopy", "CorrelationSpectroscopy");
    p.put("^\\s*FTIR", "FTIR");
    p.put("^\\s*EMCCD", "EMCCD");
    p.put("^\\s*APD", "APD");
    p.put("^\\s*CMOS", "CMOS");
    p.put("^\\s*Other", "Other");
    return p;
  }

  // -- IEnumerationHandler API methods --

  /* @see IEnumerationHandler#getEnumeration(String) */
  public Enumeration getEnumeration(String value)
    throws EnumerationException
  {
    for (String pattern : patterns.keySet()) {
      if (value.matches(pattern)) {
        String v = patterns.get(pattern);
        return DetectorType.fromString(v);
      }
    }
    throw new EnumerationException(this.getClass().getName() +
     " could not find enumeration for " + value);
  }

  /* @see IEnumerationHandler#getEntity() */
  public Class<? extends Enumeration> getEntity() {
    return DetectorType.class;
  }

}
