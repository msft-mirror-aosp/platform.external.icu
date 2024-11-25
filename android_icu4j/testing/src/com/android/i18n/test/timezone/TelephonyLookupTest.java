/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.i18n.test.timezone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.icu.testsharding.MainTestShard;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.i18n.timezone.MobileCountries;
import com.android.i18n.timezone.TelephonyLookup;
import com.android.i18n.timezone.TelephonyNetwork;
import com.android.i18n.timezone.TelephonyNetworkFinder;
import com.android.icu.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

@RunWith(ParameterizedAndroidJunit4.class)
@MainTestShard
@UsesFlags(com.android.icu.Flags.class)
public class TelephonyLookupTest {
    @ClassRule
    public static final SetFlagsRule.ClassRule mSetFlagsClassRule = new SetFlagsRule.ClassRule();

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_TELEPHONY_LOOKUP_MCC_EXTENSION);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule;

    private Path testDir;

    public TelephonyLookupTest(FlagsParameterization flags) {
        mSetFlagsRule = mSetFlagsClassRule.createSetFlagsRule(flags);
    }

    @Before
    public void setUp() throws Exception {
        testDir = Files.createTempDirectory("TelephonyLookupTest");
    }

    @After
    public void tearDown() throws Exception {
        // Delete the testDir and all contents.
        Files.walkFileTree(testDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Test
    public void createInstanceWithFallback() throws Exception {
        String validXml1 = """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        TelephonyNetwork expectedTelephonyNetwork1 =
                TelephonyNetwork.create("123", "456", "gb");
        MobileCountries expectedMobileCountries1 =
                MobileCountries.create("202", Set.of("gr"), "gr");

        String validXml2 = """
                <telephony_lookup>
                  <networks>
                    <network mcc="234" mnc="567" country="fr"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="505" default="au">
                      <country>au</country>
                      <country>nf</country>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        TelephonyNetwork expectedTelephonyNetwork2 =
                TelephonyNetwork.create("234", "567", "fr");
        MobileCountries expectedMobileCountries2 =
                MobileCountries.create("505", Set.of("au", "nf"), "au");

        String invalidXml = "<foo></foo>\n";
        checkValidateThrowsParserException(invalidXml);

        String validFile1 = createFile(validXml1);
        String validFile2 = createFile(validXml2);
        String invalidFile = createFile(invalidXml);
        String missingFile = createMissingFile();

        TelephonyLookup file1ThenFile2 =
                TelephonyLookup.createInstanceWithFallback(validFile1, validFile2);
        assertEquals(list(expectedTelephonyNetwork1),
                file1ThenFile2.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(list(expectedMobileCountries1),
                    file1ThenFile2.getTelephonyNetworkFinder().getAllMobileCountries());
        } else {
            assertEmpty(file1ThenFile2.getTelephonyNetworkFinder().getAllMobileCountries());
        }

        TelephonyLookup missingFileThenFile1 =
                TelephonyLookup.createInstanceWithFallback(missingFile, validFile1);
        assertEquals(list(expectedTelephonyNetwork1),
                missingFileThenFile1.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(list(expectedMobileCountries1),
                    missingFileThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());
        } else {
            assertEmpty(missingFileThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());
        }

        TelephonyLookup file2ThenFile1 =
                TelephonyLookup.createInstanceWithFallback(validFile2, validFile1);
        assertEquals(list(expectedTelephonyNetwork2),
                file2ThenFile1.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(list(expectedMobileCountries2),
                    file2ThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());
        } else {
            assertEmpty(file2ThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());
        }

        // We assume the file has been validated so an invalid file is not checked ahead of time.
        // We will find out when we look something up.
        TelephonyLookup invalidThenValid =
                TelephonyLookup.createInstanceWithFallback(invalidFile, validFile1);
        assertNull(invalidThenValid.getTelephonyNetworkFinder());

        // This is not a normal case: It would imply a device shipped without a file anywhere!
        TelephonyLookup missingFiles =
                TelephonyLookup.createInstanceWithFallback(missingFile, missingFile);
        assertEmpty(missingFiles.getTelephonyNetworkFinder().getAllNetworks());
        assertEmpty(missingFiles.getTelephonyNetworkFinder().getAllMobileCountries());
    }

    @Test
    public void xmlParsing_emptyFile() {
        checkValidateThrowsParserException("");
    }

    @Test
    public void xmlParsing_unexpectedRootElement() {
        checkValidateThrowsParserException("<foo></foo>\n");
    }

    @Test
    public void xmlParsing_missingNetworks() {
        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="505" default="au">
                      <country>au</country>
                      <country>nf</country>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void xmlParsing_missingMobileCountries() {
        assumeTrue(Flags.telephonyLookupMccExtension());
        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="234" mnc="567" country="fr"/>
                  </networks>
                </telephony_lookup>
                """);
    }

    @Test
    public void xmlParsing_emptyNetworkOk() throws Exception {
        {
            TelephonyLookup telephonyLookup =
                    validate("""
                            <telephony_lookup>
                              <networks></networks>
                              <mobile_countries>
                                <mobile_country mcc="505" default="au">
                                  <country>au</country>
                                </mobile_country>
                              </mobile_countries>
                            </telephony_lookup>
                            """);
            TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup
                    .getTelephonyNetworkFinder();
            assertEquals(list(), telephonyNetworkFinder.getAllNetworks());
        }
        {
            TelephonyLookup telephonyLookup =
                    validate("""
                            <telephony_lookup>
                              <networks/>
                              <mobile_countries>
                                <mobile_country mcc="505" default="au">
                                  <country>au</country>
                                </mobile_country>
                              </mobile_countries>
                            </telephony_lookup>
                            """);
            TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup
                    .getTelephonyNetworkFinder();
            assertEquals(list(), telephonyNetworkFinder.getAllNetworks());
        }
    }

    @Test
    public void xmlParsing_emptyMobileCountries() throws Exception {
        assumeTrue(Flags.telephonyLookupMccExtension());

        {
            TelephonyLookup telephonyLookup =
                    validate("""
                            <telephony_lookup>
                             <networks/>
                             <mobile_countries>
                             </mobile_countries>
                            </telephony_lookup>
                            """);
            TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup
                    .getTelephonyNetworkFinder();
            assertEmpty(telephonyNetworkFinder.getAllMobileCountries());
        }
        {
            TelephonyLookup telephonyLookup =
                    validate("""
                             <telephony_lookup>
                              <networks/>
                              <mobile_countries/>
                             </telephony_lookup>
                             """);
            TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup
                    .getTelephonyNetworkFinder();
            assertEmpty(telephonyNetworkFinder.getAllMobileCountries());
        }
    }

    @Test
    public void xmlParsing_unexpectedComments() throws Exception {
        TelephonyNetwork expectedTelephonyNetwork =
                TelephonyNetwork.create("123", "456", "gb");
        MobileCountries expectedMobileCountries =
                MobileCountries.create("202", Set.of("gr"), "gr");

        TelephonyLookup telephonyLookup = validate("""
                <telephony_lookup>
                  <networks>
                    <!-- This is a comment -->
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <!-- This is a comment -->
                  <mobile_countries>
                    <!-- This is a comment -->
                    <mobile_country mcc="202">
                      <!-- This is a comment -->
                      <country>gr</country>
                    </mobile_country>
                  </mobile_countries>
                  <!-- This is a comment -->
                </telephony_lookup>
                """);
        assertEquals(list(expectedTelephonyNetwork),
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(list(expectedMobileCountries),
                    telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
        }
    }

    @Test
    public void xmlParsing_unexpectedElementsIgnored() throws Exception {
        TelephonyNetwork expectedTelephonyNetwork =
                TelephonyNetwork.create("123", "456", "gb");
        List<TelephonyNetwork> expectedNetworks = list(expectedTelephonyNetwork);
        MobileCountries expectedMobileCountries =
                MobileCountries.create("202", Set.of("gr"), "gr");
        List<MobileCountries> expectedMobileCountriesList = list(expectedMobileCountries);

        String unexpectedElement = "<unexpected-element>\n<a /></unexpected-element>\n";

        // These tests are important because they ensure we can extend the format in future with
        // more information but could continue using the same file on older devices.
        TelephonyLookup telephonyLookup = validate("<telephony_lookup>\n"
                + " " + unexpectedElement
                + "  <networks>\n"
                + "    " + unexpectedElement
                + "    <network mcc=\"123\" mnc=\"456\" country=\"gb\"/>\n"
                + "    " + unexpectedElement
                + "  </networks>\n"
                + "  " + unexpectedElement
                + "  <mobile_countries>\n"
                + "   " + unexpectedElement
                + "    <mobile_country mcc=\"202\">\n"
                + "    " + unexpectedElement
                + "     <country>gr</country>\n"
                + "    " + unexpectedElement
                + "    </mobile_country>\n"
                + "   " + unexpectedElement
                + "  </mobile_countries>\n"
                + " " + unexpectedElement
                + "</telephony_lookup>\n");
        assertEquals(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(expectedMobileCountriesList,
                    telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
        }

        expectedNetworks = list(expectedTelephonyNetwork,
                TelephonyNetwork.create("234", "567", "fr"));
        expectedMobileCountriesList = list(expectedMobileCountries,
                MobileCountries.create("204", Set.of("nl"), "nl"));
        telephonyLookup = validate("<telephony_lookup>\n"
                + "  <networks>\n"
                + "    <network mcc=\"123\" mnc=\"456\" country=\"gb\"/>\n"
                + "    " + unexpectedElement
                + "    <network mcc=\"234\" mnc=\"567\" country=\"fr\"/>\n"
                + "  </networks>\n"
                + "  <mobile_countries>\n"
                + "    <mobile_country mcc=\"202\">\n"
                + "     <country>gr</country>\n"
                + "    </mobile_country>\n"
                + "   " + unexpectedElement
                + "    <mobile_country mcc=\"204\">\n"
                + "     <country>nl</country>\n"
                + "    </mobile_country>\n"
                + "  </mobile_countries>\n"
                + "</telephony_lookup>\n");
        assertEquals(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(expectedMobileCountriesList,
                    telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
        }
    }

    @Test
    public void xmlParsing_unexpectedTextIgnored() throws Exception {
        TelephonyNetwork expectedTelephonyNetwork =
                TelephonyNetwork.create("123", "456", "gb");
        List<TelephonyNetwork> expectedNetworks = list(expectedTelephonyNetwork);
        MobileCountries expectedMobileCountries =
                MobileCountries.create("202", Set.of("gr"), "gr");
        List<MobileCountries> expectedMobileCountriesList = list(expectedMobileCountries);

        String unexpectedText = "unexpected-text";
        TelephonyLookup telephonyLookup = validate("<telephony_lookup>\n"
                + "  " + unexpectedText
                + "  <networks>\n"
                + "  " + unexpectedText
                + "    <network mcc=\"123\" mnc=\"456\" country=\"gb\"/>\n"
                + "    " + unexpectedText
                + "  </networks>\n"
                + "  " + unexpectedText
                + "  <mobile_countries>\n"
                + "  " + unexpectedText
                + "    <mobile_country mcc=\"202\">\n"
                + "   " + unexpectedText
                + "     <country>gr</country>\n"
                + "   " + unexpectedText
                + "    </mobile_country>\n"
                + "  " + unexpectedText
                + "  </mobile_countries>\n"
                + " " + unexpectedText
                + "</telephony_lookup>\n");
        assertEquals(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(expectedMobileCountriesList,
                    telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
        } else {
            assertEmpty(telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
        }
    }

    @Test
    public void xmlParsing_truncatedInput() {
        checkValidateThrowsParserException("<telephony_lookup>\n");

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                """);

        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                    </mobile_country>
                  </mobile_countries>
                """);
    }

    @Test
    public void validateDuplicateMccMnc() {
        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" countryCode="gb"/>
                    <network mcc="123" mnc="456" countryCode="fr"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void validateDuplicateMcc() {
        assumeTrue(Flags.telephonyLookupMccExtension());
        checkValidateThrowsParserException("""
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" countryCode="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                    </mobile_country>
                    <mobile_country mcc="202">
                      <country>nl</country>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void validateCountryCodeLowerCase() {
        checkValidateThrowsParserException("""
                <telephony_lookup>
                 <networks>
                 <network mcc="123" mnc="456" countryCode="GB"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="202">
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);

        if (Flags.telephonyLookupMccExtension()) {
            checkValidateThrowsParserException("""
                    <telephony_lookup>
                      <networks>
                       <network mcc="123" mnc="456" countryCode="gb"/>
                      </networks>
                      <mobile_countries>
                       <mobile_country mcc="202">
                        <country>GR</country>
                       </mobile_country>
                      </mobile_countries>
                    </telephony_lookup>
                    """);
        }
    }

    @Test
    public void getTelephonyNetworkFinder() {
        TelephonyLookup telephonyLookup = TelephonyLookup.createInstanceFromString("""
                <telephony_lookup>
                  <networks>
                   <network mcc="123" mnc="456" country="gb"/>
                   <network mcc="234" mnc="567" country="fr"/>
                  </networks>
                  <mobile_countries>
                   <mobile_country mcc="202">
                     <country>gr</country>
                   </mobile_country>
                   <mobile_country mcc="505" default="au">
                     <country>au</country>
                     <country>nf</country>
                   </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);

        TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup.getTelephonyNetworkFinder();
        TelephonyNetwork expectedNetwork1 = TelephonyNetwork.create("123", "456", "gb");
        TelephonyNetwork expectedNetwork2 = TelephonyNetwork.create("234", "567", "fr");
        MobileCountries expectedMobileCountries1 =
                MobileCountries.create("202", Set.of("gr"), "gr");
        MobileCountries expectedMobileCountries2 =
                MobileCountries.create("505", Set.of("au", "nf"), "au");

        assertEquals(list(expectedNetwork1, expectedNetwork2),
                telephonyNetworkFinder.getAllNetworks());

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(list(expectedMobileCountries1, expectedMobileCountries2),
                    telephonyNetworkFinder.getAllMobileCountries());
        } else {
            assertEmpty(telephonyNetworkFinder.getAllMobileCountries());
        }

        assertEquals(expectedNetwork1, telephonyNetworkFinder.findNetworkByMccMnc("123", "456"));
        assertEquals(expectedNetwork2, telephonyNetworkFinder.findNetworkByMccMnc("234", "567"));
        assertNull(telephonyNetworkFinder.findNetworkByMccMnc("999", "999"));

        if (Flags.telephonyLookupMccExtension()) {
            assertEquals(expectedMobileCountries1,
                    telephonyNetworkFinder.findCountriesByMcc("202"));
            assertEquals(expectedMobileCountries2,
                    telephonyNetworkFinder.findCountriesByMcc("505"));
            assertNull(telephonyNetworkFinder.findCountriesByMcc("999"));
        }
    }

    @Test
    public void xmlParsing_networks_missingMccAttribute() {
        checkValidateThrowsParserException("""
                <telephony_lookup>
                 <networks>
                  <network mnc="456" country="gb"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="202">
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void xmlParsing_mobileCountries_missingMccAttribute() {
        assumeTrue(Flags.telephonyLookupMccExtension());
        checkValidateThrowsParserException("""
                <telephony_lookup>
                 <networks>
                  <network mcc="123" mnc="456" country="gb"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country>
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void xmlParsing_networks_missingMncAttribute() {
        TelephonyLookup telephonyLookup = TelephonyLookup.createInstanceFromString("""
                <telephony_lookup>
                 <networks>
                  <network mcc="123" country="gb"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="202">
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);
        assertNull(telephonyLookup.getTelephonyNetworkFinder());
    }

    @Test
    public void xmlParsing_network_missingCountryCodeAttribute() {
        TelephonyLookup telephonyLookup = TelephonyLookup.createInstanceFromString("""
                <telephony_lookup>
                 <networks>
                  <network mcc="123" mnc="456"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="202">
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);
        assertNull(telephonyLookup.getTelephonyNetworkFinder());
    }

    @Test
    public void xmlParsing_mobileCountry_missingCountryCode() {
        assumeTrue(Flags.telephonyLookupMccExtension());
        TelephonyLookup telephonyLookup = TelephonyLookup.createInstanceFromString("""
                <telephony_lookup>
                 <networks>
                  <network mcc="123" mnc="456" country="gb"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="202">
                   <country/>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);
        assertNull(telephonyLookup.getTelephonyNetworkFinder());
    }

    private static void checkValidateThrowsParserException(String xml) {
        assertThrows(IOException.class, () -> validate(xml));
    }

    private static TelephonyLookup validate(String xml) throws IOException {
        TelephonyLookup telephonyLookup = TelephonyLookup.createInstanceFromString(xml);
        telephonyLookup.validate();
        return telephonyLookup;
    }

    private static void assertEmpty(Collection<?> collection) {
        assertTrue("Expected empty:" + collection, collection.isEmpty());
    }

    private static <X> List<X> list(X... values) {
        return Arrays.asList(values);
    }

    private String createFile(String fileContent) throws IOException {
        Path filePath = Files.createTempFile(testDir, null, null);
        Files.write(filePath, fileContent.getBytes(StandardCharsets.UTF_8));
        return filePath.toString();
    }

    private String createMissingFile() throws IOException {
        Path filePath = Files.createTempFile(testDir, null, null);
        Files.delete(filePath);
        return filePath.toString();
    }
}
