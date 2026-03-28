package com.ifamishedx.rconclient.command;

import com.ifamishedx.rconclient.RconClientMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                            Text.literal("[RCON] Chat returned to normal").formatted(Formatting.YELLOW));
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        // With arguments → one-off chat message, no mode switch
                        String message = StringArgumentType.getString(ctx, "message");
                        MinecraftClient client = MinecraftClient.getInstance();
                        ClientPlayNetworkHandler network = client.getNetworkHandler();
                        if (network == null) {
                            ctx.getSource().sendError(Text.literal("Not connected to a server."));
                            return 0;
                        }
                        // Send as normal chat (bypasses the RCON intercept)
                        network.sendChatMessage(message);
                        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                    }))
        );
    }
}
