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
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EntityEquipmentPacketAdapter extends PacketAdapter
{
	public static final Map< Integer , Boolean > PREVIEW_TOGGLE = new HashMap<>();

	private static final Pattern VANILLA_TWEAKS_ARMORED_ELYTRA_CHESTPLATE_PATTERN
		= Pattern.compile( "\"translate\":\"item\\.([^.]+)\\.([^\"]+)" );

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
		// TODO: Default to a server config option
		final OverrideCost finalCost = cost == null ? OverrideCost.NO_CONSUME_NO_RETURN : cost;

		NBT.modify( target , targetNBT ->
		{
			// TODO: Don't allow overwrites? Force the player to remove the old cosmetic first?
			targetNBT.removeKey( "cosmetic_override" ); // Start fresh
			ReadWriteNBT cosmeticOverrideCompound = targetNBT.getOrCreateCompound( "cosmetic_override" );

			if( source != null )
			{
				ReadWriteNBT sourceNBT = NBT.itemStackToNBT( source );
				sourceNBT.removeKey( "cosmetic_override" ); // Prevent infinite nesting

				cosmeticOverrideCompound.setByte( "cost" , (byte)finalCost.ordinal() );
				cosmeticOverrideCompound.setItemStack( "item_stack" , NBT.itemStackFromNBT( sourceNBT ) );
			}
		} );
	}

	public static void removeCosmeticOverride( ItemStack itemStack )
	{
		NBT.modify( itemStack , nbt ->
		{
			nbt.removeKey( "cosmetic_override" );
		} );
	}

	public static @Nullable ItemStack extractCosmeticOverride( @Nullable ItemStack itemStack )
	{
		if( ItemStackUtil.isNullOrEmpty( itemStack ) )
			return null;

		AtomicReference< ItemStack > atomicResult = new AtomicReference<>();
		NBT.get( itemStack , nbt ->
		{
			ReadableNBT cosmeticOverrideCompound = nbt.getCompound( "cosmetic_override" );
			if( cosmeticOverrideCompound != null )
			{
				ReadableNBT cosmeticOverrideItemStackCompound = cosmeticOverrideCompound.getCompound( "item_stack" );
				ItemStack cosmeticOverrideItemStack = cosmeticOverrideItemStackCompound != null
					? NBT.itemStackFromNBT( cosmeticOverrideItemStackCompound )
					: new ItemStack( Material.AIR , 0 );
				atomicResult.set( cosmeticOverrideItemStack );
			}
			else if( itemStack.getType() == Material.ELYTRA )
			{
				// Check for Vanilla Tweaks...
				ReadableNBT armoredElytraCompound = nbt.getCompound( "armored_elytra" );
				if( armoredElytraCompound != null )
				{
					// ...which no longer stores the original item in a verbose, extendable manner
					ItemMeta itemStackMeta = itemStack.getItemMeta();
					Matcher matcher = VANILLA_TWEAKS_ARMORED_ELYTRA_CHESTPLATE_PATTERN.matcher( itemStackMeta.getAsString() );
					if( matcher.find() )
					{
						// Try our best
						String domainName = matcher.group( 1 );
						String materialName = matcher.group( 2 );
						Material material = Material.matchMaterial( String.format( "%s:%s" , domainName , materialName ) );
						if( material != null && material != Material.AIR )
						{
							ItemStack cosmeticOverrideItemStack = new ItemStack( material );
							ItemMeta cosmeticOverrideItemStackMeta = cosmeticOverrideItemStack.getItemMeta();

							// TODO: How can we determine whether the chestplate actually has enchantments?
							Map< Enchantment, Integer > enchantments = itemStackMeta.getEnchants();
							for( Enchantment enchantment : enchantments.keySet() )
								cosmeticOverrideItemStackMeta.addEnchant( enchantment , enchantments.get( enchantment ) , false );

							if( itemStackMeta.hasEnchantmentGlintOverride() )
								cosmeticOverrideItemStackMeta.setEnchantmentGlintOverride( true );

							cosmeticOverrideItemStack.setItemMeta( cosmeticOverrideItemStackMeta );
							atomicResult.set( cosmeticOverrideItemStack );
						}
					}
				}
			}
		} );

		return atomicResult.get();
	}

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
}
