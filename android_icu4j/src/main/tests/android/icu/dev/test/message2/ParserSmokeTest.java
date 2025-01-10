/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2024 and later: Unicode, Inc. and others.
// License & terms of use: https://www.unicode.org/copyright.html

package android.icu.dev.test.message2;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.icu.dev.test.CoreTestFmwk;
import android.icu.message2.MFParser;
import android.icu.testsharding.MainTestShard;

/*
 * A list of tests for the parser.
 */
@MainTestShard
@RunWith(JUnit4.class)
@SuppressWarnings({"static-method", "javadoc"})
public class ParserSmokeTest extends CoreTestFmwk {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() throws Exception {
        MFParser.parse(null);
    }

    // Other tests in CoreTest.java
}
