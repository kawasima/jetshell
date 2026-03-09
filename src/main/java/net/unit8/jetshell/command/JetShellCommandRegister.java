package net.unit8.jetshell.command;

import net.unit8.erebus.Erebus;
import net.unit8.jetshell.tool.JetShellTool;

/**
 * @author kawasima
 */
public class JetShellCommandRegister {
    public void register(JetShellTool tool) {
        ResolveContext context = new ResolveContext(new Erebus.Builder().build());

        tool.registerCommand(ResolveCommand.create(tool, context));
        tool.registerCommand(DepsCommand.create(tool, context));
        tool.registerCommand(DocCommand.create(tool, context));
        tool.registerCommand(SourceCommand.create(tool, context));
    }
}
