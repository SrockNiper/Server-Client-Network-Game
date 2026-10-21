package cz.zcu.kiv.ups.semestralka.upssemestralka;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.*;
import cz.zcu.kiv.ups.semestralka.upssemestralka.utils.PacketBuilder;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField nicknameField;
    @FXML
    private TextField serverIPField;
    @FXML
    private TextField serverPortField;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    private static String playerNickname;
    private Stage primaryStage;

    /**
     * Set the primary stage (called from Login.java)
     */
    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    public void onLogin(ActionEvent event) {
        String nickname = nicknameField.getText().trim();
        String serverIP = serverIPField.getText().trim();
        String serverPort = serverPortField.getText().trim();
        if (nickname.isEmpty()) {
            showError("Přezdívka nemůže být prázdná!");
            return;
        }

        if (nickname.length() < 2) {
            showError("Přezdívka musí mít alespoň 2 znaky!");
            return;
        }

        if (nickname.length() > 20) {
            showError("Přezdívka může mít maximálně 20 znaků!");
            return;
        }
        if (serverIP.isEmpty() || serverPort.isEmpty()) {
            showError("IP adresa a port serveru nesmí být prázdné!");
            return;
        }
        if (serverIP.length() < 8) {
            showError("Zadejte platnou IP adresu serveru!");
            return;
        }
        if (serverPort.length() < 2) {
            showError("Zadejte platný port serveru!");
            return;
        }



        playerNickname = nickname;


        openLobby();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }

    private void openLobby() {
        try {
            String serverIP = serverIPField.getText().trim();
            int serverPort = Integer.parseInt(serverPortField.getText().trim());


            Komunikace komunikace = new Komunikace(serverIP, serverPort);


            komunikace.setNickname(playerNickname);


            Login.setKomunikace(komunikace);


            LobbyModel lobbyModel = new LobbyModel();
            RoomModel roomModel = new RoomModel();
            GameModel gameModel = new GameModel();


            gameModel.setMyNickname(playerNickname);

            MessageManager messageManager = new MessageManager(komunikace, lobbyModel, roomModel, gameModel);

            komunikace.setOnConnectionRestored(() -> {
                System.out.println("[LoginController] Connection restored - requesting room state refresh");
                komunikace.send(Konstanty.CMD_LIST_ROOMS, PacketBuilder.buildLoginPacket(playerNickname));
            });

            if (!komunikace.connect()) {
                showError("Nepodařilo se připojit k serveru!");
                return;
            }

            komunikace.send(Konstanty.CMD_LIST_ROOMS, PacketBuilder.buildLoginPacket(playerNickname));

            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("lobby.fxml"));
            Scene scene = new Scene(fxmlLoader.load());

            scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

            LobbyController lobbyController = fxmlLoader.getController();
            lobbyController.setPlayerNickname(playerNickname);
            lobbyController.setMessageManager(messageManager);
            lobbyController.setPrimaryStage(primaryStage);

            primaryStage.setTitle("KOSTKY - Lobby (" + playerNickname + ")");
            primaryStage.setScene(scene);
            primaryStage.show();

        } catch (NumberFormatException e) {
            showError("Port musí být číslo!");
        } catch (IOException e) {
            showError("Chyba při načítání lobby: " + e.getMessage());
        }
    }


    public static String getPlayerNickname() {
        return playerNickname;
    }

}
