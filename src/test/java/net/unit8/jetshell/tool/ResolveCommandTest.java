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
                a -> assertCommand(a, "/resolve bad", "", null, null, "", ""),
                a -> {
                    if (a) {
                        String err = getCommandErrorOutput();
                        assertTrue(err.contains("bad"), "Expected error to mention 'bad', got: " + err);
                    }
                },
                a -> assertCompletion(a, "/resolve hoge:hoge:hoge:hoge:hoge:hoge|", true)
        );
    }
}
