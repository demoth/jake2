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

package jake2.qcommon.exec;


import jake2.qcommon.Info;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static jake2.qcommon.Defines.CVAR_USERINFO;
import static org.junit.Assert.assertTrue;

public class TestCvar {

	@Test
	public void testInit() {
		Cvar.Init();
	}

	@Before
	public void setUp() {
		Cvar.getInstance().clear();
	}

	@Test
	public void testGet() {
		Cvar.getInstance().Set("rene", "is cool.");

		Assert.assertEquals("is cool.", Cvar.getInstance().Get("rene", "default", 0).string);
	}

	@Test
	public void testGetDefault() {
		Cvar.getInstance().Set("rene1", "is cool.");

		Assert.assertEquals("default", Cvar.getInstance().Get("hello", "default", 0).string);
	}

	@Test
	public void testGetDefaultNull() {
		Cvar.getInstance().Set("rene2", "is cool.");

		Assert.assertNull(Cvar.getInstance().Get("hello2", null, 0));
	}

	@Test
	public void testFind() {
		Cvar.getInstance().Set("rene3", "is cool.");

		Assert.assertEquals("is cool.", Cvar.getInstance().FindVar("rene3").string);
	}

	@Test
	public void testVariableString() {
		Cvar.getInstance().Set("rene4", "is cool.");
		Assert.assertEquals("is cool.", Cvar.getInstance().VariableString("rene4"));
	}

	@Test
	public void testFullSetCreateNew() {
		Cvar.getInstance().FullSet("rene5", "0.56", 0);

		cvar_t rene5 = Cvar.getInstance().FindVar("rene5");
		Assert.assertNotNull(rene5);
		Assert.assertEquals("0.56", rene5.string);
		assertTrue(rene5.value > 0.5f);
	}

	@Test
	public void testFullSetOverwrite() {
		Cvar.getInstance().FullSet("rene6", "0.56", 0);
		Cvar.getInstance().FullSet("rene6", "10.6", 0);

		cvar_t rene6 = Cvar.getInstance().FindVar("rene6");
		Assert.assertNotNull(rene6);
		assertTrue(rene6.modified);
		Assert.assertEquals("10.6", rene6.string);
		assertTrue(rene6.value > 0.5f);

	}

	@Test
	public void testUserInfo() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("name", "unnamed", CVAR_USERINFO );
		cvar.Get("skin", "male/grunt", CVAR_USERINFO);
		cvar.Get("rate", "25000", CVAR_USERINFO);
		cvar.Get("msg", "1", CVAR_USERINFO );
		cvar.Get("hand", "0", CVAR_USERINFO );
		cvar.Get("fov", "90", CVAR_USERINFO );
		cvar.Get("gender", "male", CVAR_USERINFO );

		cvar.Get("wrongcharacter1", "value;", CVAR_USERINFO);
		cvar.Get("wrongcharacter2", "value\"", CVAR_USERINFO);
		cvar.Get("wrongcharacter3", "value\\", CVAR_USERINFO);
		cvar.Get("wrongcharacter4;", "value1", CVAR_USERINFO);
		cvar.Get("wrongcharacter5\"", "value2", CVAR_USERINFO);
		cvar.Get("wrongcharacter6\\", "value3", CVAR_USERINFO);
		cvar.Get("non_userinfo", "asdf", 0);

		Info.Print(cvar.Userinfo());

		Assert.assertEquals("\\msg\\1\\gender\\male\\rate\\25000\\name\\unnamed\\skin\\male/grunt\\fov\\90\\hand\\0", cvar.Userinfo());
	}

	@Test
	public void testEmptyValue() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("name", "asdf", CVAR_USERINFO );
		cvar.Get("password", "", CVAR_USERINFO );

		String userinfo = cvar.Userinfo();
		String empty = Info.Info_ValueForKey(userinfo, "password");
		assertTrue(empty.isEmpty());
	}
}
