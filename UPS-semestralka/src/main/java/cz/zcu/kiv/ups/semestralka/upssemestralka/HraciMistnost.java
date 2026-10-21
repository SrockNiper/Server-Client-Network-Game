package cz.zcu.kiv.ups.semestralka.upssemestralka;

import cz.zcu.kiv.ups.semestralka.upssemestralka.model.MessageManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HraciMistnost extends Application {
    private int[] diceValues;
    private MessageManager messageManager;

    public HraciMistnost() {
        super();
    }

    public HraciMistnost(int[] diceValues, MessageManager messageManager) {
        this.diceValues = diceValues;
        this.messageManager = messageManager;
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HraciMistnost.class.getResource("hraci_mistnost.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);


        HraciMistnostControl controller = fxmlLoader.getController();
        if (messageManager != null) {
            controller.setMessageManager(messageManager);
        }

        stage.setTitle("Hrací místnost");
        stage.setMinWidth(300);
        stage.setMinHeight(200);
        stage.setResizable(true);
        stage.setScene(scene);
        stage.show();
    }
}
