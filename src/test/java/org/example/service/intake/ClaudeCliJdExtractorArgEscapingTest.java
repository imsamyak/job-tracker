package org.example.service.intake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the Windows argv bug found while wiring this class up for real: {@link ProcessBuilder} on Windows
 * passes an argument through unquoted whenever it has no space, with no escaping of embedded '"'
 * characters - so a JSON schema or prompt full of quotes arrived at claude with every quote silently
 * stripped by claude's own argv parser (which treats a bare '"' as a quoted-mode toggle). Regression test
 * for {@code winArg}: quotes must survive, and real Windows paths (backslashes, no quotes) must be left
 * untouched rather than double-escaped.
 */
@EnabledOnOs(OS.WINDOWS)
class ClaudeCliJdExtractorArgEscapingTest {

    private String winArg(String value) throws Exception {
        Method m = ClaudeCliJdExtractor.class.getDeclaredMethod("winArg", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, value);
    }

    @Test
    void escapesEmbeddedQuotesSoTheyReachTheChildProcessIntact() throws Exception {
        String schema = "{\"type\":\"object\",\"properties\":{\"company\":{\"type\":\"string\"}}}";

        assertEquals("{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"company\\\":{\\\"type\\\":\\\"string\\\"}}}",
                winArg(schema));
    }

    @Test
    void leavesWindowsPathBackslashesUntouched() throws Exception {
        String path = "C:\\Users\\Compro\\AppData\\Local\\Temp\\jd 42.pdf";

        assertEquals(path, winArg(path), "a path with no quotes must be returned unchanged");
    }
}
