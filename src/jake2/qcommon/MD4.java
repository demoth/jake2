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

// Created on 01.02.2004 by RST.
// $Id: MD4.java,v 1.1 2004-02-01 23:31:37 rst Exp $

package jake2.qcommon;


import java.nio.ByteBuffer;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

public class MD4 {

	/* MD4 context. */
	static class MD4_CTX {
		int state[] = { 0, 0, 0, 0 }; /* state (ABCD) */
		int count[] = { 0, 0 }; /* number of bits, modulo 2^64 (lsb first) */
		byte buffer[] = new byte[64]; /* input buffer */
	}

	/* Constants for MD4Transform routine.  */
	public final static int S11 = 3;
	public final static int S12 = 7;
	public final static int S13 = 11;
	public final static int S14 = 19;
	public final static int S21 = 3;
	public final static int S22 = 5;
	public final static int S23 = 9;
	public final static int S24 = 13;
	public final static int S31 = 3;
	public final static int S32 = 9;
	public final static int S33 = 11;
	public final static int S34 = 15;

	static byte PADDING[] =
		{
			(byte) 0x80,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0,
			0 };

	/* F, G and H are basic MD4 functions. */
	public static final int F(int x, int y, int z) {
		return ((x) & (y)) | ((~x) & (z));
	}

	public static final int G(int x, int y, int z) {
		return (((x) & (y)) | ((x) & (z)) | ((y) & (z)));
	}
	public static final int H(int x, int y, int z) {
		return ((x) ^ (y) ^ (z));
	}

	/* ROTATE_LEFT rotates x left n bits. */
	public static final int ROTATE_LEFT(int x, int n) {
		return (((x) << (n)) | ((x) >>> (32 - (n))));
	}

	public static final int LONG_TO_UINT(long i) {
		return (int) (i & 0xffffffff);
	}

	public static final long UINT_TO_LONG(int i) {
		return (i & 0xffffffff);
	}

	/* FF, GG and HH are transformations for rounds 1, 2 and 3 */
	/* Rotation is separate from addition to prevent recomputation */
	public static final int FF(int a, int b, int c, int d, int x, int s) {
		long a1 = UINT_TO_LONG(a);
		a1 += F(b, c, d) + x;
		a1 = ROTATE_LEFT(LONG_TO_UINT(a1), s);
		return LONG_TO_UINT(a1);
	}

	public static final int GG(int a, int b, int c, int d, int x, int s) {
		long a1 = UINT_TO_LONG(a);
		a1 += G(b, c, d) + x + 0x5a827999;
		a1 = ROTATE_LEFT(LONG_TO_UINT(a1), s);
		return LONG_TO_UINT(a1);
	}

	public static final int HH(int a, int b, int c, int d, int x, int s) {
		long a1 = UINT_TO_LONG(a);
		a1 += H(b, c, d) + x + 0x6ed9eba1;
		a1 = ROTATE_LEFT(LONG_TO_UINT(a1), s);
		return LONG_TO_UINT(a1);
	}

	/* MD4 initialization. Begins an MD4 operation, writing a new context. */
	public static final void MD4Init(MD4_CTX context) {
		context.count[0] = context.count[1] = 0;

		/* Load magic initialization constants.*/
		context.state[0] = 0x67452301;
		context.state[1] = 0xefcdab89;
		context.state[2] = 0x98badcfe;
		context.state[3] = 0x10325476;
	}

	/* MD4 block update operation. Continues an MD4 message-digest operation, processing another message block, and updating the context. */
	public static final void MD4Update(MD4_CTX context, byte input[], int inputLen) {
		int i, index, partLen;

		System.err.println("implement the memcpy, with int to byte[] converson)");
		new Throwable().printStackTrace();
		System.exit(-1);
		/* Compute number of bytes mod 64 */
		index = ((context.count[0] >> 3) & 0x3F);

		/* Update number of bits */
		if ((context.count[0] += (inputLen << 3)) < (inputLen << 3))
			context.count[1]++;

		context.count[1] += (inputLen >> 29);

		partLen = 64 - index;

		/* Transform as many times as possible.*/
		if (inputLen >= partLen) {
			//was: memcpy(context.buffer[index],  input, partLen);
			//TODO: handle the byte [] int [] conversion
			//System.arraycopy(input, 0, context.buffer, index, partLen);

			MD4Transform(context.state, context.buffer, 0);

			for (i = partLen; i + 63 < inputLen; i += 64)
				MD4Transform(context.state, input, i);

			index = 0;
		}
		else
			i = 0;

		
		/* Buffer remaining input */
		//memcpy((POINTER) & context.buffer[index], (POINTER) & input[i], inputLen - i);
		//TODO: handle the byte [] int [] conversion
		//System.arraycopy(input, partLen, context, index, inputLen)
	}

	/* MD4 finalization. Ends an MD4 message-digest operation, writing the the message digest and zeroizing the context. */
	static void MD4Final(byte digest[], MD4_CTX context) {
		byte bits[] = new byte[8];
		int index, padLen;

		/* Save number of bits */
		Encode(bits, context.count, 8);

		/* Pad out to 56 mod 64.*/
		index = (int) ((context.count[0] >> 3) & 0x3f);
		padLen = (index < 56) ? (56 - index) : (120 - index);
		MD4Update(context, PADDING, padLen);

		/* Append length (before padding) */
		MD4Update(context, bits, 8);

		/* Store state in digest */
		Encode(digest, context.state, 16);
	}

	/* MD4 basic transformation. Transforms state based on block. */
	static void MD4Transform(int state[], byte block[], int offset) {
		int a = state[0];
		int b = state[1];
		int c = state[2];
		int d = state[3];
		int x[] = new int[16];

		Decode(x, block, 64);

		/* Round 1 */
		a = FF(a, b, c, d, x[0], S11); /* 1 */
		d = FF(d, a, b, c, x[1], S12); /* 2 */
		c = FF(c, d, a, b, x[2], S13); /* 3 */
		b = FF(b, c, d, a, x[3], S14); /* 4 */
		a = FF(a, b, c, d, x[4], S11); /* 5 */
		d = FF(d, a, b, c, x[5], S12); /* 6 */
		c = FF(c, d, a, b, x[6], S13); /* 7 */
		b = FF(b, c, d, a, x[7], S14); /* 8 */
		a = FF(a, b, c, d, x[8], S11); /* 9 */
		d = FF(d, a, b, c, x[9], S12); /* 10 */
		c = FF(c, d, a, b, x[10], S13); /* 11 */
		b = FF(b, c, d, a, x[11], S14); /* 12 */
		a = FF(a, b, c, d, x[12], S11); /* 13 */
		d = FF(d, a, b, c, x[13], S12); /* 14 */
		c = FF(c, d, a, b, x[14], S13); /* 15 */
		b = FF(b, c, d, a, x[15], S14); /* 16 */

		/* Round 2 */
		a = GG(a, b, c, d, x[0], S21); /* 17 */
		d = GG(d, a, b, c, x[4], S22); /* 18 */
		c = GG(c, d, a, b, x[8], S23); /* 19 */
		b = GG(b, c, d, a, x[12], S24); /* 20 */
		a = GG(a, b, c, d, x[1], S21); /* 21 */
		d = GG(d, a, b, c, x[5], S22); /* 22 */
		c = GG(c, d, a, b, x[9], S23); /* 23 */
		b = GG(b, c, d, a, x[13], S24); /* 24 */
		a = GG(a, b, c, d, x[2], S21); /* 25 */
		d = GG(d, a, b, c, x[6], S22); /* 26 */
		c = GG(c, d, a, b, x[10], S23); /* 27 */
		b = GG(b, c, d, a, x[14], S24); /* 28 */
		a = GG(a, b, c, d, x[3], S21); /* 29 */
		d = GG(d, a, b, c, x[7], S22); /* 30 */
		c = GG(c, d, a, b, x[11], S23); /* 31 */
		b = GG(b, c, d, a, x[15], S24); /* 32 */

		/* Round 3 */
		a = HH(a, b, c, d, x[0], S31); /* 33 */
		d = HH(d, a, b, c, x[8], S32); /* 34 */
		c = HH(c, d, a, b, x[4], S33); /* 35 */
		b = HH(b, c, d, a, x[12], S34); /* 36 */
		a = HH(a, b, c, d, x[2], S31); /* 37 */
		d = HH(d, a, b, c, x[10], S32); /* 38 */
		c = HH(c, d, a, b, x[6], S33); /* 39 */
		b = HH(b, c, d, a, x[14], S34); /* 40 */
		a = HH(a, b, c, d, x[1], S31); /* 41 */
		d = HH(d, a, b, c, x[9], S32); /* 42 */
		c = HH(c, d, a, b, x[5], S33); /* 43 */
		b = HH(b, c, d, a, x[13], S34); /* 44 */
		a = HH(a, b, c, d, x[3], S31); /* 45 */
		d = HH(d, a, b, c, x[11], S32); /* 46 */
		c = HH(c, d, a, b, x[7], S33); /* 47 */
		b = HH(b, c, d, a, x[15], S34); /* 48 */

		state[0] = LONG_TO_UINT(UINT_TO_LONG(a) + UINT_TO_LONG(state[0]));
		state[0] = LONG_TO_UINT(UINT_TO_LONG(b) + UINT_TO_LONG(state[1]));
		state[0] = LONG_TO_UINT(UINT_TO_LONG(c) + UINT_TO_LONG(state[2]));
		state[0] = LONG_TO_UINT(UINT_TO_LONG(d) + UINT_TO_LONG(state[3]));
	}

	/* Encodes input (UINT4) into output (unsigned char). Assumes len is a multiple of 4. */
	static void Encode(byte output[], int input[], int len) {
		int i, j;

		for (i = 0, j = 0; j < len; i++, j += 4) {
			output[j] = (byte) (input[i] & 0xff);
			output[j + 1] = (byte) ((input[i] >>> 8) & 0xff);
			output[j + 2] = (byte) ((input[i] >>> 16) & 0xff);
			output[j + 3] = (byte) ((input[i] >>> 24) & 0xff);
		}
	}

	/* Decodes input (unsigned char) into output (UINT4). Assumes len is a multiple of 4. */
	static void Decode(int output[], byte[] input, int len) {
		int i, j;

		for (i = 0, j = 0; j < len; i++, j += 4)
			output[i] = (input[j]) | ((input[j + 1]) << 8) | ((input[j + 2]) << 16) | ((input[j + 3]) << 24);
	}

	//===================================================================

	public static int Com_BlockChecksum(byte[] buffer, int length) {
		byte digest[] = new byte[16];
		int val;
		MD4_CTX ctx = new MD4_CTX();

		MD4Init(ctx);
		MD4Update(ctx, buffer, length);
		MD4Final(digest, ctx);

		ByteBuffer bb = ByteBuffer.wrap(digest);
		//val = digest[0] ^ digest[1] ^ digest[2] ^ digest[3];
		val = bb.getInt() ^ bb.getInt() ^ bb.getInt() ^ bb.getInt();
		return val;
	}

}
