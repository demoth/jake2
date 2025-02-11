package org.demoth.cake.stages;

/**
 * A simplified and more readable function that consumes a layout string and a stats array.
 * It outputs draw calls (here represented as {@code drawImage(...)} and {@code drawText(...)})
 * while ignoring "dirty" calls or error handling.
 * <p>
 * The idea is to parse layout instructions to place images or text on the screen.
 * The function no longer depends on external global structures like {@code ClientGlobals}.
 */
public class LayoutExecutor {

    // Example indexes for reference.
    private static final int STAT_HEALTH = 0;
    private static final int STAT_AMMO = 1;
    private static final int STAT_ARMOR = 2;
    private static final int STAT_FLASHES = 3;
    // Our parser instance.
    private static final LayoutParser layoutParser = new LayoutParser();

    // Example stub for an image-drawing operation.
    // In real code, you might pass in your own rendering or context.
    private static void drawImage(int x, int y, String imageName) {
        // Implementation stub.
    }

    // Example stub for a text-drawing operation.
    private static void drawText(int x, int y, String text, boolean alt) {
        // If alt==true, you might render a different font.
    }

    // Example stub for drawing a numeric field.
    private static void drawNumber(int x, int y, int value, int width, int color) {
        // Implementation stub.
    }

    /**
     * Simplified layout execution that:
     * 1) Reads positional tokens (xl, xr, xv, yt, yb, yv)
     * 2) Handles draw commands (like pic, picn, num, etc.)
     * 3) Relies on the provided {@code stats} array for values.
     * 4) Ignores any global or dirty calls.
     *
     * @param layout       The layout string containing drawing instructions
     * @param serverFrame  The current server frame for blinking logic
     * @param stats        Array of player stats
     * @param screenWidth  Width of the screen
     * @param screenHeight Height of the screen
     */
    public static void executeLayoutString(
            String layout,
            int serverFrame,
            int[] stats,
            int screenWidth,
            int screenHeight) {
        // If layout is invalid, do nothing.
        if (layout == null || layout.isEmpty()) {
            return;
        }

        // Variables to track current position and field width.
        int x = 0;
        int y = 0;
        int width = 3;

        layoutParser.init(layout);

        while (layoutParser.hasNext()) {
            layoutParser.next();
            String token = layoutParser.getToken();

            switch (token) {
                case "xl":
                    // Next token is integer X.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        x = layoutParser.tokenAsInt();
                    }
                    break;

                case "xr":
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        x = screenWidth + layoutParser.tokenAsInt();
                    }
                    break;

                case "xv":
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        x = (screenWidth / 2) - 160 + layoutParser.tokenAsInt();
                    }
                    break;

                case "yt":
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        y = layoutParser.tokenAsInt();
                    }
                    break;

                case "yb":
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        y = screenHeight + layoutParser.tokenAsInt();
                    }
                    break;

                case "yv":
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        y = (screenHeight / 2) - 120 + layoutParser.tokenAsInt();
                    }
                    break;

                case "pic": {
                    // Next token is a stat index used as an image reference.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        int statIndex = layoutParser.tokenAsInt();
                        int imageIndex = stats[statIndex];
                        // We'll do a simple name: e.g. "image_" + imageIndex.
                        // In real code, you might map imageIndex => actual image.
                        String imageName = "image_" + imageIndex;
                        drawImage(x, y, imageName);
                    }
                    break;
                }

                case "picn": {
                    // Next token is a string name for an image.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        String imageName = layoutParser.getToken();
                        drawImage(x, y, imageName);
                    }
                    break;
                }

                case "num": {
                    // Expect 2 subsequent tokens: width, statIndex.
                    // Then draw that number.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        width = layoutParser.tokenAsInt();
                    }
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        int statIndex = layoutParser.tokenAsInt();
                        int value = stats[statIndex];
                        // color 0 for now.
                        drawNumber(x, y, value, width, 0);
                    }
                    break;
                }

                case "hnum": {
                    // Health number.
                    int health = stats[STAT_HEALTH];
                    int color;
                    if (health > 25) {
                        color = 0; // green.
                    } else if (health > 0) {
                        // flash.
                        color = (serverFrame >> 2) & 1;
                    } else {
                        color = 1; // e.g., red.
                    }
                    drawNumber(x, y, health, 3, color);
                    break;
                }

                case "anum": {
                    // Ammo.
                    int ammo = stats[STAT_AMMO];
                    if (ammo < 0) {
                        // do not draw.
                        break;
                    }
                    int color = (ammo > 5) ? 0 : ((serverFrame >> 2) & 1);
                    drawNumber(x, y, ammo, 3, color);
                    break;
                }

                case "rnum": {
                    // Armor.
                    int armor = stats[STAT_ARMOR];
                    if (armor < 1) {
                        break;
                    }
                    drawNumber(x, y, armor, 3, 0);
                    break;
                }

                case "string": {
                    // Next token is the text to display.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        String text = layoutParser.getToken();
                        drawText(x, y, text, false);
                    }
                    break;
                }

                case "string2": {
                    // Next token is the text to display in alt mode.
                    if (layoutParser.hasNext()) {
                        layoutParser.next();
                        String text = layoutParser.getToken();
                        drawText(x, y, text, true);
                    }
                    break;
                }

                // Additional tokens like "if", "client", etc. omitted for simplicity.

                default:
                    // unhandled token.
                    break;
            }
        }
    }

    // Minimal layout parser.
    private static class LayoutParser {
        private String[] tokens;
        private int index;

        void init(String layout) {
            tokens = layout.split("\\s+");
            index = 0;
        }

        boolean hasNext() {
            return tokens != null && index < tokens.length;
        }

        void next() {
            index++;
        }

        String getToken() {
            return tokens[index];
        }

        /**
         * For convenience, we expect a token that is an integer.
         */
        int tokenAsInt() {
            return Integer.parseInt(getToken());
        }
    }
}
