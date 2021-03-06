/*
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
package com.l2jfrozen.loginserver.util;

import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

import com.l2jfrozen.loginserver.LoginController;
import com.l2jfrozen.loginserver.network.L2LoginClient;
import com.l2jfrozen.loginserver.network.serverpackets.LoginFailReason;

import com.l2jfrozen.netcore.NetcoreConfig;

import a.a.A;
import a.a.p;

/**
 * @author Shyla
 */
public class LoginServerFloodProtectorActions implements p
{
	
	private final Logger LOGGER = Logger.getLogger(LoginServerFloodProtectorActions.class);
	private final int MAX_CONCURRENT_ACTIONS_PER_PLAYER = 10;
	private final Hashtable<String, AtomicInteger> clients_concurrent_actions = new Hashtable<>();
	private final Hashtable<String, Hashtable<Integer, AtomicInteger>> clients_actions = new Hashtable<>();
	private final Hashtable<String, Hashtable<Integer, Integer>> clients_nextGameTick = new Hashtable<>();
	private final Hashtable<String, Boolean> punishes_in_progress = new Hashtable<>();
	
	/**
	 * Checks whether the request is flood protected or not.
	 * @param opcode
	 * @param opcode2
	 * @param client
	 * @return true if action is allowed, otherwise false
	 */
	@Override
	public boolean t(final int opcode, final int opcode2, final A<?> client)
	{
		
		String account = null;
		
		final L2LoginClient login_cl = (L2LoginClient) client;
		account = login_cl.getAccount();
		
		if (account == null)
			return true;
		
		// get actual concurrent actions number for account
		AtomicInteger actions_per_account = clients_concurrent_actions.get(account);
		if (actions_per_account == null)
		{
			actions_per_account = new AtomicInteger(0);
		}
		if (actions_per_account.get() < MAX_CONCURRENT_ACTIONS_PER_PLAYER)
		{
			final int actions = actions_per_account.incrementAndGet();
			
			if (NetcoreConfig.getInstance().ENABLE_MMOCORE_DEBUG)
			{
				LOGGER.info(" -- account " + account + " has performed " + actions + " concurrent actions until now");
			}
			
			clients_concurrent_actions.put(account, actions_per_account);
		}
		else
			return false;
		
		// get time in seconds
		final int curTick = (int) (System.currentTimeMillis() / 1000);
		// final int curTick = GameTimeController.getGameTicks();
		
		Hashtable<Integer, Integer> account_nextGameTicks = clients_nextGameTick.get(account);
		if (account_nextGameTicks == null)
		{
			account_nextGameTicks = new Hashtable<>();
		}
		Integer _nextGameTick = account_nextGameTicks.get(opcode);
		if (_nextGameTick == null)
		{
			_nextGameTick = curTick;
			account_nextGameTicks.put(opcode, _nextGameTick);
		}
		clients_nextGameTick.put(account, account_nextGameTicks);
		
		Boolean _punishmentInProgress = punishes_in_progress.get(account);
		if (_punishmentInProgress == null)
		{
			_punishmentInProgress = false;
		}
		else if (_punishmentInProgress)
		{
			final AtomicInteger actions = clients_concurrent_actions.get(account);
			actions.decrementAndGet();
			clients_concurrent_actions.put(account, actions);
			return false;
		}
		punishes_in_progress.put(account, _punishmentInProgress);
		
		Hashtable<Integer, AtomicInteger> received_commands_actions = clients_actions.get(account);
		if (received_commands_actions == null)
		{
			received_commands_actions = new Hashtable<>();
		}
		AtomicInteger command_count = null;
		if ((command_count = received_commands_actions.get(opcode)) == null)
		{
			command_count = new AtomicInteger(0);
			received_commands_actions.put(opcode, command_count);
		}
		clients_actions.put(account, received_commands_actions);
		
		if (curTick <= _nextGameTick && !_punishmentInProgress) // time to check operations
		{
			command_count.incrementAndGet();
			clients_actions.get(account).put(opcode, command_count);
			
			if (NetcoreConfig.getInstance().ENABLE_MMOCORE_DEBUG)
			{
				LOGGER.info("-- called OpCode " + Integer.toHexString(opcode) + " ~" + String.valueOf((NetcoreConfig.getInstance().FLOOD_PACKET_PROTECTION_INTERVAL - (_nextGameTick - curTick)) * 1000) + " ms after first command...");
				LOGGER.info("   total received packets with OpCode " + Integer.toHexString(opcode) + " into the Interval: " + command_count.get());
			}
			
			if (NetcoreConfig.getInstance().PACKET_FLOODING_PUNISHMENT_LIMIT > 0 && command_count.get() >= NetcoreConfig.getInstance().PACKET_FLOODING_PUNISHMENT_LIMIT && NetcoreConfig.getInstance().PACKET_FLOODING_PUNISHMENT_TYPE != null)
			{
				punishes_in_progress.put(account, true);
				
				if (!i(opcode, opcode2))
				{
					if (NetcoreConfig.getInstance().LOG_PACKET_FLOODING)
						LOGGER.warn("ATTENTION: Account " + account + " is flooding the server...");
					
					if ("kick".equals(NetcoreConfig.getInstance().PACKET_FLOODING_PUNISHMENT_TYPE))
					{
						if (NetcoreConfig.getInstance().LOG_PACKET_FLOODING)
							LOGGER.warn(" ------- kicking account " + account);
						kickPlayer(client, opcode);
					}
					else if ("ban".equals(NetcoreConfig.getInstance().PACKET_FLOODING_PUNISHMENT_TYPE))
					{
						if (NetcoreConfig.getInstance().LOG_PACKET_FLOODING)
							LOGGER.warn(" ------- banning account " + account);
						banAccount(client, opcode);
					}
				}
				// clear already punished account
				punishes_in_progress.remove(account);
				clients_nextGameTick.remove(account);
				clients_actions.remove(account);
				clients_concurrent_actions.remove(account);
				
				return false;
			}
			
			if (curTick == _nextGameTick)
			{ // if is the first time, just calculate the next game tick
				_nextGameTick = curTick + NetcoreConfig.getInstance().FLOOD_PACKET_PROTECTION_INTERVAL;
				clients_nextGameTick.get(account).put(opcode, _nextGameTick);
			}
			
			final AtomicInteger actions = clients_concurrent_actions.get(account);
			actions.decrementAndGet();
			clients_concurrent_actions.put(account, actions);
			
			return true;
		}
		punishes_in_progress.put(account, false);
		clients_nextGameTick.get(account).remove(opcode);
		clients_actions.get(account).remove(opcode);
		
		final AtomicInteger actions = clients_concurrent_actions.get(account);
		actions.decrementAndGet();
		clients_concurrent_actions.put(account, actions);
		
		return true;
		
	}
	
	@Override
	public boolean i(final int opcode, final int opcode2)
	{
		return !NetcoreConfig.getInstance().LS_LIST_PROTECTED_OPCODES.contains(opcode);
	}
	
	private void kickPlayer(final A<?> _client, final int opcode)
	{
		final L2LoginClient login_cl = (L2LoginClient) _client;
		login_cl.close(LoginFailReason.REASON_SYSTEM_ERROR);
		
		LOGGER.warn("Player with account " + login_cl.getAccount() + " kicked for flooding with packet " + Integer.toHexString(opcode));
		
	}
	
	private void banAccount(final A<?> _client, final int opcode)
	{
		
		final L2LoginClient login_cl = (L2LoginClient) _client;
		LoginController.getInstance().setAccountAccessLevel(login_cl.getAccount(), -100);
		login_cl.close(LoginFailReason.REASON_SYSTEM_ERROR);
		
		LOGGER.warn("Player with account " + login_cl.getAccount() + " banned for flooding forever with packet " + Integer.toHexString(opcode));
		
	}
	
}
