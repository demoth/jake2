/*
 * Cmd_Wait_f.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package jake2.qcommon;

import jake2.Globals;

/**
 * Cmd_Wait_f sets the wait bit
 */
public final class Cmd_Wait_f implements xcommand_t {

	/* (non-Javadoc)
	 * @see quake2.xcommand_t#execute()
	 */
	public void execute() {
		Globals.cmd_wait = true;
	}

}
