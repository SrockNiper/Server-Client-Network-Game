package cz.zcu.kiv.ups.semestralka.upssemestralka;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.Komunikace;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Login extends Application {

    private static Komunikace komunikaceInstance;

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Set the Komunikace instance for shutdown handling
     */
    public static void setKomunikace(Komunikace komunikace) {
        komunikaceInstance = komunikace;
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Login.class.getResource("login.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1024, 768);

        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        primaryStage.setTitle("Přihlášení");
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(768);
        primaryStage.setResizable(true);
        primaryStage.setScene(scene);

        LoginController controller = fxmlLoader.getController();
        controller.setPrimaryStage(primaryStage);

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("[Shutdown] Application closing...");
            if (komunikaceInstance != null) {
                try {

                    System.out.println("[Shutdown] Sending disconnect command to server");
                    komunikaceInstance.send(0, "");

                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("[Shutdown] Error during disconnect: " + e.getMessage());
                }


                komunikaceInstance.close();
            }
            System.out.println("[Shutdown] Goodbye!");
        });

        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("[Application] stop() called");

        if (komunikaceInstance != null) {
            komunikaceInstance.close();
        }

        super.stop();
    }
}

