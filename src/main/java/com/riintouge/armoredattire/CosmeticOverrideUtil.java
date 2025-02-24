package com.riintouge.armoredattire;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

// `/data get entity @p` will dump the NBT of the player. It's not great, but it's something.
public class CosmeticOverrideUtil
{
	public static void applyCosmeticOverride(
		@Nullable ItemStack source,
		@Nonnull ItemStack target,
		boolean returnOriginal )
	{
		NBT.modify( target , customData ->
		{
			// Force the player to remove the existing override so we don't get blamed for accidents
			ReadWriteNBT cosmeticOverrideCompound = customData.getCompound( PluginConstants.Tags.COSMETIC_OVERRIDE );
			if( cosmeticOverrideCompound != null )
				return;

			cosmeticOverrideCompound = customData.getOrCreateCompound( PluginConstants.Tags.COSMETIC_OVERRIDE );

			if( source != null )
			{
				int flags = 0;
				flags |= returnOriginal ? PluginConstants.Flags.RETURN_ORIGINAL : 0;
				cosmeticOverrideCompound.setInteger( PluginConstants.Tags.FLAGS , flags );

				ReadWriteNBT sourceNBT = NBT.itemStackToNBT( source );
				sourceNBT.removeKey( PluginConstants.Tags.COSMETIC_OVERRIDE ); // Prevent infinite nesting
				cosmeticOverrideCompound.setItemStack( PluginConstants.Tags.ITEM_STACK , NBT.itemStackFromNBT( sourceNBT ) );
			}
		} );
	}

	public static @Nullable ItemStack getCosmeticOverride( @Nullable ItemStack itemStack , boolean allowImplied )
	{
		if( ItemStackUtil.isNullOrEmpty( itemStack ) )
			return null;

		AtomicReference< ItemStack > atomicResult = new AtomicReference<>();
		NBT.get( itemStack , customData ->
		{
			ReadableNBT cosmeticOverrideCompound = customData.getCompound( PluginConstants.Tags.COSMETIC_OVERRIDE );
			if( cosmeticOverrideCompound != null )
			{
				ReadableNBT cosmeticOverrideItemStackCompound = cosmeticOverrideCompound.getCompound( PluginConstants.Tags.ITEM_STACK );
				ItemStack cosmeticOverrideItemStack = cosmeticOverrideItemStackCompound != null
					? NBT.itemStackFromNBT( cosmeticOverrideItemStackCompound )
					: new ItemStack( PluginConstants.HIDDEN_MATERIAL , 0 ); // Hidden or invalid. Can't return null here.
				atomicResult.set( cosmeticOverrideItemStack );
			}
			else if( allowImplied )
				atomicResult.set( getImpliedCosmeticOverride( itemStack ) );
		} );

		return atomicResult.get();
	}

	public static @Nullable ItemStack getImpliedCosmeticOverride( @NotNull ItemStack itemStack )
	{
		ItemStack cosmeticOverride = VanillaTweaks.resolveOverride( itemStack );
		if( !ItemStackUtil.isNullOrEmpty( cosmeticOverride ) )
			return cosmeticOverride;

		cosmeticOverride = TeaksTweaks.resolveOverride( itemStack );
		if( !ItemStackUtil.isNullOrEmpty( cosmeticOverride ) )
			return cosmeticOverride;

		// It's important that we return null for consumers, not air or an empty stack
		return null;
	}

	public static boolean hasCosmeticOverride( @Nullable ItemStack itemStack )
	{
		if( ItemStackUtil.isNullOrEmpty( itemStack ) )
			return false;

		AtomicReference< Boolean > atomicResult = new AtomicReference<>();
		NBT.get( itemStack , customData ->
		{
			// getCosmeticOverride() is not expected to return null if our top-level compound exists
			atomicResult.set( customData.getCompound( PluginConstants.Tags.COSMETIC_OVERRIDE ) != null );
		} );

		return atomicResult.get();
	}

	public static ItemStack removeCosmeticOverride( ItemStack itemStack )
	{
		AtomicReference< ItemStack > atomicResult = new AtomicReference<>();
		NBT.modify( itemStack , customData ->
		{
			ReadableNBT cosmeticOverrideCompound = customData.getCompound( "cosmetic_override" );
			if( cosmeticOverrideCompound == null )
				return;

			atomicResult.set( new ItemStack( PluginConstants.HIDDEN_MATERIAL , 0 ) );

			Integer flags = cosmeticOverrideCompound.getInteger( "flags" );
			if( flags != null )
			{
				if( ( flags & PluginConstants.Flags.RETURN_ORIGINAL ) > 0 )
				{
					ReadableNBT cosmeticOverrideItemStackCompound = cosmeticOverrideCompound.getCompound( "item_stack" );
					if( cosmeticOverrideItemStackCompound != null )
						atomicResult.set( NBT.itemStackFromNBT( cosmeticOverrideItemStackCompound ) );
				}
			}

			customData.removeKey( "cosmetic_override" );
		} );

		return atomicResult.get();
	}
}
