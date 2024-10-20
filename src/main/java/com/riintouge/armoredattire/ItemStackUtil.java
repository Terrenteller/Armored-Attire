package com.riintouge.armoredattire;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class ItemStackUtil
{
	public static void giveToPlayerOrDrop( Player player , ItemStack itemStack )
	{
		if( player == null || ItemStackUtil.isNullOrEmpty( itemStack ) )
			return;

		HashMap< Integer , ItemStack > remainder = player.getInventory().addItem( itemStack );
		if( !remainder.isEmpty() )
		{
			Location location = player.getLocation();
			location.setY( location.getY() + 1.0 );
			player.getWorld().dropItem( location , itemStack );
		}
	}

	public static boolean isNullOrEmpty( ItemStack itemStack )
	{
		return itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() == 0;
	}
}
