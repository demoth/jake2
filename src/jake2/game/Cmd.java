/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.13 2003-12-04 20:25:10 rst Exp $
 */
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
package jake2.game;

import java.util.Arrays;

import jake2.*;
import jake2.qcommon.*;

/**
 * Cmd
 */
public final class Cmd {
	
	static xcommand_t List_f = new xcommand_t() {
		public void execute() {
			cmd_function_t  cmd = Cmd.cmd_functions;
			int i = 0;

			while (cmd != null) {
				Com.Printf(cmd.name + '\n');
				i++;
				cmd = cmd.next;
			}
			Com.Printf(i + " commands\n");
		}
	};
	
	static xcommand_t Exec_f = new xcommand_t() {
		public void execute() {
			if (Cmd.Argc() != 2) {
				Com.Printf("exec <filename> : execute a script file\n");
				return;
			}

			byte[] f = null;
			f = FS.LoadFile(Cmd.Argv(1));
			if (f == null) {
				Com.Printf("couldn't exec " + Cmd.Argv(1) + "\n");
				return;
			}
			Com.Printf("execing " + Cmd.Argv(1) + "\n");

			Cbuf.InsertText(new String(f));
			
			FS.FreeFile(f);
		}
	};
	static xcommand_t Echo_f = new xcommand_t() {
		public void execute() {
			for (int i  = 1;  i < Cmd.Argc(); i++) {
				Com.Printf(Cmd.Argv(i) + " ");
			}
			Com.Printf("'\n");
		}
	};
	
	static xcommand_t Alias_f = new xcommand_t() {
		public void execute() {
			cmdalias_t a = null;
			if (Cmd.Argc() == 1) {
				Com.Printf("Current alias commands:\n");
				for (a = Globals.cmd_alias; a != null; a = a.next) {
					Com.Printf(a.name + " : " + a.value);
				}
				return;
			}

			String s = Cmd.Argv(1);
			if (s.length() > Globals.MAX_ALIAS_NAME) {
				Com.Printf("Alias name is too long\n");
				return;
			}

			// if the alias already exists, reuse it
			for (a = Globals.cmd_alias; a != null; a = a.next) {
				if (s.equalsIgnoreCase(a.name)) {
					a.value = null;
					break;
				}
			}
			
			if (a == null) {
				a = new cmdalias_t();
				a.next = Globals.cmd_alias;
				Globals.cmd_alias = a;
			}
			a.name = s;
			
			// copy the rest of the command line
			String cmd = "";
			int c = Cmd.Argc();
			for (int i = 2; i < c; i++) {
				cmd = cmd + Cmd.Argv(i);
				if (i != (c-1)) cmd = cmd + " ";
			}
			cmd = cmd + "\n";
			
			a.value = cmd;
		}
	};
	static xcommand_t Wait_f = new xcommand_t() {
		public void execute() {
			Globals.cmd_wait = true;
		}
	};
	
	public static cmd_function_t cmd_functions = null;
	static int cmd_argc;
	static String[] cmd_argv = new String[Globals.MAX_STRING_TOKENS];
	static String cmd_args;

	/**
	 * register our commands
	 */
	public static void Init() {
    	Cmd.AddCommand ("cmdlist", List_f);
		Cmd.AddCommand ("exec", Exec_f);
		Cmd.AddCommand ("echo", Echo_f);
		Cmd.AddCommand ("alias",Alias_f);
		Cmd.AddCommand ("wait", Wait_f);
	}

	/**
	 * @param cmdname
	 * @param function
	 */
	public static void AddCommand(String cmd_name, xcommand_t function) {
		cmd_function_t  cmd;
         
		// fail if the command is a variable name
		if ((Cvar.VariableString(cmd_name)).length() > 0) {
			Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined as a var\n");
			return;
		}
		
		// fail if the command already exists
		for (cmd=cmd_functions ; cmd != null ; cmd=cmd.next) {
			if (cmd_name.equals(cmd.name)) {
				Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined\n");
				return;
			}
		}
		
		cmd = new cmd_function_t();
		cmd.name = cmd_name;
		cmd.function = function;
		cmd.next = cmd_functions;
		cmd_functions = cmd; 
	}
	
	/**
	 * @return number of command arguments
	 */
	public static int Argc() {
		return cmd_argc;	
	}
	
	/**
	 * @param i index
	 * @return command argument at position i
	 */
	public static String Argv(int i) {
		if (i < 0 || i >= cmd_argc) return "";
		return cmd_argv[i];
	}

	/**
	 * 
	 */
	public static String Args() {
		return cmd_args;
	}
	
	public static void ExecuteString (String text) {       
//	00813         cmd_function_t  *cmd;
//	00814         cmdalias_t              *a;
//	00815 
//	00816         Cmd_TokenizeString (text, true);
//	00817                         
//	00818         // execute the command line
//	00819         if (!Cmd_Argc())
//	00820                 return;         // no tokens
//	00821 
//	00822         // check functions
//	00823         for (cmd=cmd_functions ; cmd ; cmd=cmd->next)
//	00824         {
//	00825                 if (!Q_strcasecmp (cmd_argv[0],cmd->name))
//	00826                 {
//	00827                         if (!cmd->function)
//	00828                         {       // forward to server command
//	00829                                 Cmd_ExecuteString (va("cmd %s", text));
//	00830                         }
//	00831                         else
//	00832                                 cmd->function ();
//	00833                         return;
//	00834                 }
//	00835         }
//	00836 
//	00837         // check alias
//	00838         for (a=cmd_alias ; a ; a=a->next)
//	00839         {
//	00840                 if (!Q_strcasecmp (cmd_argv[0], a->name))
//	00841                 {
//	00842                         if (++alias_count == ALIAS_LOOP_COUNT)
//	00843                         {
//	00844                                 Com_Printf ("ALIAS_LOOP_COUNT\n");
//	00845                                 return;
//	00846                         }
//	00847                         Cbuf_InsertText (a->value);
//	00848                         return;
//	00849                 }
//	00850         }
//	00851         
//	00852         // check cvars
//	00853         if (Cvar_Command ())
//	00854                 return;
//	00855 
//	00856         // send it as a server command if we are connected
//	00857         Cmd_ForwardToServer ();
	}

	/*
	==================
	Cmd_Give_f
	
	Give items to a client
	==================
	*/
	public static void Give_f(edict_t ent) {
		String name;
		gitem_t it;
		int index;
		int i;
		boolean give_all;
		edict_t it_ent;
	
		if (GameBase.deathmatch.value == 0 && GameBase.sv_cheats.value == 0) {
			GameBase.gi.cprintf(
				ent,
				Defines.PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}
	
		name= GameBase.gi.args();
	
		if (0 == GameBase.Q_stricmp(name, "all"))
			give_all= true;
		else
			give_all= false;
	
		if (give_all || 0 == GameBase.Q_stricmp(GameBase.gi.argv(1), "health")) {
			if (GameBase.gi.argc() == 3)
				ent.health= GameAI.atoi(GameBase.gi.argv(2));
			else
				ent.health= ent.max_health;
			if (!give_all)
				return;
		}
	
		if (give_all || 0 == GameBase.Q_stricmp(name, "weapons")) {
			for (i= 0; i < GameBase.game.num_items; i++) {
				it= GameAI.itemlist[i];
				if (null == it.pickup)
					continue;
				if (0 == (it.flags & Defines.IT_WEAPON))
					continue;
				ent.client.pers.inventory[i] += 1;
			}
			if (!give_all)
				return;
		}
	
		if (give_all || 0 == GameBase.Q_stricmp(name, "ammo")) {
			for (i= 0; i < GameBase.game.num_items; i++) {
				it= GameAI.itemlist[i];
				if (null == it.pickup)
					continue;
				if (0 == (it.flags & Defines.IT_AMMO))
					continue;
				GameAI.Add_Ammo(ent, it, 1000);
			}
			if (!give_all)
				return;
		}
	
		if (give_all || GameBase.Q_stricmp(name, "armor") == 0) {
			gitem_armor_t info;
	
			it= GameUtil.FindItem("Jacket Armor");
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(it)]= 0;
	
			it= GameUtil.FindItem("Combat Armor");
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(it)]= 0;
	
			it= GameUtil.FindItem("Body Armor");
			info= (gitem_armor_t) it.info;
			ent.client.pers.inventory[GameUtil.ITEM_INDEX(it)]= info.max_count;
	
			if (!give_all)
				return;
		}
	
		if (give_all || GameBase.Q_stricmp(name, "Power Shield") == 0) {
			it= GameUtil.FindItem("Power Shield");
			it_ent= GameUtil.G_Spawn();
			it_ent.classname= it.classname;
			GameAI.SpawnItem(it_ent, it);
			GameAI.Touch_Item(it_ent, ent, null, null);
			if (it_ent.inuse)
				GameUtil.G_FreeEdict(it_ent);
	
			if (!give_all)
				return;
		}
	
		if (give_all) {
			for (i= 0; i < GameBase.game.num_items; i++) {
				it= GameAI.itemlist[i];
				if (it.pickup != null)
					continue;
				if ((it.flags & (Defines.IT_ARMOR | Defines.IT_WEAPON | Defines.IT_AMMO)) != 0)
					continue;
				ent.client.pers.inventory[i]= 1;
			}
			return;
		}
	
		it= GameUtil.FindItem(name);
		if (it == null) {
			name= GameBase.gi.argv(1);
			it= GameUtil.FindItem(name);
			if (it == null) {
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "unknown item\n");
				return;
			}
		}
	
		if (it.pickup == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "non-pickup item\n");
			return;
		}
	
		index= GameUtil.ITEM_INDEX(it);
	
		if ((it.flags & Defines.IT_AMMO) != 0) {
			if (GameBase.gi.argc() == 3)
				ent.client.pers.inventory[index]= GameAI.atoi(GameBase.gi.argv(2));
			else
				ent.client.pers.inventory[index] += it.quantity;
		} else {
			it_ent= GameUtil.G_Spawn();
			it_ent.classname= it.classname;
			GameAI.SpawnItem(it_ent, it);
			GameAI.Touch_Item(it_ent, ent, null, null);
			if (it_ent.inuse)
				GameUtil.G_FreeEdict(it_ent);
		}
	}

	/*
	==================
	Cmd_God_f
	
	Sets client to godmode
	
	argv(0) god
	==================
	*/
	public static void God_f(edict_t ent) {
		String msg;
	
		if (GameBase.deathmatch.value == 0 && GameBase.sv_cheats.value == 0) {
			GameBase.gi.cprintf(
				ent,
				Defines.PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}
	
		ent.flags ^= Defines.FL_GODMODE;
		if (0 == (ent.flags & Defines.FL_GODMODE))
			msg= "godmode OFF\n";
		else
			msg= "godmode ON\n";
	
		GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Notarget_f
	
	Sets client to notarget
	
	argv(0) notarget
	==================
	*/
	public static void Notarget_f(edict_t ent) {
		String msg;
	
		if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
			GameBase.gi.cprintf(
				ent,
				Defines.PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}
	
		ent.flags ^= Defines.FL_NOTARGET;
		if (0 == (ent.flags & Defines.FL_NOTARGET))
			msg= "notarget OFF\n";
		else
			msg= "notarget ON\n";
	
		GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Noclip_f
	
	argv(0) noclip
	==================
	*/
	public static void Noclip_f(edict_t ent) {
		String msg;
	
		if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
			GameBase.gi.cprintf(
				ent,
				Defines.PRINT_HIGH,
				"You must run the server with '+set cheats 1' to enable this command.\n");
			return;
		}
	
		if (ent.movetype == Defines.MOVETYPE_NOCLIP) {
			ent.movetype= Defines.MOVETYPE_WALK;
			msg= "noclip OFF\n";
		} else {
			ent.movetype= Defines.MOVETYPE_NOCLIP;
			msg= "noclip ON\n";
		}
	
		GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, msg);
	}

	/*
	==================
	Cmd_Use_f
	
	Use an inventory item
	==================
	*/
	public static void Use_f(edict_t ent) {
		int index;
		gitem_t it;
		String s;
	
		s= GameBase.gi.args();
		it= GameUtil.FindItem(s);
		if (it != null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + s + "\n");
			return;
		}
		if (it.use == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Item is not usable.\n");
			return;
		}
		index= GameUtil.ITEM_INDEX(it);
		if (0 == ent.client.pers.inventory[index]) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + s + "\n");
			return;
		}
	
		it.use.use(ent, it);
	}

	/*
	==================
	Cmd_Drop_f
	
	Drop an inventory item
	==================
	*/
	public static void Drop_f(edict_t ent) {
		int index;
		gitem_t it;
		String s;
	
		s= GameBase.gi.args();
		it= GameUtil.FindItem(s);
		if (it == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + s + "\n");
			return;
		}
		if (it.drop == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Item is not dropable.\n");
			return;
		}
		index= GameUtil.ITEM_INDEX(it);
		if (0 == ent.client.pers.inventory[index]) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + s + "\n");
			return;
		}
	
		it.drop.drop(ent, it);
	}

	/*
	=================
	Cmd_Inven_f
	=================
	*/
	public static void Inven_f(edict_t ent) {
		int i;
		gclient_t cl;
	
		cl= ent.client;
	
		cl.showscores= false;
		cl.showhelp= false;
	
		if (cl.showinventory) {
			cl.showinventory= false;
			return;
		}
	
		cl.showinventory= true;
	
		GameBase.gi.WriteByte(Defines.svc_inventory);
		for (i= 0; i < Defines.MAX_ITEMS; i++) {
			GameBase.gi.WriteShort(cl.pers.inventory[i]);
		}
		GameBase.gi.unicast(ent, true);
	}

	/*
	=================
	Cmd_InvUse_f
	=================
	*/
	public static void InvUse_f(edict_t ent) {
		gitem_t it;
	
		GameAI.ValidateSelectedItem(ent);
	
		if (ent.client.pers.selected_item == -1) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "No item to use.\n");
			return;
		}
	
		it= GameAI.itemlist[ent.client.pers.selected_item];
		if (it.use == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Item is not usable.\n");
			return;
		}
		it.use.use(ent, it);
	}

	/*
	=================
	Cmd_WeapPrev_f
	=================
	*/
	public static void WeapPrev_f(edict_t ent) {
		gclient_t cl;
		int i, index;
		gitem_t it;
		int selected_weapon;
	
		cl= ent.client;
	
		if (cl.pers.weapon == null)
			return;
	
		selected_weapon= GameUtil.ITEM_INDEX(cl.pers.weapon);
	
		// scan  for the next valid one
		for (i= 1; i <= Defines.MAX_ITEMS; i++) {
			index= (selected_weapon + i) % Defines.MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;
	
			it= GameAI.itemlist[index];
			if (it.use == null)
				continue;
	
			if (0 == (it.flags & Defines.IT_WEAPON))
				continue;
			it.use.use(ent, it);
			if (cl.pers.weapon == it)
				return; // successful
		}
	}

	/*
	=================
	Cmd_WeapNext_f
	=================
	*/
	public static void WeapNext_f(edict_t ent) {
		gclient_t cl;
		int i, index;
		gitem_t it;
		int selected_weapon;
	
		cl= ent.client;
	
		if (null == cl.pers.weapon)
			return;
	
		selected_weapon= GameUtil.ITEM_INDEX(cl.pers.weapon);
	
		// scan  for the next valid one
		for (i= 1; i <= Defines.MAX_ITEMS; i++) {
			index= (selected_weapon + Defines.MAX_ITEMS - i) % Defines.MAX_ITEMS;
			if (0 == cl.pers.inventory[index])
				continue;
			it= GameAI.itemlist[index];
			if (null == it.use)
				continue;
			if (0 == (it.flags & Defines.IT_WEAPON))
				continue;
			it.use.use(ent, it);
			if (cl.pers.weapon == it)
				return; // successful
		}
	}

	/*
	=================
	Cmd_WeapLast_f
	=================
	*/
	public static void WeapLast_f(edict_t ent) {
		gclient_t cl;
		int index;
		gitem_t it;
	
		cl= ent.client;
	
		if (null == cl.pers.weapon || null == cl.pers.lastweapon)
			return;
	
		index= GameUtil.ITEM_INDEX(cl.pers.lastweapon);
		if (0 == cl.pers.inventory[index])
			return;
		it= GameAI.itemlist[index];
		if (null == it.use)
			return;
		if (0 == (it.flags & Defines.IT_WEAPON))
			return;
		it.use.use(ent, it);
	}

	/*
	=================
	Cmd_InvDrop_f
	=================
	*/
	public static void InvDrop_f(edict_t ent) {
		gitem_t it;
	
		GameAI.ValidateSelectedItem(ent);
	
		if (ent.client.pers.selected_item == -1) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "No item to drop.\n");
			return;
		}
	
		it= GameAI.itemlist[ent.client.pers.selected_item];
		if (it.drop == null) {
			GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Item is not dropable.\n");
			return;
		}
		it.drop.drop(ent, it);
	}

	/*
	==================
	Cmd_Score_f
	
	Display the scoreboard
	==================
	*/
	public static void Score_f(edict_t ent) {
		ent.client.showinventory= false;
		ent.client.showhelp= false;
	
		if (0 == GameBase.deathmatch.value && 0 == GameBase.coop.value)
			return;
	
		if (ent.client.showscores) {
			ent.client.showscores= false;
			return;
		}
	
		ent.client.showscores= true;
		GameAI.DeathmatchScoreboard(ent);
	}

	/*
	==================
	Cmd_Help_f
	
	Display the current help message
	==================
	*/
	public static void Help_f(edict_t ent) {
		// this is for backwards compatability
		if (GameBase.deathmatch.value != 0) {
			Score_f(ent);
			return;
		}
	
		ent.client.showinventory= false;
		ent.client.showscores= false;
	
		if (ent.client.showhelp
			&& (ent.client.pers.game_helpchanged == GameBase.game.helpchanged)) {
			ent.client.showhelp= false;
			return;
		}
	
		ent.client.showhelp= true;
		ent.client.pers.helpchanged= 0;
		GameAI.HelpComputer(ent);
	}

	//=======================================================================
	
	/*
	=================
	Cmd_Kill_f
	=================
	*/
	public static void Kill_f(edict_t ent) {
		if ((GameBase.level.time - ent.client.respawn_time) < 5)
			return;
		ent.flags &= ~Defines.FL_GODMODE;
		ent.health= 0;
		GameBase.meansOfDeath= Defines.MOD_SUICIDE;
		GameAI.player_die(ent, ent, ent, 100000, GameBase.vec3_origin);
	}

	/*
	=================
	Cmd_PutAway_f
	=================
	*/
	public static void PutAway_f(edict_t ent) {
		ent.client.showscores= false;
		ent.client.showhelp= false;
		ent.client.showinventory= false;
	
	}

	/*
	=================
	Cmd_Players_f
	=================
	*/
	public static void Players_f(edict_t ent) {
		int i;
		int count;
		String small;
		String large;
	
		Integer index[]= new Integer[256];
	
		count= 0;
		for (i= 0; i < GameBase.maxclients.value; i++) {
			if (GameBase.game.clients[i].pers.connected) {
				index[count]= new Integer(i);
				count++;
			}
		}
	
		// sort by frags
		//qsort(index, count, sizeof(index[0]), PlayerSort);
		//replaced by:
		Arrays.sort(index, 0, count - 1, GameAI.PlayerSort);
	
		// print information
		large= "";
	
		for (i= 0; i < count; i++) {
			small=
				GameBase.game.clients[index[i].intValue()].ps.stats[Defines.STAT_FRAGS]
					+ " "
					+ GameBase.game.clients[index[i].intValue()].pers.netname
					+ "\n";
	
			if (GameAI.strlen(small) + GameAI.strlen(large) > 1024 - 100) {
				// can't print all of them in one packet
				large += "...\n";
				break;
			}
			large += small;
		}
	
		GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "" + large + "\n" + count + " players\n");
	}

	/*
	=================
	Cmd_Wave_f
	=================
	*/
	public static void Wave_f(edict_t ent) {
		int i;
	
		i= GameAI.atoi(GameBase.gi.argv(1));
	
		// can't wave when ducked
		if ((ent.client.ps.pmove.pm_flags & Defines.PMF_DUCKED) != 0)
			return;
	
		if (ent.client.anim_priority > Defines.ANIM_WAVE)
			return;
	
		ent.client.anim_priority= Defines.ANIM_WAVE;
	
		switch (i) {
			case 0 :
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "flipoff\n");
				ent.s.frame= M_Player.FRAME_flip01 - 1;
				ent.client.anim_end= M_Player.FRAME_flip12;
				break;
			case 1 :
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "salute\n");
				ent.s.frame= M_Player.FRAME_salute01 - 1;
				ent.client.anim_end= M_Player.FRAME_salute11;
				break;
			case 2 :
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "taunt\n");
				ent.s.frame= M_Player.FRAME_taunt01 - 1;
				ent.client.anim_end= M_Player.FRAME_taunt17;
				break;
			case 3 :
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "wave\n");
				ent.s.frame= M_Player.FRAME_wave01 - 1;
				ent.client.anim_end= M_Player.FRAME_wave11;
				break;
			case 4 :
			default :
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "point\n");
				ent.s.frame= M_Player.FRAME_point01 - 1;
				ent.client.anim_end= M_Player.FRAME_point12;
				break;
		}
	}

	/*
	==================
	Cmd_Say_f
	==================
	*/
	public static void Say_f(edict_t ent, boolean team, boolean arg0) {
		/*
		int i, j;
		edict_t other;
		char p;
		String text;
		gclient_t cl;
		
		if (gi.argc() < 2 && !arg0)
			return;
		
		if (0 == ((int) (dmflags.value) & (DF_MODELTEAMS | DF_SKINTEAMS)))
			team = false;
		
		if (team)
			text = "(" + ent.client.pers.netname + "): ";
		else
			text = "" + ent.client.pers.netname + ": ";
		
		if (arg0)
		{
			strcat(text, gi.argv(0));
			strcat(text, " ");
			strcat(text, gi.args());
		}
		else
		{
			p = gi.args();
			// *p ==
			if (p == '"')
			{
				p++;
				p[strlen(p) - 1] = 0;
			}
			strcat(text, p);
		}
		
		// don't let text be too long for malicious reasons
		if (strlen(text) > 150)
			text[150] = 0;
		
		strcat(text, "\n");
		
		if (flood_msgs.value != 0)
		{
			cl = ent.client;
		
			if (level.time < cl.flood_locktill)
			{
				gi.cprintf(
					ent,
					PRINT_HIGH,
					"You can't talk for " + (int) (cl.flood_locktill - level.time) + " more seconds\n");
				return;
			}
			i = (int) (cl.flood_whenhead - flood_msgs.value + 1);
			if (i < 0)
				i = (sizeof(cl.flood_when) / sizeof(cl.flood_when[0])) + i;
			if (cl.flood_when[i] != 0 && level.time - cl.flood_when[i] < flood_persecond.value)
			{
				cl.flood_locktill = level.time + flood_waitdelay.value;
				gi.cprintf(
					ent,
					PRINT_CHAT,
					"Flood protection:  You can't talk for " + (int) flood_waitdelay.value + " seconds.\n");
				return;
			}
			cl.flood_whenhead = (cl.flood_whenhead + 1) % (sizeof(cl.flood_when) / sizeof(cl.flood_when[0]));
			cl.flood_when[cl.flood_whenhead] = level.time;
		}
		
		if (dedicated.value!=0)
			gi.cprintf(null, PRINT_CHAT, "" + text + "" );
		
		for (j = 1; j <= game.maxclients; j++)
		{
			other = g_edicts[j];
			if (!other.inuse)
				continue;
			if (other.client==null)
				continue;
			if (team)
			{
				if (!OnSameTeam(ent, other))
					continue;
			}
			gi.cprintf(other, PRINT_CHAT, "" + text+"" );
		}
		*/
	}

	/**
	 * Returns the playerlist. 
	 * TODO: The list is badly formatted at the moment, RST. 
	 */
	public static void PlayerList_f(edict_t ent) {
		int i;
		String st;
		String text;
		edict_t e2;
	
		// connect time, ping, score, name
		text= "";
	
		for (i= 0; i < GameBase.maxclients.value; i++) {
			e2= GameBase.g_edicts[1 + i];
			if (!e2.inuse)
				continue;
	
			st=
				""
					+ (GameBase.level.framenum - e2.client.resp.enterframe) / 600
					+ ":"
					+ ((GameBase.level.framenum - e2.client.resp.enterframe) % 600) / 10
					+ " "
					+ e2.client.ping
					+ " "
					+ e2.client.resp.score
					+ " "
					+ e2.client.pers.netname
					+ " "
					+ (e2.client.resp.spectator ? " (spectator)" : "")
					+ "\n";
	
			if (GameAI.strlen(text) + GameAI.strlen(st) > 1024 - 50) {
				text += "And more...\n";
				GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "" + text + "");
				return;
			}
			text += st;
	
		}
		GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, text);
	}
}
