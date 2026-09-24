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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.icu.testsharding.MainTestShard;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.i18n.timezone.MobileCountries;
import com.android.i18n.timezone.TelephonyLookup;
import com.android.i18n.timezone.TelephonyNetworkFinder;
import com.android.internal.telephony.MccTable;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@MainTestShard
public class TelephonyLookupTest {

    private Path testDir;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

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
        String validXml1 =
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="123">
                      <country>gr</country>
                      <override mnc="456">
                        <country>gb</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        MobileCountries expectedTelephonyNetwork1 =
                MobileCountries.create("123", "456", Set.of("gb"), "gb");
        MobileCountries expectedMobileCountries1 =
                MobileCountries.create("123", Set.of("gr"), "gr");

        String validXml2 =
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="234" mnc="567" country="fr"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="234" default="au">
                      <country>au</country>
                      <country>nf</country>
                      <override mnc="567">
                        <country>fr</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        MobileCountries expectedTelephonyNetwork2 =
                MobileCountries.create("234", "567", Set.of("fr"), "fr");
        MobileCountries expectedMobileCountries2 =
                MobileCountries.create("234", Set.of("au", "nf"), "au");

        String invalidXml = "<foo></foo>\n";
        checkValidateThrowsParserException(invalidXml);

        String validFile1 = createFile(validXml1);
        String validFile2 = createFile(validXml2);
        String invalidFile = createFile(invalidXml);
        String missingFile = createMissingFile();

        TelephonyLookup file1ThenFile2 =
                TelephonyLookup.createInstanceWithFallback(validFile1, validFile2);
        containsExactly(list(expectedTelephonyNetwork1),
                file1ThenFile2.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                list(expectedMobileCountries1),
                file1ThenFile2.getTelephonyNetworkFinder().getAllMobileCountries());

        TelephonyLookup missingFileThenFile1 =
                TelephonyLookup.createInstanceWithFallback(missingFile, validFile1);
        containsExactly(list(expectedTelephonyNetwork1),
                missingFileThenFile1.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                list(expectedMobileCountries1),
                missingFileThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());

        TelephonyLookup file2ThenFile1 =
                TelephonyLookup.createInstanceWithFallback(validFile2, validFile1);
        containsExactly(list(expectedTelephonyNetwork2),
                file2ThenFile1.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                list(expectedMobileCountries2),
                file2ThenFile1.getTelephonyNetworkFinder().getAllMobileCountries());

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
        checkValidateThrowsParserException(
                """
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
        checkValidateThrowsParserException(
                """
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
                    validate(
                            """
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
            assertEmpty(telephonyNetworkFinder.getAllNetworks());
        }
        {
            TelephonyLookup telephonyLookup =
                    validate(
                            """
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
            assertEmpty(telephonyNetworkFinder.getAllNetworks());
        }
    }

    @Test
    public void xmlParsing_emptyMobileCountries() throws Exception {
        {
            TelephonyLookup telephonyLookup =
                    validate(
                            """
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
                    validate(
                            """
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
        MobileCountries expectedTelephonyNetwork =
                MobileCountries.create("123", "456", Set.of("gb"), "gb");
        MobileCountries expectedMobileCountries =
                MobileCountries.create("123", Set.of("gr"), "gr");

        TelephonyLookup telephonyLookup =
                validate(
                        """
                        <telephony_lookup>
                          <networks>
                            <!-- This is a comment -->
                            <network mcc="123" mnc="456" country="gb"/>
                          </networks>
                          <!-- This is a comment -->
                          <mobile_countries>
                            <!-- This is a comment -->
                            <mobile_country mcc="123">
                              <!-- This is a comment -->
                              <country>gr</country>
                              <override mnc="456">
                                <!-- This is a comment -->
                                <country>gb</country>
                                <!-- This is a comment -->
                              </override>
                            </mobile_country>
                          </mobile_countries>
                          <!-- This is a comment -->
                        </telephony_lookup>
                        """);
        containsExactly(list(expectedTelephonyNetwork),
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                list(expectedMobileCountries),
                telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
    }

    @Test
    public void xmlParsing_unexpectedElementsIgnored() throws Exception {
        MobileCountries expectedTelephonyNetwork =
                MobileCountries.create("123", "456", Set.of("gb"), "gb");
        MobileCountries expectedTelephonyNetwork2 =
                MobileCountries.create("123", "222", Set.of("gf"), "gf");
        List<MobileCountries> expectedNetworks =
               list(expectedTelephonyNetwork, expectedTelephonyNetwork2);
        MobileCountries expectedMobileCountries =
                MobileCountries.create("123", Set.of("gr"), "gr");
        List<MobileCountries> expectedMobileCountriesList = list(expectedMobileCountries);

        String unexpectedElement = "<unexpected-element>\n<a /></unexpected-element>\n";
        String nestedUnexpectedElementWithSameName =
                """
                <unexpected-element>
                  <unexpected-element>
                    <a />
                  </unexpected-element>
                </unexpected-element>
                """;
        String nestedUnexpectedOverride =
                """
                <override mnc="222">
                  <country>gf</country>
                  <override mnc="333">
                    <country>us</country>
                  </override>
                </override>
                """;

        // These tests are important because they ensure we can extend the format in future with
        // more information but could continue using the same file on older devices.
        TelephonyLookup telephonyLookup = validate("<telephony_lookup>\n"
                + " " + unexpectedElement
                + "  <networks>\n"
                + "    " + unexpectedElement
                + "    <network mcc=\"123\" mnc=\"456\" country=\"gb\"/>\n"
                + "    <network mcc=\"123\" mnc=\"222\" country=\"gf\"/>\n"
                + "    " + unexpectedElement
                + "  </networks>\n"
                + "  " + unexpectedElement
                + "  <mobile_countries>\n"
                + "   " + unexpectedElement
                + "    <mobile_country mcc=\"123\">\n"
                + "    " + unexpectedElement
                + "     " + nestedUnexpectedElementWithSameName
                + "     <country>gr</country>\n"
                + "     " + unexpectedElement
                + "     " + nestedUnexpectedOverride
                + "     <override mnc=\"456\">"
                + "       " + unexpectedElement
                + "       " + nestedUnexpectedElementWithSameName
                + "       <country>gb</country>\n"
                + "       " + unexpectedElement
                + "     </override>\n"
                + "    " + unexpectedElement
                + "    </mobile_country>\n"
                + "   " + unexpectedElement
                + "  </mobile_countries>\n"
                + " " + unexpectedElement
                + "</telephony_lookup>\n");
        containsExactly(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                expectedMobileCountriesList,
                telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());

        expectedNetworks = list(expectedTelephonyNetwork,
                MobileCountries.create("234", "567", Set.of("fr"), "fr"));
        expectedMobileCountriesList = list(expectedMobileCountries,
                MobileCountries.create("234", Set.of("nl"), "nl"));
        telephonyLookup = validate("<telephony_lookup>\n"
                + "  <networks>\n"
                + "    <network mcc=\"123\" mnc=\"456\" country=\"gb\"/>\n"
                + "    " + unexpectedElement
                + "    <network mcc=\"234\" mnc=\"567\" country=\"fr\"/>\n"
                + "  </networks>\n"
                + "  <mobile_countries>\n"
                + "    <mobile_country mcc=\"123\">\n"
                + "     <country>gr</country>\n"
                + "     <override mnc=\"456\">\n"
                + "       <country>gb</country>\n"
                + "     </override>\n"
                + "    </mobile_country>\n"
                + "    " + unexpectedElement
                + "    <mobile_country mcc=\"234\">\n"
                + "     <country>nl</country>\n"
                + "     <override mnc=\"567\">\n"
                + "       <country>fr</country>\n"
                + "     </override>\n"
                + "    </mobile_country>\n"
                + "  </mobile_countries>\n"
                + "</telephony_lookup>\n");
        containsExactly(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                expectedMobileCountriesList,
                telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
    }

    @Test
    public void xmlParsing_unexpectedTextIgnored() throws Exception {
        MobileCountries expectedTelephonyNetwork =
                MobileCountries.create("123", "456", Set.of("gb"), "gb");
        List<MobileCountries> expectedNetworks = list(expectedTelephonyNetwork);
        MobileCountries expectedMobileCountries =
                MobileCountries.create("123", Set.of("gr"), "gr");
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
                + "    <mobile_country mcc=\"123\">\n"
                + "   " + unexpectedText
                + "     <country>gr</country>\n"
                + "   " + unexpectedText
                + "     <override mnc=\"456\">"
                + "   " + unexpectedText
                + "       <country>gb</country>\n"
                + "   " + unexpectedText
                + "     </override>\n"
                + "   " + unexpectedText
                + "    </mobile_country>\n"
                + "  " + unexpectedText
                + "  </mobile_countries>\n"
                + " " + unexpectedText
                + "</telephony_lookup>\n");
        containsExactly(expectedNetworks,
                telephonyLookup.getTelephonyNetworkFinder().getAllNetworks());

        containsExactly(
                expectedMobileCountriesList,
                telephonyLookup.getTelephonyNetworkFinder().getAllMobileCountries());
    }

    @Test
    public void xmlParsing_truncatedInput() {
        checkValidateThrowsParserException("<telephony_lookup>\n");

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                """);

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                """);

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                """);

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                """);

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                """);

        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="123" mnc="456" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="202">
                      <country>gr</country>
                """);

        checkValidateThrowsParserException(
                """
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
        checkValidateThrowsParserException(
                """
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
        checkValidateThrowsParserException(
                """
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
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                 <networks>
                  <network mcc="123" mnc="456" countryCode="GB"/>
                 </networks>
                 <mobile_countries>
                  <mobile_country mcc="123">
                    <country>us</country>
                    <override mnc="456">
                      <country>GB</country>
                    </override>
                  </mobile_country>
                  <mobile_country mcc="202">
                   <country>gr</country>
                  </mobile_country>
                 </mobile_countries>
                </telephony_lookup>
                """);

        checkValidateThrowsParserException(
                """
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

    @Test
    public void getTelephonyNetworkFinder() {
        TelephonyLookup telephonyLookup =
                TelephonyLookup.createInstanceFromString(
                        """
                        <telephony_lookup>
                          <networks>
                           <network mcc="123" mnc="456" country="gb"/>
                           <network mcc="234" mnc="567" country="fr"/>
                          </networks>
                          <mobile_countries>
                           <mobile_country mcc="123">
                             <country>gr</country>
                             <override mnc="456">
                               <country>gb</country>
                             </override>
                           </mobile_country>
                           <mobile_country mcc="234" default="au">
                             <country>au</country>
                             <country>nf</country>
                             <override mnc="567">
                               <country>fr</country>
                             </override>
                           </mobile_country>
                          </mobile_countries>
                        </telephony_lookup>
                        """);

        TelephonyNetworkFinder telephonyNetworkFinder = telephonyLookup.getTelephonyNetworkFinder();
        MobileCountries expectedNetwork1 = MobileCountries.create("123", "456", Set.of("gb"), "gb");
        MobileCountries expectedNetwork2 = MobileCountries.create("234", "567", Set.of("fr"), "fr");
        MobileCountries expectedMobileCountries1 =
                MobileCountries.create("123", Set.of("gr"), "gr");
        MobileCountries expectedMobileCountries2 =
                MobileCountries.create("234", Set.of("au", "nf"), "au");

        containsExactly(list(expectedNetwork1, expectedNetwork2),
                telephonyNetworkFinder.getAllNetworks());

        containsExactly(
                list(expectedMobileCountries1, expectedMobileCountries2),
                telephonyNetworkFinder.getAllMobileCountries());

        assertEquals(expectedNetwork1, telephonyNetworkFinder.findCountriesByMccMnc("123", "456"));
        assertEquals(expectedNetwork2, telephonyNetworkFinder.findCountriesByMccMnc("234", "567"));
        assertNull(telephonyNetworkFinder.findCountriesByMccMnc("999", "999"));

        assertEquals(expectedMobileCountries1, telephonyNetworkFinder.findCountriesByMcc("123"));
        assertEquals(expectedMobileCountries2, telephonyNetworkFinder.findCountriesByMcc("234"));
        assertNull(telephonyNetworkFinder.findCountriesByMcc("999"));
    }

    @Test
    public void xmlParsing_networks_missingMccAttribute() {
        checkValidateThrowsParserException(
                """
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
        checkValidateThrowsParserException(
                """
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
        TelephonyLookup telephonyLookup =
                TelephonyLookup.createInstanceFromString(
                        """
                        <telephony_lookup>
                         <networks>
                          <network mcc="123" country="gb"/>
                         </networks>
                         <mobile_countries>
                          <mobile_country mcc="202">
                           <country>gr</country>
                           <override>
                            <country>gb</country>
                           </override>
                          </mobile_country>
                         </mobile_countries>
                        </telephony_lookup>
                        """);
        assertNull(telephonyLookup.getTelephonyNetworkFinder());
    }

    @Test
    public void xmlParsing_network_missingCountryCodeAttribute() {
        TelephonyLookup telephonyLookup =
                TelephonyLookup.createInstanceFromString(
                        """
                        <telephony_lookup>
                         <networks>
                          <network mcc="123" mnc="456"/>
                         </networks>
                         <mobile_countries>
                          <mobile_country mcc="123">
                           <country>us</country>
                           <override mnc="456">
                            <country/>
                           </override>
                          </mobile_country>
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
        TelephonyLookup telephonyLookup =
                TelephonyLookup.createInstanceFromString(
                        """
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

    @Test
    @DisabledOnRavenwood(blockedBy = MccTable.class)
    public void telephonyFinder_shouldBeIdenticalToTelephonyMccTable() {
        TelephonyNetworkFinder telephonyNetworkFinder =
                TelephonyLookup.getInstance().getTelephonyNetworkFinder();

        telephonyNetworkFinder.getAllMobileCountries().forEach(countries -> {
            String telephonyCountry = MccTable.geoCountryCodeForMccMnc(
                    new MccTable.MccMnc(countries.getMcc(), null));

            assertEquals(telephonyCountry, countries.getDefaultCountryIsoCode());
        });
    }

    @Test
    public void xmlParsing_overrides_success() throws Exception {
        String xml =
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="310" mnc="110" country="gu"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="310" default="us">
                      <country>us</country>
                      <override mnc="110">
                        <country>gu</country>
                      </override>
                    </mobile_country>
                    <mobile_country mcc="338">
                      <country>jm</country>
                      <override mnc="05">
                        <country>jm</country>
                        <country>bb</country>
                        <country>bm</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        TelephonyLookup telephonyLookup = validate(xml);
        TelephonyNetworkFinder finder = telephonyLookup.getTelephonyNetworkFinder();

        MobileCountries mobileCountry1 = finder.findCountriesByMcc("310");
        assertEquals("310", mobileCountry1.getMcc());
        assertNull(mobileCountry1.getMnc());
        assertEquals(Set.of("us"), mobileCountry1.getCountryIsoCodes());
        assertEquals("us", mobileCountry1.getDefaultCountryIsoCode());

        MobileCountries override1 = finder.findCountriesByMccMnc("310", "110");
        assertEquals("310", override1.getMcc());
        assertEquals("110", override1.getMnc());
        assertEquals(Set.of("gu"), override1.getCountryIsoCodes());
        assertEquals("gu", override1.getDefaultCountryIsoCode());

        MobileCountries mobileCountry2 = finder.findCountriesByMcc("338");
        assertEquals("338", mobileCountry2.getMcc());
        assertNull(mobileCountry2.getMnc());
        assertEquals(Set.of("jm"), mobileCountry2.getCountryIsoCodes());
        assertEquals("jm", mobileCountry2.getDefaultCountryIsoCode());

        MobileCountries override2 = finder.findCountriesByMccMnc("338", "05");
        assertEquals("338", override2.getMcc());
        assertEquals("05", override2.getMnc());
        assertEquals(Set.of("jm", "bb", "bm"), override2.getCountryIsoCodes());
        // Default is the first country ISO code for multi-country override
        assertEquals("jm", override2.getDefaultCountryIsoCode());

        assertNull(finder.findCountriesByMccMnc("310", "999"));
    }

    @Test
    public void xmlParsing_overrides_validationFailures() {
        // No country in override
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="310">
                      <country>us</country>
                      <override mnc="110"></override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);

        // Override countries same as mobile_country countries
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="310">
                      <country>us</country>
                      <override mnc="110">
                        <country>us</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);

        // Invalid MNC
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="310">
                      <country>us</country>
                      <override mnc="1234">
                        <country>gu</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);

        // Non-normalized country code
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="310">
                      <country>us</country>
                      <override mnc="110">
                        <country>GU</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);

        // Duplicate MCC/MNC
        checkValidateThrowsParserException(
                """
                <telephony_lookup>
                  <mobile_countries>
                    <mobile_country mcc="310">
                      <country>us</country>
                      <override mnc="110">
                        <country>gu</country>
                      </override>
                      <override mnc="110">
                        <country>gb</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """);
    }

    @Test
    public void xmlParsing_ignoresNetworks_whenFlagEnabled() throws Exception {
        String xml =
                """
                <telephony_lookup>
                  <networks>
                    <network mcc="999" mnc="999" country="gb"/>
                  </networks>
                  <mobile_countries>
                    <mobile_country mcc="310" default="us">
                      <country>us</country>
                      <override mnc="110">
                        <country>gu</country>
                      </override>
                    </mobile_country>
                  </mobile_countries>
                </telephony_lookup>
                """;
        TelephonyLookup telephonyLookup = validate(xml);
        TelephonyNetworkFinder finder = telephonyLookup.getTelephonyNetworkFinder();

        assertNull(finder.findCountriesByMccMnc("999", "999"));
        // check that the override is still found
        assertNotNull(finder.findCountriesByMccMnc("310", "110"));
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

    /**
     * Asserts that the actual collection contains exactly the same elements as the expected
     * collection, ignoring order. The collections should not contain duplicate elements.
     */
    private static <T> void containsExactly(Collection<T> expected, Collection<T> actual) {
        Set<T> expectedSet = new HashSet<>(expected);
        Set<T> actualSet = new HashSet<>(actual);

        assertEquals(expected.size(), expectedSet.size());
        assertEquals(actual.size(), actualSet.size());

        assertEquals(expectedSet, actualSet);
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
