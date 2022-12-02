/* GENERATED SOURCE. DO NOT MODIFY. */
// © 2022 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html

package android.icu.dev.test.message2;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import android.icu.dev.test.TestFmwk;
import android.icu.message2.FormattedPlaceholder;
import android.icu.message2.Formatter;
import android.icu.message2.FormatterFactory;
import android.icu.message2.Mf2FunctionRegistry;
import android.icu.message2.PlainStringFormattedValue;
import android.icu.text.ListFormatter;
import android.icu.text.ListFormatter.Type;
import android.icu.text.ListFormatter.Width;
import android.icu.testsharding.MainTestShard;

/**
 * Showing a custom formatter for a list, using the existing ICU {@link ListFormatter}.
 */
@MainTestShard
@RunWith(JUnit4.class)
@SuppressWarnings("javadoc")
public class CustomFormatterListTest extends TestFmwk {

    static class ListFormatterFactory implements FormatterFactory {

        @Override
        public Formatter createFormatter(Locale locale, Map<String, Object> fixedOptions) {
            return new ListFormatterImpl(locale, fixedOptions);
        }

        static class ListFormatterImpl implements Formatter {
            private final ListFormatter lf;

            ListFormatterImpl(Locale locale, Map<String, Object> fixedOptions) {
                Object oType = fixedOptions.get("type");
                Type type = oType == null
                        ? ListFormatter.Type.AND
                                : ListFormatter.Type.valueOf(oType.toString());
                Object oWidth = fixedOptions.get("width");
                Width width = oWidth == null
                        ? ListFormatter.Width.WIDE
                                : ListFormatter.Width.valueOf(oWidth.toString());
                lf = ListFormatter.getInstance(locale, type, width);
            }

            @Override
            public String formatToString(Object toFormat, Map<String, Object> variableOptions) {
                return format(toFormat, variableOptions).toString();
            }

            @Override
            public FormattedPlaceholder format(Object toFormat, Map<String, Object> variableOptions) {
                String result;
                if (toFormat instanceof Object[]) {
                    result = lf.format((Object[]) toFormat);
                } else if (toFormat instanceof Collection<?>) {
                    result = lf.format((Collection<?>) toFormat);
                } else {
                    result = toFormat == null ? "null" : toFormat.toString();
                }
                return new FormattedPlaceholder(toFormat, new PlainStringFormattedValue(result));
            }
        }
    }

    static final Mf2FunctionRegistry REGISTRY = Mf2FunctionRegistry.builder()
            .setFormatter("listformat", new ListFormatterFactory())
            .build();

    @Test
    public void test() {
        String [] progLanguages = {
                "C/C++",
                "Java",
                "Python"
        };

        TestUtils.runTestCase(REGISTRY, new TestCase.Builder()
                .pattern("{I know {$languages :listformat type=AND}!}")
                .arguments(Args.of("languages", progLanguages))
                .expected("I know C/C++, Java, and Python!")
                .build());

        TestUtils.runTestCase(REGISTRY, new TestCase.Builder()
                .pattern("{You are allowed to use {$languages :listformat type=OR}!}")
                .arguments(Args.of("languages", Arrays.asList(progLanguages)))
                .expected("You are allowed to use C/C++, Java, or Python!")
                .build());
    }
}
