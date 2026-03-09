package net.unit8.jetshell.command;

import jdk.jshell.SourceCodeAnalysis;
import net.unit8.erebus.ArtifactSearcher;
import net.unit8.jetshell.tool.JetShellTool;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * /resolve command — resolves a Maven artifact and adds it to the classpath.
 */
class ResolveCommand {

    static JetShellTool.Command create(JetShellTool tool, ResolveContext context) {
        ArtifactSearcher searcher = new ArtifactSearcher();
        LocalRepositorySearcher localSearcher = new LocalRepositorySearcher();

        return new JetShellTool.Command("/resolve", "<spec>",
                "resolve an artifact",
                "Resolve an artifact\n" +
                "   spec is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>",
                arg -> {
                    if (arg.isEmpty()) {
                        tool.error("/resolve requires a maven spec argument");
                        return false;
                    }
                    try {
                        List<File> artifacts = context.getErebus().resolveAsFiles(arg);
                        artifacts.stream().map(File::getPath)
                                .forEach(path -> {
                                    tool.getState().addToClasspath(
                                            JetShellTool.toPathResolvingUserHome(path).toString());
                                    tool.fluff("Path %s added to classpath", path);
                                });
                        context.addResolved(arg, artifacts);
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
                    if (code.isEmpty()) return results;

                    List<String> localResults = localSearcher.search(code);
                    if (!localResults.isEmpty()) {
                        localResults.stream()
                                .map(JetShellTool.ArgSuggestion::new)
                                .forEach(results::add);
                        anchor[0] = 0;
                        return results;
                    }

                    try {
                        List<Artifact> artifacts = searcher.searchIncremental(code);
                        artifacts.stream()
                                .map(a -> new JetShellTool.ArgSuggestion(a.toString()))
                                .forEach(results::add);
                        anchor[0] = 0;
                    } catch (IOException | IllegalArgumentException ignore) {
                    }
                    return results;
                },
                JetShellTool.CommandKind.REPLAY);
    }
}
