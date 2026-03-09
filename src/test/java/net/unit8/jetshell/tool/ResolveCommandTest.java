package net.unit8.jetshell.tool;

import org.testng.annotations.Test;

/**
 * @author bitter_fox
 */
@Test
public class ResolveCommandTest extends JetShellTesting {
    public void testBadArtifactCoordinate() {
        test(
                a -> assertCommand(a, "/resolve bad", "", "|  Bad artifact coordinates bad, expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>\n", null, "", ""),
                a -> assertCompletion(a, "/resolve hoge:hoge:hoge:hoge:hoge:hoge|", true)
        );
    }
}
