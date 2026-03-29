package com.ifamishedx.rconclient.command;

import com.ifamishedx.rconclient.RconClientMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Registers the {@code /ac} command:
 * <ul>
 *   <li>{@code /ac} – switch back to <em>All Chat Mode</em> (normal chat).</li>
 *   <li>{@code /ac <message>} – send a one-off normal chat message without changing mode.</li>
 * </ul>
 */
public class AcCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("ac")
                .executes(ctx -> {
                    // No arguments → switch to All Chat Mode
                    RconClientMod.getChatModeManager().disableRconMode();
                    ctx.getSource().sendFeedback(
                            Component.literal("[RCON] Chat returned to normal").withStyle(ChatFormatting.YELLOW));
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        // With arguments → one-off chat message, no mode switch
                        String message = StringArgumentType.getString(ctx, "message");
                        Minecraft client = Minecraft.getInstance();
                        ClientPacketListener network = client.getConnection();
                        if (network == null) {
                            ctx.getSource().sendError(Component.literal("Not connected to a server."));
                            return 0;
                        }
                        // Send as normal chat (bypasses the RCON intercept)
                        network.sendChat(message);
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    }))
        );
    }
}
