/*
 * Copyright (C) 2004-2016 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfrozen.loginserver.network.gameserverpackets;

import java.util.logging.Logger;

import com.l2jfrozen.loginserver.GameServerThread;
import com.l2jfrozen.loginserver.LoginController;
import a.a.w;

/**
 * @author -Wooden-
 */
public class ChangeAccessLevel extends w
{
	protected static Logger _log = Logger.getLogger(ChangeAccessLevel.class.getName());
	
	/**
	 * @param decrypt
	 * @param server
	 */
	public ChangeAccessLevel(final byte[] decrypt, final GameServerThread server)
	{
		super(decrypt);
		final int level = D();
		final String account = S();
		
		LoginController.getInstance().setAccountAccessLevel(account, level);
		_log.info("Changed " + account + " access level to " + level);
	}
}
