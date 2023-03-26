package jake2.client;

public class DisplayMode {
    public int width;
    public int height;
    public int bitsPerPixel;
    public int refreshRate;

    public DisplayMode(int width, int height, int bitsPerPixel, int refreshRate) {
        this.width = width;
        this.height = height;
        this.bitsPerPixel = bitsPerPixel;
        this.refreshRate = refreshRate;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public int getRefreshRate() {
        return refreshRate;
    }
}