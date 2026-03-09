package net.unit8.jetshell.tool;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/**
 * Tests that jline3 completion integrates correctly with JShell's
 * SourceCodeAnalysis and command completion.
 */
@Test
public class CompletionTest {

    private Terminal terminal;
    private JetShellTool tool;

    @BeforeMethod
    public void setUp() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        tool = new JetShellTool(in, new PrintStream(out), new PrintStream(out),
                new PrintStream(out), in, new PrintStream(out), new PrintStream(out));
        tool.testPrompt = true;
        // Initialize JShell state so analysis is available
        tool.start(new String[]{"-nostartup"});

        terminal = TerminalBuilder.builder()
                .streams(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())
                .dumb(true)
                .build();
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (terminal != null) terminal.close();
    }

    private DefaultParser createParser() {
        DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);
        parser.setQuoteChars(new char[0]);
        return parser;
    }

    /**
     * Simulate what jline3 does during completion:
     * 1. Parser parses the line into a ParsedLine (determining word boundaries)
     * 2. Completer produces Candidate list
     * 3. jline3 calls CompletingParsedLine.escape() on each candidate value
     * 4. jline3 replaces word() with the escaped candidate value
     *
     * This method returns the resulting line after selecting each candidate.
     */
    private List<String> simulateCompletion(String input, int cursor,
                                            Completer completer, DefaultParser parser) {
        ParsedLine parsed = parser.parse(input, cursor, Parser.ParseContext.COMPLETE);
        List<Candidate> candidates = new ArrayList<>();
        completer.complete(null, parsed, candidates);

        List<String> results = new ArrayList<>();
        int wordStart = parsed.cursor() - parsed.wordCursor();

        for (Candidate c : candidates) {
            // jline3 calls escape() on the candidate value before inserting
            CharSequence escaped;
            if (parsed instanceof CompletingParsedLine) {
                escaped = ((CompletingParsedLine) parsed).escape(
                        c.value() + c.suffix(), c.complete());
            } else {
                escaped = c.value() + c.suffix();
            }
            String before = input.substring(0, wordStart);
            String after = cursor < input.length() ? input.substring(cursor) : "";
            String completed = before + escaped + after;
            results.add(completed);
        }
        return results;
    }

    public void testCommandCompletion() throws Exception {
        net.unit8.jetshell.command.JetShellCommandRegister register =
                new net.unit8.jetshell.command.JetShellCommandRegister();
        register.register(tool);

        DefaultParser parser = createParser();
        Completer completer = createCompleter();

        List<String> results = simulateCompletion("/re", 3, completer, parser);

        // Should contain /resolve and /reset and /reload
        assertTrue(results.stream().anyMatch(r -> r.startsWith("/resolve ")),
                "Should complete to /resolve, got: " + results);
        assertTrue(results.stream().anyMatch(r -> r.startsWith("/reset ")),
                "Should complete to /reset, got: " + results);
        assertTrue(results.stream().anyMatch(r -> r.startsWith("/reload ")),
                "Should complete to /reload, got: " + results);

        // No backslashes or quotes should appear
        for (String r : results) {
            assertFalse(r.contains("\\"), "Should not contain backslash: " + r);
            assertFalse(r.contains("'"), "Should not contain quote: " + r);
        }
    }

    public void testJavaCompletion() throws Exception {
        jdk.jshell.JShell jshell = jdk.jshell.JShell.create();
        jdk.jshell.SourceCodeAnalysis analysis = jshell.sourceCodeAnalysis();

        DefaultParser parser = createParser();

        Completer completer = (reader, line, candidates) -> {
            String text = line.line();
            int cursorPos = line.cursor();
            int[] anchor = new int[]{-1};
            List<jdk.jshell.SourceCodeAnalysis.Suggestion> suggestions =
                    analysis.completionSuggestions(text, cursorPos, anchor);
            int anchorPos = anchor[0] >= 0 ? anchor[0] : cursorPos;
            int wordStart = line.cursor() - line.wordCursor();
            String gap = anchorPos > wordStart
                    ? text.substring(wordStart, anchorPos) : "";
            suggestions.stream()
                    .map(jdk.jshell.SourceCodeAnalysis.Suggestion::continuation)
                    .distinct()
                    .forEach(c -> candidates.add(new Candidate(
                            gap + c, c, null, null, "", null, true)));
        };

        try {
            List<String> results = simulateCompletion("System.out.print", 16, completer, parser);

            assertTrue(results.stream().anyMatch(r -> r.contains("println(")),
                    "Should complete to println(, got: " + results);

            for (String r : results) {
                assertFalse(r.contains("\\"), "Should not contain backslash: " + r);
                assertFalse(r.contains("'"), "Should not contain quote: " + r);
            }
        } finally {
            jshell.close();
        }
    }

    public void testNoGarbageAfterUniqueCompletion() throws Exception {
        net.unit8.jetshell.command.JetShellCommandRegister register =
                new net.unit8.jetshell.command.JetShellCommandRegister();
        register.register(tool);

        DefaultParser parser = createParser();
        Completer completer = createCompleter();

        // "/clas" should complete to "/classpath "
        List<String> results = simulateCompletion("/clas", 5, completer, parser);
        assertTrue(results.stream().anyMatch(r -> r.equals("/classpath ")),
                "Should complete to '/classpath ', got: " + results);
    }

    private Completer createCompleter() {
        return (reader, line, candidates) -> {
            String text = line.line();
            int cursor = line.cursor();
            int[] anchor = new int[]{-1};
            List<jdk.jshell.SourceCodeAnalysis.Suggestion> suggestions;
            if (text.trim().startsWith("/")) {
                suggestions = tool.commandCompletionSuggestions(text, cursor, anchor);
            } else {
                if (tool.getState() == null) {
                    return;
                }
                suggestions = tool.getState().sourceCodeAnalysis()
                        .completionSuggestions(text, cursor, anchor);
            }
            int anchorPos = anchor[0] >= 0 ? anchor[0] : cursor;
            int wordStart = line.cursor() - line.wordCursor();
            String gap = anchorPos > wordStart
                    ? text.substring(wordStart, anchorPos) : "";
            suggestions.stream()
                    .map(jdk.jshell.SourceCodeAnalysis.Suggestion::continuation)
                    .distinct()
                    .forEach(c -> candidates.add(new Candidate(
                            gap + c, c, null, null, "", null, true)));
        };
    }
}
