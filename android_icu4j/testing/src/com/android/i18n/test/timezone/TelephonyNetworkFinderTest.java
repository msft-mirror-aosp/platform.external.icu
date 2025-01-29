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

import android.icu.testsharding.MainTestShard;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.i18n.timezone.MobileCountries;
import com.android.i18n.timezone.TelephonyNetwork;
import com.android.i18n.timezone.TelephonyNetworkFinder;
import com.android.internal.telephony.MccTable;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

@RunWith(ParameterizedAndroidJunit4.class)
@MainTestShard
@UsesFlags({
        com.android.internal.telephony.flags.Flags.class,
        com.android.icu.Flags.class
})
public class TelephonyNetworkFinderTest {
    @ClassRule
    public static final SetFlagsRule.ClassRule mSetFlagsClassRule = new SetFlagsRule.ClassRule();

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                com.android.icu.Flags.FLAG_TELEPHONY_LOOKUP_MCC_EXTENSION);
    }

    @Rule
    public final SetFlagsRule mSetFlagsRule;

    private TelephonyNetworkFinder finder;

    public TelephonyNetworkFinderTest(FlagsParameterization flags) {
        mSetFlagsRule = mSetFlagsClassRule.createSetFlagsRule(flags);
    }

    @Before
    public void setup() {
        List<TelephonyNetwork> networkList = List.of(
                TelephonyNetwork.create("123", "456", "gb"),
                TelephonyNetwork.create("234", "567", "gb"));

        List<MobileCountries> mobileCountriesList = List.of(
                MobileCountries.create("234", Set.of("uk"), "uk"),
                MobileCountries.create("310", Set.of("uk", "us"), "us"));

        finder = TelephonyNetworkFinder.create(networkList, mobileCountriesList);
    }

    @Test
    public void telephonyFinder_shouldFindNetworks() {
        TelephonyNetwork network = TelephonyNetwork.create("123", "456", "gb");

        assertEquals(network, finder.findNetworkByMccMnc("123", "456"));
        assertNull(finder.findNetworkByMccMnc("XXX", "XXX"));
        assertNull(finder.findNetworkByMccMnc("123", "XXX"));
        assertNull(finder.findNetworkByMccMnc("456", "123"));
        assertNull(finder.findNetworkByMccMnc("111", "222"));
        assertEquals(2, finder.getAllNetworks().size());
    }

    @Test
    public void telephonyFinder_shouldFindCountries() {
        MobileCountries countries = MobileCountries.create("310", Set.of("uk", "us"), "us");

        assertEquals(countries, finder.findCountriesByMcc("310"));
        assertNotNull(finder.findCountriesByMcc("234"));
        assertEquals(2, finder.getAllMobileCountries().size());
    }

    @Test
    public void telephonyFinder_countryIsoCodes_cannotBeTamperedWith() {
        MobileCountries found = finder.findCountriesByMcc("310");
        assertThrows(UnsupportedOperationException.class, () -> found.getCountryIsoCodes().add("gb"));
    }

    @EnableFlags(com.android.icu.Flags.FLAG_TELEPHONY_LOOKUP_MCC_EXTENSION)
    @DisableFlags(com.android.internal.telephony.flags.Flags.FLAG_USE_I18N_FOR_MCC_MAPPING)
    @Test
    public void telephonyFinder_shouldBeIdenticalToTelephonyMccTable() {
        finder.getAllMobileCountries().forEach(countries -> {
            String telephonyCountry = MccTable.geoCountryCodeForMccMnc(
                    new MccTable.MccMnc(countries.getMcc(), null));

            assertEquals(telephonyCountry, countries.getDefaultCountryIsoCode());
        });
    }
}
