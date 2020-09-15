package jake2.maptools;

import jake2.qcommon.CM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static jake2.maptools.Common.LoadBspFile;

public class JbspInfo {

    private static final Logger logger = LoggerFactory.getLogger("JbspInfo");

    static void info(String[] args) {
        logger.info("jbsp info: {}", args[1]);

        final CM cm = LoadBspFile(args[1]);
        logger.info("Loaded model");
        logger.info("{} models", cm.numcmodels);
        logger.info("{} brushes", cm.numbrushes);
        logger.info("{} brush sides", cm.numbrushsides);
        logger.info("{} planes", cm.numplanes);
        logger.info("{} texinfo", cm.numtexinfo);
//        logger.info("{} ent data", "NOT IMPLEMENTED");

//        logger.info("{} vertexes", "NOT IMPLEMENTED");
        logger.info("{} clusters", cm.numclusters);
        logger.info("{} nodes", cm.box_headnode);
//        logger.info("{} faces", cm);
        logger.info("{} leafs", cm.numleafs);
//        logger.info("{} leaffaces", cm);
        logger.info("{} leafbrushes", cm.numleafbrushes);
//        logger.info("{} surfedges", cm);
//        logger.info("{} lightdata", cm);
//        logger.info("{} visdata", cm);
    }
}
