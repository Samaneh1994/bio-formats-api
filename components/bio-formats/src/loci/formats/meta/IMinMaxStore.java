//
// IMinMaxStore.java
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

package loci.formats.meta;

/**
 * Interface for consumers of minima and maxima pixel intensities across a
 * given wavelength.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://dev.loci.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/meta/IMinMaxStore.java">Trac</a>,
 * <a href="http://dev.loci.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/meta/IMinMaxStore.java">SVN</a></dd></dl>
 *
 * @author Chris Allan callan at blackcat.ca
 */
public interface IMinMaxStore {

  /**
   * Populates the channel global minimum and maximum.
   * @param channel Channel index to populate.
   * @param minimum Minimum global pixel intensity.
   * @param maximum Maximum global pixel intensity.
   * @param series Image series.
   */
  void setChannelGlobalMinMax(int channel, double minimum, double maximum,
    int series);

}
