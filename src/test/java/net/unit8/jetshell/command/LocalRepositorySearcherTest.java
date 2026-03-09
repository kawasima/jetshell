package net.unit8.jetshell.command;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.*;

@Test
public class LocalRepositorySearcherTest {

    private Path tempRepo;
    private LocalRepositorySearcher searcher;

    @BeforeMethod
    public void setUp() throws IOException {
        tempRepo = Files.createTempDirectory("test-m2-repo");

        // Create fake repo structure:
        // org/apache/commons/commons-lang3/3.12.0/commons-lang3-3.12.0.pom
        // org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.pom
        // org/apache/commons/commons-text/1.10.0/commons-text-1.10.0.pom
        // com/google/guava/guava/32.1.2-jre/guava-32.1.2-jre.pom
        createArtifact("org/apache/commons", "commons-lang3", "3.12.0");
        createArtifact("org/apache/commons", "commons-lang3", "3.14.0");
        createArtifact("org/apache/commons", "commons-text", "1.10.0");
        createArtifact("com/google/guava", "guava", "32.1.2-jre");

        searcher = new LocalRepositorySearcher(tempRepo);
    }

    private void createArtifact(String groupPath, String artifactId, String version) throws IOException {
        Path dir = tempRepo.resolve(groupPath).resolve(artifactId).resolve(version);
        Files.createDirectories(dir);
        Files.createFile(dir.resolve(artifactId + "-" + version + ".pom"));
    }

    public void testSearchGroupId() {
        List<String> results = searcher.search("org.apache");
        assertTrue(results.contains("org.apache"), "Should find org.apache, got: " + results);
    }

    public void testSearchGroupIdPartial() {
        List<String> results = searcher.search("org.ap");
        assertTrue(results.stream().anyMatch(r -> r.startsWith("org.ap")),
                "Should find org.apache, got: " + results);
    }

    public void testSearchArtifactId() {
        List<String> results = searcher.search("org.apache.commons:commons-l");
        assertTrue(results.contains("org.apache.commons:commons-lang3"),
                "Should find commons-lang3, got: " + results);
        assertFalse(results.contains("org.apache.commons:commons-text"),
                "Should not find commons-text for prefix commons-l");
    }

    public void testSearchArtifactIdAll() {
        List<String> results = searcher.search("org.apache.commons:");
        assertTrue(results.contains("org.apache.commons:commons-lang3"),
                "Should find commons-lang3, got: " + results);
        assertTrue(results.contains("org.apache.commons:commons-text"),
                "Should find commons-text, got: " + results);
    }

    public void testSearchVersion() {
        List<String> results = searcher.search("org.apache.commons:commons-lang3:3.");
        assertTrue(results.contains("org.apache.commons:commons-lang3:3.12.0"),
                "Should find 3.12.0, got: " + results);
        assertTrue(results.contains("org.apache.commons:commons-lang3:3.14.0"),
                "Should find 3.14.0, got: " + results);
    }

    public void testSearchVersionAll() {
        List<String> results = searcher.search("org.apache.commons:commons-lang3:");
        assertEquals(results.size(), 2, "Should find 2 versions, got: " + results);
    }

    public void testSearchDifferentGroup() {
        List<String> results = searcher.search("com.google.guava:guava:");
        assertTrue(results.contains("com.google.guava:guava:32.1.2-jre"),
                "Should find guava version, got: " + results);
    }

    public void testSearchEmpty() {
        List<String> results = searcher.search("");
        assertTrue(results.isEmpty());
    }

    public void testSearchNoMatch() {
        List<String> results = searcher.search("nonexistent");
        assertTrue(results.isEmpty(), "Should find nothing, got: " + results);
    }
}
