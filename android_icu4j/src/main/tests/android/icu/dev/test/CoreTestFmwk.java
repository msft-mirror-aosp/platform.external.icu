/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2023 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.dev.test;

import org.junit.Rule;
import org.junit.rules.TestName;

import android.icu.util.ULocale;
import android.icu.testsharding.MainTestShard;

/**
 * A base class for testing within the cross-component tests in the `common_tests` component.
 *
 * <p>This subclass of TestFmwk allows the usage of functionality from the `core` component. TestFmwk
 * is not able to depend on `core` since it is located in `framework`, while `core` tests already
 * use TestFmwk, which requires that `core` depends on `framework`. We cannot have a cycle in our
 * dependencies.
 *
 * <p>With the allowance for this test class to use functionality from components, we can do things
 * like assert whether tests are properly resetting the ICU and JDK time zones to their original
 * values at the end of their execution.
 */
@MainTestShard
public class CoreTestFmwk extends TestFmwk {

  private android.icu.util.TimeZone testStartDefaultIcuTz;

  private java.util.TimeZone testStartDefaultJdkTz;

  private android.icu.util.ULocale testStartDefaultULocale;

  private java.util.Locale testStartDefaultLocale;

  @Rule
  public TestName name = new TestName();

  @Override
  public void localTestInitialize() {
    super.localTestInitialize();

    // Just like TestFmwk initializes JDK TimeZone and Locale before every test,
    // do the same for ICU TimeZone and ULocale.
    ULocale.setDefault(ULocale.forLocale(defaultLocale));
    android.icu.util.TimeZone.setDefault(
        android.icu.util.TimeZone.getTimeZone(defaultTimeZone.getID()));

    // Save starting timezones
    testStartDefaultIcuTz = android.icu.util.TimeZone.getDefault();
    testStartDefaultJdkTz = java.util.TimeZone.getDefault();

    // Save starting locales
    testStartDefaultULocale = android.icu.util.ULocale.getDefault();
    testStartDefaultLocale = java.util.Locale.getDefault();
  }

  @Override
  public void localTestTeardown() {
    String testMethodName = name.getMethodName();

    // Assert that timezones are in a good state

    android.icu.util.TimeZone testEndDefaultIcuTz = android.icu.util.TimeZone.getDefault();
    java.util.TimeZone testEndDefaultJdkTz = java.util.TimeZone.getDefault();

    assertEquals("In [" + testMethodName + "] Test should keep in sync ICU & JDK TZs",
        testEndDefaultIcuTz.getID(),
        testEndDefaultJdkTz.getID());

    assertEquals("In [" + testMethodName + "] Test should reset ICU default TZ",
        testStartDefaultIcuTz.getID(), testEndDefaultIcuTz.getID());
    assertEquals("In [" + testMethodName + "] Test should reset JDK default TZ",
        testStartDefaultJdkTz.getID(), testEndDefaultJdkTz.getID());

    // Assert that locales are in a good state

    android.icu.util.ULocale testEndDefaultULocale = android.icu.util.ULocale.getDefault();
    java.util.Locale testEndDefaultLocale = java.util.Locale.getDefault();

    assertEquals("In [" + testMethodName + "] Test should reset ICU ULocale",
        testStartDefaultULocale.toLanguageTag(), testEndDefaultULocale.toLanguageTag());
    assertEquals("In [" + testMethodName + "] Test should reset JDK Locale",
        testStartDefaultLocale.toLanguageTag(), testEndDefaultLocale.toLanguageTag());

    super.localTestTeardown();
  }

}
