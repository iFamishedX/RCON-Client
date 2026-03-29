package com.ifamishedx.rconclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ifamishedx.rconclient.command.AcCommand;
import com.ifamishedx.rconclient.command.RcCommand;
import com.ifamishedx.rconclient.command.RconCommand;

import java.util.concurrent.CompletableFuture;

/**
 * Main entry point for the RCON Client mod.
 * Initializes config, registers commands, and sets up chat interception.
 */
public class RconClientMod implements ClientModInitializer {

    public static final String MOD_ID = "rconclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RconClient rconClient;
    private static RconConfig config;
    private static ChatModeManager chatModeManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing RCON Client mod");

        // Initialize core components
        config = new RconConfig();
        rconClient = new RconClient();
        chatModeManager = new ChatModeManager();

        // Load config and apply saved parameters
        config.load();
        if (config.getHost() != null && config.getPort() > 0 && config.getPassword() != null) {
            rconClient.setParameters(config.getHost(), config.getPort(), config.getPassword());
            LOGGER.info("Loaded RCON parameters from config, auto-connecting...");
            // Connect in background so we don't block startup
            CompletableFuture.runAsync(() -> {
                boolean ok = rconClient.connect();
                LOGGER.info("Auto-connect result: {}", ok ? "success" : "failed");
            });
        }

        // Register client-side commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            RconCommand.register(dispatcher);
            RcCommand.register(dispatcher);
            AcCommand.register(dispatcher);
        });

        // Intercept outgoing chat when in RCON mode
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (chatModeManager.isRconMode()) {
                // Run RCON command in background thread
                CompletableFuture.runAsync(() -> {
                    String response = rconClient.sendCommand(message);
                    String display = (response != null && !response.isEmpty())
                            ? response
                            : "(no response)";
                    sendMessageToPlayer(Component.literal("[RCON] " + display).withStyle(ChatFormatting.GREEN));
                });
                // Cancel normal chat
                return false;
            }
            return true;
        });

        // Clean up on client shutdown
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Disconnecting RCON on shutdown");
            rconClient.disconnect();
        });
    }

    /** Send a text message to the local player's chat HUD (must be called from any thread). */
    public static void sendMessageToPlayer(Component text) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(text);
            }
        });
    }

    public static RconClient getRconClient() {
        return rconClient;
    }

    public static RconConfig getConfig() {
        return config;
    }

    public static ChatModeManager getChatModeManager() {
        return chatModeManager;
    }
}
