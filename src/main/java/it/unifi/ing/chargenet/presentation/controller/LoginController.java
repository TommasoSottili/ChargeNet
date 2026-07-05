package it.unifi.ing.chargenet.presentation.controller;

import it.unifi.ing.chargenet.business.exceptions.AuthenticationException;
import it.unifi.ing.chargenet.business.services.AuthService;
import it.unifi.ing.chargenet.dao.interfaces.DaoFactory;
import it.unifi.ing.chargenet.domain.users.Role;
import it.unifi.ing.chargenet.domain.users.User;
import it.unifi.ing.chargenet.presentation.navigation.NavigationManager;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

public class LoginController {

    private AuthService authService;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField passwordVisibleField;
    @FXML private ImageView togglePasswordIcon;

    private boolean isPasswordVisible = false;

    @FXML
    public void initialize() {
        DaoFactory factory = NavigationManager.getDaoFactory();
        if (factory == null) {
            System.err.println("CRITICO: DaoFactory non inizializzata nel NavigationManager!");
        } else {
            this.authService = new AuthService(factory);
        }

        // Sincronizza i due campi password (toggle mostra/nascondi)
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    @FXML
    public void handleLogin(ActionEvent event) {
        String email = emailField.getText();
        String password = passwordField.getText();

        // Validazione minima UI (fail-fast)
        if (email == null || email.trim().isEmpty() ||
                password == null || password.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Campi Incompleti",
                    "Inserisci email e password.");
            return;
        }

        try {
            User user = authService.login(email, password);

            // Salva l'utente loggato nel "session context" (NavigationManager)
            NavigationManager.loginUser(user);

            // Smistamento per ruolo (mappa sul navigation diagram)
            routeByRole(user.getRole());

        } catch (AuthenticationException e) {
            // Credenziali errate: messaggio neutro, NON dire se è l'email o la password a sbagliare
            showAlert(Alert.AlertType.ERROR, "Accesso negato", e.getMessage());
            emailField.getStyleClass().add("input-error");
            passwordField.getStyleClass().add("input-error");

        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Errore di Sistema",
                    "Si è verificato un errore critico: " + e.getMessage());
        }
    }

    /** Indirizza alla home del ruolo corretto. */
    private void routeByRole(Role role) {
        switch (role) {
            case DRIVER          -> NavigationManager.navigateTo("DRIVER_MENU");
            case STATION_OPERATOR -> NavigationManager.navigateTo("OPERATOR_MENU");
            case ENERGY_MANAGER  -> NavigationManager.navigateTo("MANAGER_MENU");
            default -> showAlert(Alert.AlertType.ERROR, "Ruolo sconosciuto",
                    "Il ruolo dell'utente non è gestito: " + role);
        }
    }

    @FXML
    public void togglePasswordVisibility(MouseEvent event) {
        isPasswordVisible = !isPasswordVisible;

        passwordField.setVisible(!isPasswordVisible);
        passwordField.setManaged(!isPasswordVisible);
        passwordVisibleField.setVisible(isPasswordVisible);
        passwordVisibleField.setManaged(isPasswordVisible);

        try {
            String iconPath = isPasswordVisible
                    ? "/it/unifi/ing/chargenet/presentation/images/hide.png"
                    : "/it/unifi/ing/chargenet/presentation/images/view.png";
            togglePasswordIcon.setImage(new Image(getClass().getResourceAsStream(iconPath)));
        } catch (Exception e) {
            System.err.println("Impossibile caricare l'icona dell'occhio: " + e.getMessage());
        }
    }

    @FXML
    public void goToRegister(ActionEvent event) {
        NavigationManager.navigateTo("REGISTER");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}