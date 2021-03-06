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

// Created on 24.01.2004 by RST.
// $Id: TestRotatePointAroundVector.java,v 1.1 2004-07-07 19:59:58 hzi Exp $

package jake2.qcommon;

import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.StringTokenizer;

// import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.client.render.*;
// import jake2.server.*;

public class TestRotatePointAroundVector
{

	static String testperp[] =
		{
			" 0.009647  0.005551 -0.296153 == -0.006815  0.977844  0.209225",
			"-0.758731  0.039112  0.214241 ==  0.076493  0.996836 -0.021599",
			" 0.113768 -0.311733  0.603937 ==  0.939749  0.156803 -0.303783",
			"-0.466186  0.341352  0.104305 ==  0.393279 -0.287967  0.873159",
			" 0.775436  0.779995 -0.863782 ==  0.962981 -0.180665  0.200072",
			" 0.814734  0.288255 -0.669693 == -0.170216  0.975423  0.139913",
			"-0.667435 -0.429661  0.683953 == -0.260653  0.927747  0.267104",
			"-0.927300 -0.585577 -0.957522 == -0.128592  0.982768 -0.132783",
			" 0.242942  0.040065  0.092075 == -0.894528  0.291345 -0.339024",
			" 0.646724 -0.933291 -0.948063 ==  0.981161  0.135531  0.137676",
			" 0.232663 -0.959211  0.253123 ==  0.975522  0.212626 -0.056109",
			"-0.250403  0.458914 -0.208372 == -0.424561  0.778093  0.462945",
			" 0.194574 -0.775337 -0.556817 ==  0.977607  0.170927  0.122753",
			" 0.741328  0.476465 -0.972738 == -0.126045  0.978140  0.165391",
			"-0.163294 -0.275933 -0.592179 ==  0.858983 -0.216251 -0.464095",
			"-0.847422 -0.768890 -0.681753 == -0.206793 -0.187629  0.960225",
			"-0.919289  0.581288  0.198026 ==  0.123803 -0.078283  0.989214",
			"-0.541896 -0.634396  0.228633 ==  0.228399  0.267386  0.936129",
			" 0.210305  0.928218 -0.243859 ==  0.975105 -0.214467  0.056344",
			"-0.399813 -0.891607 -0.711955 ==  0.974389 -0.175720 -0.140314",
			" 0.769699  0.916032  0.251807 == -0.088408 -0.105215  0.990512",
			" 0.262079 -0.921695 -0.297379 ==  0.965746  0.246954  0.079678",
			"-0.787958 -0.605119 -0.832105 == -0.186950  0.962327 -0.197425",
			" 0.891434  0.839237  0.815951 == -0.175581 -0.165300  0.970488",
			"-0.702145 -0.656422 -0.863553 == -0.186798  0.955158 -0.229739",
			" 0.473749 -0.795161 -0.679987 ==  0.950461  0.236242  0.202023",
			"-0.756531 -0.950714  0.524613 ==  0.138574  0.174143  0.974921",
			"-0.944200  0.293208 -0.783887 ==  0.111872  0.989373  0.092878",
			"-0.380537 -0.962794  0.770586 ==  0.984444 -0.137174  0.109789",
			" 0.019079 -0.668430  0.525818 ==  0.999520  0.024358 -0.019161" };

	static String tests[] =
		{
			" 0.680375 -0.211234  0.566198 #  0.596880  0.823295 -0.604897 #  120.6802 == -0.655159  0.261707  0.743340 ",
			" 0.536459 -0.444451  0.107940 # -0.045206  0.257742 -0.270431 #  184.8243 == -0.193460  0.038827  0.159348 ",
			" 0.904459  0.832390  0.271423 #  0.434594 -0.716795  0.213938 #    5.8682 ==  0.747359 -1.101788  0.087111 ",
			"-0.514226 -0.725537  0.608353 # -0.686642 -0.198111 -0.740419 #   39.1712 == -0.130630 -0.727312 -0.873737 ",
			" 0.997849 -0.563486  0.025865 #  0.678224  0.225280 -0.407937 #  229.5188 ==  0.140445 -1.014774 -0.177662 ",
			" 0.048574 -0.012834  0.945550 # -0.414966  0.542715  0.053490 #  277.1690 ==  0.464477  0.459346  0.005334 ",
			"-0.199543  0.783059 -0.433371 # -0.295083  0.615449  0.838053 #   25.1119 ==  0.132556  0.605152  0.672271 ",
			" 0.898654  0.051991 -0.827888 # -0.615572  0.326454  0.780465 #  125.6015 == -0.908882 -0.423560  1.130438 ",
			"-0.871657 -0.959954 -0.084597 # -0.873808 -0.523440  0.941268 #  324.7949 == -0.817769 -1.385161  0.919210 ",
			" 0.701840 -0.466668  0.079521 # -0.249586  0.520498  0.025071 #  240.3806 == -0.284741  0.143251 -0.292353 ",
			" 0.063213 -0.921439 -0.124725 #  0.863670  0.861620  0.441905 #  102.3456 == -0.541507  0.588085  0.875724 ",
			" 0.477069  0.279958 -0.291903 #  0.375723 -0.668052 -0.119791 #  316.8271 ==  0.464398 -0.177470 -0.014007 ",
			" 0.658402 -0.339326 -0.542064 #  0.786745 -0.299280  0.373340 #  344.3286 ==  0.726676 -0.059072  0.259724 ",
			" 0.177280  0.314608  0.717353 # -0.120880  0.847940 -0.203127 #  293.3161 ==  0.546512  0.263594 -0.238898 ",
			" 0.368437  0.821944 -0.035019 # -0.568350  0.900505  0.840257 #   53.1576 ==  0.384098  0.383469  1.141096 ",
			" 0.762124  0.282161 -0.136093 #  0.239193 -0.437881  0.572004 #  110.6848 == -0.034934 -0.356566 -0.561636 ",
			"-0.105933 -0.547787 -0.624934 # -0.447531  0.112888 -0.166997 #   61.0586 == -0.074301  0.245476 -0.298803 ",
			" 0.813608 -0.793658 -0.747849 # -0.009112  0.520950  0.969503 #  336.6014 == -0.120939  1.510490  1.400521 ",
			" 0.368890 -0.233623  0.499542 # -0.262673 -0.411679 -0.535477 #  210.4159 == -0.050155  0.463231  0.348973 ",
			"-0.511174 -0.695220  0.464297 # -0.749050  0.586941 -0.671796 #  268.2257 ==  0.022772  0.925406  0.668994 ",
			"-0.850940  0.900208 -0.894941 #  0.043127 -0.647579 -0.519875 #  287.2073 ==  1.234205 -0.290291 -0.371230 ",
			" 0.465309  0.313127  0.934810 #  0.278917  0.519470 -0.813039 #   48.5649 == -0.362583  0.755727 -0.615632 ",
			" 0.040420 -0.843536 -0.860187 # -0.590690 -0.077159  0.639355 #  206.3947 ==  0.769276  0.686703  0.210591 ",
			" 0.511162 -0.896122 -0.684386 #  0.999987 -0.591343  0.779911 #   45.1687 ==  0.096778 -1.624548  1.110771 ",
			" 0.995598 -0.891885  0.741080 # -0.855342 -0.991677  0.846138 #  213.8012 ==  2.588428  1.678911  0.857673 ",
			"-0.639255 -0.673737 -0.216620 #  0.826053  0.639390 -0.281809 #  198.8946 ==  0.277942  0.729819  0.633058 ",
			" 0.158860 -0.094848  0.374775 # -0.800720  0.061616  0.514588 #  109.5463 == -0.113519 -0.154459 -0.215363 ",
			" 0.984457  0.153942  0.755228 #  0.495619  0.257820 -0.929158 #  269.2090 ==  0.101893 -1.323990 -0.319552 ",
			" 0.666477  0.850753  0.746543 #  0.662075  0.958868  0.487622 #  325.2119 ==  1.531004  1.350724  1.039028 ",
			" 0.967191  0.333761 -0.005483 # -0.672064  0.660024  0.777897 #   27.7181 == -0.548955  0.246637  1.090340 " };

	public static void testRotate(int i, String line)
	{
		StringTokenizer tk = new StringTokenizer(line);

		float dir[] = { 0, 0, 0 };
		float point[] = { 0, 0, 0 };
		float dst[] = { 0, 0, 0 };
		float newdst[] = { 0, 0, 0 };
		float degrees = 0;

		dir[0] = Float.parseFloat(tk.nextToken());
		dir[1] = Float.parseFloat(tk.nextToken());
		dir[2] = Float.parseFloat(tk.nextToken());

		tk.nextToken();

		point[0] = Float.parseFloat(tk.nextToken());
		point[1] = Float.parseFloat(tk.nextToken());
		point[2] = Float.parseFloat(tk.nextToken());

		tk.nextToken();

		degrees = Float.parseFloat(tk.nextToken());

		tk.nextToken();

		dst[0] = Float.parseFloat(tk.nextToken());
		dst[1] = Float.parseFloat(tk.nextToken());
		dst[2] = Float.parseFloat(tk.nextToken());

		Math3D.RotatePointAroundVector(newdst, dir, point, degrees);


		System.out.println("" + i + ":" + Lib.vtofs(dst)  + "  :  " + Lib.vtofs(newdst));
		
	}
	
	public static void testPerpend(int i, String line)
	{
		StringTokenizer tk = new StringTokenizer(line);

		float dir[] = { 0, 0, 0 };

		float dst[] = { 0, 0, 0 };

		float newdst[] = { 0, 0, 0 };

		dir[0] = Float.parseFloat(tk.nextToken());
		dir[1] = Float.parseFloat(tk.nextToken());
		dir[2] = Float.parseFloat(tk.nextToken());

		tk.nextToken();

		dst[0] = Float.parseFloat(tk.nextToken());
		dst[1] = Float.parseFloat(tk.nextToken());
		dst[2] = Float.parseFloat(tk.nextToken());

		Math3D.PerpendicularVector(newdst, dir);

		System.out.println("" + i + ":" + Lib.vtofs(dst) + "  :  " + Lib.vtofs(newdst));

	}

	public static void main(String[] args)
	{
		
		System.out.println("DEG2RAD:" + Math3D.DEG2RAD(205));
		
		System.out.println("testperpendicular...");
		for (int n = 0; n < testperp.length; n++)
			testPerpend(n, testperp[n]);			
		

		System.out.println("testrotate...");
		for (int n = 0; n < tests.length; n++)
			testRotate(n, tests[n]);
	}

}
