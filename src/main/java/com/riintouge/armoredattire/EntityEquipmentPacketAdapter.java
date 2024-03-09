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
import de.tr7zw.nbtapi.NBTCompound;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntityEquipmentPacketAdapter extends PacketAdapter
{
	public static final Map< Integer , Boolean > PREVIEW_TOGGLE = new HashMap<>();

	public enum UpdateReason
	{
		OVERRIDE_UPDATED,
		DEPLOYED_ELYTRA,
		LANDED,
		PREVIEW
	}

	// TODO: Add a server config option and implement the other values
	public enum OverrideCost
	{
		NO_CONSUME_NO_RETURN,
		CONSUME_RETURN,
		CONSUME_NO_RETURN
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

			// Lying to players about themselves causes A LOT of problems, so we don't.
			// PRO: No data loss when in creative mode with equipment overrides (overrides replace original items)
			// PRO: No unexpected equipment switching cool downs
			// PRO: No failure to deploy an elytra because the update packet got delayed
			// PRO: No failure to update cosmetic overrides despite sending packets
			// PRO: Eating does not temporarily disable overrides from the player's perspective
			// PRO: No need to worry about informing the player of their overrides upon joining a server
			// CON: The player cannot normally see their own overrides
			// FIXME: Entering creative mode while a preview is active might permanently replace the items!
			if( entity.getEntityId() == targetPlayer.getEntityId() && !PREVIEW_TOGGLE.getOrDefault( entity.getEntityId() , false ) )
			{
				event.setCancelled( true );
				return;
			}
			else
				PREVIEW_TOGGLE.put( entity.getEntityId() , false );

			StructureModifier< List< Pair< EnumWrappers.ItemSlot , ItemStack > > > itemStackPairsStructure = packet.getSlotStackPairLists();
			List< Pair< EnumWrappers.ItemSlot , ItemStack > > originalPairs = itemStackPairsStructure.readSafely( 0 );
			if( originalPairs.isEmpty() )
			{
				// Safeguard against client-disconnecting packets
				event.setCancelled( true );
				return;
			}

			ArrayList< Pair< EnumWrappers.ItemSlot , ItemStack > > modifiedPairs = new ArrayList<>();
			boolean madeCosmeticChange = false;

			for( Pair< EnumWrappers.ItemSlot , ItemStack > pair : originalPairs )
			{
				if( pair.getSecond().getType() != Material.ELYTRA || entity.getPose() != Pose.FALL_FLYING )
				{
					ItemStack cosmeticOverride = extractCosmeticOverride( pair.getSecond() );
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

	// Statics

	public static void updateCosmetics( @Nonnull Player player , @Nonnull UpdateReason reason )
	{
		ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

		List< Pair< EnumWrappers.ItemSlot , ItemStack > > equipmentPairs = getEquipmentPairs( player );
		if( equipmentPairs.isEmpty() )
			return; // Not just an optimization. Empty packets cause client disconnects.

		// Start with the real equipment. Our instance will make the necessary changes.
		PacketContainer entityEquipmentPacket = new PacketContainer( PacketType.Play.Server.ENTITY_EQUIPMENT );
		entityEquipmentPacket.getIntegers().write( 0 , player.getEntityId() );
		StructureModifier< List< Pair< EnumWrappers.ItemSlot , ItemStack > > > itemStackPairsStructure = entityEquipmentPacket.getSlotStackPairLists();
		itemStackPairsStructure.write( 0 , equipmentPairs );

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
				PREVIEW_TOGGLE.put( player.getEntityId() , true );
				protocolManager.sendServerPacket( player , entityEquipmentPacket );
				break;
		}
	}

	public static void applyCosmeticOverride(
		@Nullable ItemStack source,
		@Nonnull ItemStack target,
		@Nullable OverrideCost cost )
	{
		boolean hide = false;
		if( source == null )
		{
			source = new ItemStack( Material.AIR );
			hide = true;
		}

		if( cost == null )
			cost = OverrideCost.NO_CONSUME_NO_RETURN; // TODO: Default to a server config option

		NBTItem targetNBTItem = new NBTItem( target );
		// TODO: Don't allow overwrites? Force the player to remove the old cosmetic first?
		targetNBTItem.removeKey( "cosmeticOverride" ); // Start fresh

		NBTCompound cosmeticOverrideCompound = targetNBTItem.getOrCreateCompound( "cosmeticOverride" );
		cosmeticOverrideCompound.setString( "id" , source.getType().getKey().toString() );
		cosmeticOverrideCompound.setByte( "cost" , (byte)cost.ordinal() );
		NBTCompound tag = cosmeticOverrideCompound.getOrCreateCompound( "tag" );

		if( !hide )
		{
			NBTCompound sourceNBTItem = new NBTItem( source );
			sourceNBTItem.removeKey( "cosmeticOverride" ); // Prevent infinite nesting
			tag.mergeCompound( sourceNBTItem );
		}

		targetNBTItem.applyNBT( target );
	}

	public static void removeCosmeticOverride( ItemStack itemStack )
	{
		NBTItem itemNBT = new NBTItem( itemStack );
		itemNBT.removeKey( "cosmeticOverride" );
		itemNBT.applyNBT( itemStack );
	}

	public static @Nullable ItemStack extractCosmeticOverride( @Nullable ItemStack itemStack )
	{
		if( itemStack == null || itemStack.isEmpty() )
			return null;

		NBTItem itemNBT = new NBTItem( itemStack );
		ItemStack cosmeticOverride = extractCosmeticOverride( itemNBT , "cosmeticOverride" );
		if( cosmeticOverride != null )
			return cosmeticOverride;

		if( itemStack.getType() == Material.ELYTRA )
		{
			// Vanilla Tweaks's Armored Elytra
			NBTCompound armoredElytraNBT = itemNBT.getCompound( "armElyData" );
			if( armoredElytraNBT != null )
				return extractCosmeticOverride( armoredElytraNBT , "chestplate" );
		}

		return null;
	}

	public static @Nullable ItemStack extractCosmeticOverride( @Nullable NBTCompound compound , @Nullable String nestedItemKey )
	{
		if( compound == null )
			return null;

		NBTCompound cosmeticOverrideNBT = nestedItemKey != null && !nestedItemKey.isEmpty()
			? compound.getCompound( nestedItemKey )
			: compound;
		if( cosmeticOverrideNBT == null )
			return null;

		String cosmeticOverrideMaterialName = cosmeticOverrideNBT.getString( "id" );
		NBTCompound cosmeticOverrideData = cosmeticOverrideNBT.getCompound( "tag" );
		if( cosmeticOverrideMaterialName == null || cosmeticOverrideMaterialName.isEmpty() || cosmeticOverrideData == null )
			return null;

		Material cosmeticOverrideMaterial = Material.matchMaterial( cosmeticOverrideMaterialName );
		if( cosmeticOverrideMaterial == null )
			return null;

		ItemStack cosmeticOverrideItemStack = new ItemStack( cosmeticOverrideMaterial );
		if( cosmeticOverrideItemStack.getType() == Material.AIR )
		{
			cosmeticOverrideItemStack.setAmount( 0 );
			return cosmeticOverrideItemStack;
		}

		NBTItem cosmeticOverrideNBTItem = new NBTItem( cosmeticOverrideItemStack );
		cosmeticOverrideNBTItem.mergeCompound( cosmeticOverrideData );
		cosmeticOverrideNBTItem.applyNBT( cosmeticOverrideItemStack );

		return cosmeticOverrideItemStack;
	}

	public static @Nonnull List< Pair< EnumWrappers.ItemSlot , ItemStack > > getEquipmentPairs( @Nullable LivingEntity entity )
	{
		ArrayList< Pair< EnumWrappers.ItemSlot , ItemStack > > equipmentPairs = new ArrayList<>();

		EntityEquipment entityEquipment = entity != null ? entity.getEquipment() : null;
		if( entityEquipment != null )
		{
			ItemStack helmet = entityEquipment.getHelmet();
			if( helmet != null && !helmet.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.HEAD , helmet ) );

			ItemStack chestplate = entityEquipment.getChestplate();
			if( chestplate != null && !chestplate.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.CHEST , chestplate ) );

			ItemStack leggings = entityEquipment.getLeggings();
			if( leggings != null && !leggings.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.LEGS , leggings ) );

			ItemStack boots = entityEquipment.getBoots();
			if( boots != null && !boots.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.FEET , boots ) );

			ItemStack mainHand = entityEquipment.getItemInMainHand();
			if( !mainHand.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.MAINHAND , mainHand ) );

			ItemStack offHand = entityEquipment.getItemInOffHand();
			if( !offHand.isEmpty() )
				equipmentPairs.add( new Pair<>( EnumWrappers.ItemSlot.OFFHAND , offHand ) );
		}

		return equipmentPairs;
	}
}
