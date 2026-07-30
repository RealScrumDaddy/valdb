module ch.muhmenthaler.valdb {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires java.sql;

    opens ch.muhmenthaler.valdb to javafx.fxml;
    opens ch.muhmenthaler.valdb.gui.controller to javafx.fxml;
    exports ch.muhmenthaler.valdb;
}