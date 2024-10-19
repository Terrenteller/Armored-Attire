package com.riintouge.armoredattire;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AttireCommand implements CommandExecutor , TabCompleter
{
	protected final Logger logger;

	public AttireCommand( Logger logger )
	{
		this.logger = logger;
	}

	protected boolean isHeadLikeItem( Material material )
	{
		// Taken from EnchantmentTarget.WEARABLE
		return material.equals( Material.CARVED_PUMPKIN )
			|| material.equals( Material.SKELETON_SKULL )
			|| material.equals( Material.WITHER_SKELETON_SKULL )
			|| material.equals( Material.ZOMBIE_HEAD )
			|| material.equals( Material.PIGLIN_HEAD )
			|| material.equals( Material.PLAYER_HEAD )
			|| material.equals( Material.CREEPER_HEAD )
			|| material.equals( Material.DRAGON_HEAD );
	}

	protected void sendCommandResponse( @NotNull CommandSender commandSender , @NotNull String message )
	{
		commandSender.sendMessage( "[§7Armored§cAttire§r] " + message );
	}

	// CommandExecutor overrides

	@Override
	public boolean onCommand( @NotNull CommandSender commandSender , @NotNull Command command , @NotNull String s , @NotNull String[] strings )
	{
		if( !( commandSender instanceof Player ) || !command.getLabel().equals( "attire" ) || strings.length < 1 )
			return false;

		Player player = (Player)commandSender;
		if( player.getGameMode() == GameMode.CREATIVE )
		{
			sendCommandResponse( commandSender , "This plugin has unintended side effects in creative mode!" );
			return true;
		}
		else if( strings[ 0 ].equalsIgnoreCase( "preview" ) )
		{
			EntityEquipmentPacketAdapter.updateCosmetics(
				player,
				EntityEquipmentPacketAdapter.UpdateReason.PREVIEW );

			return true;
		}
		else if( strings[ 0 ].equalsIgnoreCase( "hide" ) )
		{
			if( strings.length < 2 )
			{
				sendCommandResponse( commandSender , "Slot not specified!" );
				return true;
			}

			ItemStack targetEquipmentPiece = null;
			EntityEquipment playerEquipment = player.getEquipment();
			switch( strings[ 1 ].toLowerCase() )
			{
				case "head":
					targetEquipmentPiece = playerEquipment.getHelmet();
					break;
				case "body":
					targetEquipmentPiece = playerEquipment.getChestplate();
					break;
				case "legs":
					targetEquipmentPiece = playerEquipment.getLeggings();
					break;
				case "feet":
					targetEquipmentPiece = playerEquipment.getBoots();
					break;
				default:
					sendCommandResponse( commandSender , "Invalid slot!" );
					return true;
			}

			if( targetEquipmentPiece == null )
			{
				sendCommandResponse( commandSender , "No equipment in slot!" );
				return true;
			}

			EntityEquipmentPacketAdapter.applyCosmeticOverride( null , targetEquipmentPiece , null );
			EntityEquipmentPacketAdapter.updateCosmetics(
				player,
				EntityEquipmentPacketAdapter.UpdateReason.OVERRIDE_UPDATED );

			return true;
		}

		EntityEquipment playerEquipment = player.getEquipment();
		ItemStack sourceItemStack = playerEquipment.getItemInMainHand();
		if( ItemStackUtil.isNullOrEmpty( sourceItemStack ) )
		{
			sendCommandResponse( commandSender , "No item in main hand!" );
			return true;
		}
		else if( strings[ 0 ].equalsIgnoreCase( "clear" ) )
		{
			EntityEquipmentPacketAdapter.removeCosmeticOverride( sourceItemStack );
			return true;
		}
		else if( !strings[ 0 ].equalsIgnoreCase( "set" ) )
			return false;

		// TODO: Allow weapons and tools to be overridden by anything?
		ItemStack targetItemStack = null;
		if( EnchantmentTarget.ARMOR_HEAD.includes( sourceItemStack ) || isHeadLikeItem( sourceItemStack.getType() ) )
		{
			targetItemStack = playerEquipment.getHelmet();
		}
		else if( EnchantmentTarget.ARMOR_TORSO.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getChestplate();
		}
		else if( EnchantmentTarget.ARMOR_LEGS.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getLeggings();
		}
		else if( EnchantmentTarget.ARMOR_FEET.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getBoots();
		}
		else if( EnchantmentTarget.WEAPON.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getItemInOffHand();
			if( !EnchantmentTarget.WEAPON.includes( targetItemStack ) )
			{
				sendCommandResponse( commandSender , "Off hand item is not a weapon!" );
				return true;
			}
		}
		else if( EnchantmentTarget.TOOL.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getItemInOffHand();
			if( !EnchantmentTarget.TOOL.includes( targetItemStack ) )
			{
				sendCommandResponse( commandSender , "Off hand item is not a tool!" );
				return true;
			}
		}
		else
		{
			sendCommandResponse( commandSender , "Main hand item is not a valid override!" );
			return true;
		}

		if( ItemStackUtil.isNullOrEmpty( targetItemStack ) )
		{
			sendCommandResponse( commandSender , "No corresponding worn item!" );
			return true;
		}

		EntityEquipmentPacketAdapter.applyCosmeticOverride( sourceItemStack , targetItemStack , null );
		EntityEquipmentPacketAdapter.updateCosmetics(
			player,
			EntityEquipmentPacketAdapter.UpdateReason.OVERRIDE_UPDATED );

		sendCommandResponse( commandSender , "Override applied successfully" );
		return true;
	}

	// TabCompleter overrides

	@Override
	public @Nullable List< String > onTabComplete( @NotNull CommandSender commandSender , @NotNull Command command , @NotNull String s , @NotNull String[] strings )
	{
		if( strings.length == 1 )
		{
			ArrayList< String > subCommands = new ArrayList<>();
			subCommands.add( "clear" );
			subCommands.add( "hide" );
			subCommands.add( "set" );
			subCommands.add( "preview" );

			return subCommands;
		}
		else if( strings.length == 2 && strings[ 0 ].equalsIgnoreCase( "hide" ) )
		{
			ArrayList< String > args = new ArrayList<>();
			args.add( "head" );
			args.add( "body" );
			args.add( "legs" );
			args.add( "feet" );

			return args;
		}

		return null;
	}
}
