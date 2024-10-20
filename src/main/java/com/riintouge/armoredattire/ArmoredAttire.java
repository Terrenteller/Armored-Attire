package com.riintouge.armoredattire;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArmoredAttire extends JavaPlugin
{
	public static ArmoredAttire INSTANCE;
	public static final String MESSAGE_TEMPLATE = "[§7Armored§cAttire§r] %s";

	private final AttireCommand attireCommand = new AttireCommand();
	private final EntityActionPacketAdapter entityActionPacketAdapter = new EntityActionPacketAdapter( this , ListenerPriority.NORMAL );
	private final EntityEquipmentPacketAdapter entityEquipmentPacketAdapter = new EntityEquipmentPacketAdapter( this , ListenerPriority.NORMAL );
	private final PositionPacketAdapter positionPacketAdapter = new PositionPacketAdapter( this , ListenerPriority.NORMAL );

	// JavaPlugin overrides

	@Override
	public void onLoad()
	{
		super.onLoad();

		INSTANCE = this;

		ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		protocolManager.addPacketListener( entityActionPacketAdapter );
		protocolManager.addPacketListener( entityEquipmentPacketAdapter );
		protocolManager.addPacketListener( positionPacketAdapter );
	}

	@Override
	public void onEnable()
	{
		saveDefaultConfig();

		PluginManager pluginManager = getServer().getPluginManager();
		pluginManager.registerEvents( entityEquipmentPacketAdapter , this );
		pluginManager.registerEvents( positionPacketAdapter , this );

		PluginCommand pluginCommand = getCommand( "attire" );
		if( pluginCommand != null )
		{
			pluginCommand.setExecutor( attireCommand );
			pluginCommand.setTabCompleter( attireCommand );
		}
	}
}
