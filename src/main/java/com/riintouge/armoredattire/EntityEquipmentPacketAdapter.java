package com.riintouge.armoredattire;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityEquipmentPacketAdapter extends PacketAdapter implements Listener
{
	public static final Map< Integer , Boolean > PREVIEW_STATE = new HashMap<>();

	public enum UpdateReason
	{
		OVERRIDE_UPDATED,
		DEPLOYED_ELYTRA,
		LANDED,
		PREVIEW
	}

	public EntityEquipmentPacketAdapter( Plugin plugin , ListenerPriority listenerPriority )
	{
		super( plugin , listenerPriority , PacketType.Play.Server.ENTITY_EQUIPMENT );
	}

	// PacketAdapter overrides

	@Override
	public void onPacketSending( PacketEvent event )
	{
		try
		{
			Player targetPlayer = event.getPlayer().getPlayer();
			if( targetPlayer == null )
				return;

			PacketContainer packet = event.getPacket();
			StructureModifier< Entity > entityStructure = packet.getEntityModifier( event );
			List< Entity > entities = entityStructure.getValues();
			if( entities.size() != 1 )
				return; // We expect this packet to describe a single entity

			Entity entity = entities.get( 0 );
			if( !( entity instanceof Player || entity instanceof ArmorStand ) )
				return;
			else if( entity instanceof Player && ( (Player)entity ).getGameMode() == GameMode.CREATIVE )
				return; // Updates in creative mode stick unexpectedly and cause data loss!

			StructureModifier< List< Pair< EnumWrappers.ItemSlot , ItemStack > > > itemStackPairsStructure = packet.getSlotStackPairLists();
			List< Pair< EnumWrappers.ItemSlot , ItemStack > > originalPairs = itemStackPairsStructure.readSafely( 0 );
			if( originalPairs.isEmpty() )
			{
				// Safeguard against client-disconnecting packets
				event.setCancelled( true );
				return;
			}

			// Lying to players about themselves causes A LOT of problems, so we don't. Normally.
			// PRO: No data loss when in creative mode with equipment overrides (overrides replace original items)
			// PRO: No unexpected hand-held cool-downs
			// PRO: No failure to deploy an elytra because the update packet got delayed
			// PRO: No failure to update cosmetic overrides despite sending packets
			// PRO: Eating does not temporarily disable overrides from the player's perspective
			// PRO: No need to worry about informing the player of their overrides upon joining a server
			// CON: The player cannot normally see their own overrides
			if( entity.getEntityId() == targetPlayer.getEntityId() && !PREVIEW_STATE.getOrDefault( entity.getEntityId() , false ) )
				return;

			ArrayList< Pair< EnumWrappers.ItemSlot , ItemStack > > modifiedPairs = new ArrayList<>();
			boolean madeCosmeticChange = false;

			for( Pair< EnumWrappers.ItemSlot , ItemStack > pair : originalPairs )
			{
				if( pair.getSecond().getType() != Material.ELYTRA || entity.getPose() != Pose.FALL_FLYING )
				{
					ItemStack cosmeticOverride = CosmeticOverrideUtil.getCosmeticOverride( pair.getSecond() , true );
					if( cosmeticOverride != null )
					{
						modifiedPairs.add( new Pair<>( pair.getFirst() , cosmeticOverride ) );
						madeCosmeticChange = true;
						continue;
					}
				}

				modifiedPairs.add( pair );
			}

			if( madeCosmeticChange )
				itemStackPairsStructure.writeSafely( 0 , modifiedPairs );
		}
		catch( Exception e )
		{
			throw new RuntimeException( e );
		}
	}

	// Listener overrides

	@EventHandler
	public void onPlayerGameModeChangeEvent( PlayerGameModeChangeEvent event )
	{
		if( event.isCancelled() || event.getNewGameMode() != GameMode.CREATIVE )
			return;

		Player player = event.getPlayer();
		if( PREVIEW_STATE.getOrDefault( player.getEntityId() , false ) )
		{
			// TODO: This needs to show up in the server logs too in case the sender is not the target
			event.setCancelled( true );
			player.sendMessage(
				ArmoredAttire.MESSAGE_TEMPLATE.formatted(
					"Blocked game mode change because a preview is active!" ) );
		}
	}

	@EventHandler
	public void onPlayerQuitEvent( PlayerQuitEvent event )
	{
		PREVIEW_STATE.remove( event.getPlayer().getEntityId() );
	}

	// Statics

	public static @Nonnull List< Pair< EnumWrappers.ItemSlot , ItemStack > > getEquipmentPairs( @Nullable LivingEntity entity )
	{
		ArrayList< Pair< EnumWrappers.ItemSlot , ItemStack > > equipmentPairs = new ArrayList<>();

		EntityEquipment entityEquipment = entity != null ? entity.getEquipment() : null;
		if( entityEquipment != null )
		{
			ItemStack head = entityEquipment.getHelmet();
			if( !ItemStackUtil.isNullOrEmpty( head ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.HEAD , head ) );

			ItemStack chest = entityEquipment.getChestplate();
			if( !ItemStackUtil.isNullOrEmpty( chest ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.CHEST , chest ) );

			ItemStack legs = entityEquipment.getLeggings();
			if( !ItemStackUtil.isNullOrEmpty( legs ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.LEGS , legs ) );

			ItemStack feet = entityEquipment.getBoots();
			if( !ItemStackUtil.isNullOrEmpty( feet ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.FEET , feet ) );

			ItemStack mainHand = entityEquipment.getItemInMainHand();
			if( !ItemStackUtil.isNullOrEmpty( mainHand ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.MAINHAND , mainHand ) );

			ItemStack offHand = entityEquipment.getItemInOffHand();
			if( !ItemStackUtil.isNullOrEmpty( offHand ) )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.OFFHAND , offHand ) );
		}

		return equipmentPairs;
	}

	public static void updateCosmetics( @Nonnull Player player , @Nonnull UpdateReason reason )
	{
		List< Pair< EnumWrappers.ItemSlot , ItemStack > > equipmentPairs = getEquipmentPairs( player );
		if( equipmentPairs.isEmpty() )
			return; // Not just an optimization. Empty packets cause client disconnects.

		// Start with the real equipment. Our instance will make the necessary changes.
		PacketContainer entityEquipmentPacket = new PacketContainer( PacketType.Play.Server.ENTITY_EQUIPMENT );
		entityEquipmentPacket.getIntegers().write( 0 , player.getEntityId() );
		StructureModifier< List< Pair< EnumWrappers.ItemSlot , ItemStack > > > itemStackPairsStructure = entityEquipmentPacket.getSlotStackPairLists();
		itemStackPairsStructure.write( 0 , equipmentPairs );

		ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
		switch( reason )
		{
			case OVERRIDE_UPDATED:
				protocolManager.broadcastServerPacket( entityEquipmentPacket , player , true );
				break;
			case DEPLOYED_ELYTRA:
			case LANDED:
				protocolManager.broadcastServerPacket( entityEquipmentPacket , player , false );
				break;
			case PREVIEW:
				protocolManager.sendServerPacket( player , entityEquipmentPacket );
				break;
		}
	}
}
