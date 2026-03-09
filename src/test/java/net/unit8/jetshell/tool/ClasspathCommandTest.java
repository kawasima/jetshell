package net.unit8.jetshell.tool;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.testng.Assert.assertTrue;

@Test
public class ClasspathCommandTest extends JetShellTesting {

    private Path tempDir;

    @BeforeMethod
    public void createTempDir() throws IOException {
        tempDir = Files.createTempDirectory("jetshell-classpath-test");
    }

    @AfterMethod
    public void deleteTempDir() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> p.toFile().delete());
    }

    public void testExactPath() throws IOException {
        Path jar = Files.createFile(tempDir.resolve("mylib-1.0.jar"));
        test(
                a -> {
                    if (!a) {
                        setCommandInput("/classpath " + jar.toAbsolutePath() + "\n");
                    } else {
                        String out = getCommandOutput();
                        assertTrue(out.contains("added to classpath"), "Expected success message, got: " + out);
                    }
                }
        );
    }

    public void testGlobMatchesMultiple() throws IOException {
        Files.createFile(tempDir.resolve("mylib-1.0.jar"));
        Files.createFile(tempDir.resolve("mylib-2.0.jar"));
        String glob = tempDir.toAbsolutePath() + "/mylib-*.jar";
        test(
                a -> {
                    if (!a) {
                        setCommandInput("/classpath " + glob + "\n");
                    } else {
                        String out = getCommandOutput();
                        assertTrue(out.contains("mylib-1.0.jar"), "Expected mylib-1.0.jar in output, got: " + out);
                        assertTrue(out.contains("mylib-2.0.jar"), "Expected mylib-2.0.jar in output, got: " + out);
                    }
                }
        );
    }

    public void testGlobNoMatch() {
        String glob = tempDir.toAbsolutePath() + "/nonexistent-*.jar";
        test(
                a -> {
                    if (!a) {
                        setCommandInput("/classpath " + glob + "\n");
                    } else {
                        assertOutput(getCommandOutput(), "", "command");
                        String err = getCommandErrorOutput();
                        assertTrue(err.contains("No files matched"), "Expected no-match error, got: " + err);
                    }
                }
        );
    }

    public void testInvalidGlobPattern() {
        String glob = tempDir.toAbsolutePath() + "/[invalid";
        test(
                a -> {
                    if (!a) {
                        setCommandInput("/classpath " + glob + "\n");
                    } else {
                        assertOutput(getCommandOutput(), "", "command");
                        String err = getCommandErrorOutput();
                        assertTrue(err.contains("Cannot expand glob"), "Expected glob error, got: " + err);
                    }
                }
        );
    }

    public void testMissingArgument() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("/classpath\n");
                    } else {
                        assertOutput(getCommandOutput(), "", "command");
                        String err = getCommandErrorOutput();
                        assertTrue(err.contains("/classpath requires a path argument"), "Expected missing-arg error, got: " + err);
                    }
                }
        );
    }
}
