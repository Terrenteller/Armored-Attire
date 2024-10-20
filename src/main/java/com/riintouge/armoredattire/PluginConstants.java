package com.riintouge.armoredattire;

import org.bukkit.Material;

public class PluginConstants
{
	public static Material HIDDEN_MATERIAL = Material.AIR;

	public static class Tags
	{
		public static String COSMETIC_OVERRIDE = "cosmetic_override";
		public static String FLAGS = "flags";
		public static String ITEM_STACK = "item_stack";
	}

	public static class Flags
	{
		public static int RETURN_ORIGINAL = 1 << 0;
	}
}
