package cz.zcu.kiv.ups.semestralka.upssemestralka;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.Konstanty;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.LobbyModel;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.MessageManager;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.RoomModel;
import cz.zcu.kiv.ups.semestralka.upssemestralka.utils.PacketBuilder;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Kontroler lobby - správa seznamu místností a připojování
 */
public class LobbyController implements Initializable {

    @FXML private ListView<String> roomListView;
    @FXML private Button joinButton;
    @FXML private Button createButton;

    private ObservableList<String> displayRoomList;
    private String playerNickname;
    private MessageManager messageManager;
    private Stage primaryStage;
    private int selectedRoomId = -1;
    private Timeline refreshTimer;

    private BooleanProperty isJoining = new SimpleBooleanProperty(false);

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        displayRoomList = FXCollections.observableArrayList();
        roomListView.setItems(displayRoomList);

        roomListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            joinButton.setDisable(newVal == null || newVal.contains("PLNÁ") || isJoining.get());
        });


    }


    /**
     * Nastavení message manageru a propojení s modely
     */
    public void setMessageManager(MessageManager messageManager) {
        this.messageManager = messageManager;

        LobbyModel lobbyModel = messageManager.getLobbyModel();
        RoomModel roomModel = messageManager.getRoomModel();

        lobbyModel.getRooms().addListener((ListChangeListener<Room>) change -> {
            updateDisplayRoomList();
        });

        roomModel.joinSuccessProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                handleJoinSuccess();
            }
        });

        roomModel.joinErrorProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isEmpty()) {
                handleJoinFailure(newVal);
            }
        });
    }

    /**
     * Aktualizace zobrazovaného seznamu místností
     */
    private void updateDisplayRoomList() {
        displayRoomList.clear();

        LobbyModel lobbyModel = messageManager.getLobbyModel();
        for (Room room : lobbyModel.getRooms()) {
            String roomInfo = String.format("Místnost %d (%d/%d) - Cíl: %d %s",
                    room.getId(),
                    room.getCurrentPlayers(),
                    room.getMaxPlayers(),
                    room.getWinningScore(),
                    room.isFull() ? "(PLNÁ)" : "");
            displayRoomList.add(roomInfo);
        }

        System.out.println("[LobbyController] Updated room list: " + displayRoomList.size() + " rooms");
    }

    /**
     * Extract room ID from the formatted string
     */
    private int extractRoomId(String roomInfo) {
        String[] parts = roomInfo.split(" ");
        return Integer.parseInt(parts[1]);
    }

    /**
     * Set player nickname
     */
    public void setPlayerNickname(String nickname) {
        this.playerNickname = nickname;
    }

    /**
     * Handle join room button click (non-blocking)
     */
    @FXML
    private void onJoinRoom(ActionEvent event) {
        String selectedRoom = roomListView.getSelectionModel().getSelectedItem();
        if (selectedRoom != null && !selectedRoom.contains("PLNÁ") && !isJoining.get()) {
            try {

                selectedRoomId = extractRoomId(selectedRoom);


                messageManager.getRoomModel().resetJoinState();


                isJoining.set(true);


                joinButton.setDisable(true);
                joinButton.setText("Připojování...");


                messageManager.getKomunikace().send(Konstanty.CMD_JOIN_ROOM, PacketBuilder.buildJoinRoomPacket(selectedRoomId));

                System.out.println("[LobbyController] Sent join request for room " + selectedRoomId);

            } catch (Exception e) {
                System.err.println("[LobbyController] Error initiating join room: " + e.getMessage());
                e.printStackTrace();
                resetJoinButton();
            }
        }
    }

    /**
     * Handle create room button click
     * Shows dialog to input max players and winning score, then sends CMD_JOIN_ROOM with special format
     */
    @FXML
    private void onCreateRoom(ActionEvent event) {
        try {

            TextInputDialog playersDialog = new TextInputDialog("4");
            playersDialog.setTitle("Vytvořit místnost");
            playersDialog.setHeaderText("Nová herní místnost");
            playersDialog.setContentText("Maximální počet hráčů (2-6):");

            Optional<String> playersResult = playersDialog.showAndWait();
            if (!playersResult.isPresent()) {
                return;
            }

            int maxPlayers;
            try {
                maxPlayers = Integer.parseInt(playersResult.get().trim());
                if (maxPlayers < 2 || maxPlayers > 6) {
                    showError("Neplatný počet", "Počet hráčů musí být mezi 2 a 6.");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Neplatný vstup", "Zadejte platné číslo.");
                return;
            }


            TextInputDialog scoreDialog = new TextInputDialog("5000");
            scoreDialog.setTitle("Vytvořit místnost");
            scoreDialog.setHeaderText("Nová herní místnost");
            scoreDialog.setContentText("Vítězné skóre:");

            Optional<String> scoreResult = scoreDialog.showAndWait();
            if (!scoreResult.isPresent()) {
                return;
            }

            int winningScore;
            try {
                winningScore = Integer.parseInt(scoreResult.get().trim());
                if (winningScore < 1000 || winningScore > 20000) {
                    showError("Neplatné skóre", "Vítězné skóre musí být mezi 1000 a 20000.");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Neplatný vstup", "Zadejte platné číslo.");
                return;
            }


            messageManager.getKomunikace().send(Konstanty.CMD_JOIN_ROOM, PacketBuilder.buildCreateRoomPacket(maxPlayers, winningScore));

            System.out.println("[LobbyController] Sent create room request: C;" + maxPlayers + ";" + winningScore);


            createButton.setDisable(true);
            createButton.setText("Vytváření...");


            Timeline reenableTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
                createButton.setDisable(false);
                createButton.setText("Vytvořit novou místnost");
            }));
            reenableTimer.setCycleCount(1);
            reenableTimer.play();

        } catch (Exception e) {
            System.err.println("[LobbyController] Error creating room: " + e.getMessage());
            e.printStackTrace();
            showError("Chyba", "Nepodařilo se vytvořit místnost.");
        }
    }

    /**
     * Handle successful join (called when join success property changes)
     */
    private void handleJoinSuccess() {
        System.out.println("[LobbyController] Join successful, switching to game room");



        try {
            LobbyModel lobbyModel = messageManager.getLobbyModel();
            Room selectedRoom = null;
            for (Room room : lobbyModel.getRooms()) {
                if (room.getId() == selectedRoomId) {
                    selectedRoom = room;
                    break;
                }
            }

            if (selectedRoom == null) {
                System.err.println("[LobbyController] Varování: Místnost " + selectedRoomId + " nenalezena v seznamu, používám výchozí hodnoty");
                selectedRoom = new Room(selectedRoomId, 0, 4, 5000);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("hraci_mistnost.fxml"));
            Scene gameScene = new Scene(loader.load());

            gameScene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            HraciMistnostControl gameController = loader.getController();
            gameController.setMessageManager(messageManager);
            gameController.setRoom(selectedRoom);
            gameController.setPrimaryStage(primaryStage);

            primaryStage.setTitle("KOSTKY - Místnost " + selectedRoomId);
            primaryStage.setScene(gameScene);

            primaryStage.setWidth(primaryStage.getWidth() + 0.001);

            primaryStage.show();

        } catch (IOException e) {
            System.err.println("[LobbyController] Selhalo načtení herní místnosti: " + e.getMessage());
            showError("Chyba", "Nepodařilo se načíst herní místnost");
            resetJoinButton();


        }
    }

    private void handleJoinFailure(String errorMessage) {
        System.out.println("[LobbyController] Připojení selhalo: " + errorMessage);
        showError("Připojení selhalo", errorMessage);
        resetJoinButton();
    }

    /**
     * Reset tlačítka připojení do normálního stavu.
     */
    private void resetJoinButton() {
        isJoining.set(false);
        joinButton.setDisable(false);
        joinButton.setText("Připojit se");
    }

    /**
     * Zobrazení chybového dialogu.
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

