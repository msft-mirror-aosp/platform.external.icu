/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
 *******************************************************************************
 * Copyright (C) 1996-2012, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package android.icu.dev.test.rbbi;

import java.util.MissingResourceException;

import android.icu.impl.breakiter.LSTMBreakEngine;
import android.icu.lang.UScript;
import android.icu.testsharding.MainTestShard;

@MainTestShard
public class RBBITstUtils {
    static boolean lstmDataIsBuilt() {
        try {
            LSTMBreakEngine.createData(UScript.THAI);
            return true;
        } catch (MissingResourceException e) {
            // do nothing
        }
        try {
            LSTMBreakEngine.createData(UScript.MYANMAR);
            return true;
        } catch (MissingResourceException e) {
            // do nothing
        }
        return false;
    }

    static boolean skipLSTMTest() {
        return ! lstmDataIsBuilt();
    }

    public static boolean skipDictionaryTest() {
        return lstmDataIsBuilt();
    }
}
