package net.unit8.jetshell;

import net.unit8.jetshell.command.JetShellCommandRegister;
import net.unit8.jetshell.tool.JetShellTool;

/**
 * @author kawasima
 */
public class JetShell {
    public static void main(String[] args) throws Exception {
        JetShellTool tool = JetShellTool.create(System.in, System.out, System.err);
        new JetShellCommandRegister().register(tool);
        tool.start(args);
    }
}
