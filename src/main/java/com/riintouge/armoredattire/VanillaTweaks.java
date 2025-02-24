package com.riintouge.armoredattire;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VanillaTweaks
{
	private static final Pattern VANILLA_TWEAKS_ARMORED_ELYTRA_CHESTPLATE_PATTERN
		= Pattern.compile( "\"translate\":\"item\\.([^.]+)\\.([^\"]+)" );

	public static ItemStack resolveOverride( ItemStack itemStack )
	{
		AtomicReference< ItemStack > atomicResult = new AtomicReference<>();
		NBT.get( itemStack , customData ->
		{
			if( itemStack.getType() != Material.ELYTRA )
				return;

			// The armored elytra datapacks used to have enough overlap we could treat them similarly
			ReadableNBT armoredElytraCompound = customData.getCompound( "armored_elytra" );
			if( armoredElytraCompound == null )
				return;

			// Vanilla Tweaks now stores the chestplate as an index.
			// This makes it harder for us to be generic and extendable.
			ItemMeta itemStackMeta = itemStack.getItemMeta();
			Matcher matcher = VANILLA_TWEAKS_ARMORED_ELYTRA_CHESTPLATE_PATTERN.matcher( itemStackMeta.getAsString() );
			if( !matcher.find() )
				return;

			// Try our best...
			String domainName = matcher.group( 1 );
			String materialName = matcher.group( 2 );
			Material material = Material.matchMaterial( String.format( "%s:%s" , domainName , materialName ) );
			if( material == null || material == Material.AIR )
				return;

			ItemStack cosmeticOverrideItemStack = new ItemStack( material );
			ItemMeta cosmeticOverrideItemStackMeta = cosmeticOverrideItemStack.getItemMeta();

			// TODO: How can we determine whether the chestplate actually has enchantments?
			// It is not clear how Vanilla Tweaks stores the original chestplate data, if at all.
			Map< Enchantment, Integer > enchantments = itemStackMeta.getEnchants();
			for( Enchantment enchantment : enchantments.keySet() )
				cosmeticOverrideItemStackMeta.addEnchant( enchantment , enchantments.get( enchantment ) , false );

			if( itemStackMeta.hasEnchantmentGlintOverride() )
				cosmeticOverrideItemStackMeta.setEnchantmentGlintOverride( true );

			cosmeticOverrideItemStack.setItemMeta( cosmeticOverrideItemStackMeta );
			atomicResult.set( cosmeticOverrideItemStack );

		} );

		return atomicResult.get();
	}
}
