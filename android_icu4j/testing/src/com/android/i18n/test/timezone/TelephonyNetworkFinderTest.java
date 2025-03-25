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

import com.android.i18n.timezone.MobileCountries;
import com.android.i18n.timezone.TelephonyNetwork;
import com.android.i18n.timezone.TelephonyNetworkFinder;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

@MainTestShard
public class TelephonyNetworkFinderTest {

    private TelephonyNetworkFinder finder;

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
}
