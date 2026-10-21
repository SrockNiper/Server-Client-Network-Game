package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.function.Consumer;

/**
 * Model místnosti - správa stavu připojení/odpojení.
 */
public class RoomModel {
    // Vlastnosti výsledku připojení
    private final SimpleBooleanProperty joinSuccess = new SimpleBooleanProperty(false);
    private final SimpleStringProperty joinError = new SimpleStringProperty("");

    // Vlastnosti výsledku odpojení
    private final SimpleBooleanProperty leaveSuccess = new SimpleBooleanProperty(false);

    // Aktuální ID místnosti (-1 znamená, že hráč není v místnosti)
    private int currentRoomId = -1;

    // Notifikace událostí (JOINED, LEFT)
    private Consumer<String> onRoomEvent;

    /**
     * Vrací property úspěchu připojení pro binding.
     */
    public SimpleBooleanProperty joinSuccessProperty() {
        return joinSuccess;
    }

    /**
     * Vrací property chyby připojení pro binding.
     */
    public SimpleStringProperty joinErrorProperty() {
        return joinError;
    }

    /**
     * Vrací property úspěchu odpojení pro binding.
     */
    public SimpleBooleanProperty leaveSuccessProperty() {
        return leaveSuccess;
    }

    /**
     * Nastavení callbacku pro události v místnosti.
     */
    public void setOnRoomEvent(Consumer<String> callback) {
        this.onRoomEvent = callback;
    }

    /**
     * Vrací aktuální ID místnosti (-1 pokud hráč není v místnosti).
     */
    public int getCurrentRoomId() {
        return currentRoomId;
    }

    /**
     * Nastavení aktuálního ID místnosti.
     */
    public void setCurrentRoomId(int roomId) {
        this.currentRoomId = roomId;
        System.out.println("[RoomModel] Current room ID set to: " + roomId);
    }

    /**
     * Zpracování paketu JOIN_ROOM (CMD 2).
     */
    public void handleJoinPacket(String body) {
        System.out.println("[RoomModel] Join packet: " + body);

        if (body.equals("OK")) {
            // Join successful - room ID will be set externally by controller
            Platform.runLater(() -> {
                joinSuccess.set(true);
                joinError.set("");
            });
        } else if (body.startsWith("ERR")) {
            // Join failed
            Platform.runLater(() -> {
                joinSuccess.set(false);
                joinError.set(body);
            });
        } else if (body.startsWith("JOINED")) {
            // Another player joined - forward to game event handler
            if (onRoomEvent != null) {
                Platform.runLater(() -> onRoomEvent.accept(body));
            }
        } else if (body.matches("\\d+")) {
            // Player count update - just log it
            System.out.println("[RoomModel] Room player count: " + body);
        } else {
            System.out.println("[RoomModel] Unknown join packet format: " + body);
        }
    }

    /**
     * Zpracování paketu RETURN_TO_LOBBY (CMD 6).
     */
    public void handleLeaveLobbyPacket(String body) {
        System.out.println("[RoomModel] Paket odpojení: " + body);

        boolean success = body.startsWith("OK");
        Platform.runLater(() -> leaveSuccess.set(success));
    }

    /**
     * Reset modelu při novém pokusu o připojení.
     */
    public void resetJoinState() {
        Platform.runLater(() -> {
            joinSuccess.set(false);
            joinError.set("");
        });
    }

    /**
     * Reset celého modelu při opuštění místnosti.
     */
    public void reset() {
        System.out.println("[RoomModel] Resetování stavu místnosti...");
        Platform.runLater(() -> {
            // Reset stavu připojení
            joinSuccess.set(false);
            joinError.set("");

            // Reset stavu odpojení
            leaveSuccess.set(false);

            // Reset ID místnosti
            currentRoomId = -1;

            System.out.println("[RoomModel] Reset dokončen");
        });
    }
}

