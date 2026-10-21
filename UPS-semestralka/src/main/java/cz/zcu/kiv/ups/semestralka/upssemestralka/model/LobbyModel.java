package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

import cz.zcu.kiv.ups.semestralka.upssemestralka.Room;
import javafx.application.Platform;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Model lobby - správa stavu seznamu místností.
 */
public class LobbyModel {
    // Seznam místností
    private final ObservableList<Room> rooms = FXCollections.observableArrayList();
    private final SimpleListProperty<Room> roomsProperty = new SimpleListProperty<>(rooms);

    // Stav zpracování seznamu místností
    private int expectedRoomCount = 0;
    private List<Room> receivedRooms = new ArrayList<>();
    private boolean receivingRoomList = false;

    /**
     * Vrací property seznamu místností pro binding.
     */
    public SimpleListProperty<Room> roomsProperty() {
        return roomsProperty;
    }

    /**
     * Vrací aktuální seznam místností.
     */
    public ObservableList<Room> getRooms() {
        return rooms;
    }

    /**
     * Zpracování paketu se seznamem místností (CMD 1).
     * První paket: počet místností
     * Následující pakety: data o místnostech
     */
    public void handleRoomListPacket(String body) {
        if (body == null || body.isEmpty()) {
            return;
        }

        // Kontrola, zda jde o první paket (počet)
        if (!receivingRoomList) {
            // První paket - počet místností
            try {
                expectedRoomCount = Integer.parseInt(body);
                receivedRooms.clear();
                receivingRoomList = true;
                System.out.println("[LobbyModel] Očekávám " + expectedRoomCount + " místností");

                // Pokud je počet 0, ukončíme okamžitě
                if (expectedRoomCount == 0) {
                    finishRoomList();
                }
            } catch (NumberFormatException e) {
                System.err.println("[LobbyModel] Neplatný počet místností: " + body);
            }
        } else {
            // Následující pakety - data o místnostech
            Room room = parseRoomData(body);
            if (room != null) {
                receivedRooms.add(room);
                System.out.println("[LobbyModel] Přijata místnost " + receivedRooms.size() + "/" + expectedRoomCount);

                // Kontrola, zda jsme obdrželi všechny místnosti
                if (receivedRooms.size() >= expectedRoomCount) {
                    finishRoomList();
                }
            }
        }
    }

    /**
     * Dokončení příjmu seznamu místností a aktualizace observable property.
     */
    private void finishRoomList() {
        receivingRoomList = false;
        List<Room> newRooms = new ArrayList<>(receivedRooms);
        expectedRoomCount = 0;
        receivedRooms.clear();

        System.out.println("[LobbyModel] Seznam místností kompletní: " + newRooms.size() + " místností");

        // Aktualizace v JavaFX vlákně
        Platform.runLater(() -> {
            rooms.clear();
            rooms.addAll(newRooms);
        });
    }

    /**
     * Parsování dat o místnosti z řetězce.
     * Formát: "id;currentPlayers;maxPlayers;winningScore"
     */
    private Room parseRoomData(String body) {
        try {
            String[] parts = body.split(";");
            if (parts.length != 4) {
                System.err.println("[LobbyModel] Neplatný formát dat místnosti: " + body);
                return null;
            }

            int id = Integer.parseInt(parts[0].trim());
            int currentPlayers = Integer.parseInt(parts[1].trim());
            int maxPlayers = Integer.parseInt(parts[2].trim());
            int winningScore = Integer.parseInt(parts[3].trim());

            return new Room(id, currentPlayers, maxPlayers, winningScore);

        } catch (Exception e) {
            System.err.println("[LobbyModel] Chyba při parsování dat místnosti '" + body + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * UDP komunikační vrstva - zpracování pouze síťové komunikace.
     */
    public static class Komunikace {
        // Konstanty
        private static final String MAGIC = "DCG";

        // Síťování
        private DatagramSocket socket;
        private final String serverIP;
        private final int port;

        // Vlákno posluchače
        private Thread listenerThread;
        private volatile boolean listening = false;

        // Callback pro přijaté pakety
        private Consumer<String> onPacketReceived;

        /**
         * Konstruktor.
         *
         * @param serverIP IP adresa serveru
         * @param port Port serveru
         */
        public Komunikace(String serverIP, int port) {
            this.serverIP = serverIP;
            this.port = port;
        }

        /**
         * Připojení k serveru a spuštění vlákna posluchače.
         *
         * @return true při úspěchu, false jinak
         */
        public boolean connect() {
            try {
                // Vytvoření socketu
                this.socket = new DatagramSocket();
                System.out.println("[Komunikace] Socket vytvořen na lokálním portu: " + socket.getLocalPort());

                // Spuštění vlákna posluchače
                startListener();

                return true;
            } catch (SocketException e) {
                System.err.println("[Komunikace] Selhalo vytvoření socketu: " + e.getMessage());
                return false;
            }
        }

        /**
         * Odeslání zprávy na server.
         *
         * @param cmd ID příkazu (1-9)
         * @param body Tělo zprávy (může být null nebo prázdné)
         */
        public void send(int cmd, String body) {
            if (socket == null || socket.isClosed()) {
                System.err.println("[Komunikace] Nelze odeslat - socket není připojen");
                return;
            }

            try {
                // Formátování zprávy: DCG + CMD + LEN(2 číslice) + BODY
                String message = formatMessage(cmd, body);

                // Odeslání paketu
                InetAddress address = InetAddress.getByName(serverIP);
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);

                socket.send(packet);
                System.out.println("[Komunikace] ODESLÁNO: " + message);

            } catch (IOException e) {
                System.err.println("[Komunikace] Chyba při odesílání zprávy: " + e.getMessage());
            }
        }

        /**
         * Odpojení od serveru a zastavení posluchače.
         */
        public void disconnect() {
            System.out.println("[Komunikace] Odpojování...");

            // Zastavení vlákna posluchače
            stopListener();

            // Zavření socketu
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[Komunikace] Socket uzavřen");
            }
        }

        /**
         * Formátování zprávy podle protokolu.
         *
         * @param cmd ID příkazu
         * @param body Tělo zprávy
         * @return Zformátovaný řetězec zprávy
         */
        private String formatMessage(int cmd, String body) {
            if (body == null) {
                body = "";
            }
            return String.format("%s%d%02d%s", MAGIC, cmd, body.length(), body);
        }

        /**
         * Spuštění vlákna posluchače.
         */
        private void startListener() {
            if (listening) {
                System.out.println("[Komunikace] Posluchač již běží");
                return;
            }

            listening = true;
            listenerThread = new Thread(() -> {
                System.out.println("[Komunikace] === Vlákno posluchače spuštěno ===");

                while (listening && socket != null && !socket.isClosed()) {
                    try {
                        // Příjem paketu
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        // Konverze na řetězec
                        String rawData = new String(packet.getData(), 0, packet.getLength());
                        System.out.println("[Komunikace] PŘIJATO: " + rawData);

                        // Předání callbacku
                        if (onPacketReceived != null) {
                            onPacketReceived.accept(rawData);
                        }

                    } catch (SocketException e) {
                        if (listening) {
                            System.err.println("[Komunikace] Chyba socketu: " + e.getMessage());
                        }
                        break;
                    } catch (IOException e) {
                        if (listening) {
                            System.err.println("[Komunikace] Chyba při příjmu paketu: " + e.getMessage());
                        }
                    }
                }

                System.out.println("[Komunikace] === Vlákno posluchače zastaveno ===");
            });

            listenerThread.setDaemon(true);
            listenerThread.start();
        }

        /**
         * Zastavení vlákna posluchače.
         */
        private void stopListener() {
            if (listening) {
                System.out.println("[Komunikace] Zastavuji posluchač...");
                listening = false;

                if (listenerThread != null) {
                    listenerThread.interrupt();
                    try {
                        listenerThread.join(1000);
                    } catch (InterruptedException e) {
                        System.err.println("[Komunikace] Přerušeno při čekání na zastavení posluchače");
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
