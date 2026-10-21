package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

import javafx.application.Platform;
import java.net.*;
import java.io.*;
import java.util.function.Consumer;

/**
 * Správa komunikace přes UDP soket.
 */
public class Komunikace {


    private DatagramSocket socket;
    private final String serverIP;
    private final int port;


    private Thread listenerThread;
    private volatile boolean listening = false;


    private Thread monitorThread;
    private volatile long lastPacketTime = 0;
    private volatile boolean isLagging = false;
    private volatile boolean isDisconnected = false;
    private String currentNickname = null;


    private Consumer<String> onMessageReceived;
    private Consumer<String> onConnectionStateChanged;
    private Runnable onConnectionRestored;

    public Komunikace(String serverIP, int port) {
        this.serverIP = serverIP;
        this.port = port;
    }

    public void setOnMessageReceived(Consumer<String> callback) {
        this.onMessageReceived = callback;
    }

    public void setOnConnectionStateChanged(Consumer<String> callback) {
        this.onConnectionStateChanged = callback;
    }

    public void setOnConnectionRestored(Runnable callback) {
        this.onConnectionRestored = callback;
    }

    public void setNickname(String nickname) {
        this.currentNickname = nickname;
    }
    /**
     * Připojení k serveru vytvořením UDP soketu.
     */
    public boolean connect() {
        try {
            this.socket = new DatagramSocket();
            System.out.println("[Komunikace] Socket created on local port: " + socket.getLocalPort());


            this.lastPacketTime = System.currentTimeMillis();
            this.isLagging = false;


            startListener();


            startConnectionMonitor();

            return true;
        } catch (SocketException e) {
            System.err.println("[Komunikace] Failed to create socket: " + e.getMessage());
            return false;
        }
    }
    /**
     * Odeslání zprávy na server.
     *
     * @param cmd  Příkazový kód
     * @param body Tělo zprávy
     */
    public void send(int cmd, String body) {
        if (socket == null || socket.isClosed()) {
            System.err.println("[Komunikace] Cannot send - socket not connected");
            return;
        }
        try {
            if (body == null) body = "";
            String message = String.format("%s%d%02d%s", Konstanty.MAGIC, cmd, body.length(), body);

            InetAddress address = InetAddress.getByName(serverIP);
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
            socket.send(packet);
            System.out.println("[Komunikace] SENT: " + message);
        } catch (IOException e) {
            System.err.println("[Komunikace] Error sending message: " + e.getMessage());
        }
    }

    public void close() {
        disconnect();
    }

    public void disconnect() {
        System.out.println("[Komunikace] Disconnecting...");
        stopListener();
        if (socket != null && !socket.isClosed()) {
            socket.close();
            System.out.println("[Komunikace] Socket closed");
        }
    }
    /**
     * Spuštění posluchače pro příjem zpráv ze serveru.
     */
    public void startListener() {
        if (listening) return;
        listening = true;
        listenerThread = new Thread(() -> {
            System.out.println("[Komunikace] === Listener Thread Started ===");
            while (listening && socket != null && !socket.isClosed()) {
                try {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String rawData = new String(packet.getData(), 0, packet.getLength());
                    long now = System.currentTimeMillis();


                    if (rawData.startsWith(Konstanty.MAGIC + Konstanty.CMD_PING)) {
                        try {

                            String body = rawData.substring(6);
                            if (body.startsWith("PING ")) {
                                long serverTime = Long.parseLong(body.substring(5).trim());
                                long age = (now / 1000) - serverTime;

                                if (age > 3) {
                                    System.out.println("[Komunikace] Dropping STALE packet (age: " + age + "s)");
                                    continue;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("[Komunikace] Error processing PING packet: " + e.getMessage());
                        }
                    }


                    lastPacketTime = now;


                    if (rawData.startsWith(Konstanty.MAGIC + Konstanty.CMD_PING)) {
                        send(Konstanty.CMD_PING, "PONG");
                        System.out.println("[Komunikace] Sent PONG response to server");
                    }


                    if (isLagging || isDisconnected) {
                        System.out.println("[Komunikace] *** RECOVERED FROM " +
                                         (isDisconnected ? "DISCONNECT" : "LAG") + " ***");
                        isLagging = false;
                        isDisconnected = false;

                        if (onConnectionStateChanged != null) {
                            onConnectionStateChanged.accept("RECOVERED");
                        }

                        if (onConnectionRestored != null) {
                            Platform.runLater(onConnectionRestored);
                        }
                    }

                    if (onMessageReceived != null) {
                        onMessageReceived.accept(rawData);
                    }

                } catch (IOException e) {
                    if (listening) System.err.println("[Komunikace] Error receiving: " + e.getMessage());
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    /**
     * Spuštění monitoru pro sledování stavu připojení.
     */
    private void startConnectionMonitor() {
        if (monitorThread != null && monitorThread.isAlive()) return;

        monitorThread = new Thread(() -> {
            System.out.println("[Komunikace] Monitor started");
            while (listening) {
                try {
                    Thread.sleep(1000);
                    long silence = System.currentTimeMillis() - lastPacketTime;


                    if (silence > 10000 && silence < 11000) {
                        send(Konstanty.CMD_PING, "PING");
                    }


                    if (silence > 30000 && !isLagging && !isDisconnected) {
                        isLagging = true;
                        System.out.println("[Komunikace] ⚠️ LAGGING DETECTED (30s silent)");
                        if (onConnectionStateChanged != null) {
                            onConnectionStateChanged.accept("LAGGING");
                        }
                    }

                    if (silence > 60000 && !isDisconnected) {
                        isDisconnected = true;
                        isLagging = false;
                        System.out.println("[Komunikace] ❌ HARD DISCONNECT DETECTED (60s silent)");
                        if (onConnectionStateChanged != null) {
                            onConnectionStateChanged.accept("DISCONNECTED");
                        }
                    }

                    if (isLagging && !isDisconnected && (silence % 5000 < 1000) && currentNickname != null) {
                        System.out.println("[Komunikace] Auto-reconnect attempt (lagging)...");
                        send(Konstanty.CMD_LIST_ROOMS, currentNickname);
                    }

                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    private void stopListener() {
        listening = false;
        if (listenerThread != null) listenerThread.interrupt();
        if (monitorThread != null) monitorThread.interrupt();
    }
}