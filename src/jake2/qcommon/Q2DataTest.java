/*
 * Q2DataTest.java
 * Copyright Bytonic Software (C) 2004
 *
 * $Id: Q2DataTest.java,v 1.1 2004-09-18 12:36:53 hzi Exp $
 */
package jake2.qcommon;

/**
 * Q2DataTest
 */
public class Q2DataTest {

	static void run() {
		while (FS.LoadFile("pics/colormap.pcx") == null) {
			Q2DataDialog d = new Q2DataDialog(null, true);
			d.setVisible(true);
			try {
				d.wait();
			} catch (Exception e) {}
		}
	}
}
