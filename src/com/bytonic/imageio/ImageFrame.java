/*
 * Created on Apr 26, 2003
 *
 */
package com.bytonic.imageio;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;

/**
 * @author cwei
 *
 */
public class ImageFrame extends JFrame {

	BufferedImage image;
	Component pane;

	public ImageFrame(BufferedImage image) {
		super();
		this.image = image;

		pane = getContentPane();
		setIconImage(image);
		setSize(640, 480);

		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
	}

	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D) pane.getGraphics();
		if (this.image != null) {
			g2.drawImage(
				image,
				Math.max(0, (getWidth() - image.getWidth()) / 2),
				Math.max(0, (getHeight() - image.getHeight()) / 2),
				Color.LIGHT_GRAY,
				pane);
		} else {
			g2.drawString(
				"EMPTY IMAGE",
				this.getWidth() / 4,
				this.getHeight() / 2);
		}
	}

	public void showImage(BufferedImage image) {
		this.image = image;
		this.repaint();
	}

}
