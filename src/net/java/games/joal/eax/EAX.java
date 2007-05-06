/**
* Copyright (c) 2003 Sun Microsystems, Inc. All  Rights Reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* -Redistribution of source code must retain the above copyright notice, 
* this list of conditions and the following disclaimer.
*
* -Redistribution in binary form must reproduce the above copyright notice, 
* this list of conditions and the following disclaimer in the documentation
* and/or other materials provided with the distribution.
*
* Neither the name of Sun Microsystems, Inc. or the names of contributors may 
* be used to endorse or promote products derived from this software without 
* specific prior written permission.
* 
* This software is provided "AS IS," without a warranty of any kind.
* ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
* ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
* NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN MICROSYSTEMS, INC. ("SUN") AND ITS
* LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A
* RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
* IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT
* OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR
* PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY,
* ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
* BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
*
* You acknowledge that this software is not designed or intended for use in the
* design, construction, operation or maintenance of any nuclear facility.
*/

package net.java.games.joal.eax;

import java.nio.Buffer;


/**
 * @author Athomas Goldberg
 *
 */
public class EAX implements EAXConstants {
    static {
        System.loadLibrary("joal");
    }

	public static final int SOURCE 		= 0;
	public static final int LISTENER 	= 1;

	private final int sourceGUID = SOURCE;
	private final int listenerGUID = LISTENER;

    /**
     * @param sourceGUID
     * @param listenerGUID
     */
    EAX(int sourceGUID, int listenerGUID) {
        // this.sourceGUID = sourceGUID;
        // this.listenerGUID = listenerGUID;
    }

    /**
     * This method sets an EAX property value. <br>
     * <br>
     * <b>Interface to C Language function:</b>
     * <pre>ALenum EAXSet(const struct _GUID *propertySetID, ALuint property, ALuint source, ALvoid *value, ALuint size)</pre>
     * 
     * @param objectFlag a flag indicating a LISTENER or a SOURCE
     * @param pname the property being set 
     * @param source the ID of the source, or 0 for Listener properties
     * @param value a direct Buffer to hold the value retrieved
     * @param size the size of the Buffer
     */
    public native void EAXSet(
        int objectFlag,
        int pname,
        int source,
        Buffer value,
        int size
    );

    /**
     * This method retrieves an EAX property value. <br>
     * <br>
     * <b>Interface to C Language function:</b>
     * <pre>ALenum EAXGet(const struct _GUID *propertySetID, ALuint property, ALuint source, ALvoid *value, ALuint size)</pre>
     * 
     * @param objectFlag a flag indicating a LISTENER or a SOURCE
     * @param pname the property being queried 
     * @param source the ID of the source, or 0 for Listener properties
     * @param value a direct Buffer to hold the value retrieved
     * @param size the size of the Buffer
     */    
    public native void EAXGet(
        int objectFlag,
        int pname,
        int source,
        Buffer value,
        int size
    );

    /**
     * This method sets a source property. 
     * @param sourceID the ID of the source whose property is being set.
     * @param pname the name of the property being set
     * @param value a direct Buffer containing the value to be set
     */
    public void setSourceProperty(int sourceID, int pname, Buffer value) {
        EAXSet(sourceGUID, pname, sourceID, value, value.capacity());
    }

    /**
     * This method retrieves a source property. 
     * @param sourceID the ID of the source whose property is being retrieved.
     * @param pname the name of the property being retrieved
     * @param value a direct Buffer to hold the value to be retrieved
     */
    public void getSourceProperty(int pname, int sourceID, Buffer value) {
        EAXGet(sourceGUID, pname, sourceID, value, value.capacity());
    }

    /**
     * This method sets a Listener property. 
     * @param pname the name of the property being set
     * @param value a direct Buffer containing the value to be set
     */
    public void setListenerProperty(int pname, Buffer value) {
        EAXSet(listenerGUID, pname, 0, value, value.capacity());
    }

    /**
     * This method retrieves a Listener property. 
     * @param pname the name of the property being retrieved
     * @param value a direct Buffer to hold the value to be retrieved
     */
    public void getListenerProperty(int pname, Buffer value) {
        EAXGet(listenerGUID, pname, 0, value, value.capacity());
    }
}
