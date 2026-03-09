package net.unit8.jetshell.command;

import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.*;

@Test
public class DocCommandTest {

    private String extractClassName(String expr) throws Exception {
        Method m = DocCommand.class.getDeclaredMethod("extractClassName", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, expr);
    }

    public void testExtractClassNameFqcn() throws Exception {
        assertEquals(extractClassName("org.apache.commons.lang3.StringUtils"), "org.apache.commons.lang3.StringUtils");
    }

    public void testExtractClassNameSimple() throws Exception {
        assertEquals(extractClassName("StringUtils"), "StringUtils");
    }

    public void testExtractClassNameMethod() throws Exception {
        assertEquals(extractClassName("org.foo.Bar.method("), "org.foo.Bar");
    }

    public void testExtractClassNameField() throws Exception {
        assertEquals(extractClassName("System.out"), "System");
    }

    public void testExtractClassNameNestedMethod() throws Exception {
        assertEquals(extractClassName("System.out.println("), "System");
    }

    public void testExtractClassNameTrailingDot() throws Exception {
        // Must not throw StringIndexOutOfBoundsException
        String result = extractClassName("System.");
        assertEquals(result, "System");
    }

    public void testExtractClassNameOnlyDot() throws Exception {
        assertNull(extractClassName("."));
    }

    public void testExtractClassNameEmpty() throws Exception {
        assertNull(extractClassName(""));
    }

    public void testExtractClassNameWithParenNoClass() throws Exception {
        assertNull(extractClassName("("));
    }
}
