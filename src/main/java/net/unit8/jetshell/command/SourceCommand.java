package net.unit8.jetshell.command;

import jdk.jshell.ImportSnippet;
import net.unit8.jetshell.tool.JetShellTool;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * /source command — displays the source code of a class from resolved artifacts.
 */
class SourceCommand {

    static JetShellTool.Command create(JetShellTool tool, ResolveContext context) {
        return new JetShellTool.Command("/source", "<class>",
                "show source code for a class",
                "Display the source code of a fully-qualified class or a simple name (if imported).\n" +
                "   e.g. /source org.apache.commons.lang3.StringUtils\n" +
                "   e.g. /source StringUtils   (if imported)\n" +
                "   The source JAR is downloaded automatically if needed.",
                arg -> {
                    if (arg.isEmpty()) {
                        tool.hard("/source requires a class name");
                        return false;
                    }

                    // Resolve short name to FQCN using active imports if needed
                    String fqcn = resolveName(tool, arg);
                    String classPath = fqcn.replace('.', '/') + ".class";
                    String sourcePath = fqcn.replace('.', '/') + ".java";

                    for (File jar : context.getAllResolvedFiles()) {
                        if (!jar.getName().endsWith(".jar")) continue;

                        try (JarFile jarFile = new JarFile(jar)) {
                            if (jarFile.getJarEntry(classPath) == null) continue;

                            // Found the class — ensure source JAR exists
                            File sourceJar = context.ensureSourceJar(jar);
                            if (sourceJar == null) {
                                tool.hard("Source JAR not available for %s", jar.getName());
                                return false;
                            }

                            return displaySource(tool, sourceJar, sourcePath);
                        } catch (IOException e) {
                            tool.error("Warning: could not read JAR %s: %s", jar.getName(), e.getMessage());
                        }
                    }

                    tool.hard("Class %s not found in resolved artifacts", arg);
                    return false;
                },
                JetShellTool.CommandKind.NORMAL);
    }

    /**
     * If arg contains no '.', try to resolve it as a simple class name using active imports.
     * e.g. "StringUtils" -> "org.apache.commons.lang3.StringUtils"
     */
    private static String resolveName(JetShellTool tool, String arg) {
        if (arg.contains(".")) return arg;
        return tool.getState().imports()
                .filter(imp -> !imp.isStatic())
                .map(ImportSnippet::fullname)
                .filter(fqcn -> fqcn.endsWith("." + arg))
                .findFirst()
                .orElse(arg);
    }

    private static boolean displaySource(JetShellTool tool, File sourceJar, String sourcePath) {
        try (JarFile jarFile = new JarFile(sourceJar)) {
            JarEntry entry = jarFile.getJarEntry(sourcePath);
            if (entry == null) {
                tool.hard("Source file %s not found in %s", sourcePath, sourceJar.getName());
                return false;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8))) {
                String line;
                int lineNum = 1;
                while ((line = reader.readLine()) != null) {
                    tool.hard("%4d: %s", lineNum++, line);
                }
            }
            return true;
        } catch (IOException e) {
            tool.error("Failed to read source: %s", e.getMessage());
            return false;
        }
    }
}
