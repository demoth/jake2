package jake2.qcommon;

import jake2.game.pmove_t;
import jake2.game.trace_t;

public interface GameImports {
    // special messages
    void bprintf(int printlevel, String s);

    void dprintf(String s);

    void cprintf(edict_t ent, int printlevel, String s);

    void centerprintf(edict_t ent, String s);

    void sound(edict_t ent, int channel, int soundindex, float volume,
               float attenuation, float timeofs);

    void positioned_sound(float[] origin, edict_t ent, int channel,
                          int soundinedex, float volume, float attenuation, float timeofs);

    /*
     config strings hold all the index strings, the lightstyles,
     and misc data like the sky definition and cdtrack.
     All of the current configstrings are sent to clients when
     they connect, and changes are sent to all connected clients.
    */
    void configstring(int num, String string);

    void error(String err);

    void error(int level, String err);

    /* the *index functions create configstrings and some internal server state */
    int modelindex(String name);

    int soundindex(String name);

    int imageindex(String name);

    void setmodel(edict_t ent, String name);

    /* collision detection */
    trace_t trace(float[] start, float[] mins, float[] maxs,
                  float[] end, edict_t passent, int contentmask);

    boolean inPHS(float[] p1, float[] p2);

    void SetAreaPortalState(int portalnum, boolean open);

    boolean AreasConnected(int area1, int area2);

    /*
             an entity will never be sent to a client or used for collision
             if it is not passed to linkentity. If the size, position, or
             solidity changes, it must be relinked.
            */
    void linkentity(edict_t ent);

    void unlinkentity(edict_t ent);

    /* call before removing an interactive edict */
    int BoxEdicts(float[] mins, float[] maxs, edict_t list[],
                  int maxcount, int areatype);

    void Pmove(pmove_t pmove);

    /*
             player movement code common with client prediction
             network messaging
            */
    void multicast(float[] origin, int to);

    void unicast(edict_t ent, boolean reliable);

    void WriteByte(int c);

    void WriteShort(int c);

    void WriteString(String s);

    void WritePosition(float[] pos);

    /* some fractional bits */
    void WriteDir(float[] pos);

    /* console variable interaction */
    cvar_t cvar(String var_name, String value, int flags);

    /* console variable interaction */
    cvar_t cvar_set(String var_name, String value);

    /* console variable interaction */
    cvar_t cvar_forceset(String var_name, String value);

    /*
         add commands to the server console as if they were typed in
         for map changing, etc
        */
    void AddCommandString(String text);

    int getPointContents(float[] p);
}
