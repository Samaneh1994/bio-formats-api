/*
 * #%L
 * Top-level reader and writer APIs
 * %%
 * Copyright (C) 2016 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package loci.formats.utests;

import java.io.File;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import loci.formats.FormatOptions;


/**
 * Unit tests for {@link loci.formats.in.FormatOptions}.
 */
public class FormatOptionsTest {

  private static final String KEY = "test.key";
  private static final double DDELTA = 1e-7;
  private static final float FDELTA = 1e-7f;

  private FormatOptions opt;
  private enum One { FOO, BAR };
  private enum Two { TAR, FOO };

  private static void assertAlmostEquals(Double actual, Double expected) {
    assertEquals(actual, expected, DDELTA);
  }

  private static void assertAlmostEquals(Float actual, Float expected) {
    assertEquals(actual, expected, FDELTA);
  }

  @DataProvider(name = "booleanStrings")
  public Object[][] mkBools() {
    return new Object[][] {{"FALSE", "TRUE"},
                           {"False", "True"},
                           {"false", "true"}};
  }

  @DataProvider(name = "integerCases")
  public Object[][] mkInts() {
    return new Object[][] {{"30", new Integer(30)},
                           {"-30", new Integer(-30)}};
  }

  @DataProvider(name = "longCases")
  public Object[][] mkLongs() {
    return new Object[][] {{"30", new Long(30L)},
                           {"-30", new Long(-30L)}};
  }

  @DataProvider(name = "floatCases")
  public Object[][] mkFloats() {
    return new Object[][] {{"3.14", new Float(3.14f)},
                           {"03.14", new Float(3.14f)},
                           {"-3.14", new Float(-3.14f)}};
  }

  @DataProvider(name = "doubleCases")
  public Object[][] mkDoubles() {
    return new Object[][] {{"3.14", new Double(3.14)},
                           {"03.14", new Double(3.14)},
                           {"-3.14", new Double(-3.14)}};
  }

  @BeforeMethod
  public void setUp() {
    opt = new FormatOptions();
  }

  @Test
  public void testString() {
    assertNull(opt.get(KEY));
    assertNull(opt.get(KEY), null);
    assertEquals(opt.get(KEY, "default"), "default");
    opt.set(KEY, "v");
    assertEquals(opt.get(KEY, "default"), "v");
    assertEquals(opt.get(KEY, null), "v");
    opt.set(KEY, null);
    assertNull(opt.get(KEY));
    assertNull(opt.get(KEY), null);
    assertEquals(opt.get(KEY, "default"), "default");
  }

  @Test
  public void testEnum() {
    assertEquals(opt.getEnum(KEY, One.BAR), One.BAR);
    opt.setEnum(KEY, One.FOO);
    assertEquals(opt.getEnum(KEY, One.BAR), One.FOO);
    assertEquals(opt.getEnum(KEY, Two.TAR), Two.FOO);
    opt.set(KEY, "TAR");
    assertEquals(opt.getEnum(KEY, Two.FOO), Two.TAR);
    opt.setEnum(KEY, null);
    assertEquals(opt.getEnum(KEY, Two.FOO), Two.FOO);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testBadEnum() {
    opt.setEnum(KEY, One.BAR);
    opt.getEnum(KEY, Two.FOO);
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testEnumNullDefault() {
    opt.getEnum(KEY, null);
  }

  @Test
  public void testBoolean() {
    assertFalse(opt.getBoolean(KEY, false));
    assertTrue(opt.getBoolean(KEY, true));
    assertNull(opt.getBoolean(KEY, null));
    opt.setBoolean(KEY, false);
    assertFalse(opt.getBoolean(KEY, true));
    assertFalse(opt.getBoolean(KEY, null));
    opt.setBoolean(KEY, true);
    assertTrue(opt.getBoolean(KEY, false));
    assertTrue(opt.getBoolean(KEY, null));
    opt.setBoolean(KEY, null);
    assertFalse(opt.getBoolean(KEY, false));
    assertTrue(opt.getBoolean(KEY, true));
    assertNull(opt.getBoolean(KEY, null));
  }

  @Test(dataProvider = "booleanStrings")
  public void testBooleanFromString(String falseString, String trueString) {
    opt.set(KEY, falseString);
    assertFalse(opt.getBoolean(KEY, true));
    opt.set(KEY, trueString);
    assertTrue(opt.getBoolean(KEY, false));
  }

  @Test(expectedExceptions = IllegalArgumentException.class)
  public void testBadBoolean() {
    opt.set(KEY, "foo");
    opt.getBoolean(KEY, true);
  }

  @Test
  public void testInteger() {
    Integer one = 1;
    Integer two = 2;
    assertEquals(opt.getInteger(KEY, one), one);
    assertNull(opt.getInteger(KEY, null));
    opt.setInteger(KEY, two);
    assertEquals(opt.getInteger(KEY, one), two);
    assertEquals(opt.getInteger(KEY, null), two);
    opt.setInteger(KEY, null);
    assertEquals(opt.getInteger(KEY, one), one);
    assertNull(opt.getInteger(KEY, null));
  }

  @Test(dataProvider = "integerCases")
  public void testIntFromString(String intString, Integer expected) {
    opt.set(KEY, intString);
    assertEquals(opt.getInteger(KEY, 0), expected);
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void testBadInt() {
    opt.set(KEY, "2147483648");
    opt.getInteger(KEY, 0);
  }

  @Test
  public void testLong() {
    Long one = 1L;
    Long two = 2L;
    assertEquals(opt.getLong(KEY, one), one);
    assertNull(opt.getLong(KEY, null));
    opt.setLong(KEY, two);
    assertEquals(opt.getLong(KEY, one), two);
    assertEquals(opt.getLong(KEY, null), two);
    opt.setLong(KEY, null);
    assertEquals(opt.getLong(KEY, one), one);
    assertNull(opt.getLong(KEY, null));
  }

  @Test(dataProvider = "longCases")
  public void testLongFromString(String longString, Long expected) {
    opt.set(KEY, longString);
    assertEquals(opt.getLong(KEY, 0L), expected);
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void testBadLong() {
    opt.set(KEY, "9223372036854775808");
    opt.getLong(KEY, 0L);
  }

  @Test
  public void testFloat() {
    Float e = 2.72f;
    Float pi = 3.14f;
    assertAlmostEquals(opt.getFloat(KEY, e), e);
    assertNull(opt.getFloat(KEY, null));
    opt.setFloat(KEY, pi);
    assertAlmostEquals(opt.getFloat(KEY, e), pi);
    assertAlmostEquals(opt.getFloat(KEY, null), pi);
    opt.setFloat(KEY, null);
    assertAlmostEquals(opt.getFloat(KEY, e), e);
    assertNull(opt.getFloat(KEY, null));
  }

  @Test(dataProvider = "floatCases")
  public void testFloatFromString(String floatString, Float expected) {
    opt.set(KEY, floatString);
    assertAlmostEquals(opt.getFloat(KEY, 0f), expected);
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void testBadFloat() {
    opt.set(KEY, "foo");
    opt.getFloat(KEY, 0f);
  }

  @Test
  public void testDouble() {
    Double e = 2.72;
    Double pi = 3.14;
    assertAlmostEquals(opt.getDouble(KEY, e), e);
    assertNull(opt.getDouble(KEY, null));
    opt.setDouble(KEY, pi);
    assertAlmostEquals(opt.getDouble(KEY, e), pi);
    assertAlmostEquals(opt.getDouble(KEY, null), pi);
    opt.setDouble(KEY, null);
    assertAlmostEquals(opt.getDouble(KEY, e), e);
    assertNull(opt.getDouble(KEY, null));
  }

  @Test(dataProvider = "doubleCases")
  public void testDoubleFromString(String doubleString, Double expected) {
    opt.set(KEY, doubleString);
    assertAlmostEquals(opt.getDouble(KEY, 0.), expected);
  }

  @Test(expectedExceptions = NumberFormatException.class)
  public void testBadDouble() {
    opt.set(KEY, "foo");
    opt.getDouble(KEY, 0.);
  }

  @Test
  public void testClass() throws ClassNotFoundException {
    final Class<?> c1 = Thread.class;
    final Class<?> c2 = Number.class;
    assertEquals(opt.getClass(KEY, c1), c1);
    assertNull(opt.getClass(KEY, null));
    opt.setClass(KEY, c2);
    assertEquals(opt.getClass(KEY, null), c2);
    assertEquals(opt.getClass(KEY, c1), c2);
    opt.set(KEY, "java.lang.Thread");
    assertEquals(opt.getClass(KEY, c2), c1);
    opt.setClass(KEY, null);
    assertEquals(opt.getClass(KEY, c2), c2);
    assertNull(opt.getClass(KEY, null));
  }

  @Test(expectedExceptions = ClassNotFoundException.class)
  public void testBadClass() throws ClassNotFoundException {
    opt.set(KEY, "org.foo.Bar");
    opt.getClass(KEY, Thread.class);
  }

  @Test
  public void testFile() {
    final File f1 = new File("/foo/f1");
    final File f2 = new File("/foo/f2");
    assertEquals(opt.getFile(KEY, f1), f1);
    assertNull(opt.getFile(KEY, null));
    opt.setFile(KEY, f2);
    assertEquals(opt.getFile(KEY, f1), f2);
    assertEquals(opt.getFile(KEY, null), f2);
    opt.set(KEY, "/foo/f1");
    assertEquals(opt.getFile(KEY, f2), f1);
    opt.setFile(KEY, null);
    assertEquals(opt.getFile(KEY, f2), f2);
    assertNull(opt.getFile(KEY, null));
  }

}
