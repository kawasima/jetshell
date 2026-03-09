package net.unit8.jetshell.tool;

import jdk.jshell.*;
import jdk.jshell.Snippet.Status;
import jdk.jshell.Snippet.SubKind;
import jdk.jshell.SourceCodeAnalysis.CompletionInfo;
import jdk.jshell.SourceCodeAnalysis.Suggestion;

import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.HashSet;
import java.util.Set;

/**
 * Command line REPL tool for Java using the JShell API.
 *
 * @author kawasima
 */
public class JetShellTool {

    final InputStream cmdin;
    final PrintStream cmdout;
    final PrintStream cmderr;
    final PrintStream console;
    final InputStream userin;
    final PrintStream userout;
    final PrintStream usererr;

    private JShell state;
    private SourceCodeAnalysis analysis;
    private boolean live = false;
    private boolean regenerateOnDeath = true;

    private String cmdlineClasspath = null;
    private String cmdlineStartup = null;

    private List<String> replayableHistory;
    private List<String> replayableHistoryPrevious;
    private Set<String> startupSnippetIds;
    private boolean suppressOutput = false;

    public boolean testPrompt = false;

    static final Preferences PREFS = Preferences.userRoot().node("tool/JetShell");
    private static final String STARTUP_KEY = "STARTUP";

    static final String DEFAULT_STARTUP =
            "\n" +
            "import java.util.*;\n" +
            "import java.io.*;\n" +
            "import java.math.*;\n" +
            "import java.net.*;\n" +
            "import java.util.concurrent.*;\n" +
            "import java.util.prefs.*;\n" +
            "import java.util.regex.*;\n" +
            "void printf(String format, Object... args) { System.out.printf(format, args); }\n";

    // Command infrastructure
    private final Map<String, Command> commands = new LinkedHashMap<>();

    /**
     * @param cmdin command line input -- snippets and commands
     * @param cmdout command line output, feedback including errors
     * @param cmderr start-up errors and debugging info
     * @param console console control interaction
     * @param userin code execution input
     * @param userout code execution output  -- System.out.printf("hi")
     * @param usererr code execution error stream  -- System.err.printf("Oops")
     */
    public JetShellTool(InputStream cmdin, PrintStream cmdout, PrintStream cmderr,
                         PrintStream console,
                         InputStream userin, PrintStream userout, PrintStream usererr) {
        this.cmdin = cmdin;
        this.cmdout = cmdout;
        this.cmderr = cmderr;
        this.console = console;
        this.userin = userin;
        this.userout = userout;
        this.usererr = usererr;
        registerBuiltinCommands();
    }

    /**
     * Creates a JetShellTool wired to standard I/O streams.
     * Command I/O, user code I/O, and the console all share the same streams.
     */
    public static JetShellTool create(InputStream in, PrintStream out, PrintStream err) {
        return new JetShellTool(in, out, err, out, in, out, err);
    }

    public JShell getState() {
        return state;
    }

    // --- Output helpers ---

    public void hard(String format, Object... args) {
        if (!suppressOutput) {
            cmdout.printf("|  " + format + "%n", args);
        }
    }

    public void error(String format, Object... args) {
        if (!suppressOutput) {
            cmderr.printf("|  " + format + "%n", args);
        }
    }

    public void fluff(String format, Object... args) {
        hard(format, args);
    }

    // --- Command registration ---

    @FunctionalInterface
    public interface CommandHandler {
        boolean handle(String arg);
    }

    public interface CompletionProvider {
        List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor);
    }

    public enum CommandKind {
        NORMAL(true, true, true),
        REPLAY(true, true, true),
        HIDDEN(true, false, false);

        final boolean isRealCommand;
        final boolean showInHelp;
        final boolean shouldSuggestCompletions;

        CommandKind(boolean isRealCommand, boolean showInHelp, boolean shouldSuggestCompletions) {
            this.isRealCommand = isRealCommand;
            this.showInHelp = showInHelp;
            this.shouldSuggestCompletions = shouldSuggestCompletions;
        }
    }

    public static class Command {
        public final String command;
        public final String params;
        public final String description;
        public final String help;
        public final CommandHandler run;
        public final CompletionProvider completions;
        public final CommandKind kind;

        private static final CompletionProvider EMPTY = (input, cursor, anchor) -> Collections.emptyList();

        public Command(String command, String params, String description, String help,
                       CommandHandler run, CompletionProvider completions, CommandKind kind) {
            this.command = command;
            this.params = params;
            this.description = description;
            this.help = help;
            this.run = run;
            this.completions = completions;
            this.kind = kind;
        }

        public Command(String command, String params, String description, String help,
                       CommandHandler run, CommandKind kind) {
            this(command, params, description, help, run, EMPTY, kind);
        }
    }

    public void registerCommand(Command cmd) {
        commands.put(cmd.command, cmd);
    }

    // --- Startup ---

    public void start(String[] args) throws Exception {
        List<String> loadList = processCommandArgs(args);
        if (loadList == null) {
            return;
        }

        if (testPrompt) {
            startWithTestPrompt(loadList);
        } else {
            startInteractive(loadList);
        }
    }

    private void startInteractive(List<String> loadList) throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        Completer completer = (reader, line, candidates) -> {
            String text = line.line();
            int cursor = line.cursor();
            int[] anchor = new int[]{-1};

            // Run completion with a spinner for potentially slow operations
            List<Suggestion> suggestions = completeWithSpinner(terminal, text, cursor, anchor);

            // jline3 replaces word() with the candidate value.
            // word() is determined by the parser (default: space-delimited).
            // JShell's anchor marks the start of the text to complete.
            // We prepend the text between word-start and anchor so that
            // jline3's replacement produces the correct result.
            int anchorPos = anchor[0] >= 0 ? anchor[0] : cursor;
            int wordStart = line.cursor() - line.wordCursor();
            String gap = anchorPos > wordStart
                    ? text.substring(wordStart, anchorPos) : "";
            suggestions.stream()
                    .map(Suggestion::continuation)
                    .distinct()
                    .forEach(c -> candidates.add(new Candidate(
                            gap + c, c, null, null, "", null, false)));
        };

        org.jline.reader.impl.DefaultParser parser = new org.jline.reader.impl.DefaultParser();
        // Use empty array (not null) to avoid both escaping and quoting.
        // null escapeChars causes DefaultParser.ArgumentList.escape() to
        // wrap candidates containing spaces in single quotes.
        parser.setEscapeChars(new char[0]);
        parser.setQuoteChars(new char[0]);

        java.nio.file.Path historyFile = java.nio.file.Paths.get(
                System.getProperty("user.home"), ".jetshell_history");
        LineReader lineReader = LineReaderBuilder.builder()
                .appName("JetShell")
                .terminal(terminal)
                .completer(completer)
                .parser(parser)
                .variable(LineReader.HISTORY_FILE, historyFile)
                .build();
        lineReader.setOpt(LineReader.Option.DISABLE_EVENT_EXPANSION);

        try {
            resetState(loadList);
            hard("Welcome to JetShell -- Version %s", version());
            hard("Type /help for help");

            while (regenerateOnDeath) {
                if (!live) {
                    resetState(loadList);
                }
                runInteractive(lineReader);
            }
        } finally {
            closeState();
            terminal.close();
        }
    }

    private void startWithTestPrompt(List<String> loadList) {
        resetState(loadList);
        hard("Welcome to JetShell -- Version %s", version());
        hard("Type /help for help");

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(cmdin));
            while (regenerateOnDeath) {
                if (!live) {
                    resetState(loadList);
                }
                runWithReader(reader);
            }
        } catch (IOException ex) {
            hard("Unexpected exception: %s", ex);
        } finally {
            closeState();
        }
    }

    private void runInteractive(LineReader lineReader) {
        String incomplete = "";
        try {
            while (live) {
                String prompt = incomplete.isEmpty() ? "\n-> " : ">> ";
                String raw;
                try {
                    raw = lineReader.readLine(prompt);
                } catch (UserInterruptException ex) {
                    incomplete = "";
                    continue;
                } catch (EndOfFileException ex) {
                    regenerateOnDeath = false;
                    break;
                }
                incomplete = processInput(raw, incomplete);
            }
        } catch (Exception ex) {
            hard("Unexpected exception: %s", ex);
        }
    }

    private void runWithReader(BufferedReader reader) throws IOException {
        String incomplete = "";
        while (live) {
            String prompt = incomplete.isEmpty() ? "\u0005" : "\u0006";
            console.print(prompt);
            console.flush();

            String raw = reader.readLine();
            if (raw == null) {
                regenerateOnDeath = false;
                break;
            }
            incomplete = processInput(raw, incomplete);
        }
    }

    private String processInput(String raw, String incomplete) {
        if (raw == null) {
            regenerateOnDeath = false;
            return "";
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return incomplete;
        }
        String line = incomplete + trimmed;

        if (incomplete.isEmpty() && line.startsWith("/") && !line.startsWith("//") && !line.startsWith("/*")) {
            processCommand(line.trim());
            return "";
        } else {
            return processSourceCatchingReset(line);
        }
    }

    // --- State management ---

    private void resetState(List<String> loadList) {
        closeState();
        replayableHistoryPrevious = replayableHistory;
        replayableHistory = new ArrayList<>();

        state = JShell.builder()
                .in(userin)
                .out(userout)
                .err(usererr)
                .build();
        analysis = state.sourceCodeAnalysis();

        state.onShutdown(deadState -> {
            if (deadState == state) {
                hard("State engine terminated.");
                hard("Restore definitions with: /reload restore");
                live = false;
            }
        });
        live = true;

        if (cmdlineClasspath != null) {
            state.addToClasspath(cmdlineClasspath);
        }

        String start = cmdlineStartup != null ? cmdlineStartup
                : PREFS.get(STARTUP_KEY, DEFAULT_STARTUP);
        if (!start.isBlank()) {
            suppressOutput = true;
            processSource(start);
            suppressOutput = false;
            // Record snippet IDs that belong to startup so /list start can filter them
            startupSnippetIds = state.snippets()
                    .map(Snippet::id)
                    .collect(Collectors.toCollection(HashSet::new));
        } else {
            startupSnippetIds = new HashSet<>();
        }

        for (String loadFile : loadList) {
            cmdOpen(loadFile);
        }
    }

    private void closeState() {
        live = false;
        if (state != null) {
            state.close();
            state = null;
        }
    }

    // --- Source processing ---

    private String processSourceCatchingReset(String src) {
        try {
            return processSource(src);
        } catch (IllegalStateException ex) {
            hard("Resetting...");
            live = false;
            return "";
        }
    }

    private String processSource(String srcInput) {
        while (true) {
            CompletionInfo an = analysis.analyzeCompletion(srcInput);
            if (!an.completeness().isComplete()) {
                return an.remaining();
            }
            boolean failed = processCompleteSource(an.source());
            if (failed || an.remaining().isEmpty()) {
                return "";
            }
            srcInput = an.remaining();
        }
    }

    private boolean processCompleteSource(String source) {
        boolean failed = false;
        boolean isActive = false;
        List<SnippetEvent> events = state.eval(source);
        for (SnippetEvent e : events) {
            failed |= handleEvent(e);
            isActive |= e.causeSnippet() == null
                    && e.status().isActive()
                    && e.snippet().subKind() != SubKind.VAR_VALUE_SUBKIND;
        }
        if (isActive && live) {
            replayableHistory.add(source);
        }
        return failed;
    }

    private boolean handleEvent(SnippetEvent ste) {
        Snippet sn = ste.snippet();
        if (sn == null) {
            return false;
        }
        List<Diag> diagnostics = state.diagnostics(sn).collect(Collectors.toList());
        String source = sn.source();

        if (ste.causeSnippet() == null) {
            // Main event
            printDiagnostics(source, diagnostics);
            if (ste.status().isActive()) {
                if (ste.exception() != null) {
                    if (ste.exception() instanceof EvalException) {
                        printEvalException((EvalException) ste.exception());
                        return true;
                    } else if (ste.exception() instanceof UnresolvedReferenceException) {
                        printUnresolved((UnresolvedReferenceException) ste.exception());
                    } else {
                        hard("Unexpected execution exception: %s", ste.exception());
                        return true;
                    }
                } else {
                    displayResult(ste);
                }
            } else if (ste.status() == Status.REJECTED) {
                if (diagnostics.isEmpty()) {
                    hard("Failed.");
                }
                return true;
            }
        } else if (ste.status() == Status.REJECTED) {
            if (sn instanceof DeclarationSnippet) {
                hard("Caused failure of dependent %s", ((DeclarationSnippet) sn).name());
            }
            printDiagnostics(source, diagnostics);
        } else {
            // Update event
            if (sn instanceof DeclarationSnippet) {
                displayResult(ste);
            }
        }
        return false;
    }

    private void displayResult(SnippetEvent ste) {
        Snippet sn = ste.snippet();
        Status status = ste.status();
        boolean update = ste.causeSnippet() != null;
        String value = ste.value();

        String action = resolveAction(ste, update);
        if (action == null) {
            return;
        }
        String prefix = update ? "  Update " : "";

        switch (sn.subKind()) {
            case CLASS_SUBKIND:
                hard("%s%s class %s", prefix, action, ((TypeDeclSnippet) sn).name());
                break;
            case INTERFACE_SUBKIND:
                hard("%s%s interface %s", prefix, action, ((TypeDeclSnippet) sn).name());
                break;
            case ENUM_SUBKIND:
                hard("%s%s enum %s", prefix, action, ((TypeDeclSnippet) sn).name());
                break;
            case ANNOTATION_TYPE_SUBKIND:
                hard("%s%s annotation interface %s", prefix, action, ((TypeDeclSnippet) sn).name());
                break;
            case METHOD_SUBKIND:
                hard("%s%s method %s(%s)", prefix, action,
                        ((MethodSnippet) sn).name(), ((MethodSnippet) sn).parameterTypes());
                break;
            case VAR_DECLARATION_SUBKIND: {
                VarSnippet vk = (VarSnippet) sn;
                hard("%s%s variable %s of type %s", prefix, action, vk.name(), vk.typeName());
                break;
            }
            case VAR_DECLARATION_WITH_INITIALIZER_SUBKIND: {
                VarSnippet vk = (VarSnippet) sn;
                hard("%s%s variable %s of type %s with initial value %s",
                        prefix, action, vk.name(), vk.typeName(), value);
                break;
            }
            case TEMP_VAR_EXPRESSION_SUBKIND: {
                VarSnippet vk = (VarSnippet) sn;
                hard("Expression value is: %s", value);
                hard("  assigned to temporary variable %s of type %s", vk.name(), vk.typeName());
                break;
            }
            case VAR_VALUE_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) sn;
                hard("Variable %s of type %s has value %s", ek.name(), ek.typeName(), value);
                break;
            }
            case ASSIGNMENT_SUBKIND: {
                ExpressionSnippet ek = (ExpressionSnippet) sn;
                hard("Variable %s has been assigned the value %s", ek.name(), value);
                break;
            }
            default:
                break;
        }

        if (sn instanceof DeclarationSnippet && (status == Status.RECOVERABLE_DEFINED || status == Status.RECOVERABLE_NOT_DEFINED)) {
            hard("  however, it cannot be %s until %s is declared",
                    status == Status.RECOVERABLE_NOT_DEFINED ? "referenced" : "invoked",
                    unresolved((DeclarationSnippet) sn));
        }
    }

    private String resolveAction(SnippetEvent ste, boolean update) {
        switch (ste.status()) {
            case VALID:
            case RECOVERABLE_DEFINED:
            case RECOVERABLE_NOT_DEFINED:
                if (ste.previousStatus().isActive()) {
                    return ste.isSignatureChange() ? "Replaced" : "Modified";
                }
                return "Added";
            case OVERWRITTEN:
                return "Overwrote";
            case DROPPED:
                return "Dropped";
            case REJECTED:
                return "Rejected";
            default:
                return null;
        }
    }

    private void printDiagnostics(String source, List<Diag> diagnostics) {
        for (Diag diag : diagnostics) {
            if (diag.isError()) {
                hard("Error:");
            }
            for (String line : diag.getMessage(Locale.getDefault()).split("\\R")) {
                hard("%s", line);
            }
            int startPos = (int) diag.getStartPosition();
            int endPos = (int) diag.getEndPosition();
            String[] sourceLines = source.split("\\R");
            int pos = 0;
            for (String srcLine : sourceLines) {
                int lineEnd = pos + srcLine.length();
                if (startPos >= pos && startPos <= lineEnd) {
                    hard("%s", srcLine);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < srcLine.length(); i++) {
                        sb.append(i >= (startPos - pos) && i < (endPos - pos) ? '^' : ' ');
                    }
                    hard("%s", sb.toString());
                    break;
                }
                pos = lineEnd + 1;
            }
        }
    }

    private void printEvalException(EvalException ex) {
        hard("Exception %s: %s", ex.getExceptionClassName(), ex.getMessage());
        for (StackTraceElement ste : ex.getStackTrace()) {
            StringBuilder sb = new StringBuilder();
            String cn = ste.getClassName();
            if (!cn.isEmpty()) {
                int dot = cn.lastIndexOf('.');
                if (dot > 0) {
                    cn = cn.substring(dot + 1);
                }
                sb.append(cn).append(".");
            }
            if (!ste.getMethodName().isEmpty()) {
                sb.append(ste.getMethodName());
            }
            hard("    at %s(%s:%d)", sb, ste.getFileName(), ste.getLineNumber());
        }
    }

    private void printUnresolved(UnresolvedReferenceException ex) {
        DeclarationSnippet sn = ex.getSnippet();
        hard("Attempted to use %s which cannot be invoked until %s is declared",
                sn.name(), unresolved(sn));
    }

    private String unresolved(DeclarationSnippet key) {
        List<String> unresolved = state.unresolvedDependencies(key).collect(Collectors.toList());
        return String.join(", ", unresolved);
    }

    // --- Command processing ---

    private void processCommand(String cmd) {
        if (cmd.startsWith("/-")) {
            try {
                cmdUseHistoryEntry(Integer.parseInt(cmd.substring(1)));
                return;
            } catch (NumberFormatException ex) {
                // ignore
            }
        }
        String arg = "";
        int sp = cmd.indexOf(' ');
        if (sp >= 0) {
            arg = cmd.substring(sp + 1).trim();
            cmd = cmd.substring(0, sp);
        }
        Command command = commands.get(cmd);
        if (command == null) {
            // Try prefix matching
            Command[] matches = findCommand(cmd, c -> c.kind.isRealCommand);
            if (matches.length == 1) {
                command = matches[0];
            } else if (matches.length == 0) {
                hard("No such command: %s", cmd);
                return;
            } else {
                hard("Command '%s' is ambiguous: %s", cmd,
                        Arrays.stream(matches).map(c -> c.command).collect(Collectors.joining(", ")));
                return;
            }
        }
        command.run.handle(arg);
    }

    private Command[] findCommand(String cmd, Predicate<Command> filter) {
        return commands.values().stream()
                .filter(c -> c.command.startsWith(cmd))
                .filter(filter)
                .toArray(Command[]::new);
    }

    private static final char[] SPINNER_CHARS = {'|', '/', '-', '\\'};

    private List<Suggestion> completeWithSpinner(Terminal terminal, String text, int cursor, int[] anchor) {
        List<Suggestion> suggestions;
        if (text.trim().startsWith("/")) {
            // Command completion may involve network I/O (e.g. artifact search)
            var future = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> commandCompletionSuggestions(text, cursor, anchor));
            suggestions = waitWithSpinner(terminal, future);
        } else {
            suggestions = analysis.completionSuggestions(text, cursor, anchor);
        }
        return suggestions;
    }

    private <T> T waitWithSpinner(Terminal terminal, java.util.concurrent.Future<T> future) {
        int spinIdx = 0;
        try {
            while (!future.isDone()) {
                terminal.writer().print(" " + SPINNER_CHARS[spinIdx % SPINNER_CHARS.length] + "\b\b");
                terminal.writer().flush();
                spinIdx++;
                Thread.sleep(100);
            }
            // Clear spinner
            terminal.writer().print("  \b\b");
            terminal.writer().flush();
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            return null;
        }
    }

    public List<Suggestion> commandCompletionSuggestions(String code, int cursor, int[] anchor) {
        String prefix = code.substring(0, cursor);
        int space = prefix.indexOf(' ');
        Stream<Suggestion> result;

        if (space == -1) {
            result = commands.values().stream()
                    .distinct()
                    .filter(cmd -> cmd.kind.shouldSuggestCompletions)
                    .map(cmd -> cmd.command)
                    .filter(key -> key.startsWith(prefix))
                    .map(key -> new ArgSuggestion(key + " "));
            anchor[0] = 0;
        } else {
            String arg = prefix.substring(space + 1);
            String cmd = prefix.substring(0, space);
            Command[] candidates = findCommand(cmd, c -> true);
            if (candidates.length == 1) {
                result = candidates[0].completions.completionSuggestions(arg, cursor - space, anchor).stream();
                anchor[0] += space + 1;
            } else {
                result = Stream.empty();
            }
        }

        return result.sorted(Comparator.comparing(Suggestion::continuation))
                .collect(Collectors.toList());
    }

    // --- Built-in commands ---

    private void registerBuiltinCommands() {
        registerCommand(new Command("/list", "[all|start|<id>]",
                "list the source you have typed",
                "Show the source of snippets, prefaced with the snippet id.",
                arg -> { cmdList(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/drop", "<name or id>",
                "delete a source entry referenced by name or id",
                "Drop a snippet -- making it inactive.",
                arg -> { cmdDrop(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/save", "[all|history|start] <file>",
                "save snippet source to a file",
                "Save the specified snippets and/or commands to the specified file.",
                arg -> { cmdSave(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/open", "<file>",
                "open a file as source input",
                "Open a file and read it as input to the tool.",
                arg -> { cmdOpen(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/vars", "",
                "list the declared variables and their values",
                "List the current active jshell variables.",
                arg -> { cmdVars(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/methods", "",
                "list the declared methods and their signatures",
                "List the current active jshell methods.",
                arg -> { cmdMethods(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/classes", "",
                "list the declared classes",
                "List the current active jshell classes.",
                arg -> { cmdClasses(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/imports", "",
                "list the imported items",
                "List the current active jshell imports.",
                arg -> { cmdImports(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/exit", "",
                "exit jshell",
                "Leave the jshell tool.",
                arg -> { cmdExit(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/reset", "",
                "reset jshell",
                "Reset the jshell tool code and execution state.",
                arg -> { cmdReset(); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/reload", "[restore|quiet]",
                "reset and replay relevant history -- current or previous (restore)",
                "Reset the jshell tool code and execution state then replay each " +
                "valid snippet and any /drop commands in the order they were entered.",
                arg -> { cmdReload(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/classpath", "<path>",
                "add a path to the classpath",
                "Add a path to the classpath for evaluation.",
                arg -> { cmdClasspath(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/help", "[<command>]",
                "get information about jshell",
                "Display information about jshell.",
                arg -> { cmdHelp(arg); return true; },
                CommandKind.NORMAL));
        registerCommand(new Command("/!", "",
                "re-run last snippet",
                "Redo the last snippet.",
                arg -> { cmdUseHistoryEntry(-1); return true; },
                CommandKind.HIDDEN));
    }

    // --- Command implementations ---

    private void cmdList(String arg) {
        Stream<Snippet> snippets;
        if ("all".equals(arg)) {
            snippets = state.snippets();
        } else if ("start".equals(arg)) {
            snippets = state.snippets().filter(s -> startupSnippetIds.contains(s.id()));
        } else {
            snippets = state.snippets().filter(s -> state.status(s).isActive());
        }
        snippets.forEach(sn -> hard("%4s : %s", sn.id(), sn.source().replace("\n", "\n       ")));
    }

    private void cmdDrop(String arg) {
        if (arg.isEmpty()) {
            hard("/drop requires an argument");
            return;
        }
        state.snippets()
                .filter(sn -> sn.id().equals(arg) || (sn instanceof DeclarationSnippet && ((DeclarationSnippet) sn).name().equals(arg)))
                .filter(sn -> state.status(sn).isActive())
                .findFirst()
                .ifPresentOrElse(
                        sn -> state.drop(sn).forEach(this::handleEvent),
                        () -> hard("No such snippet: %s", arg)
                );
    }

    private void cmdSave(String arg) {
        String[] parts = arg.split("\\s+", 2);
        String filename;
        Stream<Snippet> snippets;
        if (parts.length == 2 && ("all".equals(parts[0]) || "start".equals(parts[0]) || "history".equals(parts[0]))) {
            filename = parts[1];
            if ("history".equals(parts[0])) {
                try (BufferedWriter writer = Files.newBufferedWriter(toPathResolvingUserHome(filename))) {
                    for (String entry : replayableHistory) {
                        writer.write(entry);
                        writer.newLine();
                    }
                } catch (IOException e) {
                    hard("File '%s' could not be written: %s", filename, e.getMessage());
                }
                return;
            }
            snippets = "all".equals(parts[0]) ? state.snippets() : state.snippets().filter(s -> state.status(s).isActive());
        } else {
            filename = arg;
            snippets = state.snippets().filter(s -> state.status(s).isActive());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(toPathResolvingUserHome(filename))) {
            snippets.forEach(sn -> {
                try {
                    writer.write(sn.source());
                    writer.newLine();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            hard("File '%s' could not be written: %s", filename, e.getMessage());
        }
    }

    private void cmdOpen(String filename) {
        if (filename.isEmpty()) {
            hard("/open requires a filename argument");
            return;
        }
        try {
            String content = Files.readString(toPathResolvingUserHome(filename));
            processSource(content);
        } catch (IOException e) {
            hard("File '%s' not found: %s", filename, e.getMessage());
        }
    }

    private void cmdVars() {
        state.variables()
                .filter(v -> state.status(v).isActive())
                .forEach(v -> hard("%s %s = %s", v.typeName(), v.name(), state.varValue(v)));
    }

    private void cmdMethods() {
        state.methods()
                .filter(m -> state.status(m).isActive())
                .forEach(m -> hard("%s %s", m.name(), m.signature()));
    }

    private void cmdClasses() {
        state.types()
                .filter(t -> state.status(t).isActive())
                .forEach(t -> hard("%s %s", t.subKind().name().replace("_SUBKIND", "").toLowerCase(), t.name()));
    }

    private void cmdImports() {
        state.imports()
                .forEach(i -> hard("import %s%s", i.isStatic() ? "static " : "", i.fullname()));
    }

    private void cmdExit() {
        regenerateOnDeath = false;
        live = false;
        hard("Goodbye");
    }

    private void cmdReset() {
        hard("Resetting state.");
        live = false;
    }

    private void cmdReload(String arg) {
        boolean restore = "restore".equals(arg.trim());
        boolean quiet = "quiet".equals(arg.trim());
        List<String> toReplay = restore ? replayableHistoryPrevious : replayableHistory;
        if (toReplay == null) {
            toReplay = Collections.emptyList();
        }
        hard("Resetting state.");
        resetState(Collections.emptyList());
        for (String source : toReplay) {
            if (!quiet) {
                hard("%s", source);
            }
            processSource(source);
        }
    }

    private void cmdClasspath(String arg) {
        if (arg.isEmpty()) {
            hard("/classpath requires a path argument");
            return;
        }
        state.addToClasspath(toPathResolvingUserHome(arg).toString());
        fluff("Path %s added to classpath", arg);
    }

    private void cmdHelp(String arg) {
        if (arg.isEmpty()) {
            commands.values().stream()
                    .filter(c -> c.kind.showInHelp)
                    .distinct()
                    .forEach(c -> hard("%-20s -- %s", c.command + " " + c.params, c.description));
        } else {
            Command command = commands.get("/" + arg);
            if (command == null) {
                command = commands.get(arg);
            }
            if (command != null) {
                hard("%s", command.help);
            } else {
                hard("No help found for: %s", arg);
            }
        }
    }

    private void cmdUseHistoryEntry(int index) {
        if (replayableHistory == null || replayableHistory.isEmpty()) {
            hard("No history to replay");
            return;
        }
        int idx = index < 0 ? replayableHistory.size() + index : index;
        if (idx >= 0 && idx < replayableHistory.size()) {
            String source = replayableHistory.get(idx);
            hard("%s", source);
            processSourceCatchingReset(source);
        } else {
            hard("History entry not found: %d", index);
        }
    }

    // --- Arg suggestion ---

    public static class ArgSuggestion implements Suggestion {
        private final String continuation;

        public ArgSuggestion(String continuation) {
            this.continuation = continuation;
        }

        @Override
        public String continuation() {
            return continuation;
        }

        @Override
        public boolean matchesType() {
            return false;
        }
    }

    // --- Utility ---

    public static Path toPathResolvingUserHome(String pathString) {
        if (pathString.replace("~", "").trim().isEmpty()) {
            return Paths.get(System.getProperty("user.home"));
        }
        if (pathString.startsWith("~")) {
            return Paths.get(System.getProperty("user.home"), pathString.substring(1));
        }
        return Paths.get(pathString);
    }

    private String version() {
        return System.getProperty("java.version", "unknown");
    }

    // --- Command args processing ---

    private List<String> processCommandArgs(String[] args) {
        List<String> loadList = new ArrayList<>();
        Iterator<String> ai = Arrays.asList(args).iterator();
        while (ai.hasNext()) {
            String arg = ai.next();
            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-classpath":
                    case "-cp":
                        if (cmdlineClasspath != null) {
                            cmderr.printf("Conflicting -classpath option.%n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            cmdlineClasspath = ai.next();
                        } else {
                            cmderr.printf("Argument to -classpath missing.%n");
                            return null;
                        }
                        break;
                    case "-startup":
                        if (cmdlineStartup != null) {
                            cmderr.printf("Conflicting -startup or -nostartup option.%n");
                            return null;
                        }
                        if (ai.hasNext()) {
                            String filename = ai.next();
                            try {
                                cmdlineStartup = Files.readString(Paths.get(filename));
                            } catch (IOException e) {
                                hard("File '%s' for start-up is not accessible: %s", filename, e.getMessage());
                            }
                        } else {
                            cmderr.printf("Argument to -startup missing.%n");
                            return null;
                        }
                        break;
                    case "-nostartup":
                        if (cmdlineStartup != null && !cmdlineStartup.isEmpty()) {
                            cmderr.printf("Conflicting -startup option.%n");
                            return null;
                        }
                        cmdlineStartup = "";
                        break;
                    case "-help":
                        printUsage();
                        return null;
                    case "-version":
                        cmdout.printf("jetshell %s%n", version());
                        return null;
                    default:
                        cmderr.printf("Unknown option: %s%n", arg);
                        printUsage();
                        return null;
                }
            } else {
                loadList.add(arg);
            }
        }
        return loadList;
    }

    private void printUsage() {
        cmdout.printf("Usage:   jetshell <options> <load files>%n");
        cmdout.printf("where possible options include:%n");
        cmdout.printf("  -classpath <path>          Specify where to find user class files%n");
        cmdout.printf("  -cp <path>                 Specify where to find user class files%n");
        cmdout.printf("  -startup <file>            One run replacement for the start-up definitions%n");
        cmdout.printf("  -nostartup                 Do not run the start-up definitions%n");
        cmdout.printf("  -help                      Print a synopsis of standard options%n");
        cmdout.printf("  -version                   Version information%n");
    }
}
