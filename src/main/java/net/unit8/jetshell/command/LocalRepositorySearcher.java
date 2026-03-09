package net.unit8.jetshell.command;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Searches the local Maven repository (~/.m2/repository) for artifact completion.
 *
 * Repository layout: groupId(dots→dirs)/artifactId/version/
 * e.g. org/apache/commons/commons-lang3/3.14.0/
 *
 * @author kawasima
 */
class LocalRepositorySearcher {

    private final Path repoRoot;

    LocalRepositorySearcher() {
        this(Paths.get(System.getProperty("user.home"), ".m2", "repository"));
    }

    LocalRepositorySearcher(Path repoRoot) {
        this.repoRoot = repoRoot;
    }

    /**
     * Search local repository for artifacts matching the partial coordinate.
     *
     * @param coords partial Maven coordinates (e.g. "org.apache", "org.apache.commons:commons-l", "org.apache.commons:commons-lang3:3.")
     * @return list of matching coordinate strings
     */
    List<String> search(String coords) {
        if (coords == null || coords.isEmpty()) {
            return List.of();
        }

        String[] parts = coords.split(":", -1);
        return switch (parts.length) {
            case 1 -> searchGroupId(parts[0]);
            case 2 -> searchArtifactId(parts[0], parts[1]);
            case 3 -> searchVersion(parts[0], parts[1], parts[2]);
            default -> List.of();
        };
    }

    /**
     * Search for groupIds matching the prefix.
     * Input: "org.apache.comm"
     * Searches: repo/org/apache/comm*
     */
    private List<String> searchGroupId(String partialGroupId) {
        // Convert dot notation to path: org.apache.commons -> org/apache/commons
        String pathStr = partialGroupId.replace('.', '/');
        Path searchDir;
        String prefix;

        int lastSlash = pathStr.lastIndexOf('/');
        if (lastSlash >= 0) {
            searchDir = repoRoot.resolve(pathStr.substring(0, lastSlash));
            prefix = pathStr.substring(lastSlash + 1);
        } else {
            searchDir = repoRoot;
            prefix = pathStr;
        }

        List<String> results = new ArrayList<>();
        if (!Files.isDirectory(searchDir)) {
            return results;
        }

        try (Stream<Path> entries = Files.list(searchDir)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted()
                    .limit(30)
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String groupPrefix = lastSlash >= 0
                                ? partialGroupId.substring(0, partialGroupId.lastIndexOf('.') + 1)
                                : "";
                        String fullGroup = groupPrefix + name;
                        // Always include as a groupId candidate; containsPom means it also
                        // acts as an artifactId, but the user may still want to type ":"
                        // to complete the artifactId (e.g. "com.google.guava:guava").
                        results.add(fullGroup);
                    });
        } catch (IOException e) {
            // ignore
        }
        return results;
    }

    /**
     * Search for artifactIds within a groupId.
     * Input groupId: "org.apache.commons", partialArtifact: "commons-l"
     * Searches: repo/org/apache/commons/commons-l*
     */
    private List<String> searchArtifactId(String groupId, String partialArtifact) {
        Path groupDir = repoRoot.resolve(groupId.replace('.', '/'));
        if (!Files.isDirectory(groupDir)) {
            return List.of();
        }

        List<String> results = new ArrayList<>();
        try (Stream<Path> entries = Files.list(groupDir)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(partialArtifact))
                    .filter(this::containsPom)
                    .sorted()
                    .limit(30)
                    .forEach(p -> results.add(groupId + ":" + p.getFileName()));
        } catch (IOException e) {
            // ignore
        }
        return results;
    }

    /**
     * Search for versions of a specific artifact.
     * Input groupId: "org.apache.commons", artifactId: "commons-lang3", partialVersion: "3."
     * Searches: repo/org/apache/commons/commons-lang3/3.*
     */
    private List<String> searchVersion(String groupId, String artifactId, String partialVersion) {
        Path artifactDir = repoRoot.resolve(groupId.replace('.', '/')).resolve(artifactId);
        if (!Files.isDirectory(artifactDir)) {
            return List.of();
        }

        List<String> results = new ArrayList<>();
        try (Stream<Path> entries = Files.list(artifactDir)) {
            entries.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith(partialVersion))
                    .sorted()
                    .limit(30)
                    .forEach(p -> results.add(groupId + ":" + artifactId + ":" + p.getFileName()));
        } catch (IOException e) {
            // ignore
        }
        return results;
    }

    /**
     * Check if a directory contains any .pom files in subdirectories (indicating it's an artifact directory).
     */
    private boolean containsPom(Path dir) {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(Files::isDirectory)
                    .anyMatch(sub -> {
                        try (Stream<Path> files = Files.list(sub)) {
                            return files.anyMatch(f -> f.getFileName().toString().endsWith(".pom"));
                        } catch (IOException e) {
                            return false;
                        }
                    });
        } catch (IOException e) {
            return false;
        }
    }
}
