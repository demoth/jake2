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

// Created on 09.12.2003 by RST.
// $Id: Math3D.java,v 1.8 2004-01-23 18:09:08 cwei Exp $

package jake2.util;

import java.util.Arrays;

import jake2.*;
import jake2.client.*;
import jake2.game.GameBase;
import jake2.game.cplane_t;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;

public class Math3D extends Lib {
	
	public static void set(float v1[], float v2[])
	{
		for (int i=0; i < v1.length; i++)
			v1[i]=v2[i];
	}
		 

	public static void VectorSubtract(float[] a, float[] b, float[] c) {
		c[0] = a[0] - b[0];
		c[1] = a[1] - b[1];
		c[2] = a[2] - b[2];
	}
	public static void VectorAdd(float[] a, float[] b, float[] c) {
		c[0] = a[0] + b[0];
		c[1] = a[1] + b[1];
		c[2] = a[2] + b[2];
	}
	public static void VectorCopy(float[] a, float[] b) {
		b[0] = a[0];
		b[1] = a[1];
		b[2] = a[2];
	}
	public static void VectorClear(float[] a) {
		a[0] = a[1] = a[2] = 0;
	}
	public static int VectorCompare(float[] v1, float[] v2) {
		if (v1[0] != v2[0] || v1[1] != v2[1] || v1[2] != v2[2])
			return 0;

		return 1;
	}
	public static void VectorNegate(float[] a, float[] b) {
		b[0] = -a[0];
		b[1] = -a[1];
		b[2] = -a[2];
	}
	public static void VectorSet(float[] v, float x, float y, float z) {
		v[0] = (x);
		v[1] = (y);
		v[2] = (z);
	}
	public static void VectorMA(float[] veca, float scale, float[] vecb, float[] vecc) {
		vecc[0] = veca[0] + scale * vecb[0];
		vecc[1] = veca[1] + scale * vecb[1];
		vecc[2] = veca[2] + scale * vecb[2];
	}
	public static float VectorNormalize(float[] v) {
		float length;

		length = VectorLength(v);
		if (length != 0.0f) {

			v[0] /= length;
			v[1] /= length;
			v[2] /= length;
		}
		return length;
	}
	public static float VectorNormalize2(float[] v, float[] out) {
		float length, ilength;

		length = VectorLength(v);
		if (length != 0.0f) {
			out[0] = v[0] / length;
			out[1] = v[1] / length;
			out[2] = v[2] / length;
		}
		return length;
	}
	public static float VectorLength(float v[]) {
		return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
	}
	public static void VectorInverse(float[] v) {
		v[0] = -v[0];
		v[1] = -v[1];
		v[2] = -v[2];
	}
	public static void VectorScale(float[] in, float scale, float[] out) {
		out[0] = in[0] * scale;
		out[1] = in[1] * scale;
		out[2] = in[2] * scale;
	}

	public static float vectoyaw(float[] vec) {
		float yaw;

		if (/*vec[YAW] == 0 &&*/
			vec[Defines.PITCH] == 0) {
			yaw = 0;
			if (vec[Defines.YAW] > 0)
				yaw = 90;
			else if (vec[Defines.YAW] < 0)
				yaw = -90;
		}
		else {

			yaw = (int) (Math.atan2(vec[Defines.YAW], vec[Defines.PITCH]) * 180 / Math.PI);
			if (yaw < 0)
				yaw += 360;
		}

		return yaw;
	}

	public static void vectoangles(float[] value1, float[] angles) {
		float forward;
		float yaw, pitch;

		if (value1[1] == 0 && value1[0] == 0) {
			yaw = 0;
			if (value1[2] > 0)
				pitch = 90;
			else
				pitch = 270;
		}
		else {
			if (value1[0] != 0)
				yaw = (int) (Math.atan2(value1[1], value1[0]) * 180 / Math.PI);
			else if (value1[1] > 0)
				yaw = 90;
			else
				yaw = -90;
			if (yaw < 0)
				yaw += 360;

			forward = (float) Math.sqrt(value1[0] * value1[0] + value1[1] * value1[1]);
			pitch = (int) (Math.atan2(value1[2], forward) * 180 / Math.PI);
			if (pitch < 0)
				pitch += 360;
		}

		angles[Defines.PITCH] = -pitch;
		angles[Defines.YAW] = yaw;
		angles[Defines.ROLL] = 0;
	}

	public static void RotatePointAroundVector(float[] dst, float[] dir, float[] point, float degrees) {
		float m[][] = new float[3][3];
		float im[][] = new float[3][3];
		float zrot[][] = new float[3][3];
		float tmpmat[][] = new float[3][3];
		float rot[][] = new float[3][3];
		int i;
		float[] vr = { 0.0f, 0.0f, 0.0f };
		float[] vup = { 0.0f, 0.0f, 0.0f };
		float[] vf = { 0.0f, 0.0f, 0.0f };

		vf[0] = dir[0];
		vf[1] = dir[1];
		vf[2] = dir[2];

		Math3D.PerpendicularVector(vr, dir);
		Math3D.CrossProduct(vr, vf, vup);

		m[0][0] = vr[0];
		m[1][0] = vr[1];
		m[2][0] = vr[2];

		m[0][1] = vup[0];
		m[1][1] = vup[1];
		m[2][1] = vup[2];

		m[0][2] = vf[0];
		m[1][2] = vf[1];
		m[2][2] = vf[2];

		Math3D.MatCopy(m, im); // achtung: src -> dst

		im[0][1] = m[1][0];
		im[0][2] = m[2][0];
		im[1][0] = m[0][1];
		im[1][2] = m[2][1];
		im[2][0] = m[0][2];
		im[2][1] = m[1][2];

		Math3D.MatClear(zrot);

		zrot[0][0] = zrot[1][1] = zrot[2][2] = 1.0F;

		zrot[0][0] = (float) Math.cos(Math3D.DEG2RAD(degrees));
		zrot[0][1] = (float) Math.sin(Math3D.DEG2RAD(degrees));
		zrot[1][0] = - (float) Math.sin(Math3D.DEG2RAD(degrees));
		zrot[1][1] = (float) Math.cos(Math3D.DEG2RAD(degrees));

		Math3D.R_ConcatRotations(m, zrot, tmpmat);
		Math3D.R_ConcatRotations(tmpmat, im, rot);

		for (i = 0; i < 3; i++) {
			dst[i] = rot[i][0] * point[0] + rot[i][1] * point[1] + rot[i][2] * point[2];
		}
	}
	
	
	public static void MakeNormalVectors(float[] forward, float[] right, float[] up) {
		// this rotate and negat guarantees a vector
		// not colinear with the original
		right[1] = -forward[0];
		right[2] = forward[1];
		right[0] = forward[2];

		float d = DotProduct(right, forward);
		VectorMA(right, -d, forward, right);
		VectorNormalize(right);
		CrossProduct(right, forward, up);
	}
	

	public static float SHORT2ANGLE(int x) {
		return ((x) * (360.0f / 65536));
	}

	/*
	================
	R_ConcatTransforms
	================
	*/
	public static void R_ConcatTransforms(float in1[][], float in2[][], float out[][]) {
		out[0][0] = in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1] = in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2] = in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[0][3] = in1[0][0] * in2[0][3] + in1[0][1] * in2[1][3] + in1[0][2] * in2[2][3] + in1[0][3];
		out[1][0] = in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1] = in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2] = in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[1][3] = in1[1][0] * in2[0][3] + in1[1][1] * in2[1][3] + in1[1][2] * in2[2][3] + in1[1][3];
		out[2][0] = in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1] = in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2] = in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
		out[2][3] = in1[2][0] * in2[0][3] + in1[2][1] * in2[1][3] + in1[2][2] * in2[2][3] + in1[2][3];
	}

	/**
	 * concatenates 2 matrices each [3][3].
	 */
	public static void R_ConcatRotations(float in1[][], float in2[][], float out[][]) {
		out[0][0] = in1[0][0] * in2[0][0] + in1[0][1] * in2[1][0] + in1[0][2] * in2[2][0];
		out[0][1] = in1[0][0] * in2[0][1] + in1[0][1] * in2[1][1] + in1[0][2] * in2[2][1];
		out[0][2] = in1[0][0] * in2[0][2] + in1[0][1] * in2[1][2] + in1[0][2] * in2[2][2];
		out[1][0] = in1[1][0] * in2[0][0] + in1[1][1] * in2[1][0] + in1[1][2] * in2[2][0];
		out[1][1] = in1[1][0] * in2[0][1] + in1[1][1] * in2[1][1] + in1[1][2] * in2[2][1];
		out[1][2] = in1[1][0] * in2[0][2] + in1[1][1] * in2[1][2] + in1[1][2] * in2[2][2];
		out[2][0] = in1[2][0] * in2[0][0] + in1[2][1] * in2[1][0] + in1[2][2] * in2[2][0];
		out[2][1] = in1[2][0] * in2[0][1] + in1[2][1] * in2[1][1] + in1[2][2] * in2[2][1];
		out[2][2] = in1[2][0] * in2[0][2] + in1[2][1] * in2[1][2] + in1[2][2] * in2[2][2];
	}

	public static void ProjectPointOnPlane(float[] dst, float[] p, float[] normal) {
		float d;
		float[] n = { 0.0f, 0.0f, 0.0f };
		float inv_denom;

		inv_denom = 1.0F / Math3D.DotProduct(normal, normal);

		d = Math3D.DotProduct(normal, p) * inv_denom;

		n[0] = normal[0] * inv_denom;
		n[1] = normal[1] * inv_denom;
		n[2] = normal[2] * inv_denom;

		dst[0] = p[0] - d * n[0];
		dst[1] = p[1] - d * n[1];
		dst[2] = p[2] - d * n[2];
	}

	/** assumes "src" is normalized */
	public static void PerpendicularVector(float[] dst, float[] src) {
		int pos;
		int i;
		float minelem = 1.0F;
		float tempvec[] = { 0.0f, 0.0f, 0.0f };

		// find the smallest magnitude axially aligned vector 
		for (pos = 0, i = 0; i < 3; i++) {
			if (Math.abs(src[i]) < minelem) {
				pos = i;
				minelem = Math.abs(src[i]);
			}
		}
		tempvec[0] = tempvec[1] = tempvec[2] = 0.0F;
		tempvec[pos] = 1.0F;

		// project the point onto the plane defined by src
		ProjectPointOnPlane(dst, tempvec, src);

		//normalize the result 
		Math3D.VectorNormalize(dst);
	}

	//=====================================================================	
	/** 
	 stellt fest, auf welcher Seite sich die Kiste befindet, wenn die Ebene 
	 durch Entfernung und Senkrechten-Normale gegeben ist.    
	 erste Version mit vec3_t... */

	public static int BoxOnPlaneSide(float emins[], float emaxs[], cplane_t p) {
		
		assert (emins.length == 3 && emaxs.length == 3) : "vec3_t bug";
		
		float dist1, dist2;
		int sides;

		//	   fast axial cases
		if (p.type < 3) {
			if (p.dist <= emins[p.type])
				return 1;
			if (p.dist >= emaxs[p.type])
				return 2;
			return 3;
		}

		//	   general case
		switch (p.signbits) {
			case 0 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 1 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				break;
			case 2 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 3 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				break;
			case 4 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 5 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emaxs[2];
				break;
			case 6 :
				dist1 = p.normal[0] * emaxs[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emins[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			case 7 :
				dist1 = p.normal[0] * emins[0] + p.normal[1] * emins[1] + p.normal[2] * emins[2];
				dist2 = p.normal[0] * emaxs[0] + p.normal[1] * emaxs[1] + p.normal[2] * emaxs[2];
				break;
			default :
				//TODO: error message.
				dist1 = dist2 = 0;

				break;
		}

		sides = 0;
		if (dist1 >= p.dist)
			sides = 1;
		if (dist2 < p.dist)
			sides |= 2;

		GameBase.assert1(sides != 0);

		return sides;
	}

	//	this is the slow, general version
	public static int BoxOnPlaneSide2(float[] emins, float[] emaxs, cplane_t p) {
		int i;
		float dist1, dist2;
		int sides;
		float corners[][] = new float[2][3];

		for (i = 0; i < 3; i++) {
			if (p.normal[i] < 0) {
				corners[0][i] = emins[i];
				corners[1][i] = emaxs[i];
			}
			else {
				corners[1][i] = emins[i];
				corners[0][i] = emaxs[i];
			}
		}
		dist1 = Math3D.DotProduct(p.normal, corners[0]) - p.dist;
		dist2 = Math3D.DotProduct(p.normal, corners[1]) - p.dist;
		sides = 0;
		if (dist1 >= 0)
			sides = 1;
		if (dist2 < 0)
			sides |= 2;

		return sides;
	}

	public static void AngleVectors(float[] angles, float[] forward, float[] right, float[] up) {
		float angle;
		float sr, sp, sy, cr, cp, cy;

		angle = (float) (angles[Defines.YAW] * (Math.PI * 2 / 360));
		sy = (float) Math.sin(angle);
		cy = (float) Math.cos(angle);
		angle = (float) (angles[Defines.PITCH] * (Math.PI * 2 / 360));
		sp = (float) Math.sin(angle);
		cp = (float) Math.cos(angle);
		angle = (float) (angles[Defines.ROLL] * (Math.PI * 2 / 360));
		sr = (float) Math.sin(angle);
		cr = (float) Math.cos(angle);

		if (forward != null) {
			forward[0] = cp * cy;
			forward[1] = cp * sy;
			forward[2] = -sp;
		}
		if (right != null) {
			right[0] = (-1 * sr * sp * cy + -1 * cr * -sy);
			right[1] = (-1 * sr * sp * sy + -1 * cr * cy);
			right[2] = -1 * sr * cp;
		}
		if (up != null) {
			up[0] = (cr * sp * cy + -sr * -sy);
			up[1] = (cr * sp * sy + -sr * cy);
			up[2] = cr * cp;
		}
	}

	public static void MatClear(float m[][]) {
		m[0][0] = m[0][1] = m[0][2] = m[1][0] = m[1][1] = m[1][2] = m[2][0] = m[2][1] = m[2][2] = 0.0f;
	}

	public static final void MatCopy(float src[][], float dst[][]) {
		System.arraycopy(src, 0, dst, 0, dst.length);
	}

	public static void G_ProjectSource(float[] point, float[] distance, float[] forward, float[] right, float[] result) {
		result[0] = point[0] + forward[0] * distance[0] + right[0] * distance[1];
		result[1] = point[1] + forward[1] * distance[0] + right[1] * distance[1];
		result[2] = point[2] + forward[2] * distance[0] + right[2] * distance[1] + distance[2];
	}



	public static float DotProduct(float[] x, float[] y) {
		return x[0] * y[0] + x[1] * y[1] + x[2] * y[2];
	}



	public static void CrossProduct(float[] v1, float[] v2, float[] cross) {
		cross[0] = v1[1] * v2[2] - v1[2] * v2[1];
		cross[1] = v1[2] * v2[0] - v1[0] * v2[2];
		cross[2] = v1[0] * v2[1] - v1[1] * v2[0];
	}



	public static int Q_log2(int val) {
		int answer = 0;
		while ((val >>= 1) > 0)
			answer++;
		return answer;
	}



	public static float DEG2RAD(float in) {
		return (in * (float) Math.PI) / 180.0f;
	}



	public static float anglemod(float a) {
		return (float) (360.0 / 65536) * ((int) (a * (65536 / 360.0)) & 65535);
	}



	public static int ANGLE2SHORT(float x) {
		return ((int) ((x) * 65536 / 360) & 65535);
	}



	public static float LerpAngle(float a2, float a1, float frac) {
		if (a1 - a2 > 180)
			a1 -= 360;
		if (a1 - a2 < -180)
			a1 += 360;
		return a2 + frac * (a1 - a2);
	}
}
