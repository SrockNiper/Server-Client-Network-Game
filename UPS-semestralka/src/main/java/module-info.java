module cz.zcu.kiv.ups.semestralka.upssemestralka {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;

    opens cz.zcu.kiv.ups.semestralka.upssemestralka to javafx.fxml;
    exports cz.zcu.kiv.ups.semestralka.upssemestralka;
    exports cz.zcu.kiv.ups.semestralka.upssemestralka.model;
    opens cz.zcu.kiv.ups.semestralka.upssemestralka.model to javafx.fxml;
}