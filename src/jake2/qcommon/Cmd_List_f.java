/*
 * Cmd_List_f.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package jake2.qcommon;

import jake2.game.Cmd;

/**
 * Cmd_List_f command to list all supported commands
 */
public final class Cmd_List_f implements xcommand_t {

	/* (non-Javadoc)
	 * @see quake2.xcommand_t#execute()
	 */
	public void execute() {
		cmd_function_t  cmd = Cmd.cmd_functions;
		int i = 0;

		while (cmd != null) {
			Com.print(cmd.name + '\n');
			i++;
			cmd = cmd.next;
		}
		Com.print(i + " commands\n");
	}

}
