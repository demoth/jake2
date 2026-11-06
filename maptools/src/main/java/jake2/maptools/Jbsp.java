package jake2.maptools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jbsp {
    private static final Logger logger = LoggerFactory.getLogger("JBSP");

    public static void main(String[] args) {
        if (args.length < 2) {
            logger.info("usage: [info|transparency|catalog] <args>");
            return;
        }
        if (args[0].equals("catalog")) {
            JbspCatalog.main(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        jake2.qcommon.MainCommon.registerCvars(false);
        switch (args[0]) {
            case "info":
                JbspInfo.info(args);
                break;
            case "transparency":
                JbspTransparencyInfo.transparency(args);
                break;
            default:
                logger.info("usage: [info|transparency|catalog] <args>");
        }
    }
}
