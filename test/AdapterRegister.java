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

// Created on 08.01.2004 by RST.
// $Id: AdapterRegister.java,v 1.1 2004-07-09 06:50:51 hzi Exp $
// $Log: AdapterRegister.java,v $
// Revision 1.1  2004-07-09 06:50:51  hzi
// import of Jake2
//
// Revision 1.1  2004/02/25 21:30:15  hoz
// *** empty log message ***
//
// Revision 1.2  2004/02/02 22:19:17  rst
// cosmetic
//
// Revision 1.1  2004/02/02 22:16:05  rst
// cosmetic
//
// Revision 1.2  2004/01/09 18:30:57  rst
// Superadapter replaces function pointers in save games.
//
// Revision 1.1  2004/01/08 23:56:43  rst
// some preisfrage
// 

// import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

public class AdapterRegister {

	// concept for adapter indexing for function pointers


	// the counter
	static int id =0;
	
	static class t0
	{
		// the identificator
		public int myid = id++;
		
		public String test()
		{
			return ("t0, id = " + myid);
		}
		  
	};
	// any stupid adapter
	static class t1 extends t0
	{
		public String test()
		{
			return ("t1, id = " + myid);
		}
	};
	// second adapter
	static class t2 extends t0
	{
		public String test(int x)
		{
			return ("t2, id = " + myid);
		}
	}


	// an auto test client
	public static void main(String[] args) {
		
		// program starts
		System.out.println("hello world.");
		
		t1 t1 = new t1();
		t2 t2 = new t2();
		t2 t3 = new t2();
		System.out.println(t1.test());
		System.out.println(t2.test());
		System.out.println(t2.test(5));
		System.out.println(t3.test(5));
		
		System.out.println("good bye world.");
		// program ends
	}
}
