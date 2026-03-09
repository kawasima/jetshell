package net.unit8.jetshell.tool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.jshell.SourceCodeAnalysis;

import static java.util.stream.Collectors.toList;
import net.unit8.jetshell.command.JetShellCommandRegister;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class JetShellTesting {

    private final static String DEFAULT_STARTUP_MESSAGE = "|  Welcome to";

    private WaitingTestingInputStream cmdin = null;
    private ByteArrayOutputStream cmdout = null;
    private ByteArrayOutputStream cmderr = null;
    private PromptedCommandOutputStream console = null;
    private TestingInputStream userin = null;
    private ByteArrayOutputStream userout = null;
    private ByteArrayOutputStream usererr = null;

    public JetShellTool repl = null;

    public interface ReplTest {
        void run(boolean after);
    }

    public void setCommandInput(String s) {
        cmdin.setInput(s);
    }

    public String getCommandOutput() {
        String s = cmdout.toString();
        cmdout.reset();
        return s;
    }

    public String getCommandErrorOutput() {
        String s = cmderr.toString();
        cmderr.reset();
        return s;
    }

    public void setUserInput(String s) {
        userin.setInput(s);
    }

    public String getUserOutput() {
        String s = userout.toString();
        userout.reset();
        return s;
    }

    public String getUserErrorOutput() {
        String s = usererr.toString();
        usererr.reset();
        return s;
    }

    public void test(ReplTest... tests) {
        test(new String[0], tests);
    }

    public void test(String[] args, ReplTest... tests) {
        test(args, DEFAULT_STARTUP_MESSAGE, tests);
    }

    public void test(String[] args, String startUpMessage, ReplTest... tests) {
        ReplTest[] wtests = new ReplTest[tests.length + 2];
        wtests[0] = a -> assertCommandCheckOutput(a, "<start>",
                s -> assertTrue(s.startsWith(startUpMessage), "Expected start-up message '" + startUpMessage + "' Got: " + s));
        System.arraycopy(tests, 0, wtests, 1, tests.length);
        wtests[tests.length + 1] = a -> assertCommand(a, "/exit", null);
        testRaw(args, wtests);
    }

    public void testRaw(String[] args, ReplTest... tests) {
        cmdin = new WaitingTestingInputStream();
        cmdout = new ByteArrayOutputStream();
        cmderr = new ByteArrayOutputStream();
        console = new PromptedCommandOutputStream(tests);
        userin = new TestingInputStream();
        userout = new ByteArrayOutputStream();
        usererr = new ByteArrayOutputStream();
        repl = new JetShellTool(
                cmdin,
                new PrintStream(cmdout),
                new PrintStream(cmderr),
                new PrintStream(console),
                userin,
                new PrintStream(userout),
                new PrintStream(usererr));
        new JetShellCommandRegister().register(repl);
        repl.testPrompt = true;
        try {
            repl.start(args);
        } catch (Exception ex) {
            fail("Repl tool died with exception", ex);
        }
        String cos = getCommandOutput();
        String ceos = getCommandErrorOutput();
        String uos = getUserOutput();
        String ueos = getUserErrorOutput();
        assertTrue((cos.isEmpty() || cos.startsWith("|  Goodbye")),
                "Expected a goodbye, but got: " + cos);
        assertTrue(ceos.isEmpty(), "Expected empty error output, got: " + ceos);
        assertTrue(uos.isEmpty(), "Expected empty output, got: " + uos);
        assertTrue(ueos.isEmpty(), "Expected empty error output, got: " + ueos);
    }

    public void assertCommand(boolean after, String cmd, String out) {
        assertCommand(after, cmd, out, "", null, "", "");
    }

    public void assertCommandCheckOutput(boolean after, String cmd, Consumer<String> check) {
        if (!after) {
            assertCommand(false, cmd, null);
        } else {
            String got = getCommandOutput();
            check.accept(got);
            assertCommand(true, cmd, null);
        }
    }

    public void assertCommand(boolean after, String cmd, String out, String err,
            String userinput, String print, String usererr) {
        if (!after) {
            if (userinput != null) {
                setUserInput(userinput);
            }
            setCommandInput(cmd + "\n");
        } else {
            assertOutput(getCommandOutput(), out, "command");
            assertOutput(getCommandErrorOutput(), err, "command error");
            assertOutput(getUserOutput(), print, "user");
            assertOutput(getUserErrorOutput(), usererr, "user error");
        }
    }

    public void assertCompletion(boolean after, String code, boolean isSmart, String... expected) {
        if (!after) {
            setCommandInput("\n");
        } else {
            assertCompletion(code, isSmart, expected);
        }
    }

    public void assertCompletion(String code, boolean isSmart, String... expected) {
        List<String> completions = computeCompletions(code, isSmart);
        assertEquals(completions, Arrays.asList(expected), "Command: " + code + ", output: " +
                completions.toString());
    }

    private List<String> computeCompletions(String code, boolean isSmart) {
        JetShellTool repl = this.repl != null ? this.repl
                                      : new JetShellTool(null, null, null, null, null, null, null);
        if (this.repl == null) {
            new JetShellCommandRegister().register(repl);
        }
        int cursor = code.indexOf('|');
        code = code.replace("|", "");
        assertTrue(cursor > -1, "'|' not found: " + code);
        List<SourceCodeAnalysis.Suggestion> completions =
                repl.commandCompletionSuggestions(code, cursor, new int[1]);
        return completions.stream()
                          .map(s -> s.continuation())
                          .distinct()
                          .collect(Collectors.toList());
    }

    public Consumer<String> assertStartsWith(String prefix) {
        return (output) -> assertTrue(output.startsWith(prefix), "Output: '" + output + "' does not start with: " + prefix);
    }

    public void assertOutput(String got, String expected, String kind) {
        if (expected != null) {
            assertEquals(got, expected, "Kind: " + kind + ".\n");
        }
    }

    class WaitingTestingInputStream extends TestingInputStream {

        @Override
        synchronized void setInput(String s) {
            super.setInput(s);
            notify();
        }

        synchronized void waitForInput() {
            boolean interrupted = false;
            try {
                while (available() == 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public int read() {
            waitForInput();
            return super.read();
        }

        @Override
        public int read(byte b[], int off, int len) {
            waitForInput();
            return super.read(b, off, len);
        }
    }

    class PromptedCommandOutputStream extends OutputStream {
        private final ReplTest[] tests;
        private int index = 0;
        PromptedCommandOutputStream(ReplTest[] tests) {
            this.tests = tests;
        }

        @Override
        public synchronized void write(int b) {
            if (b == 5 || b == 6) {
                if (index < (tests.length - 1)) {
                    tests[index].run(true);
                    tests[index + 1].run(false);
                } else {
                    fail("Did not exit Repl tool after test");
                }
                ++index;
            }
        }

        @Override
        public synchronized void write(byte b[], int off, int len) {
            if ((off < 0) || (off > b.length) || (len < 0)
                    || ((off + len) - b.length > 0)) {
                throw new IndexOutOfBoundsException();
            }
            for (int i = 0; i < len; ++i) {
                write(b[off + i]);
            }
        }
    }
}
