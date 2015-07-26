package com.kyzeragon.autocraftmod;

import java.io.File;
import java.util.LinkedList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.network.INetHandler;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.lwjgl.input.Keyboard;

import com.mumfrey.liteloader.ChatFilter;
import com.mumfrey.liteloader.JoinGameListener;
import com.mumfrey.liteloader.OutboundChatListener;
import com.mumfrey.liteloader.Tickable;

public class LiteModAutoCraft implements Tickable, JoinGameListener, OutboundChatListener, ChatFilter
{
	private AutoInventory autoInv;
	private AutoWorkbench autoBench;
	private CraftSettings settings;
	
	private int msgcooldown;
	private String message;
	private boolean isError;
	private boolean sentCmd;
	private LinkedList<Click> clickQueue;
	private int currClickCooldown;
	private int maxClickCooldown;

	@Override
	public String getName() {return "AutoCraft";}

	@Override
	public String getVersion() {return "1.2.3";}

	@Override
	public void init(File configPath) 
	{
		this.settings = new CraftSettings();
		this.maxClickCooldown = this.settings.getMaxClickCooldown();
		this.currClickCooldown = 0;
		this.msgcooldown = 40;
		this.sentCmd = false;
		this.clickQueue = new LinkedList<Click>();
	}

	@Override
	public void upgradeSettings(String version, File configPath, File oldConfigPath) {}

	@Override
	public void onTick(Minecraft minecraft, float partialTicks, boolean inGame, boolean clock) 
	{
		if (!inGame)
			return;
		
		if (this.msgcooldown > 0)
		{
			this.msgcooldown--;
			this.displayMessage(this.message, this.isError);
		}
		
		while (!this.clickQueue.isEmpty() && this.currClickCooldown == 0)
		{
			Click currClick = this.clickQueue.pop();
			Minecraft.getMinecraft().playerController.windowClick(currClick.getWindowID(),
					currClick.getSlot(), currClick.getData(), currClick.getAction(), 
					Minecraft.getMinecraft().thePlayer);
			if (!currClick.getDoNext())
				this.currClickCooldown = this.maxClickCooldown;
		}
		if (this.currClickCooldown > 0)
			this.currClickCooldown--;
		
		if (minecraft.thePlayer.openContainer != null
				&& minecraft.currentScreen instanceof GuiInventory)
		{
			if (Keyboard.isKeyDown(Keyboard.KEY_RETURN))
			{
				if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
					this.autoInv.storeCrafting();
				else if (this.clickQueue.isEmpty())
					this.autoInv.craft();
			}
		}
		else if (minecraft.thePlayer.openContainer != null
				&& minecraft.currentScreen instanceof GuiCrafting)
		{
			if (Keyboard.isKeyDown(Keyboard.KEY_RETURN))
			{
				if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
					this.autoBench.storeCrafting();
				else if (this.clickQueue.isEmpty())
					this.autoBench.craft();
			}
		}
		// TODO: delay?
		// TODO: auto-combine stuff still needs better algorithm
	}

	@Override
	public void onSendChatMessage(C01PacketChatMessage packet, String message) 
	{
		String[] tokens = message.split(" ");
		if (tokens[0].equalsIgnoreCase("/autocraft"))
		{
			this.sentCmd = true;
			if (tokens.length == 1)
			{
				this.logMessage("�2" + this.getName() + " �8[�2v" + this.getVersion() + "�8] �aby Kyzeragon", false);
				this.logMessage("Type �2/autocraft help �afor commands.", false);
				return;
			}
			if (tokens[1].equalsIgnoreCase("delay"))
			{
				if (tokens.length == 2)
					this.logMessage("Current crafting delay is " + this.maxClickCooldown, true);
				else if (!tokens[2].matches("[0-9]+"))
					this.logError("Must be an integer. Recommended 0~4");
				else
				{
					this.maxClickCooldown = Integer.parseInt(tokens[2]);
					this.settings.setMaxClickCooldown(this.maxClickCooldown);
					this.logMessage("Crafting delay set to " + this.maxClickCooldown, true);
				}
			}
			else if (tokens[1].equalsIgnoreCase("help"))
			{
				String[] commands = {"delay <number> - Sets the delay (in ticks) for craft clicking",
						"help - Displays this help message"};
				this.logMessage("�2" + this.getName() + " �8[�2v" + this.getVersion() + "�8] �acommands:", false);
				for (String command: commands)
					this.logMessage("/autocraft " + command, false);
			}
		}
	}

	@Override
	public boolean onChat(S02PacketChat chatPacket, IChatComponent chat, String message) 
	{
		if (message.matches(".*nknown.*ommand.*") && this.sentCmd)
		{
			this.sentCmd = false;
			return false;
		}
		return true;
	}

	@Override
	public void onJoinGame(INetHandler netHandler, S01PacketJoinGame joinGamePacket) 
	{
		this.autoInv = new AutoInventory(this);
		this.autoBench = new AutoWorkbench(this);
	}

	public void message(String message, boolean isError)
	{
		this.message = message;
		this.isError = isError;
		this.msgcooldown = 40;
	}

	private void displayMessage(String message, boolean isError)
	{
		int color = 0xFF5555;
		if (!isError)
			color = 0x55FF55;
		FontRenderer fontRender = Minecraft.getMinecraft().fontRenderer;
		fontRender.drawStringWithShadow(message, 
				Minecraft.getMinecraft().displayWidth/4 - fontRender.getStringWidth(message)/2, 
				Minecraft.getMinecraft().displayHeight/4 - 100, color);
	}
	
	/**
	 * Queues a click to be run later
	 * @param click
	 */
	public void queueClicks(LinkedList<Click> queue)
	{
		this.clickQueue.addAll(queue);
	}

	/**
	 * Logs the message to the user
	 * @param message The message to log
	 * @param addPrefix Whether to add the mod-specific prefix or not
	 */
	public static void logMessage(String message, boolean addPrefix)
	{// "�8[�2�8] �a"
		if (addPrefix)
			message = "�8[�2AutoCraft�8] �a" + message;
		ChatComponentText displayMessage = new ChatComponentText(message);
		displayMessage.setChatStyle((new ChatStyle()).setColor(EnumChatFormatting.GREEN));
		Minecraft.getMinecraft().thePlayer.addChatComponentMessage(displayMessage);
	}

	/**
	 * Logs the error message to the user
	 * @param message The error message to log
	 */
	public static void logError(String message)
	{
		ChatComponentText displayMessage = new ChatComponentText("�8[�4!�8] �c" + message + " �8[�4!�8]");
		displayMessage.setChatStyle((new ChatStyle()).setColor(EnumChatFormatting.RED));
		Minecraft.getMinecraft().thePlayer.addChatComponentMessage(displayMessage);
	}
}
