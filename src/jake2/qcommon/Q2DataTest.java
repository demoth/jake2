/*
 * Q2DataTest.java
 * Copyright Bytonic Software (C) 2004
 *
 * $Id: Q2DataTest.java,v 1.2 2004-09-19 19:53:51 hzi Exp $
 */
package jake2.qcommon;

import jake2.Jake2;

/**
 * Q2DataTest
 */
public class Q2DataTest {

	static void run() {
		while (FS.LoadFile("pics/colormap.pcx") == null) {
			Jake2.Q2Dialog.showChooseDialog();
			
			try {
				synchronized(Jake2.Q2Dialog) {
					Jake2.Q2Dialog.wait();
				}
			} catch (InterruptedException e) {}
		}
		Jake2.Q2Dialog.showStatus();
	}
}
