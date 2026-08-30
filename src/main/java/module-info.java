module ch.muhmenthaler.valdb {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    requires org.controlsfx.controls;
    requires java.sql;
    uses java.sql.Driver;

    opens ch.muhmenthaler.valdb.gui.controller to javafx.fxml;
    exports ch.muhmenthaler.valdb;
}