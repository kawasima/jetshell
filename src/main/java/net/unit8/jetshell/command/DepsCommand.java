package net.unit8.jetshell.command;

import jdk.jshell.SourceCodeAnalysis;
import net.unit8.jetshell.tool.JetShellTool;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.util.ArrayList;
import java.util.List;

/**
 * /deps command — shows resolved artifacts and dependency trees.
 */
class DepsCommand {

    static JetShellTool.Command create(JetShellTool tool, ResolveContext context) {
        return new JetShellTool.Command("/deps", "[spec]",
                "show resolved dependencies",
                "Show resolved artifacts in this session.\n" +
                "   /deps          - list all resolved artifacts\n" +
                "   /deps <spec>   - show dependency tree for a specific artifact",
                arg -> {
                    if (arg.isEmpty()) {
                        List<String> specs = context.getResolvedSpecs();
                        if (specs.isEmpty()) {
                            tool.hard("No artifacts resolved yet. Use /resolve first.");
                            return false;
                        }
                        for (String spec : specs) {
                            tool.hard("%s (%d files)", spec, context.getResolvedFiles().get(spec).size());
                        }
                        return true;
                    }

                    try {
                        DependencyNode root = context.getErebus().resolveAsDependencyNode(arg);
                        printTree(tool, root, "");
                        return true;
                    } catch (DependencyCollectionException |
                             DependencyResolutionException |
                             IllegalArgumentException e) {
                        tool.error("%s", e.getMessage());
                        return false;
                    }
                },
                (code, cursor, anchor) -> {
                    List<SourceCodeAnalysis.Suggestion> results = new ArrayList<>();
                    for (String spec : context.getResolvedSpecs()) {
                        if (spec.startsWith(code)) {
                            results.add(new JetShellTool.ArgSuggestion(spec));
                        }
                    }
                    anchor[0] = 0;
                    return results;
                },
                JetShellTool.CommandKind.NORMAL);
    }

    private static void printTree(JetShellTool tool, DependencyNode node, String indent) {
        Artifact artifact = node.getArtifact();
        if (artifact != null) {
            tool.hard("%s%s", indent, artifact);
        }
        String childIndent = indent.isEmpty() ? "  " : indent + "  ";
        for (DependencyNode child : node.getChildren()) {
            printTree(tool, child, childIndent);
        }
    }
}
