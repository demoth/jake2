/*
 * Cmd_Echo_f.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package jake2.qcommon;

import jake2.game.Cmd;

/**
 * Cmd_Echo_f
 */
public final class Cmd_Echo_f implements xcommand_t {

	/* (non-Javadoc)
	 * @see quake2.xcommand_t#execute()
	 */
	public void execute() {
			for (int i  = 1;  i < Cmd.Argc(); i++) {
				Com.print(Cmd.Argv(i) + " ");
			}
			Com.print("'\n");
	}

}
