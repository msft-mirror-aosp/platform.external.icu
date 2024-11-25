/*
 * Copyright (C) 2025 The Android Open Source Project
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
import static org.junit.Assert.assertThrows;

import android.icu.testsharding.MainTestShard;
import android.platform.test.annotations.UsesFlags;

import com.android.i18n.timezone.MobileCountries;

import org.junit.Test;

import java.util.Set;

@MainTestShard
@UsesFlags(com.android.icu.Flags.class)
public class MobileCountriesTest {

    @Test
    public void createMobileCountries_multipleRegions_expectNormalised() {
        MobileCountries mobileCountries = MobileCountries.create("340", Set.of("GP", "GF"), "gp");

        assertEquals("340", mobileCountries.getMcc());
        assertEquals(Set.of("gp", "gf"), mobileCountries.getCountryIsoCodes());
        assertEquals("gp", mobileCountries.getDefaultCountryIsoCode());
    }

    @Test
    public void createMobileCountries_singleRegions_expectNormalised() {
        MobileCountries mobileCountries = MobileCountries.create("222", Set.of("gr"), "GR");

        assertEquals("222", mobileCountries.getMcc());
        assertEquals(Set.of("gr"), mobileCountries.getCountryIsoCodes());
        assertEquals("gr", mobileCountries.getDefaultCountryIsoCode());
    }

    @Test
    public void createMobileCountries_defaultCountryNotInSet_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> MobileCountries.create("340", Set.of("gr"), "gp"));
    }

    @Test
    public void nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> MobileCountries.create(null, Set.of("gr"), "GR"));
        assertThrows(NullPointerException.class,
                () -> MobileCountries.create("222", null, "GR"));
        assertThrows(NullPointerException.class,
                () -> MobileCountries.create("222", Set.of("gr"), null));
    }
}