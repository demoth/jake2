/*
 * CL_pred.java
 * Copyright (C) 2004
 * 
 * $Id: CL_pred.java,v 1.13 2004-02-16 20:57:38 hoz Exp $
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
package jake2.client;

import jake2.game.*;
import jake2.game.entity_state_t;
import jake2.game.trace_t;
import jake2.qcommon.*;
import jake2.qcommon.CM;
import jake2.qcommon.Com;
import jake2.server.SV_GAME;

/**
 * CL_pred
 */
public class CL_pred extends CL_parse
{

	/*
	===================
	CL_CheckPredictionError
	===================
	*/
	static void CheckPredictionError()
	{
		int frame;
		int[] delta = new int[3];
		int i;
		int len;

		if (cl_predict.value == 0.0f
			|| (cl.frame.playerstate.pmove.pm_flags & PMF_NO_PREDICTION) != 0)
			return;

		// calculate the last usercmd_t we sent that the server has processed
		frame = cls.netchan.incoming_acknowledged;
		frame &= (CMD_BACKUP - 1);

		// compare what the server returned with what we had predicted it to be
		VectorSubtract(
			cl.frame.playerstate.pmove.origin,
			cl.predicted_origins[frame],
			delta);

		// save the prediction error for interpolation
		len = Math.abs(delta[0]) + Math.abs(delta[1]) + Math.abs(delta[2]);
		if (len > 640) // 80 world units
		{ // a teleport or something
			VectorClear(cl.prediction_error);
		}
		else
		{
			if (cl_showmiss.value != 0.0f
				&& (delta[0] != 0 || delta[1] != 0 || delta[2] != 0))
				Com.Printf(
					"prediction miss on "
						+ cl.frame.serverframe
						+ ": "
						+ (delta[0] + delta[1] + delta[2])
						+ "\n");

			VectorCopy(
				cl.frame.playerstate.pmove.origin,
				cl.predicted_origins[frame]);

			// save for error itnerpolation
			for (i = 0; i < 3; i++)
				cl.prediction_error[i] = delta[i] * 0.125f;
		}
	}

	/*
	====================
	CL_ClipMoveToEntities
	
	====================
	*/
	static void ClipMoveToEntities(
		float[] start,
		float[] mins,
		float[] maxs,
		float[] end,
		trace_t tr)
	{
		int i, x, zd, zu;
		trace_t trace;
		int headnode;
		float[] angles;
		entity_state_t ent;
		int num;
		cmodel_t cmodel;
		float[] bmins = new float[3];
		float[] bmaxs = new float[3];

		for (i = 0; i < cl.frame.num_entities; i++)
		{
			num = (cl.frame.parse_entities + i) & (MAX_PARSE_ENTITIES - 1);
			ent = cl_parse_entities[num];

			if (ent.solid == 0)
				continue;

			if (ent.number == cl.playernum + 1)
				continue;

			if (ent.solid == 31)
			{ // special value for bmodel
				cmodel = cl.model_clip[ent.modelindex];
				if (cmodel == null)
					continue;
				headnode = cmodel.headnode;
				angles = ent.angles;
			}
			else
			{ // encoded bbox
				x = 8 * (ent.solid & 31);
				zd = 8 * ((ent.solid >>> 5) & 31);
				zu = 8 * ((ent.solid >>> 10) & 63) - 32;

				bmins[0] = bmins[1] = -x;
				bmaxs[0] = bmaxs[1] = x;
				bmins[2] = -zd;
				bmaxs[2] = zu;

				headnode = CM.HeadnodeForBox(bmins, bmaxs);
				angles = vec3_origin; // boxes don't rotate
			}

			if (tr.allsolid)
				return;

			trace =
				CM.TransformedBoxTrace(
					start,
					end,
					mins,
					maxs,
					headnode,
					MASK_PLAYERSOLID,
					ent.origin,
					angles);

			if (trace.allsolid
				|| trace.startsolid
				|| trace.fraction < tr.fraction)
			{
				// TODO bugfix cwei
				//if (trace.ent == null) trace.ent = new edict_t(0);
				trace.ent = ent.surrounding_ent;
				if (tr.startsolid)
				{
					tr = trace;
					tr.startsolid = true;
				}
				else
					tr = trace;
			}
			else if (trace.startsolid)
				tr.startsolid = true;
		}
	}

	/*
	================
	CL_PMTrace
	================
	*/
	
	static edict_t DUMMY_ENT = new edict_t(-1);
	
	static trace_t PMTrace(float[] start, float[] mins, float[] maxs, float[] end) {
		trace_t t;

		// check against world
		t = CM.BoxTrace(start, end, mins, maxs, 0, MASK_PLAYERSOLID);

		if (t.fraction < 1.0f) {
			t.ent = DUMMY_ENT;
		}

		// check all other solid models
		CL.ClipMoveToEntities(start, mins, maxs, end, t);

		return t;
	}

	static int PMpointcontents(float[] point)
	{
		int i;
		entity_state_t ent;
		int num;
		cmodel_t cmodel;
		int contents;

		contents = CM.PointContents(point, 0);

		for (i = 0; i < cl.frame.num_entities; i++)
		{
			num = (cl.frame.parse_entities + i) & (MAX_PARSE_ENTITIES - 1);
			ent = cl_parse_entities[num];

			if (ent.solid != 31) // special value for bmodel
				continue;

			cmodel = cl.model_clip[ent.modelindex];
			if (cmodel == null)
				continue;

			contents
				|= CM.TransformedPointContents(
					point,
					cmodel.headnode,
					ent.origin,
					ent.angles);
		}

		return contents;
	}

	/*
	=================
	CL_PredictMovement
	
	Sets cl.predicted_origin and cl.predicted_angles
	=================
	*/
	static void PredictMovement()
	{
		int ack, current;
		int frame;
		int oldframe;
		usercmd_t cmd;
		pmove_t pm;
		int i;
		int step;
		int oldz;

		if (cls.state != ca_active)
			return;

		if (cl_paused.value != 0.0f)
			return;

		if (cl_predict.value == 0.0f
			|| (cl.frame.playerstate.pmove.pm_flags & PMF_NO_PREDICTION) != 0)
		{ // just set angles
			for (i = 0; i < 3; i++)
			{
				cl.predicted_angles[i] =
					cl.viewangles[i]
						+ SHORT2ANGLE(cl.frame.playerstate.pmove.delta_angles[i]);
			}
			return;
		}

		ack = cls.netchan.incoming_acknowledged;
		current = cls.netchan.outgoing_sequence;

		// if we are too far out of date, just freeze
		if (current - ack >= CMD_BACKUP)
		{
			if (cl_showmiss.value != 0.0f)
				Com.Printf("exceeded CMD_BACKUP\n");
			return;
		}

		// copy current state to pmove
		//memset (pm, 0, sizeof(pm));
		pm = new pmove_t();

		pm.trace = new pmove_t.TraceAdapter()
		{
			public trace_t trace(
				float[] start,
				float[] mins,
				float[] maxs,
				float[] end)
			{
				return CL.PMTrace(start, mins, maxs, end);
			}
		};
		pm.pointcontents = new pmove_t.PointContentsAdapter()
		{
			public int pointcontents(float[] point)
			{
				return CL.PMpointcontents(point);
			}
		};

		PMove.pm_airaccelerate = atof(cl.configstrings[CS_AIRACCEL]);

		// bugfix (rst) yeah !!!!!!!!  found the B E W E G U N G S P R O B L E M.  
		pm.s.set(cl.frame.playerstate.pmove);

		// SCR_DebugGraph (current - ack - 1, 0);
		frame = 0;

		// run frames
		while (++ack < current)
		{
			frame = ack & (CMD_BACKUP - 1);
			cmd = cl.cmds[frame];
			
			pm.cmd.set(cmd);
			
			PMove.Pmove(pm);

			// save for debug checking
			VectorCopy(pm.s.origin, cl.predicted_origins[frame]);
		}

		oldframe = (ack - 2) & (CMD_BACKUP - 1);
		oldz = cl.predicted_origins[oldframe][2];
		step = pm.s.origin[2] - oldz;
		if (step > 63 && step < 160 && (pm.s.pm_flags & PMF_ON_GROUND) != 0)
		{
			cl.predicted_step = step * 0.125f;
			cl.predicted_step_time = (int) (cls.realtime - cls.frametime * 500);
		}

		// copy results out for rendering
		cl.predicted_origin[0] = pm.s.origin[0] * 0.125f;
		cl.predicted_origin[1] = pm.s.origin[1] * 0.125f;
		cl.predicted_origin[2] = pm.s.origin[2] * 0.125f;

		VectorCopy(pm.viewangles, cl.predicted_angles);
	}

}
