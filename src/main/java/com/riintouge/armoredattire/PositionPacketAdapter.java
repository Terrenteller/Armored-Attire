package com.riintouge.armoredattire;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class PositionPacketAdapter extends PacketAdapter implements Listener
{
	public static final Map< Integer , Boolean > GROUND_MAP = new HashMap<>();

	public PositionPacketAdapter( Plugin plugin , ListenerPriority listenerPriority )
	{
		super( plugin , listenerPriority , PacketType.Play.Client.POSITION );
	}

	// PacketAdapter overrides

	@Override
	public void onPacketReceiving( PacketEvent event )
	{
		Player player = event.getPlayer().getPlayer();
		if( player == null )
			return;

		PacketContainer packet = event.getPacket();
		StructureModifier< Boolean > playerBooleans = packet.getBooleans();
		Boolean isOnGround = playerBooleans.getValues().get( 0 );
		Boolean wasOnGround = GROUND_MAP.put( player.getEntityId() , isOnGround );
		if( isOnGround != null && wasOnGround != null && isOnGround && !wasOnGround )
		{
			EntityEquipmentPacketAdapter.updateCosmetics(
				player,
				EntityEquipmentPacketAdapter.UpdateReason.LANDED );
		}
	}

	// Listener overrides

	@EventHandler
	public void onPlayerQuitEvent( PlayerQuitEvent event )
	{
		GROUND_MAP.remove( event.getPlayer().getEntityId() );
	}
}
