/*
 * Created on Apr 26, 2003
 *
 */
package jake2.imageio;

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
	
	public ImageFrame(BufferedImage image) {
		super();
		this.image = image;
		this.setSize(image.getWidth()+3, image.getHeight()+20);
		
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
	}
	
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.drawImage(image, null, 3, 20);
	}

}
