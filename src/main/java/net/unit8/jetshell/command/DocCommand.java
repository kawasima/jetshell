package net.unit8.jetshell.command;

import jdk.jshell.ImportSnippet;
import jdk.jshell.SourceCodeAnalysis;
import net.unit8.jetshell.tool.JetShellTool;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarFile;

/**
 * /doc command — shows Javadoc documentation for a Java element.
 */
class DocCommand {

    static JetShellTool.Command create(JetShellTool tool, ResolveContext context) {
        return new JetShellTool.Command("/doc", "<expression>",
                "show documentation for a Java element",
                "Show Javadoc documentation for a class, method, or field.\n" +
                "   e.g. /doc java.util.List\n" +
                "   e.g. /doc System.out.println\n" +
                "   Source JARs are downloaded automatically if needed.",
                arg -> {
                    if (arg.isEmpty()) {
                        tool.error("/doc requires an expression argument");
                        return false;
                    }
                    SourceCodeAnalysis analysis = tool.getState().sourceCodeAnalysis();
                    List<SourceCodeAnalysis.Documentation> docs =
                            analysis.documentation(arg, arg.length(), true);
                    if (docs.isEmpty()) {
                        tool.error("No documentation found for: %s", arg);
                        return false;
                    }

                    // Check if any doc is missing javadoc
                    boolean needsSourceJar = docs.stream()
                            .anyMatch(d -> d.javadoc() == null || d.javadoc().isEmpty());

                    if (needsSourceJar) {
                        // Try to find and load source JARs for the relevant class
                        String className = extractClassName(arg);
                        if (className != null && tryLoadSourceJar(tool, context, className)) {
                            // Re-query documentation after source JAR added to classpath
                            docs = analysis.documentation(arg, arg.length(), true);
                        }
                    }

                    for (SourceCodeAnalysis.Documentation doc : docs) {
                        tool.hard("%s", doc.signature());
                        String javadoc = doc.javadoc();
                        if (javadoc != null && !javadoc.isEmpty()) {
                            tool.hard("");
                            for (String line : javadoc.split("\n")) {
                                tool.hard("%s", line);
                            }
                        } else {
                            tool.hard("(No Javadoc available — source JAR not found)");
                        }
                    }
                    return true;
                },
                (code, cursor, anchor) -> {
                    SourceCodeAnalysis analysis = tool.getState().sourceCodeAnalysis();
                    return analysis.completionSuggestions(code, cursor, anchor);
                },
                JetShellTool.CommandKind.NORMAL);
    }

    /**
     * Extract the class name from an expression by stripping trailing lowercase-starting
     * segments (methods or fields) until we reach an uppercase-starting segment (class name).
     *
     * Examples:
     *   "org.foo.Bar.method("  -> "org.foo.Bar"
     *   "System.out.println("  -> "System"   (strips "println", then "out")
     *   "System.out"           -> "System"   (strips "out")
     *   "org.foo.Bar"          -> "org.foo.Bar"
     *   "StringUtils"          -> "StringUtils"
     */
    private static String extractClassName(String expr) {
        // Strip trailing '(' and everything after
        String s = expr.trim();
        int paren = s.indexOf('(');
        if (paren >= 0) s = s.substring(0, paren).trim();

        // Repeatedly strip trailing lowercase-starting segments
        while (true) {
            int lastDot = s.lastIndexOf('.');
            if (lastDot < 0) break;
            char afterDot = s.charAt(lastDot + 1);
            if (Character.isLowerCase(afterDot)) {
                s = s.substring(0, lastDot);
            } else {
                break;
            }
        }
        return s.isEmpty() ? null : s;
    }

    /**
     * If className contains no '.', resolve it to FQCN:
     * 1. Explicit non-static imports, 2. SourceCodeAnalysis (covers wildcard imports).
     */
    private static String resolveName(JetShellTool tool, String className) {
        if (className.contains(".")) return className;

        // 1. Explicit imports
        String fromImports = tool.getState().imports()
                .filter(imp -> !imp.isStatic())
                .map(ImportSnippet::fullname)
                .filter(fqcn -> fqcn.endsWith("." + className))
                .findFirst()
                .orElse(null);
        if (fromImports != null) return fromImports;

        // 2. Wildcard imports via SourceCodeAnalysis
        SourceCodeAnalysis.QualifiedNames qn =
                tool.getState().sourceCodeAnalysis().listQualifiedNames(className, className.length());
        List<String> names = qn.getNames();
        if (!names.isEmpty()) return names.get(0);

        return className;
    }

    /**
     * Find which resolved JAR contains the class, then ensure its source JAR is loaded.
     * Returns true if a source JAR was successfully added to the classpath.
     */
    private static boolean tryLoadSourceJar(JetShellTool tool, ResolveContext context, String className) {
        String fqcn = resolveName(tool, className);
        String classPath = fqcn.replace('.', '/') + ".class";

        for (File jar : context.getAllResolvedFiles()) {
            if (!jar.getName().endsWith(".jar")) continue;
            try (JarFile jarFile = new JarFile(jar)) {
                if (jarFile.getJarEntry(classPath) == null) continue;

                File sourceJar = context.ensureSourceJar(jar);
                if (sourceJar != null) {
                    tool.fluff("Loading source JAR: %s", sourceJar.getName());
                    tool.getState().addToClasspath(sourceJar.getAbsolutePath());
                    return true;
                }
                return false;
            } catch (IOException e) {
                // continue
            }
        }
        return false;
    }
}
