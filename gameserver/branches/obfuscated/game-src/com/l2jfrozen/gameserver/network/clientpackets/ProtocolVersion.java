/*
 * L2jFrozen Project - www.l2jfrozen.com 
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jfrozen.gameserver.network.clientpackets;

import org.apache.log4j.Logger;

import com.l2jfrozen.common.CommonConfig;
import com.l2jfrozen.gameserver.config.Config;
import com.l2jfrozen.gameserver.network.serverpackets.KeyPacket;
import com.l2jfrozen.gameserver.network.serverpackets.L2GameServerPacket;

public final class ProtocolVersion extends L2GameClientPacket
{
	static Logger LOGGER = Logger.getLogger(ProtocolVersion.class);
	private int _version;
	
	@Override
	protected void readImpl()
	{
		_version = H(this.getClass().getSimpleName());
	}
	
	@Override
	protected void runImpl()
	{
		// this packet is never encrypted
		if (_version == -1) // Possible bot/ddos ignore it
		{
			if (Config.DEVELOPER)
				LOGGER.info("Client: " + g().toString() + " -> Protocol Revision: " + _version + " is invalid. Possible bot or ddos. Ignore it.");
			
			g().close((L2GameServerPacket) null);
		}
		else if (_version == 65534 || _version == -2) // Ping
		{
			if (CommonConfig.DEBUG)
				LOGGER.info("DEBUG " + getType() + ": Ping received");
			
			g().close((L2GameServerPacket) null);
		}
		else if (_version < Config.MIN_PROTOCOL_REVISION || _version > Config.MAX_PROTOCOL_REVISION)
		{
			LOGGER.info("Client: " + g().toString() + " -> Protocol Revision: " + _version + " is invalid. Minimum is " + Config.MIN_PROTOCOL_REVISION + " and Maximum is " + Config.MAX_PROTOCOL_REVISION + " are supported. Closing connection.");
			LOGGER.warn("Wrong Protocol Version " + _version);
			g().close((L2GameServerPacket) null);
		}
		else
		{
			if (CommonConfig.DEBUG)
				LOGGER.debug(getType() + ": Client Protocol Revision is ok: " + _version);
			
			final KeyPacket pk = new KeyPacket(g().enableCrypt());
			g().sendPacket(pk);
		}
	}
	
	@Override
	public String getType()
	{
		return "[C] 00 ProtocolVersion";
	}
}