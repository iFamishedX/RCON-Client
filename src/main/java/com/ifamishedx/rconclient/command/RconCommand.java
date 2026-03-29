package com.ifamishedx.rconclient.command;

import com.ifamishedx.rconclient.RconClient;
import com.ifamishedx.rconclient.RconClientMod;
import com.ifamishedx.rconclient.RconConfig;
import com.mojang.brigadier.CommandDispatcher;
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
 *   <li>{@code /rcon parameter <host:port> <password>} – set RCON parameters
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
                    .then(ClientCommandManager.argument("hostport", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            // Suggest the currently saved host:port (if any)
                            String saved = RconClientMod.getConfig().getHostPort();
                            if (saved != null) builder.suggest(saved);
                            return builder.buildFuture();
                        })
                        .then(ClientCommandManager.argument("password", StringArgumentType.string())
                            .executes(ctx -> {
                                String hostPort = StringArgumentType.getString(ctx, "hostport");
                                String password = StringArgumentType.getString(ctx, "password");
                                return setParameters(ctx.getSource(), hostPort, password);
                            }))))
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
        source.sendFeedback(Component.literal("/rcon parameter <host:port> <password>").withStyle(ChatFormatting.YELLOW)
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

    private static int setParameters(FabricClientCommandSource source, String hostPort, String password) {
        // Parse host and port from "host:port"
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx <= 0 || colonIdx >= hostPort.length() - 1) {
            source.sendError(Component.literal("Invalid format. Use host:port (e.g. localhost:25575)"));
            return 0;
        }
        String host = hostPort.substring(0, colonIdx);
        int port;
        try {
            port = Integer.parseInt(hostPort.substring(colonIdx + 1));
        } catch (NumberFormatException e) {
            source.sendError(Component.literal("Invalid port number in: " + hostPort));
            return 0;
        }
        if (port < 1 || port > 65535) {
            source.sendError(Component.literal("Port must be between 1 and 65535"));
            return 0;
        }

        // Persist parameters (password is obfuscated in the config file)
        RconConfig config = RconClientMod.getConfig();
        config.setHost(host);
        config.setPort(port);
        config.setPassword(password);
        config.save();

        // Apply to client and connect in background
        RconClient rcon = RconClientMod.getRconClient();
        rcon.setParameters(host, port, password);
        source.sendFeedback(Component.literal("[RCON] Connecting to " + hostPort + "...").withStyle(ChatFormatting.YELLOW));

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
