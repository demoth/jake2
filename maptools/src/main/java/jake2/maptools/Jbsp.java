package jake2.maptools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jbsp {
    private static final Logger logger = LoggerFactory.getLogger("JBSP");

    public static void main(String[] args) {
        if (args.length < 2) {
            logger.info("usage: [info] args");
            return;
        }
        switch (args[0]) {
            case "info":
                JbspInfo.info(args);
                break;
            default:
                logger.info("usage: [info] args");
        }
    }
}
