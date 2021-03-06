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
package com.l2jfrozen.loginserver.network.serverpackets;

import a.a.aa;

/**
 *
 */
public final class PlayOk extends L2LoginServerPacket
{
	private final int _playOk1, _playOk2;
	
	public PlayOk(final aa sessionKey)
	{
		_playOk1 = sessionKey.a;
		_playOk2 = sessionKey.b;
	}
	
	@Override
	public void write()
	{
		C(0x07);
		D(_playOk1);
		D(_playOk2);
	}
}
