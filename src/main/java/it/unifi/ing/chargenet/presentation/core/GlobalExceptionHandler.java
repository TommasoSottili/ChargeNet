package it.unifi.ing.chargenet.presentation.core;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class GlobalExceptionHandler implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // Estraiamo la causa reale se l'errore è impacchettato (es. RuntimeException di riflessione)
        Throwable cause = e.getCause() != null ? e.getCause() : e;

        System.err.println("🚨 Errore non gestito catturato nel thread: " + t.getName());
        cause.printStackTrace();

        // JavaFX richiede che le modifiche all'interfaccia grafica avvengano sul thread principale
        Platform.runLater(() -> showExceptionDialog(cause));
    }

    private void showExceptionDialog(Throwable e) {
        AlertType type = AlertType.ERROR;
        String title = "Errore di Sistema";
        String header = "Si è verificato un problema imprevisto";

        // Se è un errore di business previsto dalle nostre regole (es. IllegalStateException per fondi)
        if (e instanceof IllegalStateException) {
            type = AlertType.WARNING;
            title = "Attenzione";
            header = "Operazione non consentita";
        }

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(e.getMessage() != null ? e.getMessage() : e.toString());

        alert.showAndWait();
    }
}