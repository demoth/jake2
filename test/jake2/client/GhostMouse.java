/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 07.01.2000 by RST.
// $Id: GhostMouse.java,v 1.3 2004-09-15 22:13:36 cawe Exp $
package jake2.client;

import java.awt.Dimension;
import java.awt.Robot;
import java.awt.Toolkit;

import javax.swing.JButton;
import javax.swing.JFrame;

public class GhostMouse {
    public static Dimension size;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        size = Toolkit.getDefaultToolkit().getScreenSize();

        JFrame frame = new JFrame("Ghost Mouse (tm)!");
        JButton button = new JButton("Gho Ghost");
        frame.getContentPane().add(button);
        button.addActionListener(new CircleListener(robot));

        frame.pack();
        frame.setLocation((int) (size.getWidth() - frame.getWidth()) / 2,
                (int) (size.getHeight() - frame.getHeight()) / 2);
        frame.setVisible(true);
    }
}