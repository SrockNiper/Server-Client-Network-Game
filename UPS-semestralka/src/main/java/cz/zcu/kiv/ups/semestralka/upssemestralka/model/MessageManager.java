package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

/**
 * Správce zpráv - dispatcher příchozích UDP paketů
 */
public class MessageManager {

    private final Komunikace komunikace;
    private final LobbyModel lobbyModel;
    private final RoomModel roomModel;
    private final GameModel gameModel;

    public MessageManager(Komunikace komunikace, LobbyModel lobbyModel, RoomModel roomModel, GameModel gameModel) {
        this.komunikace = komunikace;
        this.lobbyModel = lobbyModel;
        this.roomModel = roomModel;
        this.gameModel = gameModel;

        komunikace.setOnMessageReceived(this::handleIncomingMessage);
    }

    /**
     * Zpracování příchozí UDP zprávy
     */
    private void handleIncomingMessage(String rawData) {
        try {
            if (!rawData.startsWith(Konstanty.MAGIC)) {
                System.err.println("[Dispatcher] Invalid magic: " + rawData.substring(0, Math.min(10, rawData.length())));
                return;
            }

            if (rawData.length() < Konstanty.HEADER_LENGTH) {
                System.err.println("[Dispatcher] Packet too short: " + rawData);
                return;
            }

            int cmdId = Character.getNumericValue(rawData.charAt(3));
            int bodyLength = Integer.parseInt(rawData.substring(4, 6));
            String body = rawData.substring(6, Math.min(6 + bodyLength, rawData.length()));

            body = processReliableMessage(body);

            if (body.startsWith("ERR")) {
                System.err.println("[Dispatcher] Error from server: " + body);
                gameModel.handleEventPacket(body);
            }

            routeCommand(cmdId, body);

        } catch (Exception e) {
            System.err.println("[Dispatcher] Error handling packet: " + e.getMessage());
        }
    }

    /**
     * Zpracování spolehlivé zprávy s ACK
     */
    private String processReliableMessage(String body) {
        if (body.matches("^\\d+:.*")) {
            int colonIndex = body.indexOf(':');
            String messageId = body.substring(0, colonIndex);
            komunikace.send(Konstanty.CMD_ACK, messageId);
            return body.substring(colonIndex + 1);
        }
        return body;
    }

    /**
     * Směrování příkazu do příslušného modelu
     */
    private void routeCommand(int cmdId, String body) {
        switch (cmdId) {
            case Konstanty.CMD_DISCONNECT -> handleDisconnectCommand(body);
            case Konstanty.CMD_LIST_ROOMS -> handleListRoomsCommand(body);
            case Konstanty.CMD_JOIN_ROOM -> handleJoinRoomCommand(body);
            case Konstanty.CMD_GAME_PASS -> gameModel.handleDicePacket(body);
            case Konstanty.CMD_GAME_REROLL -> handleRerollCommand(body);
            case Konstanty.CMD_GAME_WIN -> gameModel.handleWinResponse(body);
            case Konstanty.CMD_RETURN_TO_LOBBY -> handleReturnToLobbyCommand(body);
            case Konstanty.CMD_VOTE -> gameModel.handleEventPacket(body);
            case Konstanty.CMD_ACK -> gameModel.clearPendingMove();
            case Konstanty.CMD_PING -> { /* Zpracováno v Komunikace */ }
            default -> System.out.println("[Dispatcher] Unknown command: " + cmdId);
        }
    }

    /**
     * Zpracování odpojení
     */
    private void handleDisconnectCommand(String body) {
        if (body.contains("Voted")) {
            roomModel.handleLeaveLobbyPacket("OK");
        } else {
            komunikace.disconnect();
        }
    }

    /**
     * Zpracování seznamu místností
     */
    private void handleListRoomsCommand(String body) {
        checkAndRecoverGhostState();
        lobbyModel.handleRoomListPacket(body);
    }

    /**
     * Detekce a oprava ghost stavu
     */
    private void checkAndRecoverGhostState() {
        if (roomModel.getCurrentRoomId() != -1 && !gameModel.isGameOver()) {
            gameModel.reset();
            roomModel.reset();
            roomModel.handleLeaveLobbyPacket("OK");
        }
    }

    /**
     * Zpracování připojení do místnosti
     */
    private void handleJoinRoomCommand(String body) {
        if (body.equals("OK")) {
            gameModel.reset();
            roomModel.reset();
        }
        roomModel.handleJoinPacket(body);
    }

    /**
     * Zpracování reroll odpovědi
     */
    private void handleRerollCommand(String body) {
        if (body.contains(";")) {
            String[] parts = body.split(";");
            if (parts.length == 2) {
                gameModel.handlePlayerStatusPacket(body);
            } else if (parts.length == 3) {
                gameModel.handlePlayerListPacket(body);
            }
        }
    }

    private void handleReturnToLobbyCommand(String body) {
        if (gameModel.isGameOver()) {
            return;
        }

        if (body.startsWith("OK")) {
            gameModel.reset();
            roomModel.reset();
        }

        roomModel.handleLeaveLobbyPacket(body);
    }

    public Komunikace getKomunikace() {
        return komunikace;
    }

    public LobbyModel getLobbyModel() {
        return lobbyModel;
    }

    public RoomModel getRoomModel() {
        return roomModel;
    }

    public GameModel getGameModel() {
        return gameModel;
    }
}

