package net.unit8.jetshell.tool;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author bitter_fox
 */
@Test
public class ResolveCommandTest extends JetShellTesting {
    public void testBadArtifactCoordinate() {
        test(
                // Pass err="" so the buffer is consumed and we assert it is empty on cmdout,
                // then check cmderr in the same step via a custom lambda that reads and clears it.
                a -> {
                    if (!a) {
                        setCommandInput("/resolve bad\n");
                    } else {
                        assertOutput(getCommandOutput(), "", "command");
                        String err = getCommandErrorOutput();
                        assertTrue(err.contains("bad"), "Expected error to mention 'bad', got: " + err);
                    }
                },
                a -> assertCompletion(a, "/resolve hoge:hoge:hoge:hoge:hoge:hoge|", true)
        );
    }
}
