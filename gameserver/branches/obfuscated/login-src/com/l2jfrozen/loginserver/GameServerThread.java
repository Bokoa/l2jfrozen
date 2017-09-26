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
package com.l2jfrozen.loginserver;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import com.l2jfrozen.common.CommonConfig;
import com.l2jfrozen.common.util.Util;
import com.l2jfrozen.loginserver.network.GameServerState;
import com.l2jfrozen.loginserver.network.L2JGameServerPacketHandler;
import com.l2jfrozen.loginserver.network.loginserverpackets.InitLS;
import com.l2jfrozen.loginserver.network.loginserverpackets.KickPlayer;
import com.l2jfrozen.loginserver.network.loginserverpackets.LoginServerFail;

import a.a.u;
import a.a.x;

/**
 * @author -Wooden-
 * @author KenM
 */
public class GameServerThread extends Thread
{
	protected static final Logger _log = Logger.getLogger(GameServerThread.class.getName());
	private final Socket _connection;
	private InputStream _in;
	private OutputStream _out;
	private final RSAPublicKey _publicKey;
	private final RSAPrivateKey _privateKey;
	private u _blowfish;
	private GameServerState _loginConnectionState = GameServerState.CONNECTED;
	
	private final String _connectionIp;
	
	private GameServerInfo _gsi;
	
	/** Authed Clients on a GameServer */
	private final Set<String> _accountsOnGameServer = ConcurrentHashMap.newKeySet();
	
	private String _connectionIPAddress;
	
	@Override
	public void run()
	{
		_connectionIPAddress = _connection.getInetAddress().getHostAddress();
		if (GameServerThread.isBannedGameserverIP(_connectionIPAddress))
		{
			_log.info("GameServerRegistration: IP Address " + _connectionIPAddress + " is on Banned IP list.");
			forceClose(LoginServerFail.REASON_IP_BANNED);
			// ensure no further processing for this connection
			return;
		}
		
		final InitLS startPacket = new InitLS(_publicKey.getModulus().toByteArray());
		try
		{
			sendPacket(startPacket);
			
			int lengthHi = 0;
			int lengthLo = 0;
			int length = 0;
			boolean checksumOk = false;
			for (;;)
			{
				lengthLo = _in.read();
				lengthHi = _in.read();
				length = (lengthHi * 256) + lengthLo;
				
				if ((lengthHi < 0) || _connection.isClosed())
				{
					_log.info("LoginServerThread: Login terminated the connection.");
					break;
				}
				
				final byte[] data = new byte[length - 2];
				
				int receivedBytes = 0;
				int newBytes = 0;
				int left = length - 2;
				while ((newBytes != -1) && (receivedBytes < (length - 2)))
				{
					newBytes = _in.read(data, receivedBytes, left);
					receivedBytes = receivedBytes + newBytes;
					left -= newBytes;
				}
				
				if (receivedBytes != (length - 2))
				{
					_log.warn("Incomplete Packet is sent to the server, closing connection.(LS)");
					break;
				}
				
				// decrypt if we have a key
				_blowfish.de(data, 0, data.length);
				checksumOk = u.b(data);
				if (!checksumOk)
				{
					_log.warn("Incorrect packet checksum, closing connection (LS)");
					return;
				}
				
				if (CommonConfig.DEBUG)
				{
					_log.warn("[C]" + System.lineSeparator() + Util.printData(data));
				}
				
				L2JGameServerPacketHandler.handlePacket(data, this);
			}
		}
		catch (final IOException e)
		{
			final String serverName = (getServerId() != -1 ? "[" + getServerId() + "] " + GameServerTable.getInstance().getServerNameById(getServerId()) : "(" + _connectionIPAddress + ")");
			final String msg = "GameServer " + serverName + ": Connection lost: " + e.getMessage();
			_log.info(msg);
		}
		finally
		{
			if (isAuthed())
			{
				_gsi.setDown();
				_log.info("Server [" + getServerId() + "] " + GameServerTable.getInstance().getServerNameById(getServerId()) + " is now set as disconnected");
			}
			LoginServer.getInstance().getGameServerListener().removeGameServer(this);
			LoginServer.getInstance().getGameServerListener().removeFloodProtection(_connectionIp);
		}
	}
	
	public boolean hasAccountOnGameServer(final String account)
	{
		return _accountsOnGameServer.contains(account);
	}
	
	public int getPlayerCount()
	{
		return _accountsOnGameServer.size();
	}
	
	/**
	 * Attachs a GameServerInfo to this Thread<br>
	 * <ul>
	 * <li>Updates the GameServerInfo values based on GameServerAuth packet</li>
	 * <li><b>Sets the GameServerInfo as Authed</b></li>
	 * </ul>
	 * @param gsi The GameServerInfo to be attached.
	 * @param port
	 * @param hosts
	 * @param maxPlayers
	 */
	public void attachGameServerInfo(final GameServerInfo gsi, final int port, final String[] hosts, final int maxPlayers)
	{
		setGameServerInfo(gsi);
		gsi.setGameServerThread(this);
		gsi.setPort(port);
		setGameHosts(hosts);
		gsi.setMaxPlayers(maxPlayers);
		gsi.setAuthed(true);
	}
	
	public void forceClose(final int reason)
	{
		sendPacket(new LoginServerFail(reason));
		
		try
		{
			_connection.close();
		}
		catch (final IOException e)
		{
			_log.info("GameServerThread: Failed disconnecting banned server, server already disconnected.");
		}
	}
	
	/**
	 * @param ipAddress
	 * @return
	 */
	public static boolean isBannedGameserverIP(final String ipAddress)
	{
		return false;
	}
	
	public GameServerThread(final Socket con)
	{
		_connection = con;
		_connectionIp = con.getInetAddress().getHostAddress();
		try
		{
			_in = _connection.getInputStream();
			_out = new BufferedOutputStream(_connection.getOutputStream());
		}
		catch (final IOException e)
		{
			_log.warn(getClass().getSimpleName() + ": " + e.getMessage());
		}
		final KeyPair pair = GameServerTable.getInstance().getKeyPair();
		_privateKey = (RSAPrivateKey) pair.getPrivate();
		_publicKey = (RSAPublicKey) pair.getPublic();
		_blowfish = new u("_;v.]05-31!|+-%xT!^[$\00");
		setName(getClass().getSimpleName() + "-" + getId() + "@" + _connectionIp);
		start();
	}
	
	/**
	 * @param sl
	 */
	public void sendPacket(final x sl)
	{
		try
		{
			final byte[] data = sl.gC();
			u.c(data);
			if (CommonConfig.DEBUG)
			{
				_log.info("[S] " + sl.getClass().getSimpleName() + ":" + System.lineSeparator() + Util.printData(data));
			}
			_blowfish.e(data, 0, data.length);
			
			final int len = data.length + 2;
			synchronized (_out)
			{
				_out.write(len & 0xff);
				_out.write((len >> 8) & 0xff);
				_out.write(data);
				_out.flush();
			}
		}
		catch (final IOException e)
		{
			_log.error("IOException while sending packet " + sl.getClass().getSimpleName());
		}
	}
	
	public void kickPlayer(final String account)
	{
		sendPacket(new KickPlayer(account));
	}
	
	/**
	 * @param hosts The gameHost to set.
	 */
	public void setGameHosts(final String[] hosts)
	{
		_log.info("Updated Gameserver [" + getServerId() + "] " + GameServerTable.getInstance().getServerNameById(getServerId()) + " IP's:");
		
		_gsi.clearServerAddresses();
		for (int i = 0; i < hosts.length; i += 2)
		{
			try
			{
				_gsi.addServerAddress(hosts[i], hosts[i + 1]);
			}
			catch (final Exception e)
			{
				_log.warn("Couldn't resolve hostname \"" + e + "\"");
			}
		}
		
		for (final String s : _gsi.getServerAddresses())
		{
			_log.info(s);
		}
	}
	
	/**
	 * @return Returns the isAuthed.
	 */
	public boolean isAuthed()
	{
		if (getGameServerInfo() == null)
		{
			return false;
		}
		return getGameServerInfo().isAuthed();
	}
	
	public void setGameServerInfo(final GameServerInfo gsi)
	{
		_gsi = gsi;
	}
	
	public GameServerInfo getGameServerInfo()
	{
		return _gsi;
	}
	
	/**
	 * @return Returns the connectionIpAddress.
	 */
	public String getConnectionIpAddress()
	{
		return _connectionIPAddress;
	}
	
	public int getServerId()
	{
		if (getGameServerInfo() != null)
		{
			return getGameServerInfo().getId();
		}
		return -1;
	}
	
	public RSAPrivateKey getPrivateKey()
	{
		return _privateKey;
	}
	
	public void SetBlowFish(final u blowfish)
	{
		_blowfish = blowfish;
	}
	
	public void addAccountOnGameServer(final String account)
	{
		_accountsOnGameServer.add(account);
	}
	
	public void removeAccountOnGameServer(final String account)
	{
		_accountsOnGameServer.remove(account);
	}
	
	public GameServerState getLoginConnectionState()
	{
		return _loginConnectionState;
	}
	
	public void setLoginConnectionState(final GameServerState state)
	{
		_loginConnectionState = state;
	}
}
