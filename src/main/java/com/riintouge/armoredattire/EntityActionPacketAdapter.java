package com.riintouge.armoredattire;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EntityActionPacketAdapter extends PacketAdapter
{
	public EntityActionPacketAdapter( Plugin plugin , ListenerPriority listenerPriority )
	{
		super( plugin , listenerPriority , PacketType.Play.Client.ENTITY_ACTION );
	}

	// PacketAdapter overrides

	@Override
	public void onPacketReceiving( PacketEvent event )
	{
		Player player = event.getPlayer().getPlayer();
		if( player == null )
			return;

		PacketContainer packet = event.getPacket();
		StructureModifier< EnumWrappers.PlayerAction > playerActionStructure = packet.getPlayerActions();
		for( EnumWrappers.PlayerAction action : playerActionStructure.getValues() )
		{
			if( action == EnumWrappers.PlayerAction.START_FALL_FLYING )
			{
				EntityEquipmentPacketAdapter.updateCosmetics(
					player,
					EntityEquipmentPacketAdapter.UpdateReason.DEPLOYED_ELYTRA );

				return;
			}
		}
	}
}
