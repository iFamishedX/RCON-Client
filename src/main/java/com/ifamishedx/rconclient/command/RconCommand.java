package com.ifamishedx.rconclient.command;

import com.ifamishedx.rconclient.RconClient;
import com.ifamishedx.rconclient.RconClientMod;
import com.ifamishedx.rconclient.RconConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.concurrent.CompletableFuture;

/**
 * Registers the {@code /rcon} command with the following sub-commands:
 * <ul>
 *   <li>{@code /rcon} – show help, connection status, and current mode.</li>
 *   <li>{@code /rcon parameter <host> <port> <password>} – set RCON parameters
 *       and immediately attempt a connection.</li>
 * </ul>
 */
public class RconCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("rcon")
                .executes(ctx -> {
                    showHelp(ctx.getSource());
                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                })
                .then(ClientCommandManager.literal("parameter")
                    .then(ClientCommandManager.argument("host", StringArgumentType.word())
                        .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                            .then(ClientCommandManager.argument("password", StringArgumentType.word())
                                .executes(ctx -> {
                                    String host     = StringArgumentType.getString(ctx, "host");
                                    int    port     = IntegerArgumentType.getInteger(ctx, "port");
                                    String password = StringArgumentType.getString(ctx, "password");
                                    return setParameters(ctx.getSource(), host, port, password);
                                })))))
        );
    }

    // -------------------------------------------------------------------------

    private static void showHelp(FabricClientCommandSource source) {
        RconClient  client = RconClientMod.getRconClient();
        RconConfig  config = RconClientMod.getConfig();
        String      mode   = RconClientMod.getChatModeManager().getModeLabel();
        String      status = client.isConnected()
                ? "Connected to " + config.getHostPort()
                : (config.getHostPort() != null ? "Disconnected (last: " + config.getHostPort() + ")" : "Not configured");

        source.sendFeedback(Component.literal("--- RCON Client ---").withStyle(ChatFormatting.GOLD));
        source.sendFeedback(Component.literal("/rcon parameter <host> <port> <password>").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" – set parameters & connect").withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Component.literal("/rc [command]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" – RCON chat mode or one-off command").withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Component.literal("/ac [message]").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" – normal chat mode or one-off message").withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Component.literal("Status: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(status).withStyle(client.isConnected() ? ChatFormatting.GREEN : ChatFormatting.RED)));
        source.sendFeedback(Component.literal("Mode:   ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(mode).withStyle(ChatFormatting.WHITE)));
    }

    private static int setParameters(FabricClientCommandSource source, String host, int port, String password) {
        String address = host + ":" + port;

        // Persist parameters (password is obfuscated in the config file)
        RconConfig config = RconClientMod.getConfig();
        config.setHost(host);
        config.setPort(port);
        config.setPassword(password);
        config.save();

        // Apply to client and connect in background
        RconClient rcon = RconClientMod.getRconClient();
        rcon.setParameters(host, port, password);
        source.sendFeedback(Component.literal("[RCON] Connecting to " + address + "...").withStyle(ChatFormatting.YELLOW));

        CompletableFuture.runAsync(() -> {
            boolean ok = rcon.connect();
            if (ok) {
                RconClientMod.sendMessageToPlayer(
                        Component.literal("[RCON] Connected and authenticated successfully!").withStyle(ChatFormatting.GREEN));
            } else {
                RconClientMod.sendMessageToPlayer(
                        Component.literal("[RCON] Connection failed – check host, port, and password.").withStyle(ChatFormatting.RED));
            }
        });

        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }
}
