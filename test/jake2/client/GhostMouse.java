/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/

// Created on 07.01.2000 by RST.
// $Id: GhostMouse.java,v 1.1 2004-07-07 19:59:56 hzi Exp $

package jake2.client;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.Robot;


// import jake2.*;
// import jake2.client.*;
// import jake2.game.*;
// import jake2.qcommon.*;
// import jake2.render.*;
// import jake2.server.*;

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
        frame.setLocation(
        (int)(size.getWidth()-frame.getWidth())/2,
        (int)(size.getHeight()-frame.getHeight())/2);
        frame.show();
    }
}
