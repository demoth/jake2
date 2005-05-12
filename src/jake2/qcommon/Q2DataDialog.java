/*
 * Q2DataDialog.java
 * Copyright (C)  2003
 * 
 * $Id: Q2DataDialog.java,v 1.13 2005-05-12 12:52:50 hzi Exp $
 */

package jake2.qcommon;

import jake2.Globals;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.*;


public class Q2DataDialog extends javax.swing.JDialog {
	
	static final String home = System.getProperty("user.home");
	static final String sep = System.getProperty("file.separator");
    
    public Q2DataDialog() {
        super();
        initComponents();
       
		DisplayMode mode = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
		int x = (mode.getWidth() - getWidth()) / 2;
		int y = (mode.getHeight() - getHeight()) / 2;
		setLocation(x, y);
		dir = home + sep + "jake2" + sep + "baseq2";
		jTextField1.setText(dir);
    }
    
    private void initComponents() {//GEN-BEGIN:initComponents
    	JComponent.setDefaultLocale(Locale.US);

        java.awt.GridBagConstraints gridBagConstraints;

        choosePanel = new javax.swing.JPanel();
        statusPanel = new JPanel();
        status = new JLabel("initializing Jake2...");
        jTextField1 = new javax.swing.JTextField();
        changeButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Jake2 - Bytonic Software");

        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        choosePanel.setLayout(new java.awt.GridBagLayout());
        choosePanel.setMaximumSize(new java.awt.Dimension(400, 100));
        choosePanel.setMinimumSize(new java.awt.Dimension(400, 100));
        choosePanel.setPreferredSize(new java.awt.Dimension(400, 100));


		
        gridBagConstraints = new java.awt.GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
		gridBagConstraints.weightx = 0;
		gridBagConstraints.anchor = GridBagConstraints.SOUTHWEST;
		choosePanel.add(new JLabel("baseq2 directory"),gridBagConstraints);
        
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 2, 5, 2);
        gridBagConstraints.weightx = 1;
        choosePanel.add(jTextField1, gridBagConstraints);

        changeButton.setText("...");
        changeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 1;
		gridBagConstraints.weightx = 0;
		gridBagConstraints.fill = java.awt.GridBagConstraints.NONE;
        gridBagConstraints.insets = new java.awt.Insets(5, 2, 5, 5);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        choosePanel.add(changeButton, gridBagConstraints);

		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 1;
		gridBagConstraints.gridwidth = 4;
		gridBagConstraints.weightx = 0;
		gridBagConstraints.weighty = 1;
		gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
		choosePanel.add(new JPanel(), gridBagConstraints);

        cancelButton.setText("Cancel");
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
		gridBagConstraints.gridwidth = 4;
		gridBagConstraints.weighty = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTH;
        choosePanel.add(cancelButton, gridBagConstraints);

        exitButton.setText("Exit");
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
        choosePanel.add(exitButton, gridBagConstraints);

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
		gridBagConstraints.gridwidth = 2;
		gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHEAST;
		choosePanel.add(okButton, gridBagConstraints);

 
		Jake2Canvas c = new Jake2Canvas();
		getContentPane().add(c, BorderLayout.CENTER);

		statusPanel.setLayout(new java.awt.GridBagLayout());
		statusPanel.setMaximumSize(new java.awt.Dimension(400, 100));
		statusPanel.setMinimumSize(new java.awt.Dimension(400, 100));
		statusPanel.setPreferredSize(new java.awt.Dimension(400, 100));
		gridBagConstraints = new java.awt.GridBagConstraints();
		gridBagConstraints.gridx = 0;
		gridBagConstraints.gridy = 0;
		gridBagConstraints.gridwidth = 1;
		gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
		gridBagConstraints.weightx = 1.0;
		statusPanel.add(status, gridBagConstraints);
		getContentPane().add(statusPanel, java.awt.BorderLayout.SOUTH);
		
		progressPanel = new ProgressPanel(this);
		installPanel = new InstallPanel(this);
		notFoundPanel = new NotFoundPanel(this);
						
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
    	
    	if (dir != null) {
    		Cvar.Set("cddir", dir);
    		FS.setCDDir();
    	}

    	synchronized(this) {
    		notifyAll();
    	}
    }

    private void changeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeButtonActionPerformed
    	JFileChooser chooser = new JFileChooser();
    	chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    	chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
    	chooser.setMultiSelectionEnabled(false);
    	chooser.setDialogTitle("choose a valid baseq2 directory");
    	chooser.showDialog(this, "OK");
    	
    	dir = null;
    	try {
			dir = chooser.getSelectedFile().getCanonicalPath();
		} catch (Exception e) {}
		if (dir != null) jTextField1.setText(dir);
		else dir = jTextField1.getText();
        
    }//GEN-LAST:event_changeButtonActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
    	System.exit(1);
    	dispose();
    }//GEN-LAST:event_formWindowClosing
        
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton changeButton;
    private javax.swing.JButton exitButton;
    private javax.swing.JButton cancelButton;
    private Jake2Canvas canvas;
    private javax.swing.JPanel choosePanel;
    private JPanel statusPanel;
    private ProgressPanel progressPanel;
    private InstallPanel installPanel;
    private NotFoundPanel notFoundPanel;
    private JLabel status;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JButton okButton;
    // End of variables declaration//GEN-END:variables
    
	private String dir;
	
	void showChooseDialog() {
		getContentPane().remove(statusPanel);
		getContentPane().remove(progressPanel);
		getContentPane().remove(installPanel);
		getContentPane().remove(notFoundPanel);
		getContentPane().add(choosePanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}
	
	void showStatus() {
		getContentPane().remove(choosePanel);
		getContentPane().remove(installPanel);
		getContentPane().add(statusPanel, BorderLayout.SOUTH);
		validate();
		repaint();		
	}
	
	void showProgressPanel() {
		getContentPane().remove(choosePanel);
		getContentPane().remove(installPanel);
		getContentPane().add(progressPanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}
	
	void showInstallPanel() {
		getContentPane().remove(choosePanel);
		getContentPane().remove(statusPanel);
		getContentPane().remove(notFoundPanel);
		getContentPane().add(installPanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}
	
	void showNotFoundPanel() {
		getContentPane().remove(choosePanel);
		getContentPane().remove(installPanel);
		getContentPane().remove(statusPanel);
		getContentPane().add(notFoundPanel, BorderLayout.SOUTH);
		validate();
		repaint();
	}	
	
	void setStatus(String text) {
		status.setText(text);
	}
	
	void testQ2Data() {
		while (FS.LoadFile("pics/colormap.pcx") == null) {
			showNotFoundPanel();
			
			try {
				synchronized(this) {
					wait();
				}
			} catch (InterruptedException e) {}
		}
		showStatus();
		repaint();
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
		
		private Q2DataDialog parent;
		private ButtonGroup selection;
		private JRadioButton dir;
		private JRadioButton install;
		private JButton exit;
		private JButton ok;
		private JLabel message;
		
		NotFoundPanel(Q2DataDialog d) {
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
			
			message = new JLabel("Quake2 level data not found");
			message.setForeground(Color.RED);
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.gridwidth = 2;
			constraints.insets = new Insets(5, 5, 2, 5);
			constraints.anchor = GridBagConstraints.CENTER;
			add(message, constraints);
						
			constraints.gridx = 1;
			constraints.gridy = 1;
			constraints.gridwidth = 2;
			constraints.weightx = 1;
			constraints.fill = GridBagConstraints.HORIZONTAL;			
			constraints.insets = new Insets(0, 2, 0, 5);
			constraints.anchor = GridBagConstraints.WEST;
			JLabel label = new JLabel("select baseq2 directory from existing Quake2 installation");
			label.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					dir.setSelected(true);
				}
			});
			add(label, constraints);
			
			constraints.gridx = 1;
			constraints.gridy = 2;
			label = new JLabel("download and install Quake2 demo data (38MB)");
			label.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					install.setSelected(true);
				}
			});
			add(label, constraints);
			
			selection = new ButtonGroup();
			dir = new JRadioButton();
			install = new JRadioButton();
			selection.add(dir);
			selection.add(install);

			constraints.gridx = 0;
			constraints.gridy = 1;
			constraints.gridwidth = 1;
			constraints.weightx = 0;
			constraints.insets = new Insets(0, 5, 0, 2);
			constraints.fill = GridBagConstraints.NONE;
			constraints.anchor = GridBagConstraints.EAST;
			dir.setSelected(true);
			add(dir, constraints);
			
			constraints.gridx = 0;
			constraints.gridy = 2;
			add(install, constraints);
			
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
			constraints.gridwidth = 1;
			constraints.anchor = GridBagConstraints.SOUTHEAST;	
			ok = new JButton("OK");
			ok.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ok();
			}});
			add(ok, constraints);				
		}
		
		private void ok() {
			if (dir.isSelected()) {
				parent.showChooseDialog();
			} else {
				parent.showInstallPanel();
			}
		}
	}
	
	static class InstallPanel extends JPanel {
		
		private Vector mirrorNames = new Vector();
		private Vector mirrorLinks = new Vector();		
		private Q2DataDialog parent;
		private JComboBox mirrorBox;
		private JTextField destDir;
		private JButton cancel;
		private JButton exit;
		private JButton install;
		private JButton choose;
		
		public InstallPanel(Q2DataDialog d) {
			initComponents();
			String dir = Q2DataDialog.home + Q2DataDialog.sep + "jake2";
			destDir.setText(dir);
			initMirrors();
			parent = d;
		}
		
		private void initComponents() {
			GridBagConstraints constraints = new GridBagConstraints();
			setLayout(new GridBagLayout());
			Dimension d = new Dimension(400, 100);
			setMinimumSize(d);
			setMaximumSize(d);
			setPreferredSize(d);
			
			constraints.gridx = 0;
			constraints.gridy = 0;
			constraints.insets = new Insets(5, 5, 0, 5);
			constraints.anchor = GridBagConstraints.SOUTHWEST;
			add(new JLabel("download mirror"), constraints);
			
			constraints.gridx = 0;
			constraints.gridy = 1;
			constraints.insets = new Insets(5, 5, 5, 5);
			add(new JLabel("destination directory"), constraints);
			
			constraints.gridx = 1;
			constraints.gridy = 0;
			constraints.weightx = 1;
			constraints.gridwidth = 3;
			constraints.insets = new Insets(5, 5, 0, 5);
			constraints.fill = GridBagConstraints.HORIZONTAL;
			mirrorBox = new JComboBox();
			add(mirrorBox, constraints);
			
			constraints.gridx = 1;
			constraints.gridy = 1;
			constraints.gridwidth = 2;
			constraints.fill = GridBagConstraints.BOTH;
			constraints.insets = new Insets(5, 5, 5, 5);
			destDir = new JTextField();
			add(destDir, constraints);
			
			constraints.gridx = 3;
			constraints.gridy = 1;
			constraints.weightx = 0;
			constraints.gridwidth = 1;
			constraints.fill = GridBagConstraints.NONE;
			choose = new JButton("...");
			choose.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					choose();
				}});
			add(choose, constraints);
			
			constraints.gridx = 0;
			constraints.gridy = 2;
			constraints.gridwidth = 1;
			constraints.weighty = 1;
			constraints.fill = GridBagConstraints.NONE;
			exit = new JButton("Exit");
			exit.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				exit();
			}});
			add(exit, constraints);
			
			constraints.gridx = 0;
			constraints.gridy = 2;
			constraints.gridwidth = 4;
			constraints.anchor = GridBagConstraints.SOUTH;
			cancel = new JButton("Cancel");
			cancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				cancel();
			}});
			add(cancel, constraints);						

			constraints.gridx = 2;
			constraints.gridy = 2;
			constraints.gridwidth = 2;
			constraints.anchor = GridBagConstraints.SOUTHEAST;
			install = new JButton("Install");
			install.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				install();
			}});
			add(install, constraints);			
		}
		
		private void readMirrors() {
			InputStream in = getClass().getResourceAsStream("/mirrors");
			BufferedReader r = new BufferedReader(new InputStreamReader(in));
			try {
				int i = 0;
				while (true) {
					String name = r.readLine();
					String value = r.readLine();
					if (name == null || value == null) break;
					mirrorNames.add(name);
					mirrorLinks.add(value);
				}
			} catch (Exception e) {} 
			finally {
				try {
					r.close();
				} catch (Exception e1) {}
				try {
					in.close();
				} catch (Exception e1) {}
			}
		}
		
		private void initMirrors() {
			readMirrors();
			for (int i = 0; i < mirrorNames.size(); i++) {
				mirrorBox.addItem(mirrorNames.get(i));
			}
			int i = Globals.rnd.nextInt(mirrorNames.size());
			mirrorBox.setSelectedIndex(i);
		}
		
		private void cancel() {
			parent.showNotFoundPanel();
		}
		
		private void install() {
			parent.progressPanel.destDir = destDir.getText();
			parent.progressPanel.mirror = (String)mirrorLinks.get(mirrorBox.getSelectedIndex());
			parent.showProgressPanel();
			new Thread(parent.progressPanel).start();
		}
		
		private void exit() {
			System.exit(0);
		}
		
		private void choose() {
			JFileChooser chooser = new JFileChooser();
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
			chooser.setMultiSelectionEnabled(false);
			chooser.setDialogTitle("choose destination directory");
			chooser.showDialog(this, "OK");
    	
			String dir = null;
			try {
				dir = chooser.getSelectedFile().getCanonicalPath();
			} catch (Exception e) {}
			if (dir != null) destDir.setText(dir);			
		}
	}
	
	static class ProgressPanel extends JPanel implements Runnable {
		
		static byte[] buf = new byte[8192];
		String destDir;
		String mirror;
		
		JProgressBar progress = new JProgressBar();
		JLabel label = new JLabel("");
		JButton cancel = new JButton("Cancel");
		Q2DataDialog parent;
		boolean running;
		
		public ProgressPanel(Q2DataDialog d) {
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
			
			InputStream in = null;
			OutputStream out = null;
			File outFile = null;
			
			label.setText("downloading...");
			
			File dir = null;
			try {
				dir = new File(destDir);
				dir.mkdirs();
			}
			catch (Exception e) {}
			try {
				if (!dir.isDirectory() || !dir.canWrite()) {
					endInstall("can't write to " + destDir);
					return;
				} 
			}
			catch (Exception e) {
				endInstall(e.getMessage());
				return;
			}
			
			try {
				URL url = new URL(mirror);
				URLConnection conn = url.openConnection();
				int length = conn.getContentLength();
				progress.setMaximum(length / 1024);
				progress.setMinimum(0);
				
				in = conn.getInputStream();

				outFile = File.createTempFile("Jake2Data", ".zip");
				outFile.deleteOnExit();
				out = new FileOutputStream(outFile);

				copyStream(in, out);
			} catch (Exception e) {
				endInstall(e.getMessage());
				return;
			} finally {
				try {
					in.close();
				} catch (Exception e) {}
				try {
					out.close();
				} catch (Exception e) {}		
			}
			
			try {
				installData(outFile.getCanonicalPath());
			} catch (Exception e) {
				endInstall(e.getMessage());
				return;
			}

			
			try {
				if (outFile != null) outFile.delete();
			} catch (Exception e) {}
			
			endInstall("installation successful");
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
		
		void endInstall(String message) {
			parent.notFoundPanel.message.setText(message);
			parent.dir = destDir + "/baseq2";
			parent.showChooseDialog();
			parent.okButtonActionPerformed(null);			
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

}
