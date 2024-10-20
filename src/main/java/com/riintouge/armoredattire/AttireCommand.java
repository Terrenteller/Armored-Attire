package com.riintouge.armoredattire;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AttireCommand implements CommandExecutor , TabCompleter
{
	public AttireCommand()
	{
		// Nothing to do
	}

	protected boolean attireClear( Player player , ItemStack sourceItemStack )
	{
		ItemStack cosmeticOverride = CosmeticOverrideUtil.removeCosmeticOverride( sourceItemStack );
		if( cosmeticOverride == null )
			return sendCommandResponse( true , player , "No override to clear" );

		ItemStackUtil.giveToPlayerOrDrop( player , cosmeticOverride );
		return sendCommandResponse( true , player , "Override removed successfully" );
	}

	protected boolean attireHide( Player player , String[] strings )
	{
		if( strings.length < 2 )
			return sendCommandResponse( true , player , "Slot not specified!" );

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
				return sendCommandResponse( true , player , "Invalid slot!" );
		}

		if( targetEquipmentPiece == null )
			return sendCommandResponse( true , player , "No equipment in slot!" );

		CosmeticOverrideUtil.applyCosmeticOverride( null , targetEquipmentPiece , false );
		EntityEquipmentPacketAdapter.updateCosmetics(
			player,
			EntityEquipmentPacketAdapter.UpdateReason.OVERRIDE_UPDATED );

		return sendCommandResponse( true , player , "Override applied successfully" );
	}

	protected boolean attirePreview( Player player , String[] strings )
	{
		boolean newState = !EntityEquipmentPacketAdapter.PREVIEW_STATE.getOrDefault( player.getEntityId() , false );
		if( strings.length >= 2 )
		{
			if( strings[ 1 ].equalsIgnoreCase( "off" ) )
				newState = false;
			else if( strings[ 1 ].equalsIgnoreCase( "on" ) )
				newState = true;
			else
				return false;
		}

		String message = String.format( "Preview %s" , newState ? "enabled" : "disabled" );
		EntityEquipmentPacketAdapter.PREVIEW_STATE.put( player.getEntityId() , newState );
		EntityEquipmentPacketAdapter.updateCosmetics(
			player,
			EntityEquipmentPacketAdapter.UpdateReason.PREVIEW );

		return sendCommandResponse( true , player , message );
	}

	protected boolean attireSet( Player player , EntityEquipment playerEquipment , ItemStack sourceItemStack )
	{
		// TODO: Allow weapons and tools to be overridden by anything? Make plugin option?
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
		else if( sourceItemStack.getType() == Material.SHIELD )
		{
			targetItemStack = playerEquipment.getItemInOffHand();
			if( targetItemStack.getType() != Material.SHIELD )
				return sendCommandResponse( true , player , "Off hand item is not a shield!" );
		}
		else if( EnchantmentTarget.WEAPON.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getItemInOffHand();
			if( !EnchantmentTarget.WEAPON.includes( targetItemStack ) )
				return sendCommandResponse( true , player , "Off hand item is not a weapon!" );
		}
		else if( EnchantmentTarget.TOOL.includes( sourceItemStack ) )
		{
			targetItemStack = playerEquipment.getItemInOffHand();
			if( !EnchantmentTarget.TOOL.includes( targetItemStack ) )
				return sendCommandResponse( true , player , "Off hand item is not a tool!" );
		}
		else
			return sendCommandResponse( true , player , "Main hand item is not a valid override!" );

		// If the target item isn't valid at this point it must be a wearable item
		if( ItemStackUtil.isNullOrEmpty( targetItemStack ) )
			return sendCommandResponse( true , player , "No corresponding worn item!" );
		else if( CosmeticOverrideUtil.hasCosmeticOverride( targetItemStack ) )
			return sendCommandResponse( true , player , "Target already overridden. Remove it first!" );

		FileConfiguration config = ArmoredAttire.INSTANCE.getConfig();
		String costString = config.getString( "overrideCost" , OverrideCost.NO_CONSUME_NO_RETURN.toString() );
		OverrideCost cost = EnumUtil.valueOfOrDefault( OverrideCost.class , costString , OverrideCost.NO_CONSUME_NO_RETURN );
		CosmeticOverrideUtil.applyCosmeticOverride( sourceItemStack , targetItemStack , cost == OverrideCost.CONSUME_RETURN );
		EntityEquipmentPacketAdapter.updateCosmetics( player , EntityEquipmentPacketAdapter.UpdateReason.OVERRIDE_UPDATED );
		if( cost != OverrideCost.NO_CONSUME_NO_RETURN )
			player.getInventory().remove( sourceItemStack );

		return sendCommandResponse( true , player , "Override applied successfully" );
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

	protected < T > T sendCommandResponse( T ret , @NotNull CommandSender commandSender , @NotNull String message )
	{
		commandSender.sendMessage( ArmoredAttire.MESSAGE_TEMPLATE.formatted( message ) );
		return ret;
	}

	// CommandExecutor overrides

	@Override
	public boolean onCommand( @NotNull CommandSender commandSender , @NotNull Command command , @NotNull String s , @NotNull String[] strings )
	{
		if( !( commandSender instanceof Player ) || !command.getLabel().equals( "attire" ) || strings.length < 1 )
			return false;

		Player player = (Player)commandSender;
		if( player.getGameMode() == GameMode.CREATIVE )
			return sendCommandResponse( true , commandSender , "This plugin has unintended side effects in creative mode!" );

		String attireCommand = strings[ 0 ];
		if( attireCommand.equalsIgnoreCase( "preview" ) )
			return attirePreview( player , strings );
		else if( attireCommand.equalsIgnoreCase( "hide" ) )
			return attireHide( player , strings );

		EntityEquipment playerEquipment = player.getEquipment();
		ItemStack sourceItemStack = playerEquipment != null ? playerEquipment.getItemInMainHand() : null;
		if( ItemStackUtil.isNullOrEmpty( sourceItemStack ) )
			return sendCommandResponse( true , commandSender , "No item in main hand to use as the override!" );
		else if( attireCommand.equalsIgnoreCase( "clear" ) )
			return attireClear( player , sourceItemStack );
		else if( attireCommand.equalsIgnoreCase( "set" ) )
			return attireSet( player , playerEquipment , sourceItemStack );

		return false;
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
		else if( strings.length == 2 && strings[ 0 ].equalsIgnoreCase( "preview" ) )
		{
			ArrayList< String > args = new ArrayList<>();
			args.add( "off" );
			args.add( "on" );

			return args;
		}

		// Returning null causes auto-complete to suggest player names
		return new ArrayList<>();
	}
}
