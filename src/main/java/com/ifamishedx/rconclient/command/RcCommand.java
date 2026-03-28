package com.ifamishedx.rconclient.command;

import com.ifamishedx.rconclient.RconClientMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;

/**
 * Registers the {@code /rc} command:
 * <ul>
 *   <li>{@code /rc} – switch to <em>RCON Chat Mode</em>.</li>
 *   <li>{@code /rc <command>} – send a one-off RCON command without changing mode.</li>
 * </ul>
 */
public class RcCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("rc")
                .executes(ctx -> {
                    // No arguments → switch to RCON Chat Mode
                    RconClientMod.getChatModeManager().enableRconMode();
                    ctx.getSource().sendFeedback(
                            Text.literal("[RCON] Chat redirected to RCON mode").formatted(Formatting.YELLOW));
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        // With arguments → one-off RCON command, no mode switch
                        String command = StringArgumentType.getString(ctx, "command");
                        ctx.getSource().sendFeedback(
                                Text.literal("[RCON] Sending: " + command).formatted(Formatting.GRAY));

                        CompletableFuture.runAsync(() -> {
                            String response = RconClientMod.getRconClient().sendCommand(command);
                            if (response == null) {
                                RconClientMod.sendMessageToPlayer(
                                        Text.literal("[RCON] Error – not connected. Use /rcon parameter first.")
                                                .formatted(Formatting.RED));
                            } else {
                                String display = response.isEmpty() ? "(no response)" : response;
                                RconClientMod.sendMessageToPlayer(
                                        Text.literal("[RCON] " + display).formatted(Formatting.GREEN));
                            }
                        });
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    }))
        );
    }
}
