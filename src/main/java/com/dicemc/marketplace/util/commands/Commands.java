package com.dicemc.marketplace.util.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dicemc.marketplace.Main;
import com.dicemc.marketplace.core.AccountGroup;
import com.dicemc.marketplace.core.Guild;
import com.dicemc.marketplace.core.MarketItem;
import com.dicemc.marketplace.core.WhitelistItem;
import com.dicemc.marketplace.gui.GuiChunkManager;
import com.dicemc.marketplace.gui.GuiChunkManager.ChunkSummary;
import com.dicemc.marketplace.item.ModItems;
import com.dicemc.marketplace.gui.GuiMarketSell;
import com.dicemc.marketplace.network.MessageAdminGuiOpen;
import com.dicemc.marketplace.util.Reference;
import com.dicemc.marketplace.util.capabilities.ChunkCapability;
import com.dicemc.marketplace.util.capabilities.ChunkProvider;
import com.dicemc.marketplace.util.datasaver.AccountSaver;
import com.dicemc.marketplace.util.datasaver.GuildSaver;
import com.dicemc.marketplace.util.datasaver.MarketSaver;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.chunk.Chunk;

public class Commands extends CommandBase{

	@Override
	public String getName() { return "gncadmin"; }

	@Override
	public String getUsage(ICommandSender sender) { return "/gncadmin <account/market/guild>"; }
	
	private void message(String str, ICommandSender sender) {
		sender.sendMessage(new TextComponentString(str));
	}
	
	public static UUID playerUUIDfromString (MinecraftServer server, String username) {
		return (EntityPlayerMP.getUUID(server.getPlayerProfileCache().getGameProfileForUsername(username)) != null) ? EntityPlayerMP.getUUID(server.getPlayerProfileCache().getGameProfileForUsername(username)) : null;
	}
	public static String playerNamefromUUID (MinecraftServer server, UUID player) {	
		return (server.getPlayerProfileCache().getProfileByUUID(player).getName() != null) ? server.getPlayerProfileCache().getProfileByUUID(player).getName() : null;
	}
	public static String guildNamefromUUID (MinecraftServer server, UUID guild) {	
		for (Guild g : GuildSaver.get(server.getEntityWorld()).GUILDS) {
			if (g.guildID.equals(guild)) return g.guildName;
		}
		return "GuildNotFound";
	}
	
	@Override
	public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
		if (sender.canUseCommand(2, this.getName())) return true;
		return false;
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		ItemStack item;
		EntityPlayerMP plyr;
		if (args.length == 0) {
			message("/gncadmin gui   (opens admin gui)", sender);
			message("/gncadmin whitelist  (displays the current chunk whitelist)", sender);
			message("/gncadmin moneybag <amount>  (gives the admin a moneybag)", sender);
			message("/gncadmin landreset  (sets chunk to default settings)", sender);
			message("/gncadmin account <player/guild> <set/add> <name> <amount>", sender);
			message("/gncadmin guild <create/claim> <name>", sender);
			return;
		}
		switch(args[0]) {
		case "gui": {
			Main.NET.sendTo(new MessageAdminGuiOpen(), (EntityPlayerMP) sender);
			break;
		}
		case "whitelist": {
			int cX = sender.getCommandSenderEntity().chunkCoordX;
			int cZ = sender.getCommandSenderEntity().chunkCoordZ;
			ChunkCapability cap = sender.getCommandSenderEntity().getEntityWorld().getChunkFromChunkCoords(cX, cZ).getCapability(ChunkProvider.CHUNK_CAP, null);
			for (int i = 0; i < cap.getWhitelist().size(); i++) {
				WhitelistItem wl = cap.getWhitelist().get(i);
				String msg = "["+wl.getBlock()+"] ["+wl.getEntity()+"]Break:"+String.valueOf(wl.getCanBreak()+"Interact:"+String.valueOf(wl.getCanInteract()));
				message(msg, sender);
			}
			break;
		}
		case "moneybag": {
			item = new ItemStack(ModItems.MONEYBAG);
			item.setTagInfo("value", new NBTTagDouble(Math.abs(Double.valueOf(args[1]))));
			server.getPlayerList().getPlayerByUUID(sender.getCommandSenderEntity().getUniqueID()).addItemStackToInventory(item);
			message("$"+args[1]+" Moneybag placed in inventory.", sender);
			break;
		}
		case "landreset": {
			int cX = sender.getCommandSenderEntity().chunkCoordX;
			int cZ = sender.getCommandSenderEntity().chunkCoordZ;
			ChunkCapability cap = sender.getCommandSenderEntity().getEntityWorld().getChunkFromChunkCoords(cX, cZ).getCapability(ChunkProvider.CHUNK_CAP, null);
			UUID owningGuild = cap.getOwner();
			cap.setOwner(Reference.NIL);
			cap.setForSale(false);
			cap.setOutpost(false);
			cap.setPlayers(new ArrayList<UUID>());
			cap.setPrice(Main.ModConfig.LAND_DEFAULT_COST);
			cap.setPublic(false);
			cap.setTempTime(System.currentTimeMillis());
			cap.fromNBTWhitelist(new NBTTagList());
			List<ChunkPos> list = GuildSaver.get(sender.getEntityWorld()).GUILDS.get(GuildSaver.get(sender.getEntityWorld()).guildIndexFromUUID(owningGuild)).coreLand;
			for (int i = list.size()-1; i > -1; i--) {
				if (list.get(i).x == cX && list.get(i).z == cZ) list.remove(i);
			}
			list = GuildSaver.get(sender.getEntityWorld()).GUILDS.get(GuildSaver.get(sender.getEntityWorld()).guildIndexFromUUID(owningGuild)).outpostLand;
			for (int i = list.size()-1; i > -1; i--) {
				if (list.get(i).x == cX && list.get(i).z == cZ) list.remove(i);
			}
			GuildSaver.get(sender.getEntityWorld()).markDirty();
			break;
		}
		case "account": {
			switch(args[1]) {
			//base arguments <player/guild>
			case "player": {
				AccountGroup acctPlayers = AccountSaver.get(server.getEntityWorld()).getPlayers();
				switch(args[2]) {
				//base arguments <balance/set/add>
				case "set": {
					acctPlayers.setBalance(playerUUIDfromString(server, args[3]), Double.valueOf(args[4]));
					AccountSaver.get(sender.getEntityWorld()).markDirty();
					message(args[3]+" account set to $"+args[4], sender);
					break;
				}
				case "add": {
					acctPlayers.addBalance(playerUUIDfromString(server, args[3]), Double.valueOf(args[4]));
					AccountSaver.get(sender.getEntityWorld()).markDirty();
					message("$"+args[4]+" added to "+args[3]+"'s account.", sender);
					break;
				}
				default: message("Invalid player account action.", sender);
				}			
				break;
			}
			case "guild": {
				AccountGroup acctGuilds = AccountSaver.get(server.getEntityWorld()).getGuilds();
				switch(args[2]) {
				//base arguments <balance/set/add>
				case "set": {
					acctGuilds.setBalance(GuildSaver.get(sender.getEntityWorld()).guildUUIDfromName(args[3]), Double.valueOf(args[4]));
					AccountSaver.get(sender.getEntityWorld()).markDirty();
					message(args[3]+" account set to $"+args[4], sender);
					break;
				}
				case "add": {
					acctGuilds.addBalance(GuildSaver.get(sender.getEntityWorld()).guildUUIDfromName(args[3]), Double.valueOf(args[4]));
					AccountSaver.get(sender.getEntityWorld()).markDirty();
					message(args[3]+" account changed by $"+args[4], sender);
					break;
				}
				default: message("Invalid guild account action.", sender);
				}
				break;
			}
			default: message("Invalid account type.", sender);
			}
			break;
		}
		case "guild": {
			List<Guild> glist = GuildSaver.get(sender.getEntityWorld()).GUILDS;
			switch(args[1]) {
			//base arguments <create/set/list/claim>
			case "create": {
				GuildSaver.get(sender.getEntityWorld()).GUILDS.add(new Guild(args[2]));
				GuildSaver.get(sender.getEntityWorld()).GUILDS.get(GuildSaver.get(sender.getEntityWorld()).guildIndexFromName(args[2])).isAdmin = true;
				GuildSaver.get(sender.getEntityWorld()).markDirty();
				AccountSaver.get(sender.getEntityWorld()).getGuilds().addAccount(GuildSaver.get(sender.getEntityWorld()).guildUUIDfromName(args[2]), Main.ModConfig.GUILD_STARTING_FUNDS);
				AccountSaver.get(sender.getEntityWorld()).markDirty();
				message("Guild "+args[2]+" created.", sender);
				break;
			}
			case "claim": {
				UUID owningGuild = GuildSaver.get(sender.getEntityWorld()).guildUUIDfromName(args[2]);
				int cX = sender.getCommandSenderEntity().chunkCoordX;
				int cZ = sender.getCommandSenderEntity().chunkCoordZ;
				ChunkCapability cap = sender.getCommandSenderEntity().getEntityWorld().getChunkFromChunkCoords(cX, cZ).getCapability(ChunkProvider.CHUNK_CAP, null);
				cap.setOwner(owningGuild);
				GuildSaver.get(sender.getEntityWorld()).GUILDS.get(GuildSaver.get(sender.getEntityWorld()).guildIndexFromUUID(owningGuild)).coreLand.add(new ChunkPos(cX, cZ));
				GuildSaver.get(sender.getEntityWorld()).markDirty();
				sender.getCommandSenderEntity().getEntityWorld().getChunkFromChunkCoords(cX, cZ).markDirty();
				message("Claimed Chunk ("+String.valueOf(cX)+","+String.valueOf(cZ)+") for "+GuildSaver.get(sender.getEntityWorld()).GUILDS.get(GuildSaver.get(sender.getEntityWorld()).guildIndexFromUUID(owningGuild)).guildName , sender);
				break;
			}
			default: message("Guild option not recognized.", sender);
			}
			break;
		}
		default: message("Invalid Argument.", sender);
		}
	}

}
