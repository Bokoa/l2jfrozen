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
package com.l2jfrozen.gameserver.network.clientpackets;

import org.apache.log4j.Logger;

import com.l2jfrozen.common.CommonConfig;
import com.l2jfrozen.gameserver.config.Config;
import com.l2jfrozen.gameserver.model.ItemRequest;
import com.l2jfrozen.gameserver.model.L2Object;
import com.l2jfrozen.gameserver.model.L2World;
import com.l2jfrozen.gameserver.model.TradeList;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.network.serverpackets.ActionFailed;
import com.l2jfrozen.gameserver.network.serverpackets.SystemMessage;
import com.l2jfrozen.gameserver.util.Util;

public final class RequestPrivateStoreSell extends L2GameClientPacket
{
	private static Logger LOGGER = Logger.getLogger(RequestPrivateStoreSell.class);
	
	private int _storePlayerId;
	private int _count;
	private int _price;
	private ItemRequest[] _items;
	
	@Override
	protected void readImpl()
	{
		_storePlayerId = D();
		_count = D();
		// count*20 is the size of a for iteration of each item
		if (_count < 0 || _count * 20 > _b.remaining() || _count > Config.MAX_ITEM_IN_PACKET)
		{
			_count = 0;
		}
		_items = new ItemRequest[_count];
		
		long priceTotal = 0;
		for (int i = 0; i < _count; i++)
		{
			final int objectId = D();
			final int itemId = D();
			final int enchant = H(this.getClass().getSimpleName());
			H(this.getClass().getSimpleName()); // TODO analyse this
			final long count = D();
			final int price = D();
			
			if (count > Integer.MAX_VALUE || count < 0)
			{
				final String msgErr = "[RequestPrivateStoreSell] player " + g().getActiveChar().getName() + " tried an overflow exploit, ban this player!";
				Util.handleIllegalPlayerAction(g().getActiveChar(), msgErr, Config.DEFAULT_PUNISH);
				_count = 0;
				_items = null;
				return;
			}
			_items[i] = new ItemRequest(objectId, itemId, enchant, (int) count, price);
			priceTotal += price * count;
		}
		
		if (priceTotal < 0 || priceTotal > Integer.MAX_VALUE)
		{
			final String msgErr = "[RequestPrivateStoreSell] player " + g().getActiveChar().getName() + " tried an overflow exploit, ban this player!";
			Util.handleIllegalPlayerAction(g().getActiveChar(), msgErr, Config.DEFAULT_PUNISH);
			_count = 0;
			_items = null;
			return;
		}
		
		if (CommonConfig.DEBUG)
		{
			
			LOGGER.info("Player " + g().getActiveChar().getName() + " requested to sell to storeId " + _storePlayerId + " Items Number: " + _count);
			
			for (int i = 0; i < _count; i++)
			{
				LOGGER.info("Requested Item ObjectID: " + _items[i].getObjectId());
				LOGGER.info("Requested Item Id: " + _items[i].getItemId());
				LOGGER.info("Requested Item count: " + _items[i].getCount());
				LOGGER.info("Requested Item enchant: " + _items[i].getCount());
				LOGGER.info("Requested Item price: " + _items[i].getPrice());
				
			}
		}
		
		_price = (int) priceTotal;
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance player = g().getActiveChar();
		if (player == null)
			return;
		
		if (!g().getFloodProtectors().getTransaction().tryPerformAction("privatestoresell"))
		{
			player.sendMessage("You selling items too fast");
			return;
		}
		
		final L2Object object = L2World.getInstance().findObject(_storePlayerId);
		if (object == null || !(object instanceof L2PcInstance))
			return;
		
		final L2PcInstance storePlayer = (L2PcInstance) object;
		if (storePlayer.getPrivateStoreType() != L2PcInstance.STORE_PRIVATE_BUY)
			return;
		
		final TradeList storeList = storePlayer.getBuyList();
		if (storeList == null)
			return;
		
		// Check if player didn't choose any items
		if (_items == null || _items.length == 0)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (storeList.getItemCount() == 0)
		{
			storePlayer.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_NONE);
			storePlayer.broadcastUserInfo();
			return;
		}
		
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendMessage("Unsufficient privileges.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (Config.SELL_BY_ITEM)
		{
			if (storePlayer.getItemCount(Config.SELL_ITEM, -1) < _price)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				sendPacket(SystemMessage.sendString("You have not enough items to buy, canceling PrivateBuy"));
				storePlayer.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_NONE);
				storePlayer.broadcastUserInfo();
				return;
			}
			
		}
		else
		{
			if (storePlayer.getAdena() < _price)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				storePlayer.sendMessage("You have not enough adena, canceling PrivateBuy.");
				storePlayer.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_NONE);
				storePlayer.broadcastUserInfo();
				return;
			}
		}
		
		if (!storeList.PrivateStoreSell(player, _items, _price))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			Util.handleIllegalPlayerAction(g().getActiveChar(), "Player " + g().getActiveChar().getName() + " provided invalid list or request! ", Config.DEFAULT_PUNISH);
			LOGGER.warn("PrivateStore sell has failed due to invalid list or request. Player: " + player.getName() + ", Private store of: " + storePlayer.getName());
			return;
		}
		
		if (storeList.getItemCount() == 0)
		{
			storePlayer.setPrivateStoreType(L2PcInstance.STORE_PRIVATE_NONE);
			storePlayer.broadcastUserInfo();
		}
		
	}
	
	@Override
	public String getType()
	{
		return "[C] 96 RequestPrivateStoreSell";
	}
}