package com.riintouge.armoredattire;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArmoredAttire extends JavaPlugin
{
	private final EntityActionPacketAdapter entityActionPacketAdapter = new EntityActionPacketAdapter( this , ListenerPriority.NORMAL );
	private final EntityEquipmentPacketAdapter entityEquipmentPacketAdapter = new EntityEquipmentPacketAdapter( this , ListenerPriority.NORMAL );
	private final PositionPacketAdapter positionPacketAdapter = new PositionPacketAdapter( this , ListenerPriority.NORMAL );
	private final AttireCommand attireCommand = new AttireCommand( getLogger() );

	// JavaPlugin overrides

	@Override
	public void onLoad()
	{
		super.onLoad();

		ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		protocolManager.addPacketListener( entityActionPacketAdapter );
		protocolManager.addPacketListener( entityEquipmentPacketAdapter );
		protocolManager.addPacketListener( positionPacketAdapter );
	}

	@Override
	public void onEnable()
	{
		PluginManager pluginManager = getServer().getPluginManager();
		pluginManager.registerEvents( positionPacketAdapter , this );

		PluginCommand pluginCommand = getCommand( "attire" );
		if( pluginCommand != null )
		{
			pluginCommand.setExecutor( attireCommand );
			pluginCommand.setTabCompleter( attireCommand );
		}
	}
}
