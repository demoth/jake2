/*
 * Cmd_Exec_f.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package jake2.qcommon;

import jake2.game.Cmd;

/**
 * Cmd_Exec_f
 */
public final class Cmd_Exec_f implements xcommand_t {

	/* (non-Javadoc)
	 * @see quake2.xcommand_t#execute()
	 */
	public void execute() {
		// TODO Auto-generated method stub

		//00373         char    *f, *f2;
		//00374         int             len;
		if (Cmd.Argc() != 2) {
			Com.print("exec <filename> : execute a script file\n");
			return;
		}

		byte[] f = null;
		int len = FS.LoadFile(Cmd.Argv(1), f);
		if (f == null) {
			Com.print("couldn't exec " + Cmd.Argv(1) + "\n");
			return;
		}
		Com.print("execing " + Cmd.Argv(1) + "\n");

		// the file doesn't have a trailing 0, so we need to copy it off
		//00391         f2 = Z_Malloc(len+1);
		//00392         memcpy (f2, f, len);
		//00393         f2[len] = 0;
		//00394 
		//00395         Cbuf_InsertText (f2);
		//00396 
		//00397         Z_Free (f2);
		//00398         FS_FreeFile (f);
		//00399 }
	}

}
