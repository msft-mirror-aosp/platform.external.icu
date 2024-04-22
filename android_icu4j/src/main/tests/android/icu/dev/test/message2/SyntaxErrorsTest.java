/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2024 and later: Unicode, Inc. and others.
// License & terms of use: https://www.unicode.org/copyright.html

package android.icu.dev.test.message2;

import java.io.Reader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.icu.dev.test.CoreTestFmwk;
import android.icu.message2.MessageFormatter;
import android.icu.testsharding.MainTestShard;

@MainTestShard
@SuppressWarnings({"static-method", "javadoc"})
@RunWith(JUnit4.class)
public class SyntaxErrorsTest extends CoreTestFmwk {
    private static final String JSON_FILE = "syntax-errors.json";

    @Test
    public void test() throws Exception {
        try (Reader reader = TestUtils.jsonReader(JSON_FILE)) {
            String[] srcList = TestUtils.GSON.fromJson(reader, String[].class);
            for (String source : srcList) {
                try {
                    MessageFormatter.builder().setPattern(source).build();
                    fail("Pattern expected to fail, but didn't: '" + source + "'");
                } catch (Exception e) {
                    // If we get here it is fine
                }
            }
        }
    }
}
