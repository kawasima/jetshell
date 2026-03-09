package net.unit8.jetshell.tool;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class BatchModeExitCodeTest extends JetShellTesting {

    public void testHadFailureSetOnRejectedSnippet() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("int x = \"not an int\";\n");
                    } else {
                        // consume outputs so testRaw's final assertions pass
                        getCommandOutput();
                        getCommandErrorOutput();
                        assertTrue(repl.hadFailure, "hadFailure should be true after a rejected snippet");
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
                        getCommandOutput();
                        getCommandErrorOutput();
                        assertFalse(repl.hadFailure, "hadFailure should be false after a valid snippet");
                    }
                }
        );
    }

    public void testHadFailureResetOnReload() {
        test(
                a -> {
                    if (!a) {
                        setCommandInput("int x = \"not an int\";\n");
                    } else {
                        getCommandOutput();
                        getCommandErrorOutput();
                        assertTrue(repl.hadFailure, "hadFailure should be true after a rejected snippet");
                    }
                },
                a -> {
                    if (!a) {
                        setCommandInput("/reload\n");
                    } else {
                        getCommandOutput();
                        getCommandErrorOutput();
                        assertFalse(repl.hadFailure, "hadFailure should be reset after /reload");
                    }
                }
        );
    }
}
