package jake2.maptools;

import jake2.qcommon.CM;

public class Common {
    static CM LoadBspFile(String path) {
        CM cm = new CM();
        final int[] checksum = new int[1];
        cm.CM_LoadMap(path, false, checksum);
        return cm;
    }
}
