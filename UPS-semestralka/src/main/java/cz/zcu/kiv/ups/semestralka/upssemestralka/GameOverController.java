package cz.zcu.kiv.ups.semestralka.upssemestralka;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.Konstanty;
import cz.zcu.kiv.ups.semestralka.upssemestralka.model.MessageManager;
import cz.zcu.kiv.ups.semestralka.upssemestralka.utils.PacketBuilder;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
/*  
* Controller pro obrazovku Game Over
*/
public class GameOverController implements Initializable {

    @FXML
    private Label winnerLabel;

    @FXML
    private TableView<PlayerInfo> scoreboardTable;

    @FXML
    private TableColumn<PlayerInfo, String> playerColumn;

    @FXML
    private TableColumn<PlayerInfo, String> finalScoreColumn;

    @FXML
    private Button backButton;

    private MessageManager messageManager;
    private Stage primaryStage;
    private String playerNickname;

    /*
    * Inicializace controlleru
    */
    @Override
    public void initialize(URL location, ResourceBundle resources) {

        playerColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        finalScoreColumn.setCellValueFactory(cellData -> cellData.getValue().scoreProperty());


        scoreboardTable.setStyle("-fx-background-color: rgba(40, 30, 20, 0.8);");
    }

    public void setMessageManager(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    public void setPlayerNickname(String nickname) {
        this.playerNickname = nickname;
    }

    public void setWinner(String winnerName) {
        winnerLabel.setText("🏆 Vítěz: " + winnerName + " 🏆");
    }

    public void setScoreboard(List<PlayerInfo> players) {
        if (players != null) {
            scoreboardTable.getItems().setAll(players);
        }
    }

    /**
     * Inicializace obrazovky Game Over s výsledky
     *
     * @param winner Přezdívka vítěze
     * @param players Konečná tabulka výsledků
     */
    public void initData(String winner, List<PlayerInfo> players) {
        if (winner != null && !winner.isEmpty()) {
            winnerLabel.setText("🏆 Vítěz: " + winner + " 🏆");
        }

        if (players != null && !players.isEmpty()) {
            scoreboardTable.getItems().clear();
            scoreboardTable.getItems().addAll(players);
        }
    }

    /**
     * Zpracování tlačítka návratu do lobby
     */
    @FXML
    private void onBackToLobby(ActionEvent event) {
        if (messageManager != null) {
            messageManager.getGameModel().reset();
            messageManager.getRoomModel().reset();

            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("lobby.fxml"));
                Scene lobbyScene = new Scene(loader.load());
                lobbyScene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

                LobbyController lobbyController = loader.getController();
                lobbyController.setMessageManager(messageManager);
                lobbyController.setPlayerNickname(playerNickname);
                lobbyController.setPrimaryStage(primaryStage);

                messageManager.getKomunikace().send(Konstanty.CMD_LIST_ROOMS, PacketBuilder.buildLoginPacket(playerNickname));

                primaryStage.setScene(lobbyScene);
                primaryStage.setTitle("Kostky - Lobby");
            primaryStage.setWidth(primaryStage.getWidth() + 0.001);
            primaryStage.show();

        } catch (IOException e) {
            System.err.println("[GameOverController] Failed to load lobby: " + e.getMessage());
        }
    }
}
}

