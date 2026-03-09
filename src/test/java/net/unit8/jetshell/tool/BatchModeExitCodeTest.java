package net.unit8.jetshell.tool;

import net.unit8.jetshell.command.JetShellCommandRegister;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class BatchModeExitCodeTest extends JetShellTesting {

    // Consume output buffers so testRaw's final assertions pass
    private void drainOutputs() {
        getCommandOutput();
        getCommandErrorOutput();
    }

    public void testHadFailureSetOnRejectedSnippet() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("int x = \"not an int\";\n");
                    } else {
                        drainOutputs();
                        assertTrue(repl.hadFailure(), "hadFailure should be true after a rejected snippet");
                    }
                }
        );
    }

    public void testHadFailureNotSetOnValidSnippet() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("int x = 1;\n");
                    } else {
                        drainOutputs();
                        assertFalse(repl.hadFailure(), "hadFailure should be false after a valid snippet");
                    }
                }
        );
    }

    public void testHadFailureSetOnRuntimeException() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("throw new RuntimeException(\"boom\");\n");
                    } else {
                        drainOutputs();
                        assertTrue(repl.hadFailure(), "hadFailure should be true after a runtime exception");
                    }
                }
        );
    }

    public void testStartReturnsOneOnFailure() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("int x = \"not an int\";\n/exit\n".getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JetShellTool tool = JetShellTool.create(in, new PrintStream(out), new PrintStream(out));
        new JetShellCommandRegister().register(tool);
        tool.testPrompt = true;
        int exitCode = tool.start(new String[0]);
        assertEquals(exitCode, 1, "start() should return 1 when a snippet fails");
    }

    public void testStartReturnsZeroOnSuccess() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("int x = 1;\n/exit\n".getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JetShellTool tool = JetShellTool.create(in, new PrintStream(out), new PrintStream(out));
        new JetShellCommandRegister().register(tool);
        tool.testPrompt = true;
        int exitCode = tool.start(new String[0]);
        assertEquals(exitCode, 0, "start() should return 0 when all snippets succeed");
    }

    public void testHadFailureResetOnReload() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("int x = \"not an int\";\n");
                    } else {
                        drainOutputs();
                        assertTrue(repl.hadFailure(), "hadFailure should be true after a rejected snippet");
                    }
                },
                a -> {
                    if (!a) {
                        setCommandInput("/reload\n");
                    } else {
                        drainOutputs();
                        assertFalse(repl.hadFailure(), "hadFailure should be reset after /reload");
                    }
                }
        );
    }
}
