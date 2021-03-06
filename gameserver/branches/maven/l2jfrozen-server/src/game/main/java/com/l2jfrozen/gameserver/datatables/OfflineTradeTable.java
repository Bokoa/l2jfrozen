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
package com.l2jfrozen.gameserver.datatables;

/**
 * @author Shyla
 */

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;

import com.l2jfrozen.common.CommonConfig;
import com.l2jfrozen.common.logs.Log;
import com.l2jfrozen.common.util.CloseUtil;
import com.l2jfrozen.common.util.database.L2DatabaseFactory;
import com.l2jfrozen.gameserver.LoginServerThread;
import com.l2jfrozen.gameserver.config.Config;
import com.l2jfrozen.gameserver.model.L2Character;
import com.l2jfrozen.gameserver.model.L2ManufactureItem;
import com.l2jfrozen.gameserver.model.L2ManufactureList;
import com.l2jfrozen.gameserver.model.L2World;
import com.l2jfrozen.gameserver.model.TradeList.TradeItem;
import com.l2jfrozen.gameserver.model.actor.instance.L2PcInstance;
import com.l2jfrozen.gameserver.network.GameClientState;
import com.l2jfrozen.gameserver.network.L2GameClient;
import com.l2jfrozen.netcore.MMOClientsManager;

public class OfflineTradeTable
{
	private static Logger LOGGER = Logger.getLogger(OfflineTradeTable.class);
	
	// SQL DEFINITIONS
	private static final String SAVE_OFFLINE_STATUS = "INSERT INTO character_offline_trade (`charId`,`time`,`type`,`title`) VALUES (?,?,?,?)";
	private static final String SAVE_ITEMS = "INSERT INTO character_offline_trade_items (`charId`,`item`,`count`,`price`,`enchant`) VALUES (?,?,?,?,?)";
	private static final String DELETE_OFFLINE_TABLE_ALL_ITEMS = "delete from character_offline_trade_items where charId=?";
	private static final String DELETE_OFFLINE_TRADER = "DELETE FROM character_offline_trade where charId=?";
	private static final String CLEAR_OFFLINE_TABLE = "DELETE FROM character_offline_trade";
	private static final String CLEAR_OFFLINE_TABLE_ITEMS = "DELETE FROM character_offline_trade_items";
	private static final String LOAD_OFFLINE_STATUS = "SELECT * FROM character_offline_trade";
	private static final String LOAD_OFFLINE_ITEMS = "SELECT * FROM character_offline_trade_items WHERE charId = ?";
	
	public static class OfflineTraderInfo
	{
		
		private final Integer charId;
		private final long time;
		private final int type;
		private final String title;
		private final List<OfflineTraderItem> items = new ArrayList<>();
		
		/**
		 * @param charId
		 * @param time
		 * @param i
		 * @param title
		 */
		public OfflineTraderInfo(final Integer charId, final long time, final int i, final String title)
		{
			super();
			this.charId = charId;
			this.time = time;
			this.type = i;
			this.title = title;
		}
		
		/**
		 * @return the charId
		 */
		public Integer getCharId()
		{
			return charId;
		}
		
		/**
		 * @return the time
		 */
		public long getTime()
		{
			return time;
		}
		
		/**
		 * @return the type
		 */
		public int getType()
		{
			return type;
		}
		
		/**
		 * @return the title
		 */
		public String getTitle()
		{
			return title;
		}
		
		/**
		 * @return the items
		 */
		public List<OfflineTraderItem> getItems()
		{
			return items;
		}
		
	}
	
	public static class OfflineTraderItem
	{
		
		private final Integer charId;
		private final Integer item;
		private final Integer count;
		private final Integer price;
		private final Integer enchant;
		
		/**
		 * @param charId
		 * @param item
		 * @param count
		 * @param price
		 * @param enchant
		 */
		public OfflineTraderItem(final Integer charId, final Integer item, final Integer count, final Integer price, final Integer enchant)
		{
			super();
			this.charId = charId;
			this.item = item;
			this.count = count;
			this.price = price;
			this.enchant = enchant;
		}
		
		/**
		 * @return the charId
		 */
		public Integer getCharId()
		{
			return charId;
		}
		
		/**
		 * @return the item
		 */
		public Integer getItem()
		{
			return item;
		}
		
		/**
		 * @return the count
		 */
		public Integer getCount()
		{
			return count;
		}
		
		/**
		 * @return the price
		 */
		public Integer getPrice()
		{
			return price;
		}
		
		/**
		 * @return the enchant
		 */
		public Integer getEnchant()
		{
			return enchant;
		}
		
	}
	
	// called when server will go off, different from storeOffliner because
	// of store of normal sellers/buyers also if not in offline mode
	public static void storeOffliners()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(false);
			PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE);
			stm.execute();
			stm.close();
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS);
			stm.execute();
			stm.close();
			
			con.setAutoCommit(false); // avoid halfway done
			stm = con.prepareStatement(SAVE_OFFLINE_STATUS);
			final PreparedStatement stm_items = con.prepareStatement(SAVE_ITEMS);
			
			for (final L2PcInstance pc : L2World.getInstance().getAllPlayers())
			{
				try
				{
					// without second check, server will store all guys that are in shop mode
					if ((pc.getPrivateStoreType() != L2PcInstance.STORE_PRIVATE_NONE)/* && (pc.isOffline()) */)
					{
						stm.setInt(1, pc.getObjectId()); // Char Id
						stm.setLong(2, pc.getOfflineStartTime());
						stm.setInt(3, pc.getPrivateStoreType()); // store type
						String title = null;
						
						switch (pc.getPrivateStoreType())
						{
							case L2PcInstance.STORE_PRIVATE_BUY:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getBuyList().getTitle();
								for (final TradeItem i : pc.getBuyList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getItem().getItemId());
									stm_items.setLong(3, i.getCount());
									stm_items.setLong(4, i.getPrice());
									stm_items.setLong(5, i.getEnchant());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case L2PcInstance.STORE_PRIVATE_SELL:
							case L2PcInstance.STORE_PRIVATE_PACKAGE_SELL:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getSellList().getTitle();
								pc.getSellList().updateItems();
								for (final TradeItem i : pc.getSellList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getObjectId());
									stm_items.setLong(3, i.getCount());
									stm_items.setLong(4, i.getPrice());
									stm_items.setLong(5, i.getEnchant());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case L2PcInstance.STORE_PRIVATE_MANUFACTURE:
								
								if (!Config.OFFLINE_CRAFT_ENABLE)
									continue;
								
								if (pc.getCreateList() == null)
									continue;
								
								if (pc.getCreateList() != null)
								{
									title = pc.getCreateList().getStoreName();
								}
								else
								{
									title = "";
								}
								
								for (final L2ManufactureItem i : pc.getCreateList().getList())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getRecipeId());
									stm_items.setLong(3, 0);
									stm_items.setLong(4, i.getCost());
									stm_items.setLong(5, 0);
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							default:
								// LOGGER.info( "OfflineTradersTable[storeTradeItems()]: Error while saving offline trader: " + pc.getObjectId() + ", store type: "+pc.getPrivateStoreType());
								// no save for this kind of shop
								continue;
						}
						stm.setString(4, title);
						stm.executeUpdate();
						stm.clearParameters();
						con.commit(); // flush
					}
				}
				catch (final Exception e)
				{
					if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
						e.printStackTrace();
					
					LOGGER.warn("OfflineTradersTable[storeTradeItems()]: Error while saving offline trader: " + pc.getObjectId() + " " + e, e);
				}
			}
			stm.close();
			stm_items.close();
			LOGGER.info("Offline traders stored.");
		}
		catch (final Exception e)
		{
			if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
				e.printStackTrace();
			
			LOGGER.warn("OfflineTradersTable[storeTradeItems()]: Error while saving offline traders: " + e, e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}
	
	public static void restoreOfflineTraders()
	{
		LOGGER.info("Loading offline traders...");
		
		final long LoadStart = System.currentTimeMillis();
		
		Connection con = null;
		
		int nTraders = 0;
		
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(false);
			
			final List<OfflineTraderInfo> offline_traders = new ArrayList<>();
			
			PreparedStatement stm = null;
			ResultSet rs = null;
			
			try
			{
				stm = con.prepareStatement(LOAD_OFFLINE_STATUS);
				rs = stm.executeQuery();
				while (rs.next())
				{
					
					final long time = rs.getLong("time");
					if (Config.OFFLINE_MAX_DAYS > 0)
					{
						final Calendar cal = Calendar.getInstance();
						cal.setTimeInMillis(time);
						cal.add(Calendar.DAY_OF_YEAR, Config.OFFLINE_MAX_DAYS);
						if (cal.getTimeInMillis() <= System.currentTimeMillis())
						{
							LOGGER.info("Offline trader with id " + rs.getInt("charId") + " reached OfflineMaxDays, kicked.");
							continue;
						}
					}
					
					final int type = rs.getInt("type");
					if (type == L2PcInstance.STORE_PRIVATE_NONE)
						continue;
					
					final OfflineTraderInfo trader = new OfflineTraderInfo(rs.getInt("charId"), time, type, rs.getString("title"));
					offline_traders.add(trader);
					
				}
				
			}
			catch (final Exception e)
			{
				if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
					e.printStackTrace();
				
				LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error loading trader: ", e);
				
			}
			finally
			{
				
				try
				{
					if (rs != null)
						rs.close();
					if (stm != null)
						stm.close();
				}
				catch (final Exception e)
				{
					if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
						e.printStackTrace();
					
					LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error closing db connection: ", e);
					
				}
				
			}
			
			for (final OfflineTraderInfo trader : offline_traders)
			{
				
				PreparedStatement stm_items = null;
				ResultSet items = null;
				
				try
				{
					
					stm_items = con.prepareStatement(LOAD_OFFLINE_ITEMS);
					stm_items.setInt(1, trader.getCharId());
					items = stm_items.executeQuery();
					
					while (items.next())
					{
						final OfflineTraderItem item = new OfflineTraderItem(trader.getCharId(), items.getInt(2), items.getInt(3), items.getInt(4), items.getInt(5));
						trader.getItems().add(item);
					}
					
				}
				catch (final Exception e)
				{
					if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
						e.printStackTrace();
					
					LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error loading trader items: ", e);
					
				}
				finally
				{
					
					try
					{
						if (items != null)
							items.close();
						if (stm_items != null)
							stm_items.close();
					}
					catch (final Exception e)
					{
						if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
							e.printStackTrace();
						
						LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error closing db connection: ", e);
						
					}
					
				}
				
			}
			
			for (final OfflineTraderInfo trader : offline_traders)
			{
				L2PcInstance player = null;
				
				if (nTraders != 0 && nTraders % 200 == 0)
					LOGGER.info("Loaded " + nTraders + " offline trade players.");
				
				try
				{
					final L2GameClient client = new L2GameClient(null);
					player = L2PcInstance.load(trader.getCharId(), true);
					client.setActiveChar(player);
					client.setAccountName(player.getAccountName());
					client.setState(GameClientState.IN_GAME);
					client.setDetached(true);
					player.setClient(client);
					player.setOfflineMode(true);
					player.setOnlineStatus(false);
					player.setOfflineStartTime(trader.getTime());
					if (Config.OFFLINE_SLEEP_EFFECT)
						player.startAbnormalEffect(L2Character.ABNORMAL_EFFECT_SLEEP);
					player.spawnMe(player.getX(), player.getY(), player.getZ());
					LoginServerThread.getInstance().addGameServerLogin(player.getAccountName(), client);
					
					if (player.getClient() != null)
						MMOClientsManager.getInstance().removeClient(player.getClient().getIdentifier());
					
					for (final OfflineTraderItem item : trader.getItems())
					{
						
						switch (trader.getType())
						{
							case L2PcInstance.STORE_PRIVATE_BUY:
								
								player.getBuyList().addItemByItemId(item.getItem(), item.getCount(), item.getPrice(), item.getEnchant());
								player.getBuyList().setTitle(trader.getTitle());
								break;
							case L2PcInstance.STORE_PRIVATE_SELL:
							case L2PcInstance.STORE_PRIVATE_PACKAGE_SELL:
								player.getSellList().addItem(item.getItem(), item.getCount(), item.getPrice());
								player.getSellList().setTitle(trader.getTitle());
								player.getSellList().setPackaged(trader.getType() == L2PcInstance.STORE_PRIVATE_PACKAGE_SELL);
								break;
							case L2PcInstance.STORE_PRIVATE_MANUFACTURE:
								final L2ManufactureList createList = new L2ManufactureList();
								createList.add(new L2ManufactureItem(item.getItem(), item.getPrice()));
								player.setCreateList(createList);
								player.getCreateList().setStoreName(trader.getTitle());
								break;
							default:
								LOGGER.info("Offline trader " + player.getName() + " finished to sell his items");
						}
						
					}
					
					player.sitDown();
					if (Config.OFFLINE_SET_NAME_COLOR)
					{
						player._originalNameColorOffline = player.getAppearance().getNameColor();
						player.getAppearance().setNameColor(Config.OFFLINE_NAME_COLOR);
					}
					
					player.setPrivateStoreType(trader.getType());
					player.setOnlineStatus(true);
					player.restoreEffects();
					player.broadcastUserInfo();
					nTraders++;
				}
				catch (final Exception e)
				{
					if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
						e.printStackTrace();
					
					LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error loading trader: ", e);
					if (player != null)
						player.logout();
				}
				
			}
			
			LOGGER.info("Loaded: " + nTraders + " offline trader(s) in " + (System.currentTimeMillis() - LoadStart) / 1000 + " seconds");
			
		}
		catch (final Exception e)
		{
			if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
				e.printStackTrace();
			
			LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error while loading offline traders: ", e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}
	
	public static void storeOffliner(final L2PcInstance pc)
	{
		if ((pc.getPrivateStoreType() == L2PcInstance.STORE_PRIVATE_NONE) || (!pc.isInOfflineMode()))
			return;
		
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection(false);
			PreparedStatement stm = con.prepareStatement(DELETE_OFFLINE_TABLE_ALL_ITEMS);
			stm.setInt(1, pc.getObjectId());
			stm.execute();
			stm.clearParameters();
			stm.close();
			stm = con.prepareStatement(DELETE_OFFLINE_TRADER);
			stm.setInt(1, pc.getObjectId());
			stm.execute();
			stm.clearParameters();
			stm.close();
			
			con.setAutoCommit(false); // avoid halfway done
			stm = con.prepareStatement(SAVE_OFFLINE_STATUS);
			final PreparedStatement stm_items = con.prepareStatement(SAVE_ITEMS);
			
			boolean save = true;
			
			try
			{
				
				stm.setInt(1, pc.getObjectId()); // Char Id
				stm.setLong(2, pc.getOfflineStartTime());
				stm.setInt(3, pc.getPrivateStoreType()); // store type
				String title = null;
				
				switch (pc.getPrivateStoreType())
				{
					case L2PcInstance.STORE_PRIVATE_BUY:
						if (!Config.OFFLINE_TRADE_ENABLE)
							break;
						title = pc.getBuyList().getTitle();
						for (final TradeItem i : pc.getBuyList().getItems())
						{
							stm_items.setInt(1, pc.getObjectId());
							stm_items.setInt(2, i.getItem().getItemId());
							stm_items.setLong(3, i.getCount());
							stm_items.setLong(4, i.getPrice());
							stm_items.setLong(5, i.getEnchant());
							stm_items.executeUpdate();
							stm_items.clearParameters();
						}
						break;
					case L2PcInstance.STORE_PRIVATE_SELL:
					case L2PcInstance.STORE_PRIVATE_PACKAGE_SELL:
						if (!Config.OFFLINE_TRADE_ENABLE)
							break;
						title = pc.getSellList().getTitle();
						pc.getSellList().updateItems();
						for (final TradeItem i : pc.getSellList().getItems())
						{
							stm_items.setInt(1, pc.getObjectId());
							stm_items.setInt(2, i.getObjectId());
							stm_items.setLong(3, i.getCount());
							stm_items.setLong(4, i.getPrice());
							stm_items.setLong(5, i.getEnchant());
							stm_items.executeUpdate();
							stm_items.clearParameters();
						}
						break;
					case L2PcInstance.STORE_PRIVATE_MANUFACTURE:
						
						if (!Config.OFFLINE_CRAFT_ENABLE)
							break;
						title = pc.getCreateList().getStoreName();
						for (final L2ManufactureItem i : pc.getCreateList().getList())
						{
							stm_items.setInt(1, pc.getObjectId());
							stm_items.setInt(2, i.getRecipeId());
							stm_items.setLong(3, 0);
							stm_items.setLong(4, i.getCost());
							stm_items.setLong(5, 0);
							stm_items.executeUpdate();
							stm_items.clearParameters();
						}
						break;
					default:
						// LOGGER.info( "OfflineTradersTable[storeOffliner()]: Error while saving offline trader: " + pc.getObjectId() + ", store type: "+pc.getPrivateStoreType());
						// no save for this kind of shop
						save = false;
				}
				
				if (save)
				{
					stm.setString(4, title);
					stm.executeUpdate();
					stm.clearParameters();
					con.commit(); // flush
				}
			}
			catch (final Exception e)
			{
				if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
					e.printStackTrace();
				
				LOGGER.warn("OfflineTradersTable[storeOffliner()]: Error while saving offline trader: " + pc.getObjectId() + " " + e, e);
			}
			
			stm.close();
			stm_items.close();
			final String text = "Offline trader " + pc.getName() + " stored.";
			Log.add(text, "Offline_trader");
		}
		catch (final Exception e)
		{
			if (CommonConfig.ENABLE_ALL_EXCEPTIONS)
				e.printStackTrace();
			
			LOGGER.warn("OfflineTradersTable[storeOffliner()]: Error while saving offline traders: " + e, e);
		}
		finally
		{
			CloseUtil.close(con);
		}
	}
	
}
