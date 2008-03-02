/*
 * DebugCulling.java
 * Copyright (C) 2003
 */
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
package jake2.render;

import jake2.Defines;
import jake2.Globals;
import jake2.client.*;
import jake2.qcommon.Com;
import jake2.qcommon.Qcommon;
import jake2.sys.KBD;

/**
 * DebugCulling
 *  
 * @author cwei
 */
public class DebugCulling
{

	static final float INSTANT_PARTICLE = -10000.0f;
	static final float PARTICLE_GRAVITY = 40.0f;

	String[] args;

	refexport_t re;
	viddef_t viddef;
	int framecount = 0;

	public DebugCulling(String[] args)
	{
		this.args = args;
	}

	public static void main(String[] args)
	{

		DebugCulling test = new DebugCulling(args);
		test.init();
		test.run();
	}

	void init()
	{

		Qcommon.Init(new String[] { "$Id: DebugCulling.java,v 1.6 2008-03-02 14:56:21 cawe Exp $" });
		// sehr wichtig !!!
		VID.Shutdown();

		this.re = Renderer.getDriver("jogl");

		re.Init(0, 0);

		viddef = Globals.viddef;
	}

	float fps = 0.0f;
	long start = 0;
	long startTime;

	void run()
	{
		startTime = System.currentTimeMillis();
		while (true)
		{
			re.updateScreen(null);
			re.getKeyboardHandler().Update();
//			try {
//				Thread.sleep(5);
//			}
//			catch (InterruptedException e) {
//			}
		}
	}

	int currentState = 0;

	void updateScreen()
	{
		re.BeginFrame(0.0f);
		
		switch (currentState)
		{
			case 0 :
				re.DrawStretchPic(0, 0, viddef.getWidth(), viddef.getHeight(), "conback");
				re.DrawPic(viddef.getWidth() / 2 - 50, viddef.getHeight() / 2, "loading");
				currentState = 1;
				break;
			case 1 :
				// register the map
				re.SetSky("space1", 0, new float[]{ 0, 0, 0 });
				re.BeginRegistration("ColorTest");
				re.EndRegistration();
				currentState = 2;
				//break;
			default :
				if (framecount % 500 == 0)
				{
					long time = System.currentTimeMillis();
					fps = 500000.0f / (time - start);
					start = time;
				}
				String text = fps + " fps";

				testMap();

				drawString(10, viddef.getHeight() - 16, text);
		}
		
		re.EndFrame();
		framecount++;
	}

	//		===================================================================

	private float fov_x = 90;
	
	private refdef_t refdef;
	
	private void testMap()
	{

		if ( refdef == null ) {
			refdef = new refdef_t();

			refdef.x = 0;
			refdef.y = 0;
			refdef.width = viddef.getWidth();
			refdef.height = viddef.getHeight();
			refdef.fov_x = fov_x;
			refdef.fov_y = CalcFov(fov_x, refdef.width -10, refdef.height-10);
			refdef.vieworg = new float[] {0, 0, 0};

			refdef.viewangles[0] = 0;
			refdef.viewangles[1] = 90;
			refdef.viewangles[2] = 0;
			
			refdef.blend =  new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

			refdef.areabits = null; // draw all
//			refdef.areabits = new byte[Defines.MAX_MAP_AREAS / 8];
//			Arrays.fill(refdef.areabits, (byte) 0xFF);


			refdef.num_entities = 0;
			refdef.entities = null;
			
			lightstyle_t light = new lightstyle_t();
			light.rgb = new float[] {1.0f, 1.0f, 1.0f};
			light.white = 3.0f;

			refdef.lightstyles = new lightstyle_t[Defines.MAX_LIGHTSTYLES];
			for (int i = 0; i < Defines.MAX_LIGHTSTYLES; i++)
			{
				refdef.lightstyles[i] = new lightstyle_t();
				refdef.lightstyles[i].rgb = new float[] { 1.0f, 1.0f, 1.0f };
				refdef.lightstyles[i].white = 3.0f; // r + g + b
			}

		}

		refdef.time = time() * 0.001f;

		refdef.viewangles[0] += KBD.my * 0.1f;
		refdef.viewangles[1] -= KBD.mx * 0.1f;

		refdef.vieworg[0] = 0; // + 30 * (float)Math.sin(time() * 0.0005f);
		refdef.vieworg[1] = -79;
		refdef.vieworg[2] = -131;
		
		// wichtig da aufloesung 1/8
		// --> ebenen schneiden nie genau die sicht
		refdef.vieworg[0] += 1.0f / 16;
		refdef.vieworg[1] += 1.0f / 16;
		refdef.vieworg[2] += 1.0f / 16;
		
		re.RenderFrame(refdef);
	}
	
	private float CalcFov(float fov_x, float width, float height)
	{
		double a;
		double x;

		if (fov_x < 1 || fov_x > 179)
			Com.Error(Defines.ERR_DROP, "Bad fov: " + fov_x);

		x = width / Math.tan(fov_x / 360 * Math.PI);

		a = Math.atan(height / x);

		a = a * 360 / Math.PI;

		return (float) a;
	}

	private void drawString(int x, int y, String text)
	{
		for (int i = 0; i < text.length(); i++)
		{
			re.DrawChar(x + 8 * i, y, (int) text.charAt(i));
		}
	}

	private final int time()
	{
		return (int) (System.currentTimeMillis() - startTime);
	}
	
}