/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 27.12.2003 by RST.
// $Id: Info.java,v 1.5 2004-10-21 03:32:53 cawe Exp $
package jake2.game;

import jake2.Defines;
import jake2.qcommon.Com;

import java.util.StringTokenizer;

public class Info {

    public static String Info_ValueForKey(String s, String key) {

        StringTokenizer tk = new StringTokenizer(s, "\\");

        while (tk.hasMoreTokens()) {
            String key1 = tk.nextToken();

            if (!tk.hasMoreTokens()) {
                Com.Printf("MISSING VALUE\n");
                return s;
            }
            String value1 = tk.nextToken();

            if (key.equals(key1))
                return value1;
        }

        return "";
    }

    /**
     * TODO RST: DANGEROUS port, returns the modified userinfo string, was
     * pointer-pointer manipulation first
     */
    public static String Info_SetValueForKey1(String s, String key, String value) {

        if (value == null || value.length() == 0)
            return s;

        if (key.indexOf('\\') != -1 || value.indexOf('\\') != -1) {
            Com.Printf("Can't use keys or values with a \\\n");
            return s;
        }

        if (key.indexOf(';') != -1) {
            Com.Printf("Can't use keys or values with a semicolon\n");
            return s;
        }

        if (key.indexOf('"') != -1 || value.indexOf('"') != -1) {
            Com.Printf("Can't use keys or values with a \"\n");
            return s;
        }

        if (key.length() > Defines.MAX_INFO_KEY - 1
                || value.length() > Defines.MAX_INFO_KEY - 1) {
            Com.Printf("Keys and values must be < 64 characters.\n");
            return s;
        }

        StringBuffer sb = new StringBuffer(Info_RemoveKey1(s, key));

        if (sb.length() + 2 + key.length() + value.length() > Defines.MAX_INFO_STRING) {

            Com.Printf("Info string length exceeded\n");
            return s;
        }

        sb.append('\\').append(key).append('\\').append(value);

        return sb.toString();
    }

    /** TODO RST: DANGEROUS port, returns now the modified userinfo string. */
    public static String Info_RemoveKey1(String s, String key) {

        StringBuffer sb = new StringBuffer(512);

        if (key.indexOf('\\') != -1) {
            Com.Printf("Can't use a key with a \\\n");
            return s;
        }

        StringTokenizer tk = new StringTokenizer(s, "\\");

        while (tk.hasMoreTokens()) {
            String key1 = tk.nextToken();

            if (!tk.hasMoreTokens()) {
                Com.Printf("MISSING VALUE\n");
                return s;
            }
            String value1 = tk.nextToken();

            if (!key.equals(key1))
                sb.append('\\').append(key1).append('\\').append(value1);
        }

        return sb.toString();

        //some old code follows

        /*
         * char * start; char pkey[512]; char value[512]; char * o;
         * 
         * if (key.indexOf('\\')!=-1) { Com.Printf ("Can't use a key with a
         * \\\n"); return s; }
         * 
         * while () { start = s; if (* s == '\\') s++; o = pkey; while (* s !=
         * '\\') { if (!* s) return; o++ = * s++; } o = 0; s++;
         * 
         * o = value; while (* s != '\\' && * s) { if (!* s) return; o++ = *
         * s++; } o = 0;
         * 
         * if (!strcmp(key, pkey)) { strcpy(start, s); // remove this part
         * return; }
         * 
         * if (!* s) return; }
         */
    }

    /*
     * ================== Info_Validate
     * 
     * Some characters are illegal in info strings because they can mess up the
     * server's parsing ==================
     */
    public static boolean Info_Validate(String s) {
        return !((s.indexOf('"') != -1) || (s.indexOf(';') != -1));
    }

    private static String fillspaces = "                     ";

    public static void Print(String s) {

        StringBuffer sb = new StringBuffer(512);
        StringTokenizer tk = new StringTokenizer(s, "\\");

        while (tk.hasMoreTokens()) {

            String key1 = tk.nextToken();

            if (!tk.hasMoreTokens()) {
                Com.Printf("MISSING VALUE\n");
                return;
            }

            String value1 = tk.nextToken();

            sb.append(key1);

            int len = key1.length();

            if (len < 20) {
                sb.append(fillspaces.substring(len));
            }

            sb.append('=').append(value1).append('\n');
        }

        Com.Printf(sb.toString());

        /*
         * some old code follows
         * 
         * char * o; int l;
         * 
         * if (* s == '\\') s++; while (* s) { o = key; while (* s && * s !=
         * '\\') o++ = * s++;
         * 
         * l = o - key; if (l < 20) { memset(o, ' ', 20 - l); key[20] = 0; }
         * else o = 0; Com_Printf("%s", key);
         * 
         * if (!* s) { Com_Printf("MISSING VALUE\n"); return; }
         * 
         * o = value; s++; while (* s && * s != '\\') o++ = * s++; o = 0;
         * 
         * if (* s) s++; Com_Printf("%s\n", value); }
         */
    }

    public static void testintern(StringBuffer in) {
        in.setLength(0);
        in.append("123!");
    }
}