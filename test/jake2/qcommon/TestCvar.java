/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 29.12.2003 by RST.
// $Id: TestCvar.java,v 1.1 2004-07-07 19:59:56 hzi Exp $

package jake2.qcommon;


import jake2.game.cvar_t;
import org.junit.Assert;
import org.junit.Test;

public class TestCvar {

	@Test
	public void testInit() {
		Cvar.Init();
	}

	@Test
	public void testGet() {
		Cvar.Set("rene", "is cool.");

		Assert.assertEquals("is cool.", Cvar.Get("rene", "default", 0).string);
	}

	@Test
	public void testGetDefault() {
		Cvar.Set("rene1", "is cool.");

		Assert.assertEquals("default", Cvar.Get("hello", "default", 0).string);
	}

	@Test
	public void testGetDefaultNull() {
		Cvar.Set("rene2", "is cool.");

		Assert.assertNull(Cvar.Get("hello2", null, 0));
	}

	@Test
	public void testFind() {
		Cvar.Set("rene3", "is cool.");

		Assert.assertEquals("is cool.", Cvar.FindVar("rene3").string);
	}

	@Test
	public void testVariableString() {
		Cvar.Set("rene4", "is cool.");
		Assert.assertEquals("is cool.", Cvar.VariableString("rene4"));
	}

	@Test
	public void testFullSetCreateNew() {
		Cvar.FullSet("rene5", "0.56", 0);

		cvar_t rene5 = Cvar.FindVar("rene5");
		Assert.assertNotNull(rene5);
		Assert.assertEquals("0.56", rene5.string);
		Assert.assertTrue(rene5.value > 0.5f);
	}

	@Test
	public void testFullSetOverwrite() {
		Cvar.FullSet("rene6", "0.56", 0);
		Cvar.FullSet("rene6", "10.6", 0);

		cvar_t rene6 = Cvar.FindVar("rene6");
		Assert.assertNotNull(rene6);
		Assert.assertTrue(rene6.modified);
		Assert.assertEquals("10.6", rene6.string);
		Assert.assertTrue(rene6.value > 0.5f);

	}
}
