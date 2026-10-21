package cz.zcu.kiv.ups.semestralka.upssemestralka;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * JavaFX model class for player information in the TableView.
 * Standard JavaFX bean with StringProperty fields for data binding.
 */
public class PlayerInfo {
    private final StringProperty name;
    private final StringProperty score;
    private final StringProperty status;

    /**
     * Constructor with all fields
     * @param name Player nickname
     * @param score Player score as string
     * @param status Player status (e.g., "Waiting", "Playing", "Ready")
     */
    public PlayerInfo(String name, String score, String status) {
        this.name = new SimpleStringProperty(name);
        this.score = new SimpleStringProperty(score);
        this.status = new SimpleStringProperty(status);
    }



    /**
     * @return StringProperty for JavaFX TableView binding
     */
    public StringProperty nameProperty() {
        return name;
    }

    /**
     * @return Current name value
     */
    public String getName() {
        return name.get();
    }

    /**
     * Set the name value
     * @param name New name
     */
    public void setName(String name) {
        this.name.set(name);
    }



    /**
     * @return StringProperty for JavaFX TableView binding
     */
    public StringProperty scoreProperty() {
        return score;
    }

    /**
     * @return Current score value
     */
    public String getScore() {
        return score.get();
    }

    /**
     * Set the score value
     * @param score New score
     */
    public void setScore(String score) {
        this.score.set(score);
    }



    /**
     * @return StringProperty for JavaFX TableView binding
     */
    public StringProperty statusProperty() {
        return status;
    }

    /**
     * @return Current status value
     */
    public String getStatus() {
        return status.get();
    }

    /**
     * Set the status value
     * @param status New status
     */
    public void setStatus(String status) {
        this.status.set(status);
    }



    @Override
    public String toString() {
        return "PlayerInfo{" +
                "name='" + name.get() + '\'' +
                ", score='" + score.get() + '\'' +
                ", status='" + status.get() + '\'' +
                '}';
    }
}

