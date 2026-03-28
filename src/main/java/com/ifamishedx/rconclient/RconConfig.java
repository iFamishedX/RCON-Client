package com.ifamishedx.rconclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/**
 * Persists RCON connection parameters (host, port, obfuscated password) to a
 * Properties file inside the Fabric config directory.
 *
 * <p>The password is stored using a simple XOR + Base64 obfuscation so that it
 * is not saved in plaintext.  This is <em>not</em> cryptographically secure but
 * satisfies the requirement that the password must not be visible as plain text
 * in the config file.
 */
public class RconConfig {

    private static final String CONFIG_FILE_NAME = "rconclient.properties";
    // XOR key used for password obfuscation – never logged or exposed
    private static final byte[] OBFUSCATION_KEY = "FabRCONClientMod!2024".getBytes(StandardCharsets.UTF_8);

    private String host;
    private int    port;
    private String password; // kept in-memory only as plaintext

    private final Path configPath;

    public RconConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE_NAME);
    }

    // -------------------------------------------------------------------------
    // Load / Save
    // -------------------------------------------------------------------------

    /** Load parameters from the config file (if it exists). */
    public void load() {
        if (!Files.exists(configPath)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(configPath)) {
            props.load(is);
        } catch (IOException e) {
            RconClientMod.LOGGER.error("Failed to load RCON config: {}", e.getMessage());
            return;
        }

        host = props.getProperty("host");
        String portStr = props.getProperty("port");
        if (portStr != null) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                RconClientMod.LOGGER.warn("Invalid port in config: {}", portStr);
            }
        }
        String obfPw = props.getProperty("password");
        if (obfPw != null && !obfPw.isEmpty()) {
            try {
                password = deobfuscate(obfPw);
            } catch (Exception e) {
                RconClientMod.LOGGER.warn("Could not decode stored password; clearing it.");
                password = null;
            }
        }
        RconClientMod.LOGGER.info("RCON config loaded (host={}, port={})", host, port);
    }

    /** Save current parameters to the config file. */
    public void save() {
        Properties props = new Properties();
        if (host     != null) props.setProperty("host",     host);
        if (port     >  0)   props.setProperty("port",     String.valueOf(port));
        if (password != null) props.setProperty("password", obfuscate(password));

        try (OutputStream os = Files.newOutputStream(configPath)) {
            props.store(os, "RCON Client configuration – do not edit the password value manually");
        } catch (IOException e) {
            RconClientMod.LOGGER.error("Failed to save RCON config: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Obfuscation helpers
    // -------------------------------------------------------------------------

    /**
     * XOR each byte of {@code plaintext} with the rolling key, then Base64-encode
     * the result.
     */
    private static String obfuscate(String plaintext) {
        byte[] bytes  = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ OBFUSCATION_KEY[i % OBFUSCATION_KEY.length]);
        }
        return Base64.getEncoder().encodeToString(result);
    }

    /** Reverse of {@link #obfuscate}. */
    private static String deobfuscate(String encoded) {
        byte[] bytes  = Base64.getDecoder().decode(encoded);
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ OBFUSCATION_KEY[i % OBFUSCATION_KEY.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getHost()     { return host; }
    public int    getPort()     { return port; }
    public String getPassword() { return password; }

    /** Return {@code "host:port"} or {@code null} if not configured. */
    public String getHostPort() {
        if (host != null && port > 0) {
            return host + ":" + port;
        }
        return null;
    }

    public void setHost(String host)         { this.host     = host; }
    public void setPort(int port)            { this.port     = port; }
    public void setPassword(String password) { this.password = password; }
}
