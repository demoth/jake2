/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Sun Microsystems nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package jake2;

import java.applet.*;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.net.*;
import java.text.*;
import java.util.*;

/**
 * An applet to smooth the adoption of newer applet content which runs
 * only with the new Java Plug-In: in particular, applets launched via
 * JNLP. It displays some informative text; clicking on the text will
 * open a new browser window pointing to the download page for the new
 * Java Plug-In. This applet runs even on very early versions of the
 * JRE and can be referenced from the applet tag's code / archive
 * attributes while the jnlp_href can point to the new-style applet's
 * code.
 * <P>
 * In 6u10 build 13, it is necessary to copy this applet's source code
 * and rename it to be exactly the same class name as is in the
 * main-class attribute in the JNLP file, preferably referenced via
 * the archive attribute in a jar named something like
 * "BackwardCompatibility.jar". In 6u10 build 14, the applet tag may
 * refer to this class via its code attribute, and the JNLP file may
 * point to an entirely different class name, allowing one copy of the
 * compatibility applet to be used for many different JNLP-launched
 * applets.
 * <P>
 * Parameters supported:
 * <P>
 * <CODE>compat_fontsize</CODE> - the font size used to draw the text.<BR>
 * <CODE>compat_fgcolor</CODE> - the color used to draw the text.<BR>
 * <CODE>compat_bgcolor</CODE> - the color used to draw the text.
 *
 * @author Kenneth Russell
 */

public class CompatibilityApplet extends java.applet.Applet {
    private Font font;
    private static final String inputText = "Click here to get the new Java Plug-In";
    private static final String url = "https://jdk6.dev.java.net/6uNea.html";

    public void init() {
        int fontSize = 36;
        try {
            fontSize = Integer.parseInt(getParameter("compat_fontsize"));
        } catch (Exception e) {
        }
        Color fgColor = Color.black;
        Color bgColor = Color.white;
        try {
            fgColor = Color.decode(getParameter("compat_fgcolor"));
        } catch (Exception e) {
        }
        try {
            bgColor = Color.decode(getParameter("compat_bgcolor"));
        } catch (Exception e) {
        }
        font = new Font("SansSerif", Font.PLAIN, fontSize);
        setForeground(fgColor);
        setBackground(bgColor);
        addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    try {
                        getAppletContext().showDocument(new URL(url), "_blank");
                    } catch (Exception ex) {
                    }
                }
            });
    }

    static class LineInfo {
        String text;
        float width;
        float height;

        LineInfo(String text, float width, float height) {
            this.text = text;
            this.width = width;
            this.height = height;
        }
    }

    public void paint(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        List/*<LineInfo>*/ lines = new ArrayList();
        FontMetrics fm = g.getFontMetrics();
        FontRenderContext frc = g.getFontRenderContext();
        Map attrs = new HashMap();
        attrs.put(TextAttribute.FONT, font);
        float totalHeight = 0;
        int curPos = 0;
        AttributedString str = new AttributedString(inputText, attrs);
        LineBreakMeasurer measurer = new LineBreakMeasurer(str.getIterator(), frc);
        while (measurer.getPosition() < inputText.length()) {
            // Give us a few pixels inset from the edges
            int nextPos = measurer.nextOffset(getWidth() - 10);
            String line = inputText.substring(curPos, nextPos);
            GlyphVector gv = font.createGlyphVector(frc, line);
            Rectangle2D bounds = gv.getVisualBounds();
            float height = (float) bounds.getHeight() + 5;
            lines.add(new LineInfo(line, (float) bounds.getWidth(), height));
            totalHeight += height;
            curPos = nextPos;
            measurer.setPosition(curPos);
        }
        // Draw the strings centered vertically and horizontally in this component
        g.setFont(font);
        float curY = (getHeight() - totalHeight) / 2;
        for (Iterator iter = lines.iterator(); iter.hasNext(); ) {
            LineInfo line = (LineInfo) iter.next();
            curY += line.height;
            float x = (getWidth() - line.width) / 2;
            g.drawString(line.text, (int) x, (int) curY);
        }
    }
}
