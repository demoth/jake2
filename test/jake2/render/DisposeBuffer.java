/*
 * Created on May 13, 2005
 *
 */
package jake2.render;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * @author cwei
 *
 */
public class DisposeBuffer {

    // 160 MB direct buffers
    static int SIZE = 1024 * 1024;
    static int COUNT = 160;
    
    public static void main(String[] args) {
        System.out.println("DirectBuffer allocation.");
        Buffer[] buf = new Buffer[COUNT];
        Runtime run = Runtime.getRuntime();
        System.gc();
        for (int i = 0; i < COUNT; i++) {
            buf[i] = ByteBuffer.allocateDirect(SIZE);
        }
        System.gc();
        System.out.println((run.totalMemory() / 1024) + "KB heap");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }
        System.out.println("DirectBuffer dispose.");
        for (int i = 0; i < COUNT; i++) {
            buf[i] = null;
        }
        System.gc();
        System.out.println((run.totalMemory() / 1024) + "KB heap");
        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
        }
    }
}
