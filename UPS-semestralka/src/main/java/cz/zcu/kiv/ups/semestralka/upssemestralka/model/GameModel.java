package cz.zcu.kiv.ups.semestralka.upssemestralka.model;

import cz.zcu.kiv.ups.semestralka.upssemestralka.PlayerInfo;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.function.Consumer;

/**
 * Model herního stavu - spravuje kostky, tahy, skóre a události
 */
public class GameModel {
    private String myNickname;

    private final SimpleBooleanProperty isMyTurn = new SimpleBooleanProperty(false);
    private final SimpleStringProperty activePlayerNick = new SimpleStringProperty("");

    private final IntegerProperty[] diceValues = new IntegerProperty[6];
    private final IntegerProperty activeDiceCount = new SimpleIntegerProperty(6);

    private final SimpleStringProperty gameStatus = new SimpleStringProperty("Čekání na hráče...");

    private final ObservableList<PlayerInfo> players = FXCollections.observableArrayList();
    private final SimpleListProperty<PlayerInfo> playersProperty = new SimpleListProperty<>(players);

    private final IntegerProperty roundScore = new SimpleIntegerProperty(0);
    private final IntegerProperty totalScore = new SimpleIntegerProperty(0);

    private final SimpleBooleanProperty winRejected = new SimpleBooleanProperty(false);

    private boolean isGameOver = false;

    private Consumer<String> onGameEvent;
    private Runnable onDiceUpdated;
    private Runnable onAckReceived;

    private PendingMove pendingMove = null;

    public GameModel() {
        for (int i = 0; i < 6; i++) {
            diceValues[i] = new SimpleIntegerProperty(0);
        }
    }

    /**
     * Čekající tah pro ACK systém
     */
    public static class PendingMove {
        public final int commandId;
        public final String mask;
        public final long timestamp;

        public PendingMove(int commandId, String mask) {
            this.commandId = commandId;
            this.mask = mask;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public void setPendingMove(int commandId, String mask) {
        this.pendingMove = new PendingMove(commandId, mask);
    }

    public void clearPendingMove() {
        if (pendingMove != null) {
            pendingMove = null;
            if (onAckReceived != null) {
                Platform.runLater(onAckReceived);
            }
        }
    }

    public PendingMove getPendingMove() {
        return pendingMove;
    }

    public boolean hasPendingMove() {
        return pendingMove != null;
    }

    public void setMyNickname(String nickname) {
        this.myNickname = nickname;
    }

    public String getMyNickname() {
        return myNickname;
    }

    public SimpleBooleanProperty isMyTurnProperty() {
        return isMyTurn;
    }

    public IntegerProperty diceValueProperty(int index) {
        if (index >= 0 && index < 6) {
            return diceValues[index];
        }
        return null;
    }

    public IntegerProperty activeDiceCountProperty() {
        return activeDiceCount;
    }

    public SimpleStringProperty gameStatusProperty() {
        return gameStatus;
    }

    public SimpleListProperty<PlayerInfo> playersProperty() {
        return playersProperty;
    }

    public ObservableList<PlayerInfo> getPlayers() {
        return players;
    }

    public IntegerProperty roundScoreProperty() {
        return roundScore;
    }

    public IntegerProperty totalScoreProperty() {
        return totalScore;
    }

    public SimpleBooleanProperty winRejectedProperty() {
        return winRejected;
    }

    public boolean isGameOver() {
        return isGameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.isGameOver = gameOver;
    }

    public void setOnGameEvent(Consumer<String> handler) {
        this.onGameEvent = handler;
    }

    public void setOnDiceUpdated(Runnable handler) {
        this.onDiceUpdated = handler;
    }

    public void setOnAckReceived(Runnable handler) {
        this.onAckReceived = handler;
    }

    // ========== Packet Handlery ==========

    /**
        * Zpracování paketu s kostkami (CMD 3)
     * Formát: "Axxxxxx" kde A je počet aktivních kostek (0-6) a x jsou hodnoty kostek (1-6)
     */
    public void handleDicePacket(String data) {
        if (data == null || data.length() != 7) {
            System.err.println("[GameModel] Invalid dice data: " + data);
            return;
        }

        System.out.println("[GameModel] Dice received: " + data);


        int activeCount = Character.getNumericValue(data.charAt(0));


        int[] newDiceValues = new int[6];
        for (int i = 0; i < 6; i++) {
            newDiceValues[i] = Character.getNumericValue(data.charAt(i + 1));
        }


        Platform.runLater(() -> {
            activeDiceCount.set(activeCount);
            for (int i = 0; i < 6; i++) {
                diceValues[i].set(newDiceValues[i]);
            }


            if (onDiceUpdated != null) {
                onDiceUpdated.run();
            }
        });
    }

    /**
     * Zpracování paketu se seznamem hráčů (CMD 4)
     * Formát: "Nick;Score;Status"
     */
    public void handlePlayerListPacket(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        try {
            String[] parts = data.split(";");
            if (parts.length != 3) {
                System.err.println("[GameModel] Invalid player data: " + data);
                return;
            }

            String nickname = parts[0].trim();
            int score = Integer.parseInt(parts[1].trim());
            String status = parts[2].trim();

            Platform.runLater(() -> {
                PlayerInfo existingPlayer = players.stream()
                        .filter(p -> p.getName().equals(nickname))
                        .findFirst()
                        .orElse(null);

                if (existingPlayer != null) {
                    existingPlayer.setScore(String.valueOf(score));

                    if (!"Lagging".equals(existingPlayer.getStatus())) {
                        existingPlayer.setStatus(status);
                    }
                } else {
                    players.add(new PlayerInfo(nickname, String.valueOf(score), status));
                }
            });

        } catch (Exception e) {
            System.err.println("[GameModel] Error parsing player data: " + e.getMessage());
        }
    }

    /**
     * Zpracování paketu se stavem hráče (CMD 4)
     * Formát: "Nick;Status"
     */
    public void handlePlayerStatusPacket(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }

        System.out.println("[GameModel] Player status update: " + data);

        try {
            String[] parts = data.split(";");
            if (parts.length != 2) {
                System.err.println("[GameModel] Invalid player status data: " + data);
                return;
            }

            String nickname = parts[0].trim();
            String status = parts[1].trim();


            Platform.runLater(() -> {
                PlayerInfo player = players.stream()
                        .filter(p -> p.getName().equals(nickname))
                        .findFirst()
                        .orElse(null);

                if (player != null) {
                    player.setStatus(status);
                    System.out.println("[GameModel] Updated " + nickname + " status to: " + status);
                } else {
                    System.err.println("[GameModel] Player not found: " + nickname);
                }
            });

        } catch (Exception e) {
            System.err.println("[GameModel] Error parsing player status data: " + e.getMessage());
        }
    }

    /**
    * Zpracování odpovědi na výhru (CMD 5)
     * Formát: "OK" nebo "ERR <důvod>"
     */
    public void handleWinResponse(String body) {
        System.out.println("[GameModel] Win response: " + body);

        if (body != null && (body.startsWith("ERR") || body.contains("ERR"))) {

            System.err.println("[GameModel] Win claim rejected by server: " + body);


            Platform.runLater(() -> {

                winRejected.set(true);

                Platform.runLater(() -> winRejected.set(false));
            });
        } else if (body != null && body.equals("OK")) {

            System.out.println("[GameModel] Win accepted by server");
        }
    }

    /**
    * Zpracování herního event paketu (CMD 7)
     * Formát: Různé textové příkazy
     * Podporované příkazy:
     * - "START" - začátek hry
     * - "TURN;Nick" nebo "TURN Nick" - změna tahu na hráče Nick
     * - "ROUND Nick Score" - aktualizace skóre kola pro hráče Nick
     * - "SCORE Nick Score" - aktualizace celkového skóre pro hráče Nick
     * - "FARKLE;Nick" nebo "FARKLE Nick" - hráč Nick dostal Farkle
     * - "WIN Nick" - hráč Nick vyhrál hru
     * - "VOTE_INIT Nick" - zahájení hlasování pro hráče Nick
     * - "VOTE_END" - ukončení hlasování
     * - "JOINED Nick" - hráč Nick se připojil
     * - "LEFT Nick" - hráč Nick odešel
     * - "PLAYER_LOST Nick" - hráč Nick prohrál
     * - "PLAYER_DISCONNECTED Nick" - hráč Nick se odpojil
     * - "LAGGING Nick" - hráč Nick má lagy
     */
    public void handleEventPacket(String body) {
        final String eventBody = (body == null) ? "" : body;

        System.out.println("[GameModel] Event: " + eventBody);

        if (eventBody.isEmpty() || eventBody.equals("START")) {

            handleGameStart();
        } else if (eventBody.startsWith("TURN;")) {

            String[] parts = eventBody.split(";");
            if (parts.length >= 2) {
                String nick = parts[1].trim();
                handleTurnChange(nick);
            }

            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("TURN ")) {

            String nick = eventBody.substring(5).trim();
            handleTurnChange(nick);

            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("ROUND ")) {

            String[] parts = eventBody.substring(6).trim().split(" ");
            if (parts.length >= 2) {
                String nick = parts[0].trim();
                int score = Integer.parseInt(parts[1].trim());
                handleRoundScore(nick, score);
            }
        } else if (eventBody.startsWith("SCORE ")) {

            String[] parts = eventBody.substring(6).trim().split(" ");
            if (parts.length >= 2) {
                String nick = parts[0].trim();
                int score = Integer.parseInt(parts[1].trim());
                handleTotalScore(nick, score);
            }
        } else if (eventBody.startsWith("FARKLE;")) {

            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("FARKLE ")) {

            String nick = eventBody.substring(7).trim();
            handleFarkle(nick);
        } else if (eventBody.startsWith("WIN ")) {

            String nick = eventBody.substring(4).trim();
            handleWin(nick);
        } else if (eventBody.startsWith("VOTE_INIT ")) {

            String laggingPlayer = eventBody.substring(10).trim();
            System.out.println("[GameModel] Vote initiated for: " + laggingPlayer);
            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.equals("VOTE_END")) {

            System.out.println("[GameModel] Vote ended");
            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("JOINED ")) {

            String joinedNick = eventBody.substring(7).trim();
            Platform.runLater(() -> {
                PlayerInfo player = players.stream()
                    .filter(p -> p.getName().equals(joinedNick))
                    .findFirst()
                    .orElse(null);

                if (player != null && "Lagging".equals(player.getStatus())) {
                    player.setStatus("Waiting");
                    System.out.println("[GameModel] Reset Lagging status for rejoined player: " + joinedNick);
                }
            });


            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("LEFT ") || eventBody.equals("READY")) {

            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("PLAYER_LOST ")) {

            String nick = eventBody.substring(12).trim();
            System.out.println("[GameModel] Player lost: " + nick);
            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        } else if (eventBody.startsWith("PLAYER_DISCONNECTED ")) {

            String nick = eventBody.substring(20).trim();
            System.out.println("[GameModel] Player disconnected: " + nick);


            Platform.runLater(() -> {
                PlayerInfo player = players.stream()
                        .filter(p -> p.getName().equals(nick))
                        .findFirst()
                        .orElse(null);

                if (player != null) {
                    player.setStatus("Disconnected");
                    System.out.println("[GameModel] Updated " + nick + " status to Disconnected");
                } else {
                    System.err.println("[GameModel] Player not found for disconnect: " + nick);
                }
            });


            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        }else if (eventBody.startsWith("LAGGING ")) {

            String nick = eventBody.substring(8).trim();
            System.out.println("[GameModel] Player lagging: " + nick);


            Platform.runLater(() -> {
                PlayerInfo player = players.stream()
                        .filter(p -> p.getName().equals(nick))
                        .findFirst()
                        .orElse(null);

                if (player != null) {
                    player.setStatus("Lagging");
                    System.out.println("[GameModel] Updated " + nick + " status to Lagging");
                }
            });


            if (onGameEvent != null) {
                Platform.runLater(() -> onGameEvent.accept(eventBody));
            }
        }else {
            System.out.println("[GameModel] Unknown event: " + eventBody);
        }
    }

    // ========== Event Handlery ==========

    /**
     * Zpracování začátku hry
     */
    private void handleGameStart() {
        Platform.runLater(() -> {
            gameStatus.set("Hra začíná...");
        });

        if (onGameEvent != null) {
            Platform.runLater(() -> onGameEvent.accept("START"));
        }
    }

    /**
     * Zpracování změny tahu
     */
    private void handleTurnChange(String activeNick) {
        String activeNickTrimmed = activeNick.trim();
        String myNickTrimmed = myNickname != null ? myNickname.trim() : "";

        Platform.runLater(() -> {
            activePlayerNick.set(activeNickTrimmed);

            for (PlayerInfo player : players) {
                if ("Lagging".equals(player.getStatus())) {
                    continue;
                }

                if (player.getName().trim().equals(activeNickTrimmed)) {
                    player.setStatus("Playing");
                } else {
                    player.setStatus("Waiting");
                }
            }


            if (activeNickTrimmed.equals(myNickTrimmed)) {
                isMyTurn.set(true);
                gameStatus.set("*** Je váš tah! ***");
            } else {
                isMyTurn.set(false);
                gameStatus.set("Hraje: " + activeNickTrimmed);
            }
        });
    }

    /**
     * Zpracování aktualizace skóre
     */
    private void handleRoundScore(String nick, int score) {
        Platform.runLater(() -> {
            if (nick.trim().equals(myNickname != null ? myNickname.trim() : "")) {
                roundScore.set(score);
            }
        });

        if (onGameEvent != null) {
            Platform.runLater(() -> onGameEvent.accept("ROUND " + nick + " " + score));
        }
    }

    /**
     * Zpracování aktualizace celkového skóre
     */
    private void handleTotalScore(String nick, int score) {
        Platform.runLater(() -> {

            for (PlayerInfo player : players) {
                if (player.getName().trim().equals(nick.trim())) {
                    player.setScore(String.valueOf(score));
                    break;
                }
            }

            if (nick.trim().equals(myNickname != null ? myNickname.trim() : "")) {
                totalScore.set(score);
            }
        });

        if (onGameEvent != null) {
            Platform.runLater(() -> onGameEvent.accept("SCORE " + nick + " " + score));
        }
    }

    private void handleFarkle(String nick) {
        if (onGameEvent != null) {
            Platform.runLater(() -> onGameEvent.accept("FARKLE " + nick));
        }
    }

    private void handleWin(String nick) {
        Platform.runLater(() -> {
            isMyTurn.set(false);
            gameStatus.set("Konec hry! Vyhrává: " + nick);
        });

        if (onGameEvent != null) {
            Platform.runLater(() -> onGameEvent.accept("WIN " + nick));
        }
    }

    public void reset() {
        Platform.runLater(() -> {
            isMyTurn.set(false);
            activePlayerNick.set("");

            activeDiceCount.set(6);
            for (int i = 0; i < 6; i++) {
                diceValues[i].set(0);
            }

            roundScore.set(0);
            totalScore.set(0);
            winRejected.set(false);
            isGameOver = false;
            gameStatus.set("Čekání na hráče...");

            players.clear();
        });
    }
}

