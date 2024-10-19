package com.riintouge.armoredattire;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ItemStackUtil
{
	public static boolean isNullOrEmpty( ItemStack itemStack )
	{
		return itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() == 0;
	}
}
