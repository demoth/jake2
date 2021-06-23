package jake2.qcommon;

import jake2.qcommon.util.Vargs;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Common procedures for main functions
 */
public class MainCommon {
    public static void debugLogTraces() {
        if (Globals.showtrace.value != 0.0f) {
            Com.Printf("%4i traces  %4i points\n", new Vargs(2).add(Globals.c_traces).add(Globals.c_pointcontents));
            Globals.c_traces= 0;
            Globals.c_brush_traces= 0;
            Globals.c_pointcontents= 0;
        }
    }

    public static int adjustTime(int msec) {
        if (Globals.fixedtime.value != 0.0f) {
            msec= (int) Globals.fixedtime.value;
        } else if (Globals.timescale.value != 0.0f) {
            msec *= Globals.timescale.value;
            if (msec < 1)
                msec = 1;
        }
        return msec;
    }

}
