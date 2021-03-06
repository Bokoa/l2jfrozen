/*
 * L2jFrozen Project - www.l2jfrozen.com 
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package com.l2jfrozen.gameserver.network.serverpackets;

import com.l2jfrozen.gameserver.model.L2Party;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;

/**
 * This class ...
 * @version $Revision: 1.4.2.1.2.5 $ $Date: 2005/03/27 15:29:57 $
 */
public final class PartySmallWindowAdd extends L2GameServerPacket
{
	private static final String _S__64_PARTYSMALLWINDOWADD = "[S] 4f PartySmallWindowAdd";
	
	private final L2PcInstance _member;
	private final int _leaderId;
	private final int _distribution;
	
	public PartySmallWindowAdd(final L2PcInstance member, final L2Party party)
	{
		_member = member;
		_leaderId = party.getPartyLeaderOID();
		_distribution = party.getLootDistribution();
	}
	
	@Override
	protected final void writeImpl()
	{
		C(0x4f);
		D(_leaderId); // c3
		D(_distribution); // c3
		D(_member.getObjectId());
		S(_member.getName());
		
		D((int) _member.getCurrentCp()); // c4
		D(_member.getMaxCp()); // c4
		
		D((int) _member.getCurrentHp());
		D(_member.getMaxHp());
		D((int) _member.getCurrentMp());
		D(_member.getMaxMp());
		D(_member.getLevel());
		D(_member.getClassId().getId());
		D(0);// D(0x01); ??
		D(0);
	}
	
	/*
	 * (non-Javadoc)
	 * @see com.l2jfrozen.gameserver.serverpackets.ServerBasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _S__64_PARTYSMALLWINDOWADD;
	}
}
