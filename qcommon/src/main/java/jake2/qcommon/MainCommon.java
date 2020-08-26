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

    @Deprecated
    public static void debugLogStatsFile() {
        if (Globals.log_stats.modified) {
            Globals.log_stats.modified= false;

            if (Globals.log_stats.value != 0.0f) {

                if (Globals.log_stats_file != null) {
                    try {
                        Globals.log_stats_file.close();
                    } catch (IOException e) {
                    }
                    Globals.log_stats_file= null;
                }

                try {
                    Globals.log_stats_file= new FileWriter("stats.log");
                } catch (IOException e) {
                    Globals.log_stats_file= null;
                }
                if (Globals.log_stats_file != null) {
                    try {
                        Globals.log_stats_file.write("entities,dlights,parts,frame time\n");
                    } catch (IOException e) {
                    }
                }

            } else {

                if (Globals.log_stats_file != null) {
                    try {
                        Globals.log_stats_file.close();
                    } catch (IOException e) {
                    }
                    Globals.log_stats_file= null;
                }
            }
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
