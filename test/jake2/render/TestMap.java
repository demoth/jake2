/*
 * TestMap.java
 * Copyright (C) 2003
 *
 * $Id: TestMap.java,v 1.13 2004-02-02 17:52:36 rst Exp $
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
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.sys.IN;
import jake2.sys.KBD;
import jake2.util.*;

import java.awt.Dimension;
import java.util.*;

/**
 * TestMap
 *  
 * @author cwei
 */
public class TestMap
{

	static final float INSTANT_PARTICLE = -10000.0f;
	static final float PARTICLE_GRAVITY = 40.0f;

	String[] args;

	refexport_t re;
	refimport_t ri;
	viddef_t viddef;
	int framecount = 0;

	public TestMap(String[] args)
	{
		this.args = args;
	}

	public static void main(String[] args)
	{

		TestMap test = new TestMap(args);
		test.init();
		test.run();
	}

	void init()
	{

		// only for testing
		// a simple refimport_t implementation
		ri = new refimport_t()
		{
			public void Sys_Error(int err_level, String str)
			{
				VID.Error(err_level, str, null);
			}

			public void Sys_Error(int err_level, String str, Vargs vargs)
			{
				VID.Error(err_level, str, vargs);
			}

			public void Cmd_AddCommand(String name, xcommand_t cmd)
			{
				Cmd.AddCommand(name, cmd);
			}

			public void Cmd_RemoveCommand(String name)
			{
				Cmd.RemoveCommand(name);
			}

			public int Cmd_Argc()
			{
				return Cmd.Argc();
			}

			public String Cmd_Argv(int i)
			{
				return Cmd.Argv(i);
			}

			public void Cmd_ExecuteText(int exec_when, String text)
			{
				Cbuf.ExecuteText(exec_when, text);
			}

			public void Con_Printf(int print_level, String str)
			{
				VID.Printf(print_level, str, null);
			}

			public void Con_Printf(int print_level, String str, Vargs vargs)
			{
				VID.Printf(print_level, str, vargs);
			}

			public byte[] FS_LoadFile(String name)
			{
				return FS.LoadFile(name);
			}

			public int FS_FileLength(String name)
			{
				return FS.FileLength(name);
			}

			public void FS_FreeFile(byte[] buf)
			{
				FS.FreeFile(buf);
			}

			public String FS_Gamedir()
			{
				return FS.Gamedir();
			}

			public cvar_t Cvar_Get(String name, String value, int flags)
			{
				return Cvar.Get(name, value, flags);
			}

			public cvar_t Cvar_Set(String name, String value)
			{
				return Cvar.Set(name, value);
			}

			public void Cvar_SetValue(String name, float value)
			{
				Cvar.SetValue(name, value);
			}

			public boolean Vid_GetModeInfo(Dimension dim, int mode)
			{
				return VID.GetModeInfo(dim, mode);
			}

			public void Vid_MenuInit()
			{
				VID.MenuInit();
			}

			public void Vid_NewWindow(int width, int height)
			{
				VID.NewWindow(width, height);
			}

			public void updateScreenCallback()
			{
				TestMap.this.updateScreen();
			}
		};

		Qcommon.InitForTestMap(new String[] { "TestMap $Id: TestMap.java,v 1.13 2004-02-02 17:52:36 rst Exp $" });
		// sehr wichtig !!!
		VID.Shutdown();

		this.re = Renderer.getDriver("jogl", ri);

		re.Init();
		
		// init keyboard
		Cmd.AddCommand("+tforward", forward_down);
		Cmd.AddCommand("-tforward", forward_up);
		Cbuf.AddText("bind UPARROW +tforward");
		Cbuf.Execute();
		Cmd.AddCommand("+tbackward", backward_down);
		Cmd.AddCommand("-tbackward", backward_up);
		Cbuf.AddText("bind DOWNARROW +tbackward");
		Cbuf.Execute();
		Cmd.AddCommand("+tleft", left_down);
		Cmd.AddCommand("-tleft", left_up);
		Cbuf.AddText("bind LEFTARROW +tleft");
		Cbuf.Execute();
		Cmd.AddCommand("+tright", right_down);
		Cmd.AddCommand("-tright", right_up);
		Cbuf.AddText("bind RIGHTARROW +tright");
		Cbuf.Execute();
		Cmd.AddCommand("togglemouse", togglemouse);
		Cbuf.AddText("bind t togglemouse");
		Cbuf.Execute();
		Globals.cls.key_dest = Defines.key_game;
		Globals.cls.state = Defines.ca_active;

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
			re.updateScreen();
			KBD.Update();
			Cbuf.Execute();
		}
	}

	int currentState = 0;

	void updateScreen()
	{
		re.BeginFrame(0.0f);
		
		switch (currentState)
		{
			case 0 :
				re.DrawStretchPic(0, 0, viddef.width, viddef.height, "conback");
				re.DrawPic(viddef.width / 2 - 50, viddef.height / 2, "loading");
				currentState = 1;
				break;
			case 1 :
				// register the map
				re.SetSky("space1", 0, new float[]{ 0, 0, 0 });
				re.BeginRegistration("base1");
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

				drawString(10, viddef.height - 16, text);
		}
		
		re.EndFrame();
		framecount++;
	}

	//		===================================================================


	static final int FORWARD = 2;
	static final int FORWARD_MASK = ~FORWARD;
	static final int BACKWARD = 4;
	static final int BACKWARD_MASK = ~BACKWARD;
	static final int LEFT = 8;
	static final int LEFT_MASK = ~LEFT;
	static final int RIGHT = 16;
	static final int RIGHT_MASK = ~RIGHT;
	

	int movePlayer = 0;

	// forward
	xcommand_t forward_down = new xcommand_t() {
		public void execute() {
			movePlayer |=  FORWARD;
			movePlayer &= BACKWARD_MASK;
		}
	};
	xcommand_t forward_up = new xcommand_t() {
		public void execute() {
			movePlayer &=  FORWARD_MASK;
		}
	};
	// backward
	xcommand_t backward_down = new xcommand_t() {
		public void execute() {
			movePlayer |=  BACKWARD; 
			movePlayer &= FORWARD_MASK;
		}
	};
	xcommand_t backward_up = new xcommand_t() {
		public void execute() {
			movePlayer &=  BACKWARD_MASK;
		}
	};
	// left
	xcommand_t left_down = new xcommand_t() {
		public void execute() {
			movePlayer |=  LEFT;
			movePlayer &= RIGHT_MASK;
		}
	};
	xcommand_t left_up = new xcommand_t() {
		public void execute() {
			movePlayer &=  LEFT_MASK;
		}
	};
	// right
	xcommand_t right_down = new xcommand_t() {
		public void execute() {
			movePlayer |=  RIGHT;
			movePlayer &=  LEFT_MASK;
		}
	};
	xcommand_t right_up = new xcommand_t() {
		public void execute() {
			movePlayer &=  RIGHT_MASK;
		}
	};

	private float yaw = 0;

	private float fov_x = 90;
	
	private refdef_t refdef;
	
	private entity_t ent;
	
	float[] vpn = {0, 0, 0};
	float[] vright = {0, 0, 0};
	float[] vup = {0, 0, 0};
	
	private void testMap()
	{

		if ( refdef == null ) {
			refdef = new refdef_t();

			refdef.x = 0;
			refdef.y = 0;
			refdef.width = viddef.width;
			refdef.height = viddef.height;
			refdef.fov_x = (Globals.fov == null) ? this.fov_x : Globals.fov.value;
			refdef.fov_x = this.fov_x;
			refdef.fov_y = Math3D.CalcFov(refdef.fov_x, refdef.width, refdef.height);
			refdef.vieworg = new float[] {140, -140, 50};
			refdef.viewangles = new float[] {0, 0, 0};

			refdef.blend =  new float[] { 0.0f, 0.0f, 0.0f, 0.0f };

			refdef.areabits = null; // draw all
//			refdef.areabits = new byte[Defines.MAX_MAP_AREAS / 8];
//			Arrays.fill(refdef.areabits, (byte) 0xFF);

			// load a monster
			ent = new entity_t();
			
			model_t weapon = re.RegisterModel("models/monsters/soldier/tris.md2");
			image_t weaponSkin = re.RegisterSkin("models/monsters/soldier/skin.pcx");

			ent.model = weapon;
			ent.skin = weaponSkin;
			ent.origin = new float[] { -60, 80, 25 };
			Math3D.VectorCopy(ent.origin, ent.oldorigin);
			ent.angles = new float[] { 0, 300, 0 };

			refdef.num_entities = 1;
			refdef.entities = new entity_t[] {ent};
			
			lightstyle_t light = new lightstyle_t();
			light.rgb = new float[] {1.0f, 1.0f, 1.0f};
			light.white = 3.0f;

			refdef.lightstyles = new lightstyle_t[Defines.MAX_LIGHTSTYLES];
			for (int i = 0; i < Defines.MAX_LIGHTSTYLES; i++) {
				refdef.lightstyles[i] = new lightstyle_t();
				refdef.lightstyles[i].rgb = new float[] {1.0f, 1.0f, 1.0f};
				refdef.lightstyles[i].white = 3.0f; // r + g + b
			}

			refdef.viewangles[1] = 130;
			// set the start time
			refdef.time = time() * 0.001f;
		}

		refdef.viewangles[0] += KBD.my * 0.1f;
		refdef.viewangles[1] -= KBD.mx * 0.1f; // 90 + 180 * (float)Math.sin(time() * 0.0001f);
		
		float dt = time() * 0.001f - refdef.time;

		if (movePlayer != 0) {
			
			float velocity = 150f * dt;
			Math3D.AngleVectors(refdef.viewangles, vpn, vright, vup);

			// forward		
			if ((movePlayer & FORWARD_MASK) != 0)
				Math3D.VectorMA(refdef.vieworg, -velocity, vpn, refdef.vieworg);
			// backward
			if ((movePlayer & BACKWARD_MASK) != 0)
				Math3D.VectorMA(refdef.vieworg, velocity, vpn, refdef.vieworg);
			// left
			if ((movePlayer & LEFT_MASK) != 0)
				Math3D.VectorMA(refdef.vieworg, velocity, vright, refdef.vieworg);
			// right
			if ((movePlayer & RIGHT_MASK) != 0)
				Math3D.VectorMA(refdef.vieworg, -velocity, vright, refdef.vieworg);
			
			// wichtig da aufloesung 1/8
			// --> ebenen schneiden nie genau die sicht
			refdef.vieworg[0] += 1.0f / 16;
			refdef.vieworg[1] += 1.0f / 16;
			refdef.vieworg[2] += 1.0f / 16;
		}
		
		refdef.time = time() * 0.001f;

		// particle init
		particles.clear();
		
		// check the enemy distance
		float[] diff = {0, 0, 0};
		Math3D.VectorSubtract( refdef.vieworg, ent.origin, diff);

		if (Math3D.VectorLength(diff) < 250 && active_particles.size() == 0) {
			RailTrail(ent.origin, refdef.vieworg);
		} else {
			// monster and partice animation
			if (active_particles.size() > 0) {
				// monster
				ent.frame = (int)((time() * 0.013f) % 15);
				// monster look at you :-)
				Math3D.VectorNormalize(diff);
				Math3D.vectoangles(diff, ent.angles);
				
				// particles
				animateParticles();

				particle_t[] tmp = new particle_t[particles.size()];
				particles.toArray(tmp);

				refdef.particles = tmp;
				refdef.num_particles = tmp.length;
			}
			else {
				ent.frame = 0;
				refdef.num_particles = 0;
			}
		}
		
		re.RenderFrame(refdef);
	}
	
	private Vector particles = new Vector(1024); // = new particle_t[20];
	private LinkedList active_particles = new LinkedList();
	private boolean explode = false; 
	private float[] target; 
	
	private boolean initParticles = true;
	
	private void animateParticles()
	{
		cparticle_t p;
		float alpha;
		float	time, time2;
		float[] org = {0, 0, 0};
		int color;
		particle_t particle;
		
		time = 0.0f;

		for (Iterator it = active_particles.iterator(); it.hasNext();)
		{
			p = (cparticle_t) it.next();

			// PMM - added INSTANT_PARTICLE handling for heat beam
			if (p.alphavel != INSTANT_PARTICLE)
			{
				time = (time() - p.time) * 0.001f;
				alpha = p.alpha + time*p.alphavel;
				if (alpha <= 0)
				{	// faded out
					it.remove();
					continue;
				}
			}
			else
			{
				alpha = p.alpha;
			}

			if (alpha > 1.0)
				alpha = 1;
			color = (int)p.color;

			time2 = time*time;

			org[0] = p.org[0] + p.vel[0]*time + p.accel[0]*time2;
			org[1] = p.org[1] + p.vel[1]*time + p.accel[1]*time2;
			org[2] = p.org[2] + p.vel[2]*time + p.accel[2]*time2;

			particle = new particle_t();
			particle.alpha = alpha;
			Math3D.VectorCopy(org, particle.origin);
			particle.color = color;
			
			particles.add(particle);
			 
			// PMM
			if (p.alphavel == INSTANT_PARTICLE)
			{
				p.alphavel = 0.0f;
				p.alpha = 0.0f;
			}
		}
	}
	
	private void RailTrail(float[] start, float[] end)
	{
		float[] move = {0, 0, 0};
		float[] vec = {0, 0, 0};
		float	len;
		int j;
		cparticle_t	p;
		float	dec;
		float[] right = {0, 0, 0};
		float[] up = {0, 0, 0};
		int i;
		float	d, c, s;
		float[] dir = {0, 0, 0};

		Math3D.VectorCopy (start, move);
		Math3D.VectorSubtract (end, start, vec);
		len = Math3D.VectorNormalize(vec);

		Math3D.MakeNormalVectors(vec, right, up);

		for (i=0 ; i<len ; i++)
		{

			p = new cparticle_t();		
			p.time = time();
			Math3D.VectorClear (p.accel);

			d = i * 0.1f;
			c = (float)Math.cos(d);
			s = (float)Math.sin(d);

			Math3D.VectorScale (right, c, dir);
			Math3D.VectorMA (dir, s, up, dir);

			p.alpha = 1.0f;
			p.alphavel = -1.0f / (1 + Lib.frand() * 0.2f);
			p.color = 0x74 + (Lib.rand() & 7);
			for (j=0 ; j<3 ; j++)
			{
				p.org[j] = move[j] + dir[j]*3;
				p.vel[j] = dir[j]*6;
			}

			Math3D.VectorAdd (move, vec, move);

			active_particles.add(p);	
		}

		dec = 0.75f;
		Math3D.VectorScale (vec, dec, vec);
		Math3D.VectorCopy (start, move);

		while (len > 0)
		{
			len -= dec;

			p = new cparticle_t();

			p.time = time();
			Math3D.VectorClear (p.accel);

			p.alpha = 1.0f;
			p.alphavel = -1.0f / (0.6f + Lib.frand() * 0.2f);
			p.color = 0x0 + Lib.rand()&15;

			for (j=0 ; j<3 ; j++)
			{
				p.org[j] = move[j] + Lib.crand()*3;
				p.vel[j] = Lib.crand()*3;
				p.accel[j] = 0;
			}

			Math3D.VectorAdd (move, vec, move);
			active_particles.add(p);	
		}
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
	
	static xcommand_t togglemouse = new xcommand_t() {
		public void execute() {
			IN.toggleMouse();
		}
	};
}
