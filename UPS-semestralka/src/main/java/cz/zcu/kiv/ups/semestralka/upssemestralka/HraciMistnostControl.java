package cz.zcu.kiv.ups.semestralka.upssemestralka;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.FarkleRules;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.GameModel;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.Konstanty;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.MessageManager;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.RoomModel;
import cz.zcu.kiv.ups.semestralka.upssemestralka.utils.PacketBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Kontroler herní místnosti
 */
public class HraciMistnostControl implements Initializable {

    @FXML private HBox diceContainer;
    @FXML private GridPane dice1, dice2, dice3, dice4, dice5, dice6;
    @FXML private Label roomTitleLabel, gameStatusLabel, currentRoundScoreLabel, selectedScoreLabel, winConditionLabel;
    @FXML private Button readyButton, throwButton, bankButton, leaveButton;
    @FXML private TableView<PlayerInfo> playerTableView;
    @FXML private TableColumn<PlayerInfo, String> nicknameColumn, scoreColumn, statusColumn;
    @FXML private TextArea gameLogArea;

    private Canvas canvas1, canvas2, canvas3, canvas4, canvas5, canvas6;
    private final Set<Canvas> selectedDices = new HashSet<>();
    private final int[] currentDiceValues = new int[6];
    private final ObservableList<PlayerInfo> playerList = FXCollections.observableArrayList();

    private MessageManager messageManager;
    private Room currentRoom;
    private Stage primaryStage;
    private String myNickname;
    
    private boolean isMyTurn = false;
    private boolean isAnimating = false;
    private boolean isBustAnimating = false;
    
    private String pendingDiceData = null;
    private Alert currentVoteDialog = null;
    
    private Timeline ackRetryTimer = null;
    private Timeline reconnectionTimer = null;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupDiceCanvas();
        setupGameLog();
        lockGameControls();
    }

    /**
     * Nastavení sloupců tabulky hráčů
     */
    private void setupTableColumns() {
        nicknameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        scoreColumn.setCellValueFactory(cellData -> cellData.getValue().scoreProperty());
        statusColumn.setCellValueFactory(cellData -> cellData.getValue().statusProperty());

        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("Lagging") || item.contains("Disconnected") || item.contains("Reconnecting")) {
                        setStyle("-fx-text-fill: #ff4444; -fx-font-weight: bold;");
                    } else if (item.equals("Playing")) {
                        setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        playerTableView.setItems(playerList);
        playerTableView.setStyle("-fx-background-color: rgba(40, 30, 20, 0.8);");
    }

    /**
     * Inicializace canvas prvků pro kostky
     */
    private void setupDiceCanvas() {
        GridPane[] dices = {dice1, dice2, dice3, dice4, dice5, dice6};
        Canvas[] canvases = new Canvas[6];

        for (int i = 0; i < dices.length; i++) {
            Canvas canvas = new Canvas(80, 80);
            canvases[i] = canvas;
            GridPane dice = dices[i];

            dice.getChildren().clear();
            dice.getChildren().add(canvas);
            dice.setPrefSize(80, 80);
            dice.setMaxSize(80, 80);
            dice.setMinSize(80, 80);
            dice.setAlignment(javafx.geometry.Pos.CENTER);

            canvas.setOnMouseClicked(this::onDiceClick);
            canvas.setOnMouseEntered(e -> {
                if (!isAnimating && isMyTurn) {
                    dice.setStyle(getHoverStyle(canvas));
                }
            });
            canvas.setOnMouseExited(e -> {
                if (!isAnimating) {
                    dice.setStyle(getDiceStyle(canvas));
                }
            });

            currentDiceValues[i] = 1;
            drawDie(canvas.getGraphicsContext2D(), 0, 0, 80, 1);
        }

        canvas1 = canvases[0];
        canvas2 = canvases[1];
        canvas3 = canvases[2];
        canvas4 = canvases[3];
        canvas5 = canvases[4];
        canvas6 = canvases[5];

        diceContainer.widthProperty().addListener((obs, oldVal, newVal) -> redrawAllDice());
        diceContainer.heightProperty().addListener((obs, oldVal, newVal) -> redrawAllDice());
    }

    /**
     * Nastavení herního logu
     */
    private void setupGameLog() {
        gameLogArea.setEditable(false);
        addLogEntry("Vítejte ve hře Kostky!");
    }

    /**
     * Nastavení message manageru a posluchačů událostí
     */
    public void setMessageManager(MessageManager messageManager) {
        this.messageManager = messageManager;
        if (messageManager == null) return;

        var gameModel = messageManager.getGameModel();
        var roomModel = messageManager.getRoomModel();

        this.myNickname = gameModel.getMyNickname();

        messageManager.getKomunikace().setOnConnectionStateChanged(this::handleConnectionStateChange);
        gameModel.setOnAckReceived(this::handleAckReceived);

        setupPlayerListBinding(gameModel);
        setupDiceListeners(gameModel);
        setupTurnListener(gameModel);
        setupGameStateListeners(gameModel);
        setupRoomListeners(roomModel);
    }

    /**
     * Propojení seznamu hráčů
     */
    private void setupPlayerListBinding(GameModel gameModel) {
        playerList.setAll(gameModel.getPlayers());
        gameModel.getPlayers().addListener((javafx.collections.ListChangeListener<PlayerInfo>) change -> {
            while (change.next()) {
                if (change.wasAdded()) playerList.addAll(change.getAddedSubList());
                if (change.wasRemoved()) playerList.removeAll(change.getRemoved());
            }
            playerTableView.refresh();
        });
    }

    /**
     * Nastavení posluchačů pro kostky
     */
    private void setupDiceListeners(GameModel gameModel) {
        for (int i = 0; i < 6; i++) {
            final int index = i;
            gameModel.diceValueProperty(i).addListener((obs, oldVal, newVal) -> updateDiceValue(index, newVal.intValue()));
        }

        gameModel.activeDiceCountProperty().addListener((obs, oldVal, newVal) -> 
            Platform.runLater(this::redrawDice)
        );

        gameModel.setOnDiceUpdated(() -> {
            for (int i = 0; i < 6; i++) {
                currentDiceValues[i] = gameModel.diceValueProperty(i).get();
            }
            Platform.runLater(this::redrawDice);
        });
    }

    /**
     * Nastavení posluchače tahu
     */
    private void setupTurnListener(GameModel gameModel) {
        gameModel.isMyTurnProperty().addListener((obs, oldVal, newVal) -> {
            isMyTurn = newVal;

            if (newVal && !isBustAnimating) {
                unlockGameControls();
                Platform.runLater(() -> {
                    Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
                    for (int i = 0; i < 6; i++) {
                        if (currentDiceValues[i] > 0) {
                            canvases[i].setVisible(true);
                            canvases[i].setDisable(false);
                            canvases[i].setOpacity(1.0);
                            drawDice(canvases[i], currentDiceValues[i]);
                        }
                    }
                    forceRefreshUI();
                });
            } else {
                lockGameControls();
                Platform.runLater(() -> {
                    clearDiceSelection();
                    Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
                    for (int i = 0; i < 6; i++) {
                        if (currentDiceValues[i] > 0) {
                            canvases[i].setDisable(true);
                            canvases[i].setOpacity(0.7);
                        }
                    }
                });
            }
        });
    }

    /**
     * Nastavení posluchačů herního stavu
     */
    private void setupGameStateListeners(GameModel gameModel) {
        gameModel.gameStatusProperty().addListener((obs, oldVal, newVal) -> 
            Platform.runLater(() -> gameStatusLabel.setText(newVal))
        );

        gameModel.winRejectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                Platform.runLater(() -> {
                    stopAckRetryTimer();
                    if (messageManager != null && messageManager.getGameModel() != null) {
                        messageManager.getGameModel().clearPendingMove();
                    }

                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Výhra zamítnuta");
                    alert.setHeaderText("Server odmítl výhru");
                    alert.setContentText("Vaše skóre nebylo dostatečné pro vítězství.\nZkuste předat body (Bank) místo nárokování výhry.");
                    alert.show();

                    unlockGameControls();
                    isMyTurn = true;
                    addLogEntry("*** CHYBA: Server odmítl výhru - zkuste předat body ***");
                });
            }
        });

        gameModel.setOnGameEvent(this::handleGameEvent);
    }

    /**
     * Nastavení posluchačů místnosti
     */
    private void setupRoomListeners(RoomModel roomModel) {
        roomModel.setOnRoomEvent(this::handleGameEvent);
        roomModel.leaveSuccessProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) switchToLobby();
        });
    }

    /**
     * Hlavní dispatcher herních událostí
     */
    private void handleGameEvent(String message) {
        addLogEntry("Event: " + message);

        if (message.startsWith("JOINED ")) {
            handlePlayerJoined(message.substring(7).trim());
        } else if (message.startsWith("LEFT ")) {
            handlePlayerLeft(message.substring(5).trim());
        } else if (message.equals("READY") || message.equals("START")) {
            handleGameStart();
        } else if (message.startsWith("TURN;") || message.startsWith("TURN ")) {
            handleTurnChange(message);
        } else if (message.startsWith("ROUND ")) {
            handleRoundScore(message);
        } else if (message.startsWith("SCORE ")) {
            handleTotalScore(message);
        } else if (message.startsWith("FARKLE;") || message.startsWith("FARKLE ")) {
            handleBustEvent(message);
        } else if (message.startsWith("WIN ")) {
            handleWinEvent(message.substring(4).trim());
        } else if (message.startsWith("VOTE_INIT ")) {
            handleVoteInit(message.substring(10).trim());
        } else if (message.equals("VOTE_END")) {
            closeVoteDialog();
        } else if (message.startsWith("VOTE_KICK ")) {
            handleVoteKick(message.substring(10).trim());
        } else if (message.startsWith("LAGGING ")) {
            handleLagging(message.substring(8).trim());
        } else if (message.startsWith("OPPONENT_LOST ")) {
            handleOpponentLost(message.substring(14).trim());
        } else if (message.startsWith("VOTE_WAIT ")) {
            handleVoteWait(message.substring(10).trim());
        } else if (message.startsWith("PLAYER_LOST ")) {
            handlePlayerLost(message.substring(12).trim());
        } else if (message.startsWith("ERR:") || message.startsWith("ERR ")) {
            handleServerError(message);
        }
    }

    /**
     * Zpracování připojení hráče
     */
    private void handlePlayerJoined(String nickname) {
        addLogEntry(nickname + " se připojil do místnosti");
        var gameModel = messageManager.getGameModel();
        Platform.runLater(() -> {
            boolean exists = gameModel.getPlayers().stream()
                    .anyMatch(p -> p.getName().equals(nickname));
            if (!exists) {
                gameModel.getPlayers().add(new PlayerInfo(nickname, "0", "Waiting"));
            }
        });
    }

    /**
     * Zpracování opuštění místnosti hráčem
     */
    private void handlePlayerLeft(String nickname) {
        addLogEntry(nickname + " opustil místnost");
        var gameModel = messageManager.getGameModel();
        Platform.runLater(() -> {
            gameModel.getPlayers().removeIf(p -> p.getName().equals(nickname));
        });
    }

    /**
     * Zpracování zahájení hry
     */
    private void handleGameStart() {
        Platform.runLater(() -> {
            addLogEntry("*** HRA ZAČÍNÁ! ***");
            clearDiceSelection();
            gameStatusLabel.setText("Hra probíhá...");
            forceRefreshUI();
        });
    }

    /**
     * Zpracování změny tahu
     */
    private void handleTurnChange(String message) {
        String[] parts = message.split(";");
        if (parts.length >= 2) {
            String nickname = parts[1].trim();
            
            if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                updateDiceFromData(parts[2].trim());
            }
            
            if (nickname.equals(myNickname) && !isBustAnimating) {
                unlockGameControls();
            }
            
            addLogEntry(isMyTurn ? "*** Je váš tah! ***" : "Hraje: " + nickname);
            forceRefreshUI();
        }
    }

    /**
     * Zpracování skóre v kole
     */
    private void handleRoundScore(String message) {
        String[] parts = message.substring(6).trim().split(" ");
        if (parts.length >= 2) {
            String nickname = parts[0].trim();
            int score = Integer.parseInt(parts[1].trim());
            addLogEntry(nickname + " má v kole: " + score + " bodů");
            Platform.runLater(() -> {
                if (nickname.equals(myNickname)) {
                    currentRoundScoreLabel.setText("Skóre v kole: " + score + " bodů");
                }
            });
            forceRefreshUI();
        }
    }

    /**
     * Zpracování celkového skóre
     */
    private void handleTotalScore(String message) {
        String[] parts = message.substring(6).trim().split(" ");
        if (parts.length >= 2) {
            String nickname = parts[0].trim();
            int score = Integer.parseInt(parts[1].trim());
            addLogEntry(nickname + " má celkem: " + score + " bodů");
        }
    }

    /**
     * Zpracování bustu
     */
    private void handleBustEvent(String message) {
        String[] parts = message.split("[; ]");
        
        if (parts.length >= 2) {
            String nickname = parts[1].trim();
            
            if (parts.length > 2 && parts[2].trim().length() == 7) {
                updateDiceFromData(parts[2].trim());
            }
            
            handleBust(nickname);
        }
    }

    /**
     * Zpracování bustu s animací
     */
    private void handleBust(String nickname) {
        Platform.runLater(() -> {
            isBustAnimating = true;
            lockGameControls();

            addLogEntry("*** SMŮLA! " + nickname + " nic nenahrál! ***");
            boolean isMyBust = nickname.equals(myNickname);

            Timeline bustAnimation = new Timeline(new KeyFrame(Duration.seconds(3), e -> {
                isBustAnimating = false;

                if (pendingDiceData != null) {
                    String diceToApply = pendingDiceData;
                    pendingDiceData = null;
                    updateDiceFromData(diceToApply);
                }

                boolean actuallyMyTurn = messageManager != null
                    && messageManager.getGameModel() != null
                    && messageManager.getGameModel().isMyTurnProperty().get();

                if (actuallyMyTurn) {
                    this.isMyTurn = true;
                    unlockGameControls();
                    addLogEntry("*** Je váš tah po bustu! ***");
                    
                    Platform.runLater(() -> {
                        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
                        for (int i = 0; i < 6; i++) {
                            if (currentDiceValues[i] > 0) {
                                canvases[i].setVisible(true);
                                canvases[i].setDisable(false);
                                canvases[i].setOpacity(1.0);
                                drawDice(canvases[i], currentDiceValues[i]);
                            }
                        }
                        forceRefreshUI();
                    });
                }
            }));
            bustAnimation.play();

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Smůla!");
            alert.setHeaderText(isMyBust ? "Nic nepadlo!" : nickname + " nic nenahrál!");
            alert.setContentText("Kolo končí bez bodů.");
            alert.show();

            javafx.animation.PauseTransition alertClose = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            alertClose.setOnFinished(ev -> {
                if (alert.isShowing()) alert.close();
            });
            alertClose.play();
        });
    }

    /**
     * Zpracování výhry
     */
    private void handleWinEvent(String nickname) {
        Platform.runLater(() -> {
            addLogEntry("*** HRA SKONČILA! Vyhrává: " + nickname + " ***");

            var gameModel = messageManager.getGameModel();
            if (playerTableView != null) {
                playerTableView.refresh();
            }

            lockGameControls();
            gameModel.setGameOver(true);
            messageManager.getKomunikace().send(Konstanty.CMD_RETURN_TO_LOBBY, "");

            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
            pause.setOnFinished(event -> {
                Platform.runLater(() -> {
                    javafx.animation.PauseTransition finalSync = new javafx.animation.PauseTransition(javafx.util.Duration.millis(100));
                    finalSync.setOnFinished(syncEvent -> {
                        Platform.runLater(() -> {
                            if (playerTableView != null) {
                                playerTableView.refresh();
                            }

                            List<PlayerInfo> finalScoreboard = new ArrayList<>(gameModel.getPlayers());
                            showGameOverScreen(nickname, finalScoreboard);
                        });
                    });
                    finalSync.play();
                });
            });
            pause.play();
        });
    }

    /**
     * Zobrazení Game Over obrazovky
     */
    private void showGameOverScreen(String winner, List<PlayerInfo> scoreboard) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("game_over.fxml"));
            Scene gameOverScene = new Scene(loader.load());
            gameOverScene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            GameOverController controller = loader.getController();
            controller.setMessageManager(messageManager);
            controller.setPlayerNickname(myNickname);
            controller.setWinner(winner);
            controller.setScoreboard(scoreboard);
            controller.setPrimaryStage(primaryStage);

            primaryStage.setTitle("Kostky - Konec hry");
            primaryStage.setScene(gameOverScene);
            primaryStage.setWidth(primaryStage.getWidth() + 0.001);
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("Failed to load Game Over scene: " + e.getMessage());
            showSimpleWinAlert(winner);
        }
    }

    /**
     * Jednoduchý alert výhry
     */
    private void showSimpleWinAlert(String winner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Konec hry!");
        alert.setHeaderText("Vítěz: " + winner);
        alert.setContentText(winner + " vyhrál hru!");
        alert.showAndWait();
    }

    /**
     * Zpracování inicializace hlasování
     */
    private void handleVoteInit(String laggingPlayer) {
        var gameModel = messageManager.getGameModel();
        Platform.runLater(() -> {
            PlayerInfo player = gameModel.getPlayers().stream()
                .filter(p -> p.getName().equals(laggingPlayer))
                .findFirst()
                .orElse(null);

            if (player != null) {
                player.setStatus("Lagging");
                playerTableView.refresh();
                addLogEntry(laggingPlayer + " má problémy s připojením");
            }
        });

        showVoteDialog(laggingPlayer);
    }

    /**
     * Zpracování laggujícího hráče
     */
    private void handleLagging(String nickname) {
        var gameModel = messageManager.getGameModel();
        Platform.runLater(() -> {
            PlayerInfo player = gameModel.getPlayers().stream()
                .filter(p -> p.getName().equals(nickname))
                .findFirst()
                .orElse(null);

            if (player != null) {
                player.setStatus("Lagging");
                playerTableView.refresh();
                addLogEntry(nickname + " laguje");
            }
        });
    }

    /**
     * Zpracování chyb od serveru
     */
    private void handleServerError(String message) {
        System.err.println("[ERROR] Server error: " + message);

        stopAckRetryTimer();

        if (messageManager != null && messageManager.getGameModel() != null) {
            messageManager.getGameModel().clearPendingMove();
        }

        if (message.startsWith("ERR:NotEnough")) {
            handleAutoFallbackToBank();
        } else {
            Platform.runLater(() -> {
                unlockGameControls();
                isMyTurn = true;
                addLogEntry("Chyba serveru: " + message);
            });
            handleDesyncError(message);
        }
    }

    /**
     * Automatický fallback z Win na Bank
     */
    private void handleAutoFallbackToBank() {
        Platform.runLater(() -> {
            

            String mask = createMask();
            int selectedScore = FarkleRules.calculateScore(mask);
            int myTotal = messageManager.getGameModel().totalScoreProperty().get();
            int projectedTotal = myTotal + selectedScore;


            if (messageManager != null) {
                var gameModel = messageManager.getGameModel();
                gameModel.setPendingMove(Konstanty.CMD_GAME_PASS, mask);
                messageManager.getKomunikace().send(Konstanty.CMD_GAME_PASS, PacketBuilder.buildGamePassPacket(mask));
                startAckRetryTimer();
            }
        });
    }

    /**
     * Zpracování chyb desynchronizace
     */
    private void handleDesyncError(String errorMessage) {
        Platform.runLater(() -> {
            addLogEntry("*** CHYBA: " + errorMessage + " - Synchronizace stavu... ***");

            if (errorMessage.contains("InvalidDice") || errorMessage.contains("Cheating")) {
                clearDiceSelection();
            }

            if (messageManager != null && myNickname != null) {
                messageManager.getKomunikace().send(Konstanty.CMD_LIST_ROOMS, 
                    PacketBuilder.buildLoginPacket(myNickname));
                forceRefreshUI();

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Synchronizační problém");
                alert.setHeaderText("Stav hry byl obnoven");
                alert.setContentText("Server odmítl poslední akci:\n" + errorMessage + 
                    "\n\nStav hry byl automaticky synchronizován.");
                alert.show();
            }
        });
    }

    /**
     * Dialog pro hlasování
     */
    private void showVoteDialog(String laggingPlayer) {
        Platform.runLater(() -> {
            closeVoteDialog();
            addLogEntry("*** HLASOVÁNÍ: " + laggingPlayer + " nereaguje ***");

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Hlasování o vyhození");
            alert.setHeaderText("Hráč " + laggingPlayer + " nereaguje");
            alert.setContentText("Chcete hráče vyhodit ze hry?");

            ButtonType kickButton = new ButtonType("Vyhodit");
            ButtonType waitButton = new ButtonType("Počkat");
            alert.getButtonTypes().setAll(kickButton, waitButton);

            currentVoteDialog = alert;

            alert.showAndWait().ifPresent(response -> {
                if (response == kickButton) {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildVoteKickPacket());
                    addLogEntry("Hlasovali jste pro vyhození hráče " + laggingPlayer);
                } else {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildVoteWaitPacket());
                    addLogEntry("Hlasovali jste pro čekání na hráče " + laggingPlayer);
                }
                currentVoteDialog = null;
            });
        });
    }

    /**
     * Zavření dialogu hlasování
     */
    private void closeVoteDialog() {
        Platform.runLater(() -> {
            if (currentVoteDialog != null) {
                currentVoteDialog.close();
                currentVoteDialog = null;
                addLogEntry("Hlasování ukončeno");
            }
        });
    }

    private void handleVoteKick(String nickname) {
        Platform.runLater(() -> {
            addLogEntry("*** HLASOVÁNÍ: " + nickname + " je neaktivní ***");

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Neaktivní hráč");
            alert.setHeaderText("Hráč " + nickname + " nereaguje");
            alert.setContentText("Chcete hráče vyhodit ze hry?");

            ButtonType kickButton = new ButtonType("Vyhodit");
            ButtonType waitButton = new ButtonType("Počkat");
            alert.getButtonTypes().setAll(kickButton, waitButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == kickButton) {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildNumericVotePacket(true));
                    addLogEntry("Hlasovali jste pro vyhození hráče " + nickname);
                } else {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildNumericVotePacket(false));
                    addLogEntry("Hlasovali jste pro čekání na hráče " + nickname);
                }
            });
        });
    }

    private void handleOpponentLost(String nickname) {
        Platform.runLater(() -> {
            addLogEntry("*** SOUPEŘ ODPOJEN: " + nickname + " ***");

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Soupeř odpojen");
            alert.setHeaderText("Hráč " + nickname + " se odpojil");
            alert.setContentText("Jste poslední hráč ve hře. Chcete vzít výhru?");

            ButtonType winButton = new ButtonType("Vzít výhru");
            ButtonType waitButton = new ButtonType("Počkat");
            alert.getButtonTypes().setAll(winButton, waitButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == winButton) {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildNumericVotePacket(true));
                    addLogEntry("Vzali jste výhru");
                } else {
                    messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildNumericVotePacket(false));
                    addLogEntry("Čekáte na soupeře");
                }
            });
        });
    }

    private void handleVoteWait(String nickname) {
        Platform.runLater(() -> {
            addLogEntry("Čekání na hráče: " + nickname + "...");
            gameStatusLabel.setText("Čeká se na: " + nickname);
        });
    }

    private void handlePlayerLost(String nickname) {
        Platform.runLater(() -> {
            addLogEntry("*** HRÁČ ODSTRANĚN: " + nickname + " ***");

            var gameModel = messageManager.getGameModel();
            gameModel.getPlayers().removeIf(p -> p.getName().equals(nickname));
            gameStatusLabel.setText("Hráč " + nickname + " byl odstraněn ze hry");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Hráč odstraněn");
            alert.setHeaderText("Hráč opustil hru");
            alert.setContentText(nickname + " byl odstraněn ze hry.");
            alert.show();
        });
    }

    /**
     * Zpracování změn stavu připojení
     */
    private void handleConnectionStateChange(String newState) {
        Platform.runLater(() -> {
            switch (newState) {
                case "NORMAL":
                case "RECOVERED":
                    handleConnectionRecovered();
                    break;
                case "LAGGING":
                case "UNSTABLE":
                    roomTitleLabel.setText("Nestabilní spojení");
                    
                    break;
                case "DISCONNECTED":
                    roomTitleLabel.setText("Ztráta spojení - Obnovuji...");
                   
                    startReconnectionTimer();
                    break;
            }
        });
    }

    /**
     * Zpracování obnoveného spojení
     */
    private void handleConnectionRecovered() {
        stopReconnectionTimer();
        addLogEntry("Spojení obnoveno - synchronizuji...");
        
        roomTitleLabel.setText("Hra: Kostky - Místnost " + 
            (currentRoom != null ? currentRoom.getId() : "?") +
            (currentRoom != null ? " (Cíl: " + currentRoom.getWinningScore() + " bodů)" : ""));

        if (messageManager != null) {
            var gameModel = messageManager.getGameModel();

            if (gameModel.hasPendingMove()) {
                var pendingMove = gameModel.getPendingMove();
                long age = System.currentTimeMillis() - pendingMove.timestamp;

                if (age < 60000) {
                    addLogEntry("⚡ Opětovné odeslání nedokončeného tahu...");
                    resendPendingMove(pendingMove);
                    startAckRetryTimer();
                } else {
                    gameModel.clearPendingMove();
                }
            }

            messageManager.getKomunikace().send(Konstanty.CMD_LIST_ROOMS, 
                PacketBuilder.buildLoginPacket(myNickname));
        }
    }

    /**
     * Opětovné odeslání čekajícího tahu
     */
    private void resendPendingMove(GameModel.PendingMove pendingMove) {
        if (pendingMove.commandId == Konstanty.CMD_GAME_REROLL) {
            messageManager.getKomunikace().send(Konstanty.CMD_GAME_REROLL,
                PacketBuilder.buildGameRerollPacket(pendingMove.mask));
        } else if (pendingMove.commandId == Konstanty.CMD_GAME_PASS) {
            messageManager.getKomunikace().send(Konstanty.CMD_GAME_PASS,
                PacketBuilder.buildGamePassPacket(pendingMove.mask));
        } else if (pendingMove.commandId == Konstanty.CMD_GAME_WIN) {
            messageManager.getKomunikace().send(Konstanty.CMD_GAME_WIN,
                PacketBuilder.buildGameWinPacket(pendingMove.mask));
        }
    }

    private void handleAckReceived() {
        Platform.runLater(() -> {
            stopAckRetryTimer();
            addLogEntry("Server potvrdil tah");
            if (isMyTurn) {
                unlockGameControls();
            }
        });
    }

    private void startAckRetryTimer() {
        stopAckRetryTimer();

        ackRetryTimer = new Timeline(new KeyFrame(Duration.seconds(2), event -> {
            var gameModel = messageManager.getGameModel();

            if (gameModel.hasPendingMove()) {
                var pendingMove = gameModel.getPendingMove();
                long age = System.currentTimeMillis() - pendingMove.timestamp;

                if (age < 60000) {
                    addLogEntry("Opakuji tah (čekám na potvrzení)...");
                    resendPendingMove(pendingMove);
                } else {
                    stopAckRetryTimer();
                    gameModel.clearPendingMove();
                    addLogEntry("Tah vypršel - obnovte spojení nebo zkuste znovu");
                }
            } else {
                stopAckRetryTimer();
            }
        }));

        ackRetryTimer.setCycleCount(Timeline.INDEFINITE);
        ackRetryTimer.play();
    }

    private void stopAckRetryTimer() {
        if (ackRetryTimer != null) {
            ackRetryTimer.stop();
            ackRetryTimer = null;
        }
    }

    private void startReconnectionTimer() {
        stopReconnectionTimer();

        reconnectionTimer = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            addLogEntry("Pokus o opětovné připojení...");
            if (messageManager != null && myNickname != null) {
                messageManager.getKomunikace().send(Konstanty.CMD_LIST_ROOMS,
                    PacketBuilder.buildLoginPacket(myNickname));
            }
        }));

        reconnectionTimer.setCycleCount(Timeline.INDEFINITE);
        reconnectionTimer.play();
    }

    private void stopReconnectionTimer() {
        if (reconnectionTimer != null) {
            reconnectionTimer.stop();
            reconnectionTimer = null;
        }
    }

    private void updateDiceValue(int index, int value) {
        if (isBustAnimating) return;
        if (index >= 0 && index < 6) {
            currentDiceValues[index] = value;
        }
    }

    /**
     * Aktualizace kostek z dat zprávy
     */
    private void updateDiceFromData(String diceData) {
        if (diceData == null || diceData.length() != 7) {
            System.err.println("Invalid dice data format: " + diceData);
            return;
        }

        if (isBustAnimating) {
            this.pendingDiceData = diceData;
            return;
        }

        try {
            for (int i = 0; i < 6; i++) {
                currentDiceValues[i] = Character.getNumericValue(diceData.charAt(i + 1));
            }

            Platform.runLater(() -> {
                redrawDice();
                selectedScoreLabel.setText("Zvolené kostky: 0 bodů");
            });
        } catch (Exception e) {
            System.err.println("Error parsing dice data: " + e.getMessage());
        }
    }

    private void redrawAllDice() {
        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
        for (int i = 0; i < canvases.length; i++) {
            if (canvases[i] != null && currentDiceValues[i] >= 1 && currentDiceValues[i] <= 6) {
                drawDice(canvases[i], currentDiceValues[i]);
            }
        }
    }

    /**
     * Překreslení všech kostek
     */
    private void redrawDice() {
        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
        GridPane[] dices = {dice1, dice2, dice3, dice4, dice5, dice6};

        selectedDices.clear();

        for (int i = 0; i < 6; i++) {
            Canvas canvas = canvases[i];
            GridPane parent = dices[i];
            int value = currentDiceValues[i];

            canvas.setVisible(true);

            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

            if (parent != null) {
                parent.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            }

            if (value == 0) {
                canvas.setDisable(true);
                canvas.setOpacity(0.0);
            } else if (value >= 1 && value <= 6) {
                drawDie(gc, 0, 0, 80, value);

                if (parent != null) {
                    parent.setStyle(getDiceStyle(canvas));
                }

                if (isMyTurn) {
                    canvas.setDisable(false);
                    canvas.setOpacity(1.0);
                } else {
                    canvas.setDisable(true);
                    canvas.setOpacity(0.7);
                }
            }
        }
    }

    /**
     * Vykreslení kostky
     */
    private void drawDie(GraphicsContext gc, double x, double y, double size, int value) {
        gc.clearRect(x, y, size, size);

        gc.setFill(Color.WHITE);
        gc.fillRoundRect(x, y, size, size, 12, 12);

        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRoundRect(x, y, size, size, 12, 12);

        gc.setFill(Color.BLACK);
        double dotRadius = size / 12;
        double padding = size / 5;
        double centerX = x + size / 2;
        double centerY = y + size / 2;
        double left = x + padding;
        double right = x + size - padding;
        double top = y + padding;
        double bottom = y + size - padding;

        switch (value) {
            case 1 -> gc.fillOval(centerX - dotRadius, centerY - dotRadius, dotRadius * 2, dotRadius * 2);
            case 2 -> {
                gc.fillOval(left - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
            }
            case 3 -> {
                gc.fillOval(left - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(centerX - dotRadius, centerY - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
            }
            case 4 -> {
                gc.fillOval(left - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(left - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
            }
            case 5 -> {
                gc.fillOval(left - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(centerX - dotRadius, centerY - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(left - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
            }
            case 6 -> {
                gc.fillOval(left - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, top - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(left - dotRadius, centerY - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, centerY - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(left - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
                gc.fillOval(right - dotRadius, bottom - dotRadius, dotRadius * 2, dotRadius * 2);
            }
        }
    }

    private void drawDice(Canvas canvas, int value) {
        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
        for (int i = 0; i < canvases.length; i++) {
            if (canvases[i] == canvas) {
                currentDiceValues[i] = value;
                break;
            }
        }
        drawDie(canvas.getGraphicsContext2D(), 0, 0, 80, value);
    }

    private void onDiceClick(MouseEvent event) {
        if (isBustAnimating || isAnimating || messageManager == null || !isMyTurn) {
            if (!isMyTurn) addLogEntry("Není váš tah!");
            return;
        }

        Canvas clickedCanvas = (Canvas) event.getSource();
        int diceValue = getCurrentDiceValue(clickedCanvas);

        if (selectedDices.contains(clickedCanvas)) {
            selectedDices.remove(clickedCanvas);
            addLogEntry("Odznačena kostka (hodnota: " + diceValue + ")");
        } else {
            selectedDices.add(clickedCanvas);
            addLogEntry("Označena kostka (hodnota: " + diceValue + ")");
        }

        updateDiceVisualState(clickedCanvas);
        updateSelectedScoreDisplay();
    }

    private int getCurrentDiceValue(Canvas canvas) {
        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};
        for (int i = 0; i < canvases.length; i++) {
            if (canvases[i] == canvas) {
                return currentDiceValues[i];
            }
        }
        return 1;
    }

    private void updateDiceVisualState(Canvas canvas) {
        GridPane parent = (GridPane) canvas.getParent();
        if (parent != null) {
            parent.setStyle(getDiceStyle(canvas));
        }
    }

    private String getDiceStyle(Canvas canvas) {
        String baseStyle = "-fx-border-width: 3px; -fx-border-radius: 12px; -fx-background-radius: 12px;";
        if (selectedDices.contains(canvas)) {
            return baseStyle + "-fx-background-color: #4CAF50; -fx-border-color: #FFD700; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(76, 175, 80, 0.8), 15, 0.7, 0, 0);";
        } else {
            return baseStyle + "-fx-background-color: rgba(60, 40, 20, 0.5); -fx-border-color: #8b7355; -fx-cursor: hand;";
        }
    }

    private String getHoverStyle(Canvas canvas) {
        String baseStyle = "-fx-border-width: 3px; -fx-border-radius: 12px; -fx-background-radius: 12px;";
        if (selectedDices.contains(canvas)) {
            return baseStyle + "-fx-background-color: #66BB6A; -fx-border-color: #FFC107; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(102, 187, 106, 0.9), 20, 0.8, 0, 0);";
        } else {
            return baseStyle + "-fx-background-color: rgba(212, 175, 55, 0.3); -fx-border-color: #d4af37; -fx-cursor: hand;";
        }
    }

    /**
     * Vytvoření masky z vybraných kostek
     */
    private String createMask() {
        StringBuilder mask = new StringBuilder();
        Canvas[] canvases = {canvas1, canvas2, canvas3, canvas4, canvas5, canvas6};

        for (int i = 0; i < 6; i++) {
            if (selectedDices.contains(canvases[i])) {
                mask.append(currentDiceValues[i]);
            } else {
                mask.append('0');
            }
        }

        return mask.toString();
    }

    private void clearDiceSelection() {
        Platform.runLater(() -> {
            selectedDices.clear();
            selectedScoreLabel.setText("Zvolené kostky: 0 bodů");
        });
    }

    private void updateSelectedScoreDisplay() {
        Platform.runLater(() -> {
            int selectedScore = FarkleRules.calculateScore(createMask());
            selectedScoreLabel.setText("Zvolené kostky: " + selectedScore + " bodů");

            if (selectedScore > 0 && isMyTurn) {
                enableBankButton();
            } else {
                disableBankButton();
            }
        });
    }

    @FXML
    public void onThrowDices(ActionEvent event) {
        if (isBustAnimating || !isMyTurn || isAnimating || messageManager == null) {
            if (!isMyTurn) showInvalidTurnAlert();
            return;
        }

        String mask = createMask();

        if (PacketBuilder.isEmptyMask(mask)) {
            showEmptySelectionAlert();
            return;
        }

        if (!FarkleRules.isValidSelection(mask)) {
            showInvalidSelectionAlert();
            return;
        }

        addLogEntry("Házím kostkami s maskou: " + mask);

        var gameModel = messageManager.getGameModel();
        gameModel.setPendingMove(Konstanty.CMD_GAME_REROLL, mask);

        lockGameControls();
        addLogEntry("Čekám na potvrzení serveru...");

        messageManager.getKomunikace().send(Konstanty.CMD_GAME_REROLL, PacketBuilder.buildGameRerollPacket(mask));
        startAckRetryTimer();
    }

    @FXML
    public void onBankScore(ActionEvent event) {
        if (!isMyTurn || messageManager == null) {
            if (!isMyTurn) showInvalidTurnAlert();
            return;
        }

        String mask = createMask();

        if (!FarkleRules.isValidSelection(mask)) {
            showInvalidSelectionAlert();
            return;
        }

        var gameModel = messageManager.getGameModel();
        int myTotal = gameModel.totalScoreProperty().get();
        int myRound = gameModel.roundScoreProperty().get();
        int winningScore = (currentRoom != null) ? currentRoom.getWinningScore() : 5000;
        int selectedScore = FarkleRules.calculateScore(mask);
        int projectedTotal = myTotal + selectedScore;

        boolean hasEnoughToWin = (projectedTotal >= winningScore);

        if (FarkleRules.isWinningMove(myTotal, myRound, mask, winningScore) && hasEnoughToWin) {
            addLogEntry("Hlásím vítězství! (Celkem: " + projectedTotal + " bodů)");
            gameModel.setPendingMove(Konstanty.CMD_GAME_WIN, mask);
            messageManager.getKomunikace().send(Konstanty.CMD_GAME_WIN, PacketBuilder.buildGameWinPacket(mask));
        } else {
            if (!hasEnoughToWin && FarkleRules.isWinningMove(myTotal, myRound, mask, winningScore)) {
                addLogEntry("Nedostatečné skóre (" + projectedTotal + "/" + winningScore + ") - Ukládám body");
            } else {
                addLogEntry("Předávám tah s maskou: " + mask + " (" + selectedScore + " bodů)");
            }
            gameModel.setPendingMove(Konstanty.CMD_GAME_PASS, mask);
            messageManager.getKomunikace().send(Konstanty.CMD_GAME_PASS, PacketBuilder.buildGamePassPacket(mask));
        }

        lockGameControls();
        addLogEntry("Čekám na potvrzení serveru...");
        startAckRetryTimer();

        Platform.runLater(() -> currentRoundScoreLabel.setText("Skóre v kole: 0 bodů"));
        isMyTurn = false;
    }

    @FXML
    public void onReady(ActionEvent event) {
        if (messageManager == null) return;

        messageManager.getKomunikace().send(Konstanty.CMD_VOTE, PacketBuilder.buildReadyPacket());
        addLogEntry("Hlásím připravenost...");

        readyButton.setDisable(true);
        readyButton.setText("✓ PŘIPRAVEN");
    }

    @FXML
    public void onLeaveRoom(ActionEvent event) {
        if (messageManager != null) {
            addLogEntry("Opouštím místnost...");
            leaveButton.setDisable(true);
            leaveButton.setText("Opouštím...");
            messageManager.getKomunikace().send(Konstanty.CMD_RETURN_TO_LOBBY, "");
        }
    }

    private void showInvalidTurnAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Neplatný tah");
        alert.setHeaderText("Není váš tah!");
        alert.setContentText("Počkejte, až na vás přijde řada.");
        alert.show();
    }

    private void showEmptySelectionAlert() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Neplatný tah");
        alert.setHeaderText("Žádné kostky nebyly vybrány");
        alert.setContentText("Vyberte kostky, které chcete ponechat, před opětovným hodem.");
        alert.show();
    }

    private void showInvalidSelectionAlert() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Neplatný výběr");
        alert.setHeaderText("Výběr obsahuje kostky bez hodnoty");
        alert.setContentText("Nemůžete vybrat kostky, které nesbírají body.\n\n" +
                           "Pouze 1s a 5s sbírají body individuálně.\n" +
                           "Ostatní kostky (2, 3, 4, 6) musí být alespoň 3 stejné.");
        alert.showAndWait();
    }

    private void lockGameControls() {
        Platform.runLater(() -> {
            throwButton.setDisable(true);
            bankButton.setDisable(true);
        });
    }

    private void unlockGameControls() {
        Platform.runLater(() -> {
            throwButton.setDisable(false);
            bankButton.setDisable(false);
            addLogEntry("Je váš tah!");
        });
    }

    private void enableBankButton() {
        Platform.runLater(() -> bankButton.setDisable(false));
    }

    private void disableBankButton() {
        Platform.runLater(() -> bankButton.setDisable(true));
    }

    private void forceRefreshUI() {
        Platform.runLater(() -> {
            if (primaryStage != null && primaryStage.isShowing()) {
                try {
                    primaryStage.setWidth(primaryStage.getWidth() + 0.001);
                } catch (Exception e) {
                    System.err.println("Error forcing refresh: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Přechod zpět do lobby
     */
    private void switchToLobby() {
        addLogEntry("Vráceno do lobby!");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby.fxml"));
            Scene lobbyScene = new Scene(loader.load());
            lobbyScene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            LobbyController lobbyController = loader.getController();
            lobbyController.setMessageManager(messageManager);
            lobbyController.setPlayerNickname(myNickname);
            lobbyController.setPrimaryStage(primaryStage);

            messageManager.getKomunikace().send(Konstanty.CMD_LIST_ROOMS, PacketBuilder.buildLoginPacket(myNickname));

            primaryStage.setScene(lobbyScene);
            primaryStage.setTitle("Kostky - Lobby");
            primaryStage.setWidth(primaryStage.getWidth() + 0.001);
            primaryStage.show();
        } catch (IOException e) {
            System.err.println("Failed to load lobby: " + e.getMessage());
            addLogEntry("CHYBA: Nelze načíst lobby!");
        }
    }

    /**
     * Přidání záznamu do herního logu
     */
    public void addLogEntry(String message) {
        Platform.runLater(() -> {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            gameLogArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    public void setRoom(Room room) {
        this.currentRoom = room;
        if (room != null) {
            if (messageManager != null) {
                messageManager.getRoomModel().setCurrentRoomId(room.getId());
            }
            roomTitleLabel.setText("Hra: Kostky - Místnost " + room.getId() + " (Cíl: " + room.getWinningScore() + " bodů)");
            winConditionLabel.setText("První na " + room.getWinningScore() + " bodů vyhrává!");
            addLogEntry("Připojeno do místnosti " + room.getId());
        }
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }
}

