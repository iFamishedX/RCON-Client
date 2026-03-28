package com.ifamishedx.rconclient;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal RCON client implementing the Source RCON protocol.
 *
 * <p>Packet format (little-endian):
 * <pre>
 *   [4 bytes] packet length (excludes this field)
 *   [4 bytes] request ID
 *   [4 bytes] type
 *   [n bytes] null-terminated payload
 *   [1 byte]  null padding
 * </pre>
 *
 * <p>Types:
 * <ul>
 *   <li>3 – SERVERDATA_AUTH</li>
 *   <li>2 – SERVERDATA_EXECCOMMAND / SERVERDATA_AUTH_RESPONSE</li>
 *   <li>0 – SERVERDATA_RESPONSE_VALUE</li>
 * </ul>
 */
public class RconClient {

    private static final int TYPE_AUTH         = 3;
    private static final int TYPE_EXECCOMMAND  = 2;
    private static final int TYPE_AUTH_RESPONSE = 2;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 5_000;

    private String host;
    private int port;
    private String password;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final AtomicInteger requestIdCounter = new AtomicInteger(1);
    private volatile boolean connected     = false;
    private volatile boolean authenticated = false;

    public RconClient() {}

    /** Configure connection parameters (does not connect). */
    public synchronized void setParameters(String host, int port, String password) {
        this.host     = host;
        this.port     = port;
        this.password = password;
    }

    /**
     * Connect and authenticate.  Closes any existing connection first.
     *
     * @return {@code true} if connected and authenticated successfully.
     */
    public synchronized boolean connect() {
        disconnect();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());
            connected = true;
            return authenticate();
        } catch (Exception e) {
            RconClientMod.LOGGER.error("RCON connect failed: {}", e.getMessage());
            closeSocket();
            return false;
        }
    }

    /** Authenticate using the stored password. */
    private boolean authenticate() {
        try {
            int id = requestIdCounter.getAndIncrement();
            sendPacket(id, TYPE_AUTH, password);
            RconPacket response = readPacket();
            // Auth response: ID mirrors request on success, -1 on failure
            if (response != null && response.id != -1) {
                authenticated = true;
                return true;
            }
            RconClientMod.LOGGER.error("RCON authentication failed (wrong password?)");
            authenticated = false;
            return false;
        } catch (Exception e) {
            RconClientMod.LOGGER.error("RCON auth error: {}", e.getMessage());
            authenticated = false;
            return false;
        }
    }

    /**
     * Send a command to the RCON server and return the response string.
     * Auto-reconnects if the connection has dropped.
     *
     * @return response text, empty string if server sent no data, or {@code null} on error.
     */
    public synchronized String sendCommand(String command) {
        if (!connected || !authenticated) {
            if (!reconnect()) {
                return null;
            }
        }
        try {
            int id = requestIdCounter.getAndIncrement();
            sendPacket(id, TYPE_EXECCOMMAND, command);
            RconPacket response = readPacket();
            return response != null ? response.payload : null;
        } catch (Exception e) {
            RconClientMod.LOGGER.error("RCON sendCommand error: {}", e.getMessage());
            connected     = false;
            authenticated = false;
            return null;
        }
    }

    /** Attempt to reconnect using existing parameters. */
    private boolean reconnect() {
        if (host != null && port > 0 && password != null) {
            RconClientMod.LOGGER.info("Attempting RCON reconnect...");
            return connect();
        }
        return false;
    }

    /** Close the connection gracefully. */
    public synchronized void disconnect() {
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            RconClientMod.LOGGER.error("Error closing RCON socket: {}", e.getMessage());
        }
        socket        = null;
        in            = null;
        out           = null;
        connected     = false;
        authenticated = false;
    }

    // -------------------------------------------------------------------------
    // Packet I/O
    // -------------------------------------------------------------------------

    /** Write a packet to the server. */
    private void sendPacket(int id, int type, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        // length field = id(4) + type(4) + payload + null-terminator(1) + padding(1)
        int length = 4 + 4 + payloadBytes.length + 2;

        ByteBuffer buf = ByteBuffer.allocate(4 + length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(payloadBytes);
        buf.put((byte) 0); // null terminator
        buf.put((byte) 0); // padding

        out.write(buf.array());
        out.flush();
    }

    /** Read one packet from the server. */
    private RconPacket readPacket() throws IOException {
        // Read the 4-byte length field
        byte[] lenBytes = new byte[4];
        in.readFully(lenBytes);
        int length = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // Maximum RCON packet: 4096-byte payload + 10-byte header (length, id, type, two null bytes)
        if (length < 10 || length > 4106) {
            throw new IOException("Invalid RCON packet length: " + length);
        }

        byte[] data = new byte[length];
        in.readFully(data);

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int id   = buf.getInt();
        int type = buf.getInt();
        // Payload is everything between the type field and the two trailing null bytes
        int payloadLen = length - 10;
        byte[] payloadBytes = new byte[payloadLen];
        buf.get(payloadBytes);

        String payload = new String(payloadBytes, StandardCharsets.UTF_8);
        return new RconPacket(id, type, payload);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public boolean isConnected()     { return connected && authenticated; }
    public String  getHost()         { return host; }
    public int     getPort()         { return port; }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private static final class RconPacket {
        final int    id;
        final int    type;
        final String payload;

        RconPacket(int id, int type, String payload) {
            this.id      = id;
            this.type    = type;
            this.payload = payload;
        }
    }
}
