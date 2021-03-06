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
package com.l2jfrozen.gameserver.network.serverpackets;

import java.util.Calendar;

import org.apache.log4j.Logger;

import com.l2jfrozen.gameserver.datatables.sql.ClanTable;
import com.l2jfrozen.gameserver.model.L2Clan;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.model.entity.siege.Fort;

/**
 * Shows the Siege Info<BR>
 * <BR>
 * packet type id 0xc9<BR>
 * format: cdddSSdSdd<BR>
 * <BR>
 * c = c9<BR>
 * d = FortID<BR>
 * d = Show Owner Controls (0x00 default || >=0x02(mask?) owner)<BR>
 * d = Owner ClanID<BR>
 * S = Owner ClanName<BR>
 * S = Owner Clan LeaderName<BR>
 * d = Owner AllyID<BR>
 * S = Owner AllyName<BR>
 * d = current time (seconds)<BR>
 * d = Siege time (seconds) (0 for selectable)<BR>
 * d = (UNKNOW) Siege Time Select Related?
 * @author programmos
 */
public class FortressSiegeInfo extends L2GameServerPacket
{
	private static final String _S__C9_SIEGEINFO = "[S] c9 SiegeInfo";
	private static Logger LOGGER = Logger.getLogger(FortressSiegeInfo.class);
	private final Fort _fort;
	
	public FortressSiegeInfo(final Fort fort)
	{
		_fort = fort;
	}
	
	@Override
	protected final void writeImpl()
	{
		final L2PcInstance activeChar = g().getActiveChar();
		if (activeChar == null)
			return;
		
		C(0xc9);
		D(_fort.getFortId());
		D(_fort.getOwnerId() == activeChar.getClanId() && activeChar.isClanLeader() ? 0x01 : 0x00);
		D(_fort.getOwnerId());
		if (_fort.getOwnerId() > 0)
		{
			final L2Clan owner = ClanTable.getInstance().getClan(_fort.getOwnerId());
			if (owner != null)
			{
				S(owner.getName()); // Clan Name
				S(owner.getLeaderName()); // Clan Leader Name
				D(owner.getAllyId()); // Ally ID
				S(owner.getAllyName()); // Ally Name
			}
			else
			{
				LOGGER.warn("Null owner for fort: " + _fort.getName());
			}
		}
		else
		{
			S("NPC"); // Clan Name
			S(""); // Clan Leader Name
			D(0); // Ally ID
			S(""); // Ally Name
		}
		
		D((int) (Calendar.getInstance().getTimeInMillis() / 1000));
		D((int) (_fort.getSiege().getSiegeDate().getTimeInMillis() / 1000));
		D(0x00); // number of choices?
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.l2jfrozen.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__C9_SIEGEINFO;
	}
	
}
