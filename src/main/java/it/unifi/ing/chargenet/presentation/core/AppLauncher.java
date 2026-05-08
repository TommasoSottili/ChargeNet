package it.unifi.ing.chargenet.presentation.core;

import javafx.application.Application;
import javafx.stage.Stage;

public class AppLauncher extends Application {

    // Reso pubblico e statico come da tuo diagramma
    public static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        // TODO: Salvare lo stage, impostare il GlobalExceptionHandler
        // e navigare verso la prima schermata tramite NavigationManager
    }

    @Override
    public void stop() {
        // TODO: Chiusura pulita delle risorse (es. spegnere il thread GridMonitor)
    }

    public static void main(String[] args) {
        launch(args);
    }
}