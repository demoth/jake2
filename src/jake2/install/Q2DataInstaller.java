/*
 * Q2DataDialog.java
 * Copyright (C)  2003
 * 
 * $Id: Q2DataInstaller.java,v 1.2 2004-10-31 19:45:28 hzi Exp $
 */

package jake2.install;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.*;


public class Q2DataInstaller extends javax.swing.JDialog {
	
	static final String home = System.getProperty("user.home");
	static final String sep = System.getProperty("file.separator");
	String destdir;
    
    public Q2DataInstaller(String dir) {
        super();
        destdir = dir;
        initComponents();
       
		DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
		int x = (mode.getWidth() - getWidth()) / 2;
		int y = (mode.getHeight() - getHeight()) / 2;
		setLocation(x, y);
   }
    
    private void initComponents() {//GEN-BEGIN:initComponents
        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Jake2 - Bytonic Software");

        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
 
		Jake2Canvas c = new Jake2Canvas();
		getContentPane().add(c, BorderLayout.CENTER);
		
		progressPanel = new ProgressPanel(this);
		notFoundPanel = new NotFoundPanel(this);
		
		getContentPane().add(notFoundPanel, java.awt.BorderLayout.SOUTH);
		
        pack();
    }
	
    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {
		showNotFoundPanel();
    }
    
    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {
    	System.exit(1);
    	dispose();
    }
    
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {

    	synchronized(this) {
    		notifyAll();
    	}
    }

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
    	System.exit(1);
    	dispose();
    }//GEN-LAST:event_formWindowClosing
        

    private ProgressPanel progressPanel;
    private NotFoundPanel notFoundPanel;
    	
	void showChooseDialog() {
		getContentPane().remove(progressPanel);
		getContentPane().remove(notFoundPanel);
		validate();
		repaint();
	}
		
	void showProgressPanel() {
		getContentPane().remove(notFoundPanel);
		getContentPane().add(progressPanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}
	
	void showNotFoundPanel() {
		getContentPane().remove(progressPanel);
		getContentPane().add(notFoundPanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}	
		
	void installQ2Data() {
		showNotFoundPanel();
		synchronized(this) {
			try {
				wait();
			} catch (Exception e) {}
		}
		setVisible(false);
		dispose();
		
	}
		
	static class Jake2Canvas extends Canvas {
		private Image image;
		Jake2Canvas() {
			setSize(400, 200);
			try {
				image = ImageIO.read(getClass().getResource("/splash.png"));
			} catch (Exception e) {}

		}
		
		
		/* (non-Javadoc)
		 * @see java.awt.Component#paint(java.awt.Graphics)
		 */
		public void paint(Graphics g) {
			g.drawImage(image, 0, 0, null);
		}

	}
	
	static class NotFoundPanel extends JPanel {
		
		private Q2DataInstaller parent;
		private JTextField jTextField1;
		private JButton changeButton;
		private JButton exit;
		private JButton ok;
		private JLabel message;
		
		NotFoundPanel(Q2DataInstaller d) {
			parent = d;
			initComponents();
		}
		
		private void initComponents() {
			GridBagConstraints constraints = new GridBagConstraints();
			setLayout(new GridBagLayout());
			Dimension d = new Dimension(400, 100);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
			
			message = new JLabel("install Quake2 demo data");
			message.setForeground(Color.BLACK);
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.gridwidth = 2;
			constraints.insets = new Insets(5, 5, 2, 5);
			constraints.anchor = GridBagConstraints.CENTER;
			add(message, constraints);

			constraints.gridx = 0;
			constraints.gridy = 1;
			constraints.gridwidth = 1;
			constraints.insets = new java.awt.Insets(5, 5, 5, 5);
			constraints.weightx = 0;
			constraints.anchor = GridBagConstraints.SOUTHWEST;
			add(new JLabel("Quake2 demo"),constraints);
	        
			jTextField1 = new JTextField();
			jTextField1.setText("../Quake2Demo/q2-314-demo-x86.exe");
	        constraints.gridx = 1;
	        constraints.gridy = 1;
	        constraints.gridwidth = 2;
	        constraints.fill = java.awt.GridBagConstraints.BOTH;
	        constraints.insets = new java.awt.Insets(5, 2, 5, 2);
	        constraints.weightx = 1;
	        add(jTextField1, constraints);

	        changeButton = new JButton();
	        changeButton.setText("...");
	        changeButton.addActionListener(new java.awt.event.ActionListener() {
	            public void actionPerformed(java.awt.event.ActionEvent evt) {
	                changeButtonActionPerformed();
	            }
	        });
	        constraints.gridx = 3;
	        constraints.gridy = 1;
	        constraints.gridwidth = 1;
			constraints.weightx = 0;
			constraints.fill = java.awt.GridBagConstraints.NONE;
	        constraints.insets = new java.awt.Insets(5, 2, 5, 5);
	        constraints.anchor = java.awt.GridBagConstraints.EAST;
	        add(changeButton, constraints);
						
			constraints.gridx = 0;
			constraints.gridy = 3;
			constraints.gridwidth = 2;
			constraints.weighty = 1;
			constraints.insets = new Insets(5, 5, 5, 5);
			constraints.fill = GridBagConstraints.NONE;
			constraints.anchor = GridBagConstraints.SOUTHWEST;
			exit = new JButton("Exit");
			exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}});
			add(exit, constraints);			
			
			constraints.gridx = 2;
			constraints.gridy = 3;
			constraints.gridwidth = 2;
			constraints.anchor = GridBagConstraints.SOUTHEAST;	
			ok = new JButton("OK");
			ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ok();
			}});
			add(ok, constraints);				
		}
		
		private void ok() {
			parent.progressPanel.destDir = parent.destdir;
			parent.progressPanel.mirror = jTextField1.getText();
			File f = new File(jTextField1.getText());
			if (f.canRead()) {
				parent.showProgressPanel();
				new Thread(parent.progressPanel).start();
			} else {
			    message.setText("could not read " + jTextField1.getText());
			}
		}
		
		private void changeButtonActionPerformed() {
	    	JFileChooser chooser = new JFileChooser();
	    	chooser.setCurrentDirectory(new File("."));
	    	chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
	    	chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
	    	chooser.setMultiSelectionEnabled(false);
	    	chooser.setDialogTitle("select Quake2 demo file");
	    	int ret = chooser.showDialog(this, "OK");
	    	
	    	if (ret == JFileChooser.APPROVE_OPTION) {
                String dir = null;
                try {
                    dir = chooser.getSelectedFile().getCanonicalPath();
                } catch (Exception e) {
                }
                if (dir != null)
                    jTextField1.setText(dir);
            }
		}
	}
		
	static class ProgressPanel extends JPanel implements Runnable {
		
		static byte[] buf = new byte[8192];
		String destDir;
		String mirror;
		
		JProgressBar progress = new JProgressBar();
		JLabel label = new JLabel("");
		JButton cancel = new JButton("Cancel");
		Q2DataInstaller parent;
		boolean running;
		
		public ProgressPanel(Q2DataInstaller d) {
			initComponents();
			parent = d;
		}
		
		void initComponents() {
			progress.setMinimum(0);
			progress.setMaximum(100);
			progress.setStringPainted(true);
			setLayout(new GridBagLayout());
			GridBagConstraints gridBagConstraints = new GridBagConstraints();

			gridBagConstraints = new GridBagConstraints();
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			gridBagConstraints.gridwidth = 1;
			gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
			gridBagConstraints.insets = new Insets(5, 10, 5, 10);
			gridBagConstraints.weightx = 1.0;
			gridBagConstraints.anchor = GridBagConstraints.SOUTH;
			add(label, gridBagConstraints);
			
			gridBagConstraints.gridy = 1;
			gridBagConstraints.anchor = GridBagConstraints.NORTH;
			add(progress, gridBagConstraints);
			
			gridBagConstraints.gridy = 1;
			gridBagConstraints.anchor = GridBagConstraints.SOUTH;
			gridBagConstraints.fill = GridBagConstraints.NONE;
			gridBagConstraints.weighty = 1;
			gridBagConstraints.weightx = 0;
			cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancel();
			}});
			add(cancel, gridBagConstraints);
			
			Dimension d = new Dimension(400, 100);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
		}
		
		void cancel() {
			synchronized(this) {
				running = false;
			}
		}
						
		public void run() {
			synchronized(this) {
				running = true;
			}
						
			File dir = null;
			try {
				dir = new File(destDir);
				dir.mkdirs();
			}
			catch (Exception e) {}
			try {
				if (!dir.isDirectory() || !dir.canWrite()) {
					endInstall(false, "can't write to " + destDir);
					return;
				} 
			}
			catch (Exception e) {
				endInstall(false, e.getMessage());
				return;
			}
						
			try {
				installData(mirror);
			} catch (Exception e) {
				endInstall(false, e.getMessage());
				return;
			}
			
			endInstall(true, "installation successful");
		}
		
		
		void installData(String filename) throws Exception {
			InputStream in = null;
			OutputStream out = null;
			try {
				ZipFile f = new ZipFile(filename);
				Enumeration e = f.entries();
				while (e.hasMoreElements()) {
					ZipEntry entry = (ZipEntry)e.nextElement();
					String name = entry.getName();
					int i;
					if ((i = name.indexOf("/baseq2")) > -1 && name.indexOf(".dll") == -1) {
						name = destDir + name.substring(i);
						File outFile = new File(name);
						if (entry.isDirectory()) {
							outFile.mkdirs();
						} else {
							label.setText("installing " + outFile.getName());
							progress.setMaximum((int)entry.getSize()/1024);
							progress.setValue(0); 
							outFile.getParentFile().mkdirs();
							out = new FileOutputStream(outFile);
							in = f.getInputStream(entry);
							copyStream(in, out);
						}
					}
				}
			} catch (Exception e) {
				throw e;
			} finally {
				try {in.close();} catch (Exception e1) {}
				try {out.close();} catch (Exception e1) {}				
			}
		}
		
		void endInstall(boolean exit, String text) {
		    parent.notFoundPanel.message.setText(text);
		    parent.showNotFoundPanel();
			
			if (exit) {
			    parent.okButtonActionPerformed(null);
			    System.exit(0);
			}
		}
		
		void copyStream(InputStream in, OutputStream out) throws Exception {
			try {
				int c = 0;
				int l;
				while ((l = in.read(buf)) > 0) {
					if (!running) throw new Exception("installation canceled");
					out.write(buf, 0, l);
					c += l;
					int k = c / 1024;
					progress.setValue(k);
					progress.setString(k + "/" + progress.getMaximum() + " KB");
				}
			} catch (Exception e) {
				throw e;
			} finally {
				try {
					in.close();
				} catch (Exception e) {}
				try {
					out.close();
				} catch (Exception e) {}
			}			
		}
	}

	public static void main(String[] args) {
		
		if (args.length != 1) System.exit(1);
		
		Q2DataInstaller installer = new Q2DataInstaller(args[0]);
		installer.setVisible(true);
		installer.installQ2Data();
		
	}
}
