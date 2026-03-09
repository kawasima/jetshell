package net.unit8.jetshell.command;

import net.unit8.erebus.Erebus;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Shared state for resolved artifacts across commands.
 */
public class ResolveContext {
    private final Erebus erebus;
    private final List<String> resolvedSpecs = new ArrayList<>();
    private final Map<String, List<File>> resolvedFiles = new LinkedHashMap<>();

    public ResolveContext(Erebus erebus) {
        this.erebus = erebus;
    }

    public Erebus getErebus() {
        return erebus;
    }

    public void addResolved(String spec, List<File> files) {
        resolvedSpecs.add(spec);
        resolvedFiles.put(spec, files);
    }

    public List<String> getResolvedSpecs() {
        return Collections.unmodifiableList(resolvedSpecs);
    }

    public Map<String, List<File>> getResolvedFiles() {
        return Collections.unmodifiableMap(resolvedFiles);
    }

    /**
     * Returns all resolved JAR files across all specs.
     */
    public List<File> getAllResolvedFiles() {
        List<File> all = new ArrayList<>();
        resolvedFiles.values().forEach(all::addAll);
        return all;
    }

    /**
     * Derives the source JAR file path from a regular JAR file.
     * e.g. commons-lang3-3.14.0.jar -> commons-lang3-3.14.0-sources.jar
     */
    public static File sourceJarFor(File jar) {
        String name = jar.getName();
        if (name.endsWith(".jar")) {
            String sourceName = name.substring(0, name.length() - 4) + "-sources.jar";
            return new File(jar.getParentFile(), sourceName);
        }
        return null;
    }

    /**
     * Ensures the source JAR for the given JAR exists locally, downloading it if needed.
     * Returns the source JAR file, or null if unavailable.
     */
    public File ensureSourceJar(File jar) {
        File sourceJar = sourceJarFor(jar);
        if (sourceJar != null && sourceJar.exists()) {
            return sourceJar;
        }

        // Derive GAV from local repo path using Path API (OS-independent):
        // <repoRoot>/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar
        Path repoRoot = Paths.get(System.getProperty("user.home"), ".m2", "repository");
        Path jarPath = jar.toPath().toAbsolutePath();
        if (!jarPath.startsWith(repoRoot)) return null;

        Path relative = repoRoot.relativize(jarPath);
        int nameCount = relative.getNameCount();
        // Expect at least: <groupId>/<artifactId>/<version>/<file> = 4 segments
        if (nameCount < 4) return null;

        String version = relative.getName(nameCount - 2).toString();
        String artifactId = relative.getName(nameCount - 3).toString();
        StringBuilder groupId = new StringBuilder();
        for (int i = 0; i < nameCount - 3; i++) {
            if (i > 0) groupId.append('.');
            groupId.append(relative.getName(i).toString());
        }

        try {
            String sourceSpec = groupId + ":" + artifactId + ":jar:sources:" + version;
            // resolveAsFiles is called for its side effect: downloading the source JAR
            // into the local repository so that sourceJar.exists() becomes true.
            erebus.resolveAsFiles(sourceSpec);
            return sourceJar != null && sourceJar.exists() ? sourceJar : null;
        } catch (DependencyCollectionException | DependencyResolutionException |
                 IllegalArgumentException e) {
            return null;
        }
    }
}
