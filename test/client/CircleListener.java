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
// $Id: CircleListener.java,v 1.1 2004-01-07 18:18:53 rst Exp $

package client;

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

class CircleListener implements ActionListener {
    Robot robot;
    public CircleListener(Robot robot) {
        this.robot = robot;
    }

    public void actionPerformed(ActionEvent evt) {
        int originx = (int)GhostMouse.size.getWidth()/2;
        int originy = (int)GhostMouse.size.getHeight()/2;
        double pi = 3.1457;

        for(double theta = 0; theta < 4*pi; theta=theta+0.1) {
            double radius = theta * 20;
            double x = Math.cos(theta) * radius + originx;
            double y = Math.sin(theta) * radius + originy;
            robot.mouseMove((int)x,(int)y);
            try{Thread.sleep(25);} catch (Exception ex) { }
        }
    }

}

