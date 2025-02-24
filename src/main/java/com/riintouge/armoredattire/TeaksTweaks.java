package com.riintouge.armoredattire;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;

// Some of this class was lifted from TeaksTweaks and re-written for consistency
public class TeaksTweaks
{
	public static NamespacedKey getKey( @NotNull String key )
	{
		return new NamespacedKey( "teakstweaks" , key );
	}

	public static ItemStack fromByteArray( byte[] data )
	{
		if( data != null && data.length > 0 )
		{
			try
			{
				ByteArrayInputStream inputStream = new ByteArrayInputStream( data );
				BukkitObjectInputStream dataInput = new BukkitObjectInputStream( inputStream );
				return (ItemStack)dataInput.readObject();
			}
			catch( IOException | ClassNotFoundException e )
			{
				// The original code wraps the exception with RuntimeException, but we don't care
			}
		}

		return null;
	}

	public static ItemStack resolveOverride( ItemStack itemStack )
	{
		if( itemStack == null || itemStack.getType() != Material.ELYTRA )
			return null;

		ItemMeta itemStackMeta = itemStack.getItemMeta();
		if( itemStackMeta == null )
			return null;

		PersistentDataContainer persistentDataContainer = itemStackMeta.getPersistentDataContainer();
		return TeaksTweaks.fromByteArray(
			persistentDataContainer.get(
				getKey( "chestplate_storage" ),
				PersistentDataType.BYTE_ARRAY ) );
	}
}
